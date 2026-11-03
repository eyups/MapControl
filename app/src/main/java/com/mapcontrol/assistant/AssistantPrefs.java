package com.mapcontrol.assistant;

import android.content.Context;
import android.content.SharedPreferences;

import com.mapcontrol.BuildConfig;
import com.mapcontrol.util.TargetPackageStore;

/**
 * Sesli asistan tercihleri. Tüm anahtarlar tek {@link #PREFS} dosyasında
 * ({@code MapControlPrefs}) tutulur; varsayılan olarak asistan kapalıdır.
 */
public final class AssistantPrefs {

    public static final String PREFS = TargetPackageStore.PREFS_NAME;

    /** Asistanın tamamı: false iken hiçbir model/mikrofon/thread yüklenmez. */
    public static final String KEY_ENABLED = "assistantEnabled";
    /** Uyandırma kelimesi (Türkçe, küçük harf saklanır). */
    public static final String KEY_WAKE_WORD = "assistantWakeWord";
    /** 0 düşük, 1 orta, 2 yüksek. */
    public static final String KEY_SENSITIVITY = "assistantWakeSensitivity";
    public static final String KEY_API_KEY = "assistantGeminiApiKey";
    public static final String KEY_MODEL = "assistantGeminiModel";
    /** iFlytek (araç) veya Sherpa (gömülü). */
    public static final String KEY_TTS_ENGINE = "assistantTtsEngine";
    public static final String KEY_VOICE_REPLY = "assistantVoiceReply";
    /** none | tone | speak */
    public static final String KEY_WAKE_SOUND = "assistantWakeSound";
    public static final String KEY_OVERLAY = "assistantOverlayEnabled";
    public static final String KEY_OVERLAY_X = "assistantOverlayX";
    public static final String KEY_OVERLAY_Y = "assistantOverlayY";
    public static final String KEY_MODEL_DOWNLOADED = "assistantWakeModelDownloaded";
    public static final String KEY_CONFIRM_POLICY = "assistantConfirmPolicy";
    public static final String KEY_LAST_SESSION = "assistantLastSessionId";
    public static final String KEY_LAST_INTERACTION = "assistantLastInteractionMs";

    public static final String TTS_IFLYTEK = "iflytek";
    public static final String TTS_SHERPA = "sherpa";

    public static final String WAKE_SOUND_NONE = "none";
    public static final String WAKE_SOUND_TONE = "tone";
    public static final String WAKE_SOUND_SPEAK = "speak";

    public static final String DEFAULT_WAKE_WORD = "hey deck";
    public static final String DEFAULT_MODEL = "gemini-3.5-flash-lite";
    public static final int DEFAULT_SENSITIVITY = 1;

    private AssistantPrefs() {
    }

    public static SharedPreferences prefs(Context context) {
        return TargetPackageStore.prefs(context);
    }

    public static boolean isEnabled(Context context) {
        return context != null && prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static String wakeWord(Context context) {
        String value = prefs(context).getString(KEY_WAKE_WORD, DEFAULT_WAKE_WORD);
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_WAKE_WORD;
        }
        return value.trim();
    }

    public static int sensitivity(Context context) {
        int value = prefs(context).getInt(KEY_SENSITIVITY, DEFAULT_SENSITIVITY);
        return Math.max(0, Math.min(2, value));
    }

    /**
     * Kayıtlı anahtar geçerli değilse build zamanı anahtarına düşer
     * ({@code local.properties} → {@code GEMINI_API_KEY}). Anahtar loglanmaz.
     */
    public static String apiKey(Context context) {
        String stored = context == null ? null
                : prefs(context).getString(KEY_API_KEY, "");
        if (isPlausibleKey(stored)) {
            return stored.trim();
        }
        String built = BuildConfig.GEMINI_API_KEY;
        if (isPlausibleKey(built)) {
            return built.trim();
        }
        return stored == null ? "" : stored.trim();
    }

    public static void setApiKey(Context context, String key) {
        String value = key == null ? "" : key.trim();
        prefs(context).edit().putString(KEY_API_KEY, value).apply();
    }

    public static boolean isPlausibleKey(String key) {
        if (key == null) {
            return false;
        }
        String value = key.trim();
        if (value.length() < 20) {
            return false;
        }
        return value.startsWith("AIza") || value.startsWith("AQ.");
    }

    public static boolean hasApiKey(Context context) {
        return isPlausibleKey(apiKey(context));
    }

    public static String model(Context context) {
        String value = prefs(context).getString(KEY_MODEL, "");
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_MODEL;
        }
        return value.trim();
    }

    public static String ttsEngine(Context context) {
        String value = prefs(context).getString(KEY_TTS_ENGINE, TTS_IFLYTEK);
        return TTS_SHERPA.equals(value) ? TTS_SHERPA : TTS_IFLYTEK;
    }

    public static boolean voiceReply(Context context) {
        return prefs(context).getBoolean(KEY_VOICE_REPLY, true);
    }

    public static String wakeSound(Context context) {
        String value = prefs(context).getString(KEY_WAKE_SOUND, WAKE_SOUND_TONE);
        if (WAKE_SOUND_NONE.equals(value) || WAKE_SOUND_SPEAK.equals(value)) {
            return value;
        }
        return WAKE_SOUND_TONE;
    }

    public static boolean overlayEnabled(Context context) {
        return isEnabled(context) && prefs(context).getBoolean(KEY_OVERLAY, false);
    }

    /** Loglanmaz: yalnızca "kayıtlı mı" bilgisini döner. */
    public static String maskedApiKey(Context context) {
        String key = apiKey(context);
        if (key.length() <= 10) {
            return key.isEmpty() ? "" : "••••";
        }
        return key.substring(0, 4) + "…" + key.substring(key.length() - 4);
    }
}
