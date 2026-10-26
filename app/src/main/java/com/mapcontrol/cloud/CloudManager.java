package com.mapcontrol.cloud;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import com.mapcontrol.BuildConfig;

import org.json.JSONObject;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Elle başlatılan bulut oturumu. Boot'ta veya uygulama açılışında ağ başlatmaz.
 */
public final class CloudManager {

    public enum State { STOPPED, PAIRING, RUNNING, ERROR }

    public interface Listener {
        void onCloudStateChanged();
    }

    public interface PairCallback {
        void onSuccess();

        void onError(String message);
    }

    private static final long HEARTBEAT_INTERVAL_MS = 30_000L;
    private static final long TRIP_SCAN_INTERVAL_MS = 60_000L;
    private static final long HEALTH_INTERVAL_MS = 5 * 60_000L;
    private static final long LOOP_INTERVAL_MS = 1_000L;

    private static volatile CloudManager instance;

    private final Context appContext;
    private final CloudConfig config;
    private final CloudHttp http = new CloudHttp();
    private final Handler mainHandler = new Handler(android.os.Looper.getMainLooper());
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private final CloudBackoffPolicy backoff = new CloudBackoffPolicy();

    private HandlerThread workerThread;
    private Handler worker;
    private volatile boolean running;
    private volatile boolean heartbeatOk;
    private volatile int uploadIntervalSec = 15;
    private volatile boolean serverShareLocation;
    private volatile long nextHeartbeatAt;
    private volatile long nextTelemetryAt;
    private volatile long nextTelemetrySendAt;
    private volatile long nextTripsAt;
    private volatile long nextAttemptAt;
    private volatile State state = State.STOPPED;
    private volatile String detail = "";
    private TelemetryUploader telemetry;
    private TripUploader tripUploader;
    private CommandExecutor commandExecutor;
    private CommandPoller commandPoller;
    private HealthUploader healthUploader;
    private long nextHealthAt;

    private final Runnable loopRunnable = this::loop;

