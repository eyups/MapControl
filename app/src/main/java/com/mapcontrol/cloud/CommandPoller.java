package com.mapcontrol.cloud;

import android.os.Handler;
import android.os.HandlerThread;

import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.Set;

/** Araç token'ıyla 25 sn long-poll; komutları tek kez yürütür. */
final class CommandPoller {

    private static final int MAX_SEEN = 50;
    private static final int POLL_WAIT_S = 25;
    private static final int POLL_READ_TIMEOUT_MS = 35_000;

    private final CloudConfig config;
    private final CloudHttp http;
    private final CommandExecutor executor;
    private final Runnable onAuthRevoked;
    private final CloudBackoffPolicy backoff = new CloudBackoffPolicy(5_000L);
    private final Set<String> seen = new LinkedHashSet<>();

    private HandlerThread thread;
    private Handler handler;
    private volatile boolean running;

    CommandPoller(CloudConfig config, CloudHttp http, CommandExecutor executor,
            Runnable onAuthRevoked) {
        this.config = config;
        this.http = http;
        this.executor = executor;
        this.onAuthRevoked = onAuthRevoked;
    }

    void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new HandlerThread("MapControlCommandPoll");
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
                    "/api/v1/car/commands?wait=" + POLL_WAIT_S, token,
                    POLL_READ_TIMEOUT_MS);
            if (response.code == 200) {
                handleCommand(response.body);
                backoff.reset();
                delay = 0L;
            } else if (response.code == 204) {
                backoff.reset();
                delay = 0L;
            } else if (response.code == 409) {
                delay = 1_000L;
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

    private void handleCommand(String body) {
        try {
            JSONObject command = new JSONObject(body);
            String id = command.optString("id", "");
            if (id.isEmpty() || !remember(id)) {
                return;
            }
            executor.execute(command);
        } catch (Exception ignored) {
            // Bozuk komut sonraki poll'u durdurmaz.
        }
    }

    private boolean remember(String id) {
        synchronized (seen) {
            if (seen.contains(id)) {
                return false;
            }
            seen.add(id);
            while (seen.size() > MAX_SEEN) {
                String oldest = seen.iterator().next();
                seen.remove(oldest);
            }
            return true;
        }
    }

    private void schedule(long delayMs) {
        if (running && handler != null) {
            handler.postDelayed(this::poll, Math.max(0L, delayMs));
        }
    }
}
