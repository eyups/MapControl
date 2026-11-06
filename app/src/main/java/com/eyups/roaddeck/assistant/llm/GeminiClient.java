package com.eyups.roaddeck.assistant.llm;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

/**
 * Google AI Studio (Gemini) REST istemcisi.
 *
 * <ul>
 *   <li>Anahtar {@code x-goog-api-key} başlığında; URL'de/log'da asla değil.</li>
 *   <li>Bağlanma 5 sn, okuma 15 sn; 5xx/ağ hatasında bir kez yeniden dener.</li>
 *   <li>HTTP hatası Türkçe sesli mesaja çevrilir ({@link #describeError}).</li>
 * </ul>
 */
public final class GeminiClient {

    public static final String BASE = "https://generativelanguage.googleapis.com/v1beta";

    public static final int ERR_NONE = 0;
    public static final int ERR_AUTH = 1;
    public static final int ERR_QUOTA = 2;
    public static final int ERR_NETWORK = 3;
    public static final int ERR_SERVER = 4;
    public static final int ERR_HTTP = 5;

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 15000;

    public static final class Reply {
        public final int kind;
        public final int httpCode;
        public final String body;
        public final String error;

        Reply(int kind, int httpCode, String body, String error) {
            this.kind = kind;
            this.httpCode = httpCode;
            this.body = body == null ? "" : body;
            this.error = error == null ? "" : error;
        }

        public boolean isOk() {
            return kind == ERR_NONE;
        }
    }

    public Reply generateContent(String model, String apiKey, String requestBody) {
        String safeModel = sanitizeModel(model);
        String url = BASE + "/models/" + safeModel + ":generateContent";
        return post(url, apiKey, requestBody);
    }

    /** Ayar ekranındaki "Bağlantıyı test et" için model listesi. */
    public Reply listModels(String apiKey) {
        return get(BASE + "/models?pageSize=50", apiKey);
    }

    private Reply post(String url, String apiKey, String requestBody) {
        Reply first = execute(url, apiKey, requestBody);
        if (first.kind == ERR_NETWORK || first.httpCode >= 500) {
            return execute(url, apiKey, requestBody);
        }
        return first;
    }

    private Reply get(String url, String apiKey) {
        Reply first = execute(url, apiKey, null);
        if (first.kind == ERR_NETWORK || first.httpCode >= 500) {
            return execute(url, apiKey, null);
        }
        return first;
    }

    private Reply execute(String url, String apiKey, String requestBody) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("x-goog-api-key", apiKey == null ? "" : apiKey);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            if (requestBody != null) {
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                byte[] data = requestBody.getBytes(UTF8);
                connection.setFixedLengthStreamingMode(data.length);
                OutputStream out = connection.getOutputStream();
                try {
                    out.write(data);
                    out.flush();
                } finally {
                    out.close();
                }
            }
            int code = connection.getResponseCode();
            String body = readBody(code >= 400 ? connection.getErrorStream()
                    : connection.getInputStream());
            if (code >= 200 && code < 300) {
                return new Reply(ERR_NONE, code, body, "");
            }
            String message = GeminiResponseParser.errorMessage(body);
            int kind;
            if (code == 401 || code == 403) {
                kind = ERR_AUTH;
            } else if (code == 429) {
                kind = ERR_QUOTA;
            } else if (code >= 500) {
                kind = ERR_SERVER;
            } else {
                kind = ERR_HTTP;
            }
            return new Reply(kind, code, body, message);
        } catch (Throwable t) {
            return new Reply(ERR_NETWORK, -1, "", t.getClass().getSimpleName());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readBody(InputStream input) {
        if (input == null) {
            return "";
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(2048);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), UTF8);
        } catch (Throwable t) {
            return "";
        } finally {
            try {
                input.close();
            } catch (Throwable ignored) {
            }
        }
    }

    /** Model adı URL yoluna giriyor; yalnızca güvenli karakterlere izin ver. */
    public static String sanitizeModel(String model) {
        if (model == null) {
            return "gemini-3.5-flash-lite";
        }
        String value = model.trim();
        if (value.startsWith("models/")) {
            value = value.substring("models/".length());
        }
        if (!value.matches("[A-Za-z0-9._-]{2,80}")) {
            return "gemini-3.5-flash-lite";
        }
        return value;
    }

    public static String describeError(int kind, int httpCode, String apiMessage) {
        switch (kind) {
            case ERR_AUTH:
                return "API anahtarı geçersiz. Ayarlardan kontrol et.";
            case ERR_QUOTA:
                return "Kota doldu. Biraz sonra tekrar dene.";
            case ERR_NETWORK:
                return "İnternet bağlantısı yok.";
            case ERR_SERVER:
                return "Sunucu şu an yanıt vermiyor.";
            default:
                if (apiMessage != null && !apiMessage.isEmpty()) {
                    return "İstek başarısız: " + apiMessage;
                }
                return "İstek başarısız (" + httpCode + ").";
        }
    }
}
