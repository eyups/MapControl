package com.mapcontrol.cloud;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

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

    Response getJson(String path, String deviceToken, int readTimeoutMs) throws IOException {
        if (path == null || !path.startsWith("/api/v1/") || path.contains("..")) {
            throw new IOException("invalid_path");
        }
        URL url = new URL(CloudConfig.BASE_URL + path);
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IOException("https_required");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        if (!(connection instanceof HttpsURLConnection)) {
            connection.disconnect();
            throw new IOException("https_required");
        }
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(readTimeoutMs);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("User-Agent", "RoadDeck-Cloud/1");
            if (deviceToken != null && !deviceToken.isEmpty()) {
                connection.setRequestProperty("Authorization", "Device " + deviceToken);
            }
            int code = connection.getResponseCode();
            InputStream input = code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            return new Response(code, readLimited(input));
        } finally {
            connection.disconnect();
        }
    }

    Response postFile(String path, String deviceToken, String fileName,
            String contentType, byte[] fileBytes, int maxRequestBytes) throws IOException {
        if (path == null || !path.startsWith("/api/v1/") || path.contains("..")) {
            throw new IOException("invalid_path");
        }
        if (fileBytes == null || fileBytes.length > maxRequestBytes) {
            throw new IOException("request_too_large");
        }
        URL url = new URL(CloudConfig.BASE_URL + path);
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IOException("https_required");
        }
        String boundary = "----MapControl" + UUID.randomUUID().toString().replace("-", "");
        String safeName = fileName == null ? "log.txt" : fileName.replace("\"", "");
        ByteArrayOutputStream multipart = new ByteArrayOutputStream();
        multipart.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        multipart.write(("Content-Disposition: form-data; name=\"file\"; filename=\""
                + safeName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        multipart.write(("Content-Type: " + (contentType == null
                ? "application/octet-stream" : contentType) + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        multipart.write(fileBytes);
        multipart.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        byte[] raw = multipart.toByteArray();
        if (raw.length > maxRequestBytes + 64 * 1024) {
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
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Content-Type",
                    "multipart/form-data; boundary=" + boundary);
            connection.setRequestProperty("User-Agent", "RoadDeck-Cloud/1");
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
            return new Response(code, readLimited(input));
        } finally {
            connection.disconnect();
        }
    }

    Response postParkingPhotos(String path, String deviceToken, long tsMs,
            String appVersion, Double lat, Double lon, byte[][] images,
            int maxRequestBytes) throws IOException {
        if (path == null || !path.startsWith("/api/v1/") || path.contains("..")) {
            throw new IOException("invalid_path");
        }
        if (images == null || images.length == 0) {
            throw new IOException("no_images");
        }
        String boundary = "----MapControl" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream multipart = new ByteArrayOutputStream();
        writeFormField(multipart, boundary, "ts_ms", Long.toString(tsMs));
        if (appVersion != null && !appVersion.isEmpty()) {
            writeFormField(multipart, boundary, "app_version",
                    appVersion.replaceAll("[^A-Za-z0-9._-]", ""));
        }
        if (lat != null && lon != null) {
            writeFormField(multipart, boundary, "lat", Double.toString(lat));
            writeFormField(multipart, boundary, "lon", Double.toString(lon));
        }
        int total = 0;
        int index = 0;
        for (byte[] image : images) {
            if (image == null || image.length == 0) {
                continue;
            }
            total += image.length;
            if (total > maxRequestBytes) {
                throw new IOException("request_too_large");
            }
            multipart.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            multipart.write(("Content-Disposition: form-data; name=\"images\"; filename=\"park-"
                    + index + ".jpg\"\r\n").getBytes(StandardCharsets.UTF_8));
            multipart.write("Content-Type: image/jpeg\r\n\r\n"
                    .getBytes(StandardCharsets.UTF_8));
            multipart.write(image);
            multipart.write("\r\n".getBytes(StandardCharsets.UTF_8));
            index++;
        }
        multipart.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        byte[] raw = multipart.toByteArray();
        if (raw.length > maxRequestBytes + 64 * 1024) {
            throw new IOException("request_too_large");
        }

        URL url = new URL(CloudConfig.BASE_URL + path);
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IOException("https_required");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        if (!(connection instanceof HttpsURLConnection)) {
            connection.disconnect();
            throw new IOException("https_required");
        }
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Content-Type",
                    "multipart/form-data; boundary=" + boundary);
            connection.setRequestProperty("User-Agent", "RoadDeck-Cloud/1");
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
            return new Response(code, readLimited(input));
        } finally {
            connection.disconnect();
        }
    }

    Response postDvrIndex(String path, String deviceToken, String manifestJson,
            List<byte[]> thumbnails, int maxRequestBytes) throws IOException {
        if (path == null || !path.startsWith("/api/v1/") || path.contains("..")) {
            throw new IOException("invalid_path");
        }
        String boundary = "----MapControl" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream multipart = new ByteArrayOutputStream();
        writeFormField(multipart, boundary, "manifest", manifestJson);
        int total = 0;
        for (int index = 0; index < thumbnails.size(); index++) {
            byte[] data = thumbnails.get(index);
            if (data == null || data.length == 0) {
                continue;
            }
            total += data.length;
            if (total > maxRequestBytes) {
                throw new IOException("request_too_large");
            }
            multipart.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            multipart.write(("Content-Disposition: form-data; name=\"thumb_"
                    + index + "\"; filename=\"thumb-" + index + ".jpg\"\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            multipart.write("Content-Type: image/jpeg\r\n\r\n"
                    .getBytes(StandardCharsets.UTF_8));
            multipart.write(data);
            multipart.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        multipart.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        byte[] raw = multipart.toByteArray();
        return postRaw(path, deviceToken, "multipart/form-data; boundary=" + boundary, raw);
    }

    /** Video dosyasını belleğe almadan buluta yükler; MB sınırı yoktur. */
    Response postVideoStreaming(String path, String deviceToken, File file,
            String contentType) throws IOException {
        if (path == null || !path.startsWith("/api/v1/") || path.contains("..")) {
            throw new IOException("invalid_path");
        }
        if (file == null || !file.isFile() || file.length() <= 0) {
            throw new IOException("file_missing");
        }
        String boundary = "----MapControl" + UUID.randomUUID().toString().replace("-", "");
        byte[] header = ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"clip.mp4\"\r\n"
                + "Content-Type: " + (contentType == null ? "video/mp4" : contentType)
                + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] footer = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        long length = header.length + file.length() + footer.length;
        return streamFile(CloudConfig.BASE_URL + path, deviceToken, file, header, footer, length,
                "multipart/form-data; boundary=" + boundary, null);
    }

    /** Video dosyasını telefonun yerel alıcısına ham gövde olarak akıtır. */
    Response postLocalVideo(String url, String requestId, String localToken, File file)
            throws IOException {
        if (url == null || !url.startsWith("http://") || file == null || !file.isFile()) {
            throw new IOException("invalid_local_target");
        }
        byte[] header = new byte[0];
        byte[] footer = new byte[0];
        return streamFile(url, null, file, header, footer, file.length(),
                "video/mp4", new String[][]{{"X-Dvr-Request", requestId},
                        {"X-Dvr-Token", localToken == null ? "" : localToken}});
    }

    private Response streamFile(String absoluteUrl, String deviceToken, File file,
            byte[] header, byte[] footer, long totalLength, String contentType,
            String[][] extraHeaders) throws IOException {
        URL url = new URL(absoluteUrl);
        String protocol = url.getProtocol();
        if (!"https".equalsIgnoreCase(protocol) && !"http".equalsIgnoreCase(protocol)) {
            throw new IOException("invalid_protocol");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        if ("https".equalsIgnoreCase(protocol) && !(connection instanceof HttpsURLConnection)) {
            connection.disconnect();
            throw new IOException("https_required");
        }
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Content-Type", contentType);
            connection.setRequestProperty("User-Agent", "RoadDeck-Cloud/1");
            if (deviceToken != null && !deviceToken.isEmpty()) {
                connection.setRequestProperty("Authorization", "Device " + deviceToken);
            }
            if (extraHeaders != null) {
                for (String[] headerPair : extraHeaders) {
                    connection.setRequestProperty(headerPair[0], headerPair[1]);
                }
            }
            connection.setFixedLengthStreamingMode(totalLength);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(header);
                try (InputStream input = new FileInputStream(file)) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                }
                output.write(footer);
            }
            int code = connection.getResponseCode();
            InputStream input = code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            return new Response(code, readLimited(input));
        } finally {
            connection.disconnect();
        }
    }

    private Response postRaw(String path, String deviceToken, String contentType, byte[] raw)
            throws IOException {
        URL url = new URL(CloudConfig.BASE_URL + path);
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IOException("https_required");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        if (!(connection instanceof HttpsURLConnection)) {
            connection.disconnect();
            throw new IOException("https_required");
        }
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Content-Type", contentType);
            connection.setRequestProperty("User-Agent", "RoadDeck-Cloud/1");
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
            return new Response(code, readLimited(input));
        } finally {
            connection.disconnect();
        }
    }

    private static void writeFormField(ByteArrayOutputStream out, String boundary,
            String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name
                + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
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
            connection.setRequestProperty("User-Agent", "RoadDeck-Cloud/1");
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

    Response putJson(String path, String deviceToken, JSONObject body, int maxRequestBytes)
            throws IOException {
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
            connection.setRequestMethod("PUT");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("User-Agent", "RoadDeck-Cloud/1");
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
            return new Response(code, readLimited(input));
        } finally {
            connection.disconnect();
        }
    }

    /** F4: APK'yı akış hâlinde dosyaya indirir; boyut sınırını aşarsa siler. */
    Response downloadToFile(String path, String deviceToken, File target, long maxBytes)
            throws IOException {
        return downloadToFile(path, deviceToken, target, maxBytes,
                "application/vnd.android.package-archive");
    }

    Response downloadToFile(String path, String deviceToken, File target, long maxBytes,
            String accept) throws IOException {
        if (path == null || !path.startsWith("/api/v1/") || path.contains("..")) {
            throw new IOException("invalid_path");
        }
        URL url = new URL(CloudConfig.BASE_URL + path);
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IOException("https_required");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        if (!(connection instanceof HttpsURLConnection)) {
            connection.disconnect();
            throw new IOException("https_required");
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            connection.disconnect();
            throw new IOException("storage_unavailable");
        }
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(120_000);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", accept);
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("User-Agent", "RoadDeck-Cloud/1");
            if (deviceToken != null && !deviceToken.isEmpty()) {
                connection.setRequestProperty("Authorization", "Device " + deviceToken);
            }
            int code = connection.getResponseCode();
            if (code != 200) {
                InputStream input = connection.getErrorStream();
                return new Response(code, readLimited(input));
            }
            long declared = connection.getContentLengthLong();
            if (declared > maxBytes) {
                throw new IOException("response_too_large");
            }
            try (InputStream input = connection.getInputStream();
                    FileOutputStream output = new FileOutputStream(target)) {
                byte[] chunk = new byte[8192];
                long total = 0L;
                int read;
                while ((read = input.read(chunk)) != -1) {
                    total += read;
                    if (total > maxBytes) {
                        throw new IOException("response_too_large");
                    }
                    output.write(chunk, 0, read);
                }
            }
            return new Response(code, "");
        } catch (IOException error) {
            target.delete();
            throw error;
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
