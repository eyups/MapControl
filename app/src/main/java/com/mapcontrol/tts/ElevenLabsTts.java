package com.mapcontrol.tts;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.audiofx.LoudnessEnhancer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** ElevenLabs TTS + son 10 sürüşün MP3 önbelleği. */
public final class ElevenLabsTts {
    private static final String TAG = "ElevenLabsTts";
    private static final String ENDPOINT = "https://api.elevenlabs.io/v1/text-to-speech/";
    private static final String MODEL_EXPRESSIVE = "eleven_v3";
    private static final String MODEL_FALLBACK = "eleven_multilingual_v2";
    private static final String CACHE_DIR = "tts_cache";
    private static final int MAX_CACHE = 10;

    public interface FailureCallback {
        void onFailure(String reason);
    }

    public interface FileCallback {
        void onSuccess(File file);
        void onFailure(String reason);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "elevenlabs-tts");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static MediaPlayer player;
    private static LoudnessEnhancer loudness;

    private ElevenLabsTts() {
    }

    public static void speak(Context context, String text, String apiKey, String voiceId,
                             FailureCallback onFailure) {
        speak(context, text, apiKey, voiceId, 0L, true, onFailure);
    }

    /**
     * @param cacheId sürüş id'si; 0 ise önbellek kullanılmaz
     * @param online  ağ durumu; önbellek varsa offline'da da oynatılır
     */
    public static void speak(Context context, String text, String apiKey, String voiceId,
                             long cacheId, boolean online, FailureCallback onFailure) {
        final Context app = context.getApplicationContext();
        final int gen = GENERATION.incrementAndGet();
        stopPlayer();
        EXECUTOR.execute(() -> {
            String voice = voiceId == null || voiceId.trim().isEmpty()
                    ? MapControlSpeech.DEFAULT_VOICE_ID : voiceId.trim();
            String hash = sha256(text + "|" + voice);
            File cached = cacheId > 0 ? cacheFile(app, cacheId) : null;
            File meta = cacheId > 0 ? metaFile(app, cacheId) : null;

            // 1) Önbellek geçerliyse API'ye hiç gitme (offline dahil).
            if (cached != null && cached.isFile() && cached.length() > 0
                    && cacheMetaMatches(meta, hash, voice)) {
                Log.i(TAG, "Önbellek isabeti: " + cached.getName()
                        + " (" + cached.length() + " bayt)");
                if (gen == GENERATION.get()) {
                    play(app, cached, gen, onFailure, true);
                }
                return;
            }

            // 2) Önbellek yok ve ağ yoksa Sherpa fallback.
            if (!online) {
                if (gen == GENERATION.get() && onFailure != null) {
                    onFailure.onFailure("offline");
                }
                return;
            }

            // 3) Eski/geçersiz önbelleği temizle.
            if (cached != null) {
                cached.delete();
                if (meta != null) meta.delete();
            }

            File out = cached != null
                    ? new File(cached.getParentFile(), cached.getName() + ".part")
                    : new File(app.getCacheDir(), "elevenlabs_" + gen + ".mp3");
            int code = -1;
            try {
                Log.i(TAG, "v3 isteği: " + text.length() + " karakter; " + preview(text));
                code = request(text, MODEL_EXPRESSIVE, apiKey, voice, out, app);
                if (code != HttpURLConnection.HTTP_OK
                        && code != HttpURLConnection.HTTP_UNAUTHORIZED) {
                    Log.w(TAG, "v3 başarısız (HTTP " + code + "), v2 düz metinle deneniyor");
                    out.delete();
                    String plain = SpeechText.plain(text);
                    Log.i(TAG, "v2 isteği: " + plain.length() + " karakter; " + preview(plain));
                    code = request(plain, MODEL_FALLBACK, apiKey, voice, out, app);
                }
            } catch (Throwable t) {
                Log.w(TAG, "v3 istek hatası (" + t.getClass().getSimpleName() + "), v2 deneniyor");
                out.delete();
                try {
                    String plain = SpeechText.plain(text);
                    code = request(plain, MODEL_FALLBACK, apiKey, voice, out, app);
                } catch (Throwable t2) {
                    Log.w(TAG, "v2 istek hatası", t2);
                    code = -1;
                }
            }
            if (code != HttpURLConnection.HTTP_OK) {
                out.delete();
                if (gen == GENERATION.get() && onFailure != null) {
                    onFailure.onFailure("HTTP " + code);
                }
                return;
            }
            if (gen != GENERATION.get()) {
                out.delete();
                return;
            }

            if (cached != null) {
                File finalFile = cached;
                if (!out.renameTo(finalFile)) {
                    try (FileInputStream in = new FileInputStream(out);
                         FileOutputStream fos = new FileOutputStream(finalFile)) {
                        copy(in, fos);
                    } catch (Throwable t) {
                        Log.w(TAG, "Önbelleğe yazılamadı", t);
                        out.delete();
                        finalFile = out;
                    }
                    out.delete();
                }
                if (finalFile == cached) {
                    writeMeta(meta, hash, voice);
                    pruneCache(app);
                }
                Log.i(TAG, "MP3 hazır: " + finalFile.length() + " bayt");
                play(app, finalFile, gen, onFailure, finalFile == cached);
            } else {
                Log.i(TAG, "MP3 indirildi: " + out.length() + " bayt");
                play(app, out, gen, onFailure, false);
            }
        });
    }

