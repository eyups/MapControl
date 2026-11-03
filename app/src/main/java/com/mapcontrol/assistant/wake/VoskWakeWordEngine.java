package com.mapcontrol.assistant.wake;

import android.util.Log;

import com.mapcontrol.assistant.llm.MiniJson;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Varsayılan uyandırma motoru: Vosk Android + {@code vosk-model-small-tr},
 * grammar modunda ({@code ["<kelime>", "[unk]"]}).
 *
 * <p>Vosk sınıfları reflection ile yüklenir: kütüphane APK'da yoksa sınıf
 * yükleme hatası uygulamayı düşürmez, {@link #isReady()} false döner ve asistan
 * yalnız push-to-talk ile çalışır. Eğitim gerektirmediği için kullanıcının
 * yazdığı herhangi bir kelime çalışır.</p>
 */
public final class VoskWakeWordEngine implements WakeWordEngine {

    private static final String TAG = "VaVoskKws";

    private final File modelDir;
    private final Object lock = new Object();

    private Object model;
    private Object recognizer;
    private Class<?> modelClass;
    private Class<?> recognizerClass;
    private Method acceptWaveFormShort;
    private Method acceptWaveFormBytes;
    private Method getResult;
    private Method resetMethod;
    private Method recognizerClose;
    private Method modelClose;
    private String phrase = "";
    private Listener listener;
    private byte[] pcmBytes = new byte[1600];

    public VoskWakeWordEngine(File modelDir) {
        this.modelDir = modelDir;
    }

    public static boolean libraryAvailable() {
        try {
            Class.forName("org.vosk.Model");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public String id() {
        return "vosk-grammar";
    }

    @Override
    public boolean isReady() {
        if (modelDir == null || !WakeWordModelStore.looksComplete(modelDir)) {
            return false;
        }
        return libraryAvailable();
    }

    @Override
    public void start(String phrase, Listener listener) throws Exception {
        synchronized (lock) {
            closeLocked();
            this.phrase = WakeWordNormalizer.normalize(phrase);
            this.listener = listener;
            modelClass = Class.forName("org.vosk.Model");
            recognizerClass = Class.forName("org.vosk.Recognizer");
            try {
                // vosk-android 0.3.x: Model(String path)
                model = modelClass.getConstructor(String.class)
                        .newInstance(modelDir.getAbsolutePath());
            } catch (NoSuchMethodException e) {
                model = modelClass.getConstructor(File.class).newInstance(modelDir);
            }
            String grammar = "[\"" + this.phrase.replace("\"", "") + "\", \"[unk]\"]";
            Constructor<?> constructor;
            Object created;
            try {
                constructor = recognizerClass.getConstructor(modelClass, float.class, String.class);
                created = constructor.newInstance(model, 16000f, grammar);
            } catch (NoSuchMethodException e) {
                constructor = recognizerClass.getConstructor(modelClass, float.class);
                created = constructor.newInstance(model, 16000f);
            }
            recognizer = created;
            try {
                acceptWaveFormShort = recognizerClass.getMethod(
                        "acceptWaveForm", short[].class, int.class);
            } catch (NoSuchMethodException ignored) {
                acceptWaveFormShort = null;
            }
            acceptWaveFormBytes = recognizerClass.getMethod(
                    "acceptWaveForm", byte[].class, int.class);
            getResult = recognizerClass.getMethod("getResult");
            resetMethod = recognizerClass.getMethod("reset");
            recognizerClose = recognizerClass.getMethod("close");
            modelClose = modelClass.getMethod("close");
            Log.i(TAG, "Vosk KWS hazır: " + this.phrase);
        }
    }

    @Override
    public void feed(short[] pcm, int length) {
        Listener current;
        String target;
        synchronized (lock) {
            if (recognizer == null || pcm == null || length <= 0) {
                return;
            }
            try {
                Object accepted;
                if (acceptWaveFormShort != null) {
                    accepted = acceptWaveFormShort.invoke(recognizer, pcm, length);
                } else {
                    int bytes = length * 2;
                    if (pcmBytes.length < bytes) {
                        pcmBytes = new byte[bytes];
                    }
                    for (int i = 0; i < length; i++) {
                        short value = pcm[i];
                        pcmBytes[i * 2] = (byte) (value & 0xFF);
                        pcmBytes[i * 2 + 1] = (byte) ((value >> 8) & 0xFF);
                    }
                    accepted = acceptWaveFormBytes.invoke(recognizer, pcmBytes, bytes);
                }
                if (!(accepted instanceof Boolean) || !((Boolean) accepted)) {
                    return;
                }
                Object result = getResult.invoke(recognizer);
                String text = extractText(result == null ? "" : String.valueOf(result));
                if (text.isEmpty()) {
                    return;
                }
                current = listener;
                target = phrase;
                if (WakeWordNormalizer.matches(text, target)) {
                    resetMethod.invoke(recognizer);
                } else {
                    return;
                }
            } catch (Throwable t) {
                Log.w(TAG, "KWS feed hatası: " + t.getClass().getSimpleName());
                return;
            }
        }
        if (current != null) {
            current.onWakeWord(target);
        }
    }

    private static String extractText(String json) {
        Map<String, Object> map = MiniJson.parseObject(json);
        if (map == null) {
            return "";
        }
        Object text = map.get("text");
        return text instanceof String ? (String) text : "";
    }

    @Override
    public void reset() {
        synchronized (lock) {
            if (recognizer != null && resetMethod != null) {
                try {
                    resetMethod.invoke(recognizer);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    @Override
    public void stop() {
        synchronized (lock) {
            listener = null;
            if (recognizer != null && resetMethod != null) {
                try {
                    resetMethod.invoke(recognizer);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    @Override
    public void release() {
        synchronized (lock) {
            closeLocked();
        }
    }

    private void closeLocked() {
        listener = null;
        if (recognizer != null && recognizerClose != null) {
            try {
                recognizerClose.invoke(recognizer);
            } catch (Throwable ignored) {
            }
        }
        recognizer = null;
        if (model != null && modelClose != null) {
            try {
                modelClose.invoke(model);
            } catch (Throwable ignored) {
            }
        }
        model = null;
    }
}
