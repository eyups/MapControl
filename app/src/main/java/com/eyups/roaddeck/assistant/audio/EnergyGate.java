package com.eyups.roaddeck.assistant.audio;

/**
 * İki aşamalı kapının ilk katmanı: 20-30 ms çerçevelerde RMS + uyarlanabilir
 * gürültü tabanı. Konuşma benzeri enerji yoksa KWS'ye veri gönderilmez.
 * Saf Java; sentetik PCM ile JVM test edilir.
 */
public final class EnergyGate {

    public static final int SAMPLE_RATE = 16000;
    public static final int FRAME_SAMPLES = 400; // 25 ms

    public static final int SENSITIVITY_LOW = 0;
    public static final int SENSITIVITY_MEDIUM = 1;
    public static final int SENSITIVITY_HIGH = 2;

    private final float multiplier;
    private final float minRms;
    private float noiseFloor = 120f;
    private float lastRms;
    private boolean speech;

    public EnergyGate(int sensitivity) {
        switch (sensitivity) {
            case SENSITIVITY_LOW:
                multiplier = 4.0f;
                minRms = 350f;
                break;
            case SENSITIVITY_HIGH:
                multiplier = 2.0f;
                minRms = 160f;
                break;
            default:
                multiplier = 3.0f;
                minRms = 250f;
        }
    }

    /**
     * @return true → çerçevede konuşma benzeri enerji var
     */
    public boolean process(short[] pcm, int length) {
        if (pcm == null || length <= 0) {
            return false;
        }
        float rms = rms(pcm, Math.min(length, pcm.length));
        lastRms = rms;
        float threshold = Math.max(minRms, noiseFloor * multiplier);
        boolean detected = rms >= threshold;
        if (!detected) {
            // Yalnızca sessiz çerçevelerle tabanı güncelle; konuşma tabanı şişirmesin.
            noiseFloor = clamp(noiseFloor * 0.98f + rms * 0.02f, 20f, 2000f);
        }
        speech = detected;
        return detected;
    }

    public boolean isSpeech() {
        return speech;
    }

    public float lastRms() {
        return lastRms;
    }

    public float noiseFloor() {
        return noiseFloor;
    }

    public void reset() {
        noiseFloor = 120f;
        lastRms = 0f;
        speech = false;
    }

    public static float rms(short[] pcm, int length) {
        if (pcm == null || length <= 0) {
            return 0f;
        }
        double sum = 0d;
        for (int i = 0; i < length; i++) {
            double value = pcm[i];
            sum += value * value;
        }
        return (float) Math.sqrt(sum / length);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
