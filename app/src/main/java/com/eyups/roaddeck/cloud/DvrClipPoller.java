package com.eyups.roaddeck.cloud;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.eyups.roaddeck.dvr.DvrClipStore;
import com.eyups.roaddeck.dvr.DvrController;

import org.json.JSONObject;

import java.io.File;
import java.util.Collections;
import java.util.List;

/** DVR klip isteklerini long-poll eder; buluta veya telefonun yerel alıcısına yükler. */
final class DvrClipPoller {

    private static final String TAG = "DvrClipPoller";
    private static final int POLL_WAIT_S = 25;
    private static final int POLL_READ_TIMEOUT_MS = 35_000;

    private final Context context;
    private final CloudConfig config;
    private final CloudHttp http;
    private final Runnable onAuthRevoked;
    private final CloudBackoffPolicy backoff = new CloudBackoffPolicy(5_000L);

    private HandlerThread thread;
    private Handler handler;
    private volatile boolean running;

    DvrClipPoller(Context context, CloudConfig config, CloudHttp http,
            Runnable onAuthRevoked) {
        this.context = context.getApplicationContext();
        this.config = config;
        this.http = http;
        this.onAuthRevoked = onAuthRevoked;
    }

    void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new HandlerThread("RoadDeckDvrPoll");
        thread.start();
        handler = new Handler(thread.getLooper());
        handler.post(this::poll);
    }

    void stop() {
        running = false;
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        if (thread != null) {
            thread.quitSafely();
            thread = null;
        }
        handler = null;
    }

    private void poll() {
        if (!running) {
            return;
        }
        String token = config.token();
        if (token == null || token.isEmpty()) {
            schedule(backoff.nextDelayMs());
            return;
        }
        long delay;
        try {
            CloudHttp.Response response = http.getJson(
                    "/api/v1/car/dvr/requests?wait=" + POLL_WAIT_S,
                    token,
                    POLL_READ_TIMEOUT_MS);
            if (response.code == 200) {
                handleRequest(response.body);
                backoff.reset();
                delay = 0L;
            } else if (response.code == 204) {
                backoff.reset();
                delay = 0L;
            } else if (response.code == 401) {
                running = false;
                onAuthRevoked.run();
                return;
            } else {
                delay = backoff.nextDelayMs();
            }
        } catch (Exception error) {
            delay = backoff.nextDelayMs();
        }
        schedule(delay);
    }

    private void handleRequest(String body) {
        try {
            JSONObject request = new JSONObject(body);
            String id = request.optString("id", "");
            String clipKey = request.optString("clip_key", "");
            String camera = request.optString("camera", "auto");
            String transport = request.optString("transport", "cloud");
            if (id.isEmpty() || clipKey.isEmpty()) {
                return;
            }
            File file = findClipFile(clipKey, camera);
            if (file == null) {
                report(id, "failed", "clip_missing", 0L);
                return;
            }
            if ("local".equals(transport)) {
                String localIp = request.optString("local_ip", "");
                int localPort = request.optInt("local_port", 0);
                String localToken = request.optString("local_token", "");
                if (localIp.isEmpty() || localPort <= 0 || localToken.isEmpty()) {
                    report(id, "failed", "local_target_missing", file.length());
                    return;
                }
                String url = "http://" + localIp + ":" + localPort + "/dvr/upload";
                try {
                    CloudHttp.Response response = http.postLocalVideo(
                            url, id, localToken, file);
                    if (response.code >= 200 && response.code < 300) {
                        report(id, "uploaded", "", file.length());
                    } else {
                        report(id, "failed", "local_http_" + response.code, file.length());
                    }
                } catch (Exception error) {
                    report(id, "failed", "local_error", file.length());
                }
            } else {
                try {
                    CloudHttp.Response response = http.postVideoStreaming(
                            "/api/v1/car/dvr/requests/" + id + "/clip",
                            config.token(),
                            file,
                            "video/mp4");
                    if (response.code < 200 || response.code >= 300) {
                        report(id, "failed", "cloud_http_" + response.code, file.length());
                    }
                } catch (Exception error) {
                    report(id, "failed", "cloud_error", file.length());
                }
            }
        } catch (Exception error) {
            Log.w(TAG, "DVR isteği işlenemedi", error);
        }
    }

    private File findClipFile(String clipKey, String camera) {
        List<DvrClipStore.Clip> clips = DvrClipStore.list(
                DvrController.get(context).getInternalDir(),
                Collections.emptySet());
        DvrClipStore.Clip match = null;
        for (DvrClipStore.Clip clip : clips) {
            if (clipKey.equals(clip.base)) {
                match = clip;
                break;
            }
        }
        if (match == null) {
            return null;
        }
        if ("mosaic".equals(camera)) {
            return match.mosaic;
        }
        if ("front".equals(camera) || "right".equals(camera)
                || "left".equals(camera) || "rear".equals(camera)) {
            int index = "front".equals(camera) ? 0
                    : "right".equals(camera) ? 1
                    : "left".equals(camera) ? 2 : 3;
            if (index < match.camFiles.length) {
                File selected = match.camFiles[index];
                if (selected != null) {
                    return selected;
                }
            }
        }
        return match.mosaic != null ? match.mosaic : match.primary();
    }

    private void report(String requestId, String status, String error, long sizeBytes) {
        try {
            JSONObject body = new JSONObject();
            body.put("status", status);
            if (error != null && !error.isEmpty()) {
                body.put("error", error);
            }
            if (sizeBytes > 0) {
                body.put("size_bytes", sizeBytes);
            }
            http.postJson(
                    "/api/v1/car/dvr/requests/" + requestId + "/result",
                    config.token(),
                    body,
                    4096);
        } catch (Exception failure) {
            Log.w(TAG, "DVR sonucu bildirilemedi", failure);
        }
    }

    private void schedule(long delayMs) {
        if (running && handler != null) {
            handler.postDelayed(this::poll, Math.max(0L, delayMs));
        }
    }
}