    /** Profilde önceden üretilmiş MP3'ü API çağrısı yapmadan oynatır. */
    public static void playCached(Context context, File file, FailureCallback onFailure) {
        if (context == null || file == null || !file.isFile() || file.length() <= 0) {
            if (onFailure != null) {
                onFailure.onFailure("cache-missing");
            }
            return;
        }
        final Context app = context.getApplicationContext();
        final int gen = GENERATION.incrementAndGet();
        stopPlayer();
        MAIN.post(() -> play(app, file, gen, onFailure, true));
    }

    /** Hoş geldin sesi gibi kalıcı dosya üretimi; oynatma yapmaz. */
    public static void synthesizeToFile(Context context, String text, String apiKey,
                                        String voiceId, File out, FileCallback callback) {
        final Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            String voice = voiceId == null || voiceId.trim().isEmpty()
                    ? MapControlSpeech.DEFAULT_VOICE_ID : voiceId.trim();
            out.getParentFile().mkdirs();
            int code = -1;
            try {
                Log.i(TAG, "Dosya v3 isteği: " + text.length() + " karakter; " + preview(text));
                code = request(text, MODEL_EXPRESSIVE, apiKey, voice, out, app);
                if (code != HttpURLConnection.HTTP_OK
                        && code != HttpURLConnection.HTTP_UNAUTHORIZED) {
                    out.delete();
                    String plain = SpeechText.plain(text);
                    code = request(plain, MODEL_FALLBACK, apiKey, voice, out, app);
                }
            } catch (Throwable t) {
                Log.w(TAG, "Dosya istek hatası (" + t.getClass().getSimpleName() + "), v2 deneniyor");
                out.delete();
                try {
                    code = request(SpeechText.plain(text), MODEL_FALLBACK, apiKey, voice, out, app);
                } catch (Throwable t2) {
                    Log.w(TAG, "Dosya v2 hatası", t2);
                    code = -1;
                }
            }
            if (code == HttpURLConnection.HTTP_OK && out.isFile() && out.length() > 0) {
                Log.i(TAG, "Dosya hazır: " + out.length() + " bayt");
                if (callback != null) callback.onSuccess(out);
            } else {
                out.delete();
                if (callback != null) callback.onFailure("HTTP " + code);
            }
        });
    }

    private static int request(String text, String model, String apiKey,
                               String voice, File out, Context context) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(ENDPOINT + URLEncoder.encode(voice, "UTF-8")
                    + "?output_format=mp3_44100_128");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(20000);
            connection.setDoOutput(true);
            connection.setRequestProperty("xi-api-key", apiKey);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "audio/mpeg");

            SharedPreferences prefs = context.getSharedPreferences(
                    MapControlSpeech.PREFS, Context.MODE_PRIVATE);
            float speed = prefs.getInt(MapControlSpeech.KEY_TTS_SPEED, 100) / 100f;
            float stability = prefs.getInt(MapControlSpeech.KEY_TTS_STABILITY, 45) / 100f;
            float clarity = prefs.getInt(MapControlSpeech.KEY_TTS_CLARITY, 80) / 100f;
            JSONObject voiceSettings = new JSONObject();
            voiceSettings.put("stability", stability);
            voiceSettings.put("similarity_boost", clarity);
            voiceSettings.put("speed", speed);
            voiceSettings.put("style", 0.45);
            voiceSettings.put("use_speaker_boost", true);

            JSONObject body = new JSONObject();
            body.put("text", text);
            body.put("model_id", model);
            body.put("voice_settings", voiceSettings);

            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload);
            }

            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, model + " HTTP " + code + " " + readError(connection));
                return code;
            }
            try (InputStream in = connection.getInputStream();
                 FileOutputStream fos = new FileOutputStream(out)) {
                copy(in, fos);
            }
            return code;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void play(Context app, File file, int gen, FailureCallback onFailure,
                             boolean cached) {
        MAIN.post(() -> {
            if (gen != GENERATION.get()) {
                if (!cached) file.delete();
                return;
            }
            TtsAudioFocus.acquire(app, ElevenLabsTts::stop);
            try {
                MediaPlayer mp = new MediaPlayer();
                mp.setAudioAttributes(new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .build());
                mp.setDataSource(file.getAbsolutePath());
                mp.setVolume(1.0f, 1.0f);
                mp.setOnCompletionListener(p -> {
                    p.release();
                    if (player == p) player = null;
                    releaseLoudness();
                    TtsAudioFocus.release();
                    if (!cached) file.delete();
                });
                mp.setOnErrorListener((p, what, extra) -> {
                    p.release();
                    if (player == p) player = null;
                    releaseLoudness();
                    TtsAudioFocus.release();
                    file.delete();
                    if (gen == GENERATION.get() && onFailure != null) {
                        onFailure.onFailure("MediaPlayer " + what + "/" + extra);
                    }
                    return true;
                });
                player = mp;
                mp.prepare();
                try {
                    if (mp.getAudioSessionId() > 0) {
                        releaseLoudness();
                        loudness = new LoudnessEnhancer(mp.getAudioSessionId());
                        loudness.setTargetGain(300);
                        loudness.setEnabled(true);
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "LoudnessEnhancer eklenemedi", t);
                }
                MAIN.postDelayed(() -> {
                    if (gen != GENERATION.get()) {
                        return;
                    }
                    try {
                        mp.start();
                        Log.i(TAG, "Oynatma başladı" + (cached ? " (önbellek)" : ""));
                    } catch (Throwable t) {
                        Log.w(TAG, "Oynatma başlatılamadı", t);
                        try { mp.release(); } catch (Throwable ignored) {}
                        if (player == mp) player = null;
                        releaseLoudness();
                        TtsAudioFocus.release();
                        if (onFailure != null) onFailure.onFailure("play");
                    }
                }, TtsAudioFocus.DUCK_SETTLE_MS);
            } catch (Throwable t) {
                releaseLoudness();
                TtsAudioFocus.release();
                file.delete();
                Log.w(TAG, "Oynatma hatası", t);
                if (gen == GENERATION.get() && onFailure != null) {
                    onFailure.onFailure("play");
                }
            }
        });
    }

    public static void stop() {
        GENERATION.incrementAndGet();
        TtsAudioFocus.release();
        stopPlayer();
    }

    private static void stopPlayer() {
        MAIN.post(() -> {
            try {
                releaseLoudness();
                if (player != null) {
                    player.stop();
                    player.release();
                    player = null;
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private static void releaseLoudness() {
        try {
            if (loudness != null) {
                loudness.release();
                loudness = null;
            }
        } catch (Throwable ignored) {
        }
    }

    private static File cacheDir(Context context) {
        File dir = new File(context.getFilesDir(), CACHE_DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static File cacheFile(Context context, long id) {
        return new File(cacheDir(context), "trip_" + Math.abs(id) + ".mp3");
    }

    private static File metaFile(Context context, long id) {
        return new File(cacheDir(context), "trip_" + Math.abs(id) + ".json");
    }

    private static boolean cacheMetaMatches(File meta, String hash, String voice) {
        if (meta == null || !meta.isFile()) return false;
        try (FileInputStream in = new FileInputStream(meta)) {
            byte[] buffer = new byte[(int) meta.length()];
            int n = in.read(buffer);
            if (n <= 0) return false;
            JSONObject object = new JSONObject(new String(buffer, 0, n, StandardCharsets.UTF_8));
            return hash.equals(object.optString("hash"))
                    && voice.equals(object.optString("voice"));
        } catch (Throwable t) {
            return false;
        }
    }

    private static void writeMeta(File meta, String hash, String voice) {
        if (meta == null) return;
        try {
            JSONObject object = new JSONObject();
            object.put("hash", hash);
            object.put("voice", voice);
            object.put("createdAt", System.currentTimeMillis());
            try (FileOutputStream fos = new FileOutputStream(meta)) {
                fos.write(object.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable t) {
            Log.w(TAG, "Meta yazılamadı", t);
        }
    }

    private static void pruneCache(Context context) {
        File dir = cacheDir(context);
        File[] files = dir.listFiles((d, name) -> name.startsWith("trip_") && name.endsWith(".mp3"));
        if (files == null) return;
        List<File> list = new ArrayList<>(Arrays.asList(files));
        list.sort(Comparator.comparingLong(File::lastModified).reversed());
        for (int i = MAX_CACHE; i < list.size(); i++) {
            File old = list.get(i);
            File meta = new File(dir, old.getName().replace(".mp3", ".json"));
            old.delete();
            meta.delete();
            Log.i(TAG, "Eski önbellek silindi: " + old.getName());
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Throwable t) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
        }
    }

    private static String preview(String text) {
        return text.substring(0, Math.min(80, text.length()));
    }

    private static String readError(HttpURLConnection connection) {
        try (InputStream in = connection.getErrorStream()) {
            if (in == null) return "";
            byte[] buffer = new byte[512];
            int n = in.read(buffer);
            if (n <= 0) return "";
            return new String(buffer, 0, n, StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return "";
        }
    }
}
