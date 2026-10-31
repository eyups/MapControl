package com.mapcontrol.tts;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.mapcontrol.BuildConfig;

import java.util.Locale;

/**
 * TTS yönlendirici:
 * - Sherpa seçiliyse her zaman gömülü/offline motor.
 * - ElevenLabs seçiliyse internet + API key varsa ElevenLabs; yoksa/hata olursa Sherpa.
 * - iFlytek seçiliyse aracın sistem TTS servisi; servis yoksa/başlatılamazsa Sherpa.
 */
public final class MapControlSpeech {
    private static final String TAG = "MapControlSpeech";

    public static final String PREFS = "MapControlPrefs";
    public static final String KEY_ENGINE = "ttsEngine";
    /** Kullanıcı motoru bilinçli seçti mi? Ayarlar sıfırlanınca false olur. */
    public static final String KEY_ENGINE_CONFIGURED = "ttsEngineConfigured";
    public static final String ENGINE_SHERPA = "sherpa";
    public static final String ENGINE_ELEVENLABS = "elevenlabs";
    public static final String ENGINE_IFLYTEK = "iflytek";
    public static final int ENGINE_MODE_SHERPA = 0;
    public static final int ENGINE_MODE_ELEVENLABS = 1;
    public static final int ENGINE_MODE_IFLYTEK = 2;
    public static final String KEY_API_KEY = "elevenLabsApiKey";
    public static final String KEY_VOICE_ID = "elevenLabsVoiceId";
    public static final String DEFAULT_VOICE_ID = "mF7tIc9VLrznhGooGjaT";
    private static final String LEGACY_DEFAULT_VOICE_ID = "21m00Tcm4TlvDq8ikWAM";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Runnable finishRunnable;
    private static boolean speaking;

    private MapControlSpeech() {
    }

    public static void speak(Context context, String text) {
        speak(context, text, text, 0L);
    }

    public static void speak(Context context, String plainText, String expressiveText) {
        speak(context, plainText, expressiveText, 0L);
    }

    /**
     * @param plainText      Sherpa/offline için düz metin
     * @param expressiveText ElevenLabs için duygu tonu eklenmiş metin
     * @param cacheId        sürüş id'si; 0 ise ses önbelleği kullanılmaz
     */
    public static void speak(Context context, String plainText, String expressiveText,
                             long cacheId) {
        speak(context, plainText, expressiveText, cacheId, null);
    }

    /**
     * @param onFinished konuşma bitince/basarisiz olunca ana thread'de çalışır.
     */
    public static void speak(Context context, String plainText, String expressiveText,
                             long cacheId, Runnable onFinished) {
        if (context == null || plainText == null || plainText.trim().isEmpty()) {
            finish(onFinished);
            return;
        }
        if (speaking) {
            return;
        }
        String localText = TurkishSpeechText.normalize(SpeechText.plain(plainText));
        String cloudText = TurkishSpeechText.normalize(
                (expressiveText == null || expressiveText.trim().isEmpty()
                        ? plainText : expressiveText).trim());
        if (localText.isEmpty() || cloudText.isEmpty()) {
            finish(onFinished);
            return;
        }
        Log.i(TAG, "TTS metni: " + preview(localText));
        Context app = context.getApplicationContext();
        String engine = engine(app);
        if (ENGINE_IFLYTEK.equals(engine)) {
            speaking = true;
            IflytekTts.speak(app, localText, (ok, message) -> {
                if (!ok) {
                    Log.w(TAG, "iFlytek TTS kullanılamadı (" + message
                            + "); Sherpa'ya düşülüyor");
                    MapControlTts.speak(app, localText);
                }
            }, () -> {
                TtsAudioFocus.release();
                finish(onFinished);
            });
            return;
        }
        if (ENGINE_ELEVENLABS.equals(engine)) {
            String key = resolveApiKey(app);
            String voice = voiceId(app);
            if (key == null || key.trim().isEmpty()) {
                Log.i(TAG, "ElevenLabs seçili ama API key yok; Sherpa'ya düşülüyor");
                MapControlTts.speak(app, localText);
                return;
            }
            boolean online = isOnline(app);
            Log.i(TAG, "ElevenLabs isteği gönderiliyor: " + cloudText.length()
                    + " karakter, cacheId=" + cacheId + ", online=" + online);
            speaking = true;
            scheduleFinish(onFinished, localText);
            ElevenLabsTts.speak(app, cloudText, key.trim(), voice, cacheId, online, reason -> {
                Log.w(TAG, "ElevenLabs başarısız (" + reason + "); Sherpa'ya düşülüyor");
                MapControlTts.speak(app, localText);
            });
            return;
        }
        speaking = true;
        scheduleFinish(onFinished, localText);
        MapControlTts.speak(app, localText);
    }

