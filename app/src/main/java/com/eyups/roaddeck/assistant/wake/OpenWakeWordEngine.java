package com.eyups.roaddeck.assistant.wake;

import android.content.Context;

import java.io.File;

/**
 * openWakeWord adaptoru. ONNX Runtime + mel/embedding/keyword modelleri
 * cihaza eklenmeden etkinlesmez; bu durumda {@link #isReady()} false doner
 * ve cagiran Vosk'a duser. Model dizini: files/wake/openwakeword/.
 */
public final class OpenWakeWordEngine implements WakeWordEngine {

    private final Context context;

    public OpenWakeWordEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    public static File modelDir(Context context) {
        return new File(context.getFilesDir(), "wake/openwakeword");
    }

    @Override
    public String id() {
        return "openwakeword";
    }

    @Override
    public boolean isReady() {
        try {
            Class.forName("ai.onnxruntime.OrtEnvironment");
            File dir = modelDir(context);
            return new File(dir, "melspectrogram.onnx").isFile()
                    && new File(dir, "embedding_model.onnx").isFile()
                    && new File(dir, "keyword.onnx").isFile();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void start(String phrase, Listener listener) throws Exception {
        throw new UnsupportedOperationException("openWakeWord runtime not bundled");
    }

    @Override
    public void feed(short[] pcm, int length) {
    }

    @Override
    public void reset() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void release() {
    }
}