    private CloudManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.config = new CloudConfig(this.appContext);
    }

    public static CloudManager get(Context context) {
        CloudManager local = instance;
        if (local == null) {
            synchronized (CloudManager.class) {
                local = instance;
                if (local == null) {
                    local = new CloudManager(context);
                    instance = local;
                }
            }
        }
        return local;
    }

    public void addListener(Listener listener) {
        if (listener == null) {
            return;
        }
        listeners.add(listener);
        mainHandler.post(listener::onCloudStateChanged);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public boolean isPaired() {
        return config.isPaired();
    }

    public boolean isRunning() {
        return running;
    }

    public State getState() {
        return state;
    }

    public String getDetail() {
        return detail;
    }

    public void pair(String code, PairCallback callback) {
        String normalized = code == null
                ? "" : code.trim().toUpperCase(Locale.US);
        if (normalized.length() != 8) {
            callback.onError("invalid_code");
            return;
        }
        if (running) {
            stop();
        }
        setState(State.PAIRING, "");
        Handler target = ensureWorker();
        target.post(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("code", normalized);
                body.put("kind", "car");
                body.put("name", "MapControl");
                body.put("app_version", BuildConfig.VERSION_NAME);
                CloudHttp.Response response = http.postJson(
                        "/api/v1/devices/pair", null, body, 16 * 1024);
                if (response.code == 201) {
                    JSONObject parsed = new JSONObject(response.body);
                    String token = parsed.optString("token", "");
                    String deviceId = parsed.optString("device_id", "");
                    String expiresAt = parsed.optString("token_expires_at", "");
                    if (token.isEmpty()) {
                        throw new Exception("missing_token");
                    }
                    config.savePair(deviceId, token, expiresAt);
                    setState(State.STOPPED, "Eşleştirildi");
                    mainHandler.post(callback::onSuccess);
                    return;
                }
                String message = responseError(response, "pair_failed");
                setState(State.ERROR, message);
                mainHandler.post(() -> callback.onError(message));
            } catch (Exception error) {
                setState(State.ERROR, "pair_failed");
                mainHandler.post(() -> callback.onError("pair_failed"));
            }
        });
    }

    public void start() {
        if (!config.isPaired()) {
            setState(State.ERROR, "unpaired");
            return;
        }
        if (running) {
            return;
        }
        running = true;
        heartbeatOk = false;
        backoff.reset();
        nextAttemptAt = 0L;
        nextHeartbeatAt = 0L;
        nextTelemetryAt = 0L;
        nextTelemetrySendAt = 0L;
        nextTripsAt = 0L;
        telemetry = new TelemetryUploader(appContext, config);
        tripUploader = new TripUploader(appContext, config, http);
        commandExecutor = new CommandExecutor(appContext, config, http, telemetry);
        commandPoller = new CommandPoller(config, http, commandExecutor, this::onCommandAuthRevoked);
        healthUploader = new HealthUploader(appContext, config, http);
        nextHealthAt = 0L;
        setState(State.RUNNING, "Başlatılıyor");
        Handler target = ensureWorker();
        target.post(() -> {
            if (!running) {
                return;
            }
            if (telemetry != null) {
                telemetry.start();
            }
            if (commandPoller != null) {
                commandPoller.start();
            }
            loop();
        });
    }

    public void stop() {
        running = false;
        heartbeatOk = false;
        if (worker != null) {
            worker.removeCallbacksAndMessages(null);
        }
        TelemetryUploader activeTelemetry = telemetry;
        telemetry = null;
        if (activeTelemetry != null) {
            activeTelemetry.stop();
        }
        if (commandPoller != null) {
            commandPoller.stop();
            commandPoller = null;
        }
        if (commandExecutor != null) {
            commandExecutor.stop();
            commandExecutor = null;
        }
        healthUploader = null;
        tripUploader = null;
        setState(State.STOPPED, "Durduruldu");
    }

    private synchronized Handler ensureWorker() {
        if (workerThread == null) {
            workerThread = new HandlerThread("MapControlCloud");
            workerThread.start();
            worker = new Handler(workerThread.getLooper());
        }
        return worker;
    }

    private void loop() {
        if (!running) {
            return;
        }
        if (telemetry != null) {
            JSONObject immediateFinal = telemetry.captureFinal(System.currentTimeMillis());
            if (immediateFinal != null) {
                sendFinal(immediateFinal);
            }
        }
        long now = SystemClock.elapsedRealtime();
        if (now >= nextHeartbeatAt && now >= nextAttemptAt) {
            if (sendHeartbeat()) {
                now = SystemClock.elapsedRealtime();
                nextHeartbeatAt = now + HEARTBEAT_INTERVAL_MS;
                nextTelemetryAt = now;
                nextTelemetrySendAt = now;
                nextTripsAt = now;
            }
        }
        now = SystemClock.elapsedRealtime();
        if (now >= nextTelemetryAt) {
            captureTelemetry();
            nextTelemetryAt = now + uploadIntervalSec * 1000L;
        }
        if (heartbeatOk && now >= nextTelemetrySendAt) {
            boolean sent = sendPendingTelemetry();
            nextTelemetrySendAt = now + (sent ? 6_000L : 15_000L);
        }
        if (heartbeatOk && now >= nextTripsAt) {
            sendTrips();
            nextTripsAt = now + TRIP_SCAN_INTERVAL_MS;
        }
        if (heartbeatOk && now >= nextHealthAt) {
            sendHealth();
            nextHealthAt = now + HEALTH_INTERVAL_MS;
        }
        schedule(LOOP_INTERVAL_MS);
    }

    private boolean sendHeartbeat() {
        if (!running) {
            return false;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("ts_ms", System.currentTimeMillis());
            body.put("app_version", BuildConfig.VERSION_NAME);
            int powerMode = telemetry == null ? -1 : telemetry.powerMode();
            if (powerMode >= 0 && powerMode <= 10) {
                body.put("power_mode", powerMode);
            }
            CloudHttp.Response response = http.postJson(
                    "/api/v1/car/heartbeat", config.token(), body, 16 * 1024);
            if (response.code == 200) {
                applyConfig(response.body);
                heartbeatOk = true;
                backoff.reset();
                nextAttemptAt = 0L;
                setState(State.RUNNING, "Çalışıyor");
                return true;
            }
            if (response.code == 401) {
                revoke("Cihaz iptal edildi; yeniden eşleştirin.");
                return false;
            }
            networkFailure("Sunucu yanıtı: " + response.code);
            return false;
        } catch (Exception error) {
            networkFailure("Sunucuya ulaşılamadı");
            return false;
        }
    }

    private void captureTelemetry() {
        if (!running) {
            return;
        }
        TelemetryUploader current = telemetry;
        if (current == null) {
            return;
        }
        JSONObject finalPayload = current.capture(
                System.currentTimeMillis(), serverShareLocation);
        if (finalPayload != null) {
            sendFinal(finalPayload);
        }
    }

    private void sendFinal(JSONObject finalPayload) {
        TelemetryUploader current = telemetry;
        if (current == null) {
            return;
        }
        try {
            http.postJson("/api/v1/car/telemetry", config.token(),
                    finalPayload, 16 * 1024, 3_000);
        } catch (Exception ignored) {
            // final fire-and-forget: kontak kapanışında tek deneme.
        } finally {
            current.markFinalSent();
        }
    }

    private boolean sendPendingTelemetry() {
        TelemetryUploader current = telemetry;
        if (current == null) {
            return false;
        }
        JSONObject payload = current.pendingPayload();
        if (payload == null) {
            return true;
        }
        try {
            CloudHttp.Response response = http.postJson(
                    "/api/v1/car/telemetry", config.token(), payload, 16 * 1024);
            if (response.code == 204) {
                current.removePending();
                setState(State.RUNNING, "Çalışıyor");
                return true;
            }
            if (response.code == 401) {
                revoke("Cihaz iptal edildi; yeniden eşleştirin.");
                return false;
            }
            if (response.code == 400 || response.code == 413) {
                current.removePending();
                setState(State.RUNNING, "Telemetri reddedildi: " + response.code);
                return true;
            }
            setState(State.RUNNING, "Telemetri gönderilemedi: " + response.code);
            return false;
        } catch (Exception error) {
            setState(State.RUNNING, "Telemetri bağlantısı bekleniyor");
            return false;
        }
    }

    private void sendHealth() {
        HealthUploader current = healthUploader;
        if (current == null) {
            return;
        }
        try {
            CloudHttp.Response response = current.upload();
            if (response.code == 401) {
                revoke("Cihaz iptal edildi; yeniden eşleştirin.");
            }
        } catch (Exception ignored) {
            // Sağlık raporu sonraki turda tekrar denenir.
        }
    }

    private void sendTrips() {
        if (!running) {
            return;
        }
        TripUploader current = tripUploader;
        if (current == null) {
            return;
        }
        TripUploader.Result result = current.uploadPending();
        if (result == TripUploader.Result.AUTH_FAILED) {
            revoke("Cihaz iptal edildi; yeniden eşleştirin.");
        } else if (result == TripUploader.Result.ERROR) {
            setState(State.RUNNING, "Sürüşler bekliyor");
        }
    }

    private void applyConfig(String body) {
        try {
            JSONObject root = new JSONObject(body);
            JSONObject configObject = root.optJSONObject("config");
            if (configObject == null) {
                return;
            }
            uploadIntervalSec = CloudTelemetryPolicy.uploadIntervalSeconds(
                    configObject.optInt("upload_interval_s", 15));
            serverShareLocation = configObject.optBoolean("share_location", false);
        } catch (Exception ignored) {
            uploadIntervalSec = 15;
            serverShareLocation = false;
        }
    }

    private void networkFailure(String message) {
        heartbeatOk = false;
        nextAttemptAt = SystemClock.elapsedRealtime() + backoff.nextDelayMs();
        setState(State.RUNNING, message);
    }

    private void onCommandAuthRevoked() {
        revoke("Cihaz iptal edildi; yeniden eşleştirin.");
    }

    private void revoke(String message) {
        running = false;
        heartbeatOk = false;
        config.clearPair();
        TelemetryUploader activeTelemetry = telemetry;
        telemetry = null;
        if (activeTelemetry != null) {
            activeTelemetry.stop();
        }
        if (commandPoller != null) {
            commandPoller.stop();
            commandPoller = null;
        }
        if (commandExecutor != null) {
            commandExecutor.stop();
            commandExecutor = null;
        }
        healthUploader = null;
        tripUploader = null;
        if (worker != null) {
            worker.removeCallbacksAndMessages(null);
        }
        setState(State.ERROR, message);
    }

    private void schedule(long delayMs) {
        if (running && worker != null) {
            worker.postDelayed(loopRunnable, delayMs);
        }
    }

    private void setState(State newState, String newDetail) {
        state = newState;
        detail = newDetail == null ? "" : newDetail;
        mainHandler.post(() -> {
            for (Listener listener : listeners) {
                listener.onCloudStateChanged();
            }
        });
    }

    private static String responseError(CloudHttp.Response response, String fallback) {
        try {
            JSONObject root = new JSONObject(response.body);
            JSONObject error = root.optJSONObject("error");
            if (error != null) {
                String message = error.optString("message", "");
                if (!message.isEmpty()) {
                    return message;
                }
            }
        } catch (Exception ignored) {
            // Ayrıntı yoksa genel mesaj kullanılır.
        }
        return fallback;
    }
}