    private static String preview(String text) {
        return text.substring(0, Math.min(120, text.length()));
    }

    public static void stop() {
        speaking = false;
        if (finishRunnable != null) {
            MAIN.removeCallbacks(finishRunnable);
        }
        finishRunnable = null;
        IflytekTts.stop(null, null);
        ElevenLabsTts.stop();
        MapControlTts.stop();
    }

    public static boolean isElevenLabsSelected(Context context) {
        return ENGINE_ELEVENLABS.equals(engine(context));
    }

    public static boolean isIflytekSelected(Context context) {
        return ENGINE_IFLYTEK.equals(engine(context));
    }

    public static boolean isSpeaking() {
        return speaking;
    }

    private static void scheduleFinish(Runnable onFinished, String text) {
        if (finishRunnable != null) {
            MAIN.removeCallbacks(finishRunnable);
        }
        long durationMs = 1500L + Math.min(55000L, text.length() * 95L);
        finishRunnable = () -> finish(onFinished);
        MAIN.postDelayed(finishRunnable, durationMs);
    }

    private static void finish(Runnable onFinished) {
        speaking = false;
        if (finishRunnable != null) {
            MAIN.removeCallbacks(finishRunnable);
        }
        finishRunnable = null;
        if (onFinished != null) {
            MAIN.post(onFinished);
        }
    }

    /**
     * Kayıtlı motor yoksa: gömülü API anahtarı varsa ElevenLabs, yoksa Sherpa.
     * Araçta ayarlar sıfırlansa bile anahtar BuildConfig'ten gelir.
     */
    public static String engine(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean configured = prefs.getBoolean(KEY_ENGINE_CONFIGURED, false);
        String stored = prefs.getString(KEY_ENGINE, null);
        String fallback = hasApiKey(context) ? ENGINE_ELEVENLABS : ENGINE_SHERPA;
        if (!configured) {
            // İlk kurulum/ayar sıfırlanması: gömülü anahtar varsa ElevenLabs'a sabitle.
            // Kullanıcı Ayarlar'dan seçim yapınca KEY_ENGINE_CONFIGURED=true olur.
            if (stored == null || !stored.equals(fallback)) {
                prefs.edit().putString(KEY_ENGINE, fallback)
                        .putBoolean(KEY_ENGINE_CONFIGURED, true).apply();
            }
            return fallback;
        }
        return stored == null || stored.trim().isEmpty() ? fallback : stored;
    }

    public static String apiKey(Context context) {
        String key = resolveApiKey(context);
        return key == null ? "" : key;
    }

    public static String voiceId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String pref = prefs.getString(KEY_VOICE_ID, "");
        String build = BuildConfig.ELEVENLABS_VOICE_ID;
        String fallback = build == null || build.trim().isEmpty()
                ? DEFAULT_VOICE_ID : build.trim();
        if (pref == null || pref.trim().isEmpty()) {
            return fallback;
        }
        pref = pref.trim();
        // Eski varsayılan (Rachel) kayıtlıysa yeni varsayılana geçir.
        if (LEGACY_DEFAULT_VOICE_ID.equals(pref)) {
            prefs.edit().putString(KEY_VOICE_ID, fallback).apply();
            return fallback;
        }
        return pref;
    }

    public static boolean hasApiKey(Context context) {
        String key = resolveApiKey(context);
        return key != null && !key.trim().isEmpty();
    }

    public static boolean isOnline(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            // Araçta ethernet/cellular "validated" bayrağı false kalabiliyor;
            // ping/HTTP çalışsa da VALIDATED şartı TTS'i gereksiz Sherpa'ya düşürüyor.
            return caps != null
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Throwable t) {
            Log.w(TAG, "Ağ durumu okunamadı", t);
            return false;
        }
    }

    private static String resolveApiKey(Context context) {
        String pref = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_API_KEY, "");
        pref = pref == null ? "" : pref.trim();
        // Kayıtlı anahtar geçerliyse (ElevenLabs anahtarı 51 karakter) onu kullan.
        if (isValidApiKey(pref)) {
            return pref;
        }
        // Eski/eksik anahtar veya ayar sıfırlanması: APK'ya gömülü anahtara düş.
        String build = BuildConfig.ELEVENLABS_API_KEY;
        build = build == null ? "" : build.trim();
        if (isValidApiKey(build)) {
            return build;
        }
        return pref;
    }

    public static boolean isValidApiKey(String key) {
        return key != null && key.trim().length() == 51;
    }
}
