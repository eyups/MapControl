package com.mapcontrol.cloud;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.mapcontrol.tts.MapControlSpeech;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Sunucudan gelen allowlist komutlarını araçta uygular. */
final class CommandExecutor {

    private static final class Pending {
        final CommandExecutor owner;
        final String commandId;
        final String type;
        final JSONObject payload;
        final String message;

        Pending(CommandExecutor owner, String commandId, String type,
                JSONObject payload, String message) {
            this.owner = owner;
            this.commandId = commandId;
            this.type = type;
            this.payload = payload;
            this.message = message;
        }
    }

    private static volatile Pending pending;

    private final Context appContext;
    private final CloudConfig config;
    private final CloudHttp http;
    private final TelemetryUploader telemetry;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService resultExecutor = Executors.newSingleThreadExecutor();

    CommandExecutor(Context context, CloudConfig config, CloudHttp http,
            TelemetryUploader telemetry) {
        this.appContext = context.getApplicationContext();
        this.config = config;
        this.http = http;
        this.telemetry = telemetry;
    }

    void execute(JSONObject command) {
        if (command == null) {
            return;
        }
        String commandId = command.optString("id", "");
        String type = command.optString("type", "");
        if (commandId.isEmpty()) {
            return;
        }
        if (!"ping".equals(type) && !"nav.navigate".equals(type)
                && !"tts.say".equals(type)) {
            sendResult(commandId, "rejected", reason("unsupported_type"));
            return;
        }
        if (telemetry.powerMode() != 2) {
            sendResult(commandId, "rejected", reason("power_off"));
            return;
        }
        if ("ping".equals(type)) {
            JSONObject detail = new JSONObject();
            try {
                detail.put("ok", true);
            } catch (Exception ignored) {
            }
            sendResult(commandId, "done", detail);
            return;
        }
        if (telemetry.speedKmh() > 0) {
            sendResult(commandId, "rejected", reason("moving"));
            return;
        }
        JSONObject payload = command.optJSONObject("payload");
        if (payload == null) {
            sendResult(commandId, "failed", reason("missing_payload"));
            return;
        }
        String message;
        if ("tts.say".equals(type)) {
            message = payload.optString("text", "").trim();
            if (message.isEmpty() || message.length() > 200) {
                sendResult(commandId, "failed", reason("invalid_text"));
                return;
            }
        } else {
            String name = payload.optString("name", "").trim();
            double lat = payload.optDouble("lat", Double.NaN);
            double lon = payload.optDouble("lon", Double.NaN);
            if (name.isEmpty() || Double.isNaN(lat) || Double.isNaN(lon)
                    || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                sendResult(commandId, "failed", reason("invalid_target"));
                return;
            }
            message = name;
        }
        if (pending != null) {
            sendResult(commandId, "rejected", reason("busy"));
            return;
        }
        pending = new Pending(this, commandId, type, payload, message);
        mainHandler.post(() -> {
            try {
                Intent intent = new Intent(appContext, CloudCommandActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                appContext.startActivity(intent);
            } catch (Exception error) {
                Pending active = pending;
                pending = null;
                if (active != null) {
                    sendResult(active.commandId, "failed", reason("confirmation_failed"));
                }
            }
        });
    }

    static PendingView pendingView() {
        Pending active = pending;
        if (active == null) {
            return null;
        }
        return new PendingView(active.type, active.message);
    }

    static void resolvePending(boolean accepted) {
        Pending active = pending;
        pending = null;
        if (active != null) {
            active.owner.finishPending(active, accepted);
        }
    }

    private void finishPending(Pending active, boolean accepted) {
        if (!accepted) {
            sendResult(active.commandId, "rejected", reason("user_rejected"));
            return;
        }
        try {
            if ("tts.say".equals(active.type)) {
                MapControlSpeech.speak(appContext, active.message);
                JSONObject detail = new JSONObject();
                detail.put("ok", true);
                detail.put("spoken", true);
                sendResult(active.commandId, "done", detail);
                return;
            }
            launchNavigation(active.payload);
            JSONObject detail = new JSONObject();
            detail.put("ok", true);
            detail.put("target", active.message);
            sendResult(active.commandId, "done", detail);
        } catch (Exception error) {
            sendResult(active.commandId, "failed", reason("execution_failed"));
        }
    }

    private void launchNavigation(JSONObject payload) {
        double lat = payload.optDouble("lat");
        double lon = payload.optDouble("lon");
        String name = payload.optString("name", "");
        String app = payload.optString("app", "");
        Uri uri;
        if ("yandex".equals(app)) {
            uri = Uri.parse("yandexnavi://build_route_on_map?lat_to=" + lat
                    + "&lon_to=" + lon);
        } else {
            uri = Uri.parse("google.navigation:q=" + lat + "," + lon + "&mode=d");
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            appContext.startActivity(intent);
        } catch (Exception firstFailure) {
            Uri fallback = Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + ","
                    + lon + "(" + Uri.encode(name) + ")");
            Intent geo = new Intent(Intent.ACTION_VIEW, fallback);
            geo.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appContext.startActivity(geo);
        }
    }

    private void sendResult(String commandId, String status, JSONObject detail) {
        resultExecutor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("status", status);
                body.put("detail", detail);
                http.postJson("/api/v1/car/commands/" + commandId + "/result",
                        config.token(), body, 4096);
            } catch (Exception ignored) {
                // Sonraki poll 401 görürse oturum kapanır; sonuç tekrarı sunucuda idempotent.
            }
        });
    }

    private static JSONObject reason(String value) {
        JSONObject detail = new JSONObject();
        try {
            detail.put("ok", false);
            detail.put("reason", value);
        } catch (Exception ignored) {
        }
        return detail;
    }

    void stop() {
        pending = null;
        resultExecutor.shutdownNow();
    }

    static final class PendingView {
        final String type;
        final String message;

        PendingView(String type, String message) {
            this.type = type;
            this.message = message;
        }
    }
}
