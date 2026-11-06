package com.eyups.roaddeck.assistant.command;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Yerel komut tanima: Vosk modelini gramer modunda calistirir.
 * Uyandirma motorundan bagimsizdir; yalnizca uyandirma sonrasi kisa komut icin.
 */
public final class VoskCommandRecognizer {

    private static final String TAG = "VaVoskCmd";
    private final File modelDir;
    private final Object lock = new Object();

    private Object model;
    private Object recognizer;
    private Class<?> recognizerClass;
    private Method acceptShort;
    private Method acceptBytes;
    private Method getFinal;
    private Method reset;
    private Method closeRecognizer;
    private Method closeModel;
    private String grammar = "";
    private byte[] bytes = new byte[3200];

    public VoskCommandRecognizer(File modelDir) {
        this.modelDir = modelDir;
    }

    public boolean isReady() {
        return modelDir != null && modelDir.isDirectory()
                && new File(modelDir, "final.mdl").isFile();
    }

    public synchronized void prepare(String[] phrases) {
        StringBuilder g = new StringBuilder("[");
        for (int i = 0; i < phrases.length; i++) {
            if (i > 0) g.append(',');
            g.append('"').append(phrases[i].replace("\"", "")).append('"');
        }
        if (phrases.length == 0) g.append('"').append("[unk]").append('"');
        g.append(",\"[unk]\"]");
        grammar = g.toString();
    }

    public synchronized String recognize(short[] pcm, int length) {
        try {
            ensure();
            if (recognizer == null || pcm == null || length <= 0) return "";
            if (acceptShort != null) {
                acceptShort.invoke(recognizer, pcm, length);
            } else {
                int n = length * 2;
                if (bytes.length < n) bytes = new byte[n];
                for (int i = 0; i < length; i++) {
                    bytes[i * 2] = (byte) (pcm[i] & 0xff);
                    bytes[i * 2 + 1] = (byte) ((pcm[i] >> 8) & 0xff);
                }
                acceptBytes.invoke(recognizer, bytes, n);
            }
            Object result = getFinal.invoke(recognizer);
            String text = extract(result == null ? "" : String.valueOf(result));
            try { reset.invoke(recognizer); } catch (Throwable ignored) {}
            Log.i(TAG, "komut metni: " + text);
            return text;
        } catch (Throwable t) {
            Log.w(TAG, "recognize: " + t.getClass().getSimpleName());
            return "";
        }
    }

    private void ensure() throws Exception {
        if (recognizer != null) return;
        Class<?> mc = Class.forName("org.vosk.Model");
        Class<?> rc = Class.forName("org.vosk.Recognizer");
        try {
            model = mc.getConstructor(String.class).newInstance(modelDir.getAbsolutePath());
        } catch (NoSuchMethodException e) {
            model = mc.getConstructor(File.class).newInstance(modelDir);
        }
        String g = grammar.isEmpty() ? null : grammar;
        if (g != null) {
            try {
                recognizer = rc.getConstructor(mc, float.class, String.class)
                        .newInstance(model, 16000f, g);
            } catch (NoSuchMethodException e) {
                recognizer = rc.getConstructor(mc, float.class).newInstance(model, 16000f);
            }
        } else {
            recognizer = rc.getConstructor(mc, float.class).newInstance(model, 16000f);
        }
        recognizerClass = rc;
        try {
            acceptShort = rc.getMethod("acceptWaveForm", short[].class, int.class);
        } catch (NoSuchMethodException e) {
            acceptShort = null;
        }
        acceptBytes = rc.getMethod("acceptWaveForm", byte[].class, int.class);
        getFinal = rc.getMethod("getFinalResult");
        reset = rc.getMethod("reset");
        closeRecognizer = rc.getMethod("close");
        closeModel = mc.getMethod("close");
        Log.i(TAG, "Vosk komut hazir, gramer=" + grammar);
    }

    private static String extract(String json) {
        if (json == null || json.isEmpty()) return "";
        try {
            JSONObject o = new JSONObject(json);
            return o.optString("text", "").trim();
        } catch (Throwable t) {
            int i = json.indexOf("\"text\"");
            if (i < 0) return "";
            int a = json.indexOf('"', i + 6);
            int b = json.indexOf('"', a + 1);
            return a >= 0 && b > a ? json.substring(a + 1, b) : "";
        }
    }

    public synchronized void close() {
        try { if (recognizer != null && closeRecognizer != null) closeRecognizer.invoke(recognizer); } catch (Throwable ignored) {}
        try { if (model != null && closeModel != null) closeModel.invoke(model); } catch (Throwable ignored) {}
        recognizer = null; model = null;
    }
}
