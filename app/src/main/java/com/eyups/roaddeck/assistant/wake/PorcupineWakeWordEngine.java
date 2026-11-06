package com.eyups.roaddeck.assistant.wake;

import android.content.Context;
import android.util.Log;

import com.eyups.roaddeck.assistant.AssistantPrefs;

import java.io.File;
import java.lang.reflect.Method;

/**
 * Picovoice Porcupine adaptoru. Kutuphane ve .ppn dosyasi opsiyoneldir;
 * yoksa {@link #isReady()} false doner ve cagiran Vosk'a duser.
 */
public final class PorcupineWakeWordEngine implements WakeWordEngine {

    private static final String TAG = "VaPorcupine";
    public static final String KEY_ACCESS = "assistantPorcupineAccessKey";

    private final Context context;
    private Object porcupine;
    private Method process;
    private Method delete;
    private Listener listener;

    public PorcupineWakeWordEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    public static File keywordFile(Context context) {
        return new File(context.getFilesDir(), "wake/porcupine.ppn");
    }

    @Override
    public String id() {
        return "porcupine";
    }

    @Override
    public boolean isReady() {
        try {
            Class.forName("ai.picovoice.porcupine.Porcupine");
            String key = AssistantPrefs.prefs(context).getString(KEY_ACCESS, "");
            return key != null && !key.trim().isEmpty() && keywordFile(context).isFile();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void start(String phrase, Listener listener) throws Exception {
        this.listener = listener;
        Class<?> cls = Class.forName("ai.picovoice.porcupine.Porcupine");
        Class<?> builderCls = Class.forName("ai.picovoice.porcupine.Porcupine$Builder");
        Object builder = builderCls.getConstructor().newInstance();
        String key = AssistantPrefs.prefs(context).getString(KEY_ACCESS, "");
        builderCls.getMethod("setAccessKey", String.class).invoke(builder, key);
        builderCls.getMethod("setKeywordPath", String.class)
                .invoke(builder, keywordFile(context).getAbsolutePath());
        int sensitivity = AssistantPrefs.sensitivity(context);
        float value = sensitivity <= 0 ? 0.35f : sensitivity >= 2 ? 0.75f : 0.55f;
        try {
            builderCls.getMethod("setSensitivity", float.class).invoke(builder, value);
        } catch (NoSuchMethodException ignored) {
        }
        porcupine = builderCls.getMethod("build").invoke(builder);
        process = cls.getMethod("process", short[].class);
        delete = cls.getMethod("delete");
        Log.i(TAG, "Porcupine hazir");
    }

    @Override
    public void feed(short[] pcm, int length) {
        if (porcupine == null || process == null || pcm == null || length <= 0) return;
        try {
            short[] frame = pcm;
            if (length != pcm.length) {
                frame = new short[length];
                System.arraycopy(pcm, 0, frame, 0, length);
            }
            Object r = process.invoke(porcupine, (Object) frame);
            if (r instanceof Integer && ((Integer) r) >= 0) {
                Listener l = listener;
                if (l != null) l.onWakeWord("porcupine");
            }
        } catch (Throwable t) {
            Log.w(TAG, "feed: " + t.getClass().getSimpleName());
        }
    }

    @Override
    public void reset() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void release() {
        try {
            if (porcupine != null && delete != null) delete.invoke(porcupine);
        } catch (Throwable ignored) {
        }
        porcupine = null;
        process = null;
        delete = null;
    }
}
