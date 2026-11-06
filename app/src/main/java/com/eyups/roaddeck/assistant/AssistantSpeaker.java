package com.eyups.roaddeck.assistant;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;

import com.eyups.roaddeck.tts.IflytekTts;
import com.eyups.roaddeck.tts.RoadDeckSpeech;
import com.eyups.roaddeck.tts.SpeechText;
import com.eyups.roaddeck.tts.TtsAudioFocus;
import com.eyups.roaddeck.tts.TurkishSpeechText;

/**
 * Asistan seslendirme: iFlytek (araç kanalı) varsayılan, yoksa Sherpa.
 * Barge-in: {@link #stop()} anında keser ve dinleyiciye haber verir.
 */
public final class AssistantSpeaker {

    public interface Listener {
        void onDone(boolean interrupted);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long FALLBACK_BASE_MS = 1500L;
    private static final long FALLBACK_PER_CHAR_MS = 95L;

    private final Context appContext;
    private volatile boolean speaking;
    private int generation;
    private Runnable fallbackFinish;
    private ToneGenerator tone;

    public AssistantSpeaker(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public boolean isSpeaking() {
        return speaking;
    }

    public void speak(String text, Listener listener) {
        String clean = clean(text);
        if (clean.isEmpty()) {
            if (listener != null) {
                MAIN.post(() -> listener.onDone(false));
            }
            return;
        }
        stopInternal(false);
        speaking = true;
        final int currentGeneration = ++generation;
        if ("iflytek".equals(AssistantPrefs.ttsEngine(appContext))) {
            IflytekTts.speak(appContext, clean, (ok, message) -> {
                if (!ok) {
                    speakSherpa(clean, currentGeneration, listener);
                }
            }, () -> finish(currentGeneration, false, listener));
        } else {
            speakSherpa(clean, currentGeneration, listener);
        }
    }

    private void speakSherpa(String text, int ignoredGeneration, Listener listener) {
        // Fallback yeni bir nesil açar; iFlytek'in geç gelen "bitti" çağrısı
        // bu konuşmayı erken kesmesin.
        final int currentGeneration = ++generation;
        RoadDeckSpeech.stop();
        com.eyups.roaddeck.tts.RoadDeckTts.speak(appContext, text);
        TtsAudioFocus.acquire(appContext, null);
        long duration = FALLBACK_BASE_MS
                + Math.min(55000L, text.length() * FALLBACK_PER_CHAR_MS);
        Runnable finish = () -> finish(currentGeneration, false, listener);
        fallbackFinish = finish;
        MAIN.postDelayed(finish, duration);
    }

    private void finish(int currentGeneration, boolean interrupted, Listener listener) {
        if (currentGeneration != generation) {
            return;
        }
        speaking = false;
        if (fallbackFinish != null) {
            MAIN.removeCallbacks(fallbackFinish);
            fallbackFinish = null;
        }
        TtsAudioFocus.release();
        if (listener != null) {
            MAIN.post(() -> listener.onDone(interrupted));
        }
    }

    /** Barge-in: konuşmayı kes. */
    public void stop() {
        stopInternal(true);
    }

    private void stopInternal(boolean notify) {
        boolean wasSpeaking = speaking;
        generation++;
        speaking = false;
        if (fallbackFinish != null) {
            MAIN.removeCallbacks(fallbackFinish);
            fallbackFinish = null;
        }
        try {
            IflytekTts.stop(appContext, null);
        } catch (Throwable ignored) {
        }
        RoadDeckSpeech.stop();
        if (wasSpeaking && notify) {
            TtsAudioFocus.release();
        }
    }

    /** Uyanma bip'i; ayar "tone" ise kullanılır. */
    public void beep() {
        try {
            tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 55);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
            MAIN.postDelayed(() -> {
                if (tone != null) {
                    tone.release();
                    tone = null;
                }
            }, 500L);
        } catch (Throwable ignored) {
        }
    }

    /** Markdown/emoji ve TTS direktiflerini temizler. */
    public static String clean(String text) {
        if (text == null) {
            return "";
        }
        String value = SpeechText.plain(text)
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .replace("#", "")
                .replaceAll("[\\p{So}\\p{Cn}]", "")
                .replaceAll("\\s+", " ")
                .trim();
        return TurkishSpeechText.normalize(value);
    }
}
