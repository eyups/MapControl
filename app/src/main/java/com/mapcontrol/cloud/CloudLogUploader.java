package com.mapcontrol.cloud;

import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

/** Log sekmesinden elle tetiklenen, sınırlı ve redakte edilmiş log yükleme. */
final class CloudLogUploader {

    private static final int MAX_LOG_BYTES = 256 * 1024;
    private static final int MAX_REQUEST_BYTES = 5 * 1024 * 1024;
    private static final Pattern DEVICE_TOKEN = Pattern.compile(
            "(?i)(authorization:\\s*device\\s+)\\S+");
    private static final Pattern BEARER = Pattern.compile(
            "(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]+");
    private static final Pattern TOKEN_FIELD = Pattern.compile(
            "(?i)(\"?token\"?\\s*[:=]\\s*\"?)[^\\s\",}]+");

    interface Callback {
        void onResult(boolean ok, String message);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    void upload(CloudConfig config, CloudHttp http, Callback callback) {
        new Thread(() -> {
            try {
                byte[] payload = buildPayload();
                String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                        .format(new Date());
                CloudHttp.Response response = http.postFile(
                        "/api/v1/car/logs", config.token(),
                        "mapcontrol-" + stamp + ".txt", "text/plain",
                        payload, MAX_REQUEST_BYTES);
                boolean ok = response.code == 201;
                String message = ok ? "ok" : "http_" + response.code;
                mainHandler.post(() -> callback.onResult(ok, message));
            } catch (Exception error) {
                mainHandler.post(() -> callback.onResult(false, "error"));
            }
        }, "MapControlLogUpload").start();
    }

    private byte[] buildPayload() throws Exception {
        Process process = null;
        String raw = "";
        try {
            process = new ProcessBuilder("logcat", "-d", "-t", "1500").start();
            raw = readLimited(process.getInputStream(), MAX_LOG_BYTES);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        String redacted = redact(raw);
        String header = "RoadDeck cloud log\n"
                + "generated=" + new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                .format(new Date()) + "\n"
                + "note=manual upload; bounded at 256 KB\n\n";
        return (header + redacted).getBytes(StandardCharsets.UTF_8);
    }

    private static String readLimited(InputStream input, int maxBytes) throws Exception {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096];
            int read;
            int total = 0;
            while ((read = stream.read(chunk)) != -1) {
                total += read;
                if (total > maxBytes) {
                    break;
                }
                output.write(chunk, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String redact(String value) {
        String result = DEVICE_TOKEN.matcher(value).replaceAll("$1[REDACTED]");
        result = BEARER.matcher(result).replaceAll("$1[REDACTED]");
        result = TOKEN_FIELD.matcher(result).replaceAll("$1[REDACTED]");
        return result;
    }
}
