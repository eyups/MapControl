package com.mapcontrol.cloud;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

/** Düz HTTPS/JSON istemcisi; TLS doğrulaması varsayılan Android davranışındadır. */
final class CloudHttp {

    static final int CONNECT_TIMEOUT_MS = 10_000;
    static final int READ_TIMEOUT_MS = 30_000;
    static final int MAX_RESPONSE_BYTES = 64 * 1024;

    static final class Response {
        final int code;
        final String body;

        Response(int code, String body) {
            this.code = code;
            this.body = body == null ? "" : body;
        }
    }

    Response postJson(String path, String deviceToken, JSONObject body, int maxRequestBytes)
            throws IOException {
        return postJson(path, deviceToken, body, maxRequestBytes, READ_TIMEOUT_MS);
    }

    Response postJson(String path, String deviceToken, JSONObject body,
            int maxRequestBytes, int readTimeoutMs) throws IOException {
        if (path == null || !path.startsWith("/api/v1/") || path.contains("..")) {
            throw new IOException("invalid_path");
        }
        URL url = new URL(CloudConfig.BASE_URL + path);
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IOException("https_required");
        }

        byte[] raw = body == null ? new byte[0]
                : body.toString().getBytes(StandardCharsets.UTF_8);
        if (raw.length > maxRequestBytes) {
            throw new IOException("request_too_large");
        }

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        if (!(connection instanceof HttpsURLConnection)) {
            connection.disconnect();
            throw new IOException("https_required");
        }
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(readTimeoutMs);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("User-Agent", "MapControl-Cloud/1");
            if (deviceToken != null && !deviceToken.isEmpty()) {
                connection.setRequestProperty("Authorization", "Device " + deviceToken);
            }
            connection.setFixedLengthStreamingMode(raw.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(raw);
            }

            int code = connection.getResponseCode();
            InputStream input = code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String responseBody = readLimited(input);
            return new Response(code, responseBody);
        } finally {
            connection.disconnect();
        }
    }

    private static String readLimited(InputStream input) throws IOException {
        if (input == null) {
            return "";
        }
        try (InputStream stream = input; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096];
            int read;
            int total = 0;
            while ((read = stream.read(chunk)) != -1) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new IOException("response_too_large");
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8.name());
        }
    }
}
