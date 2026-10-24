package com.mapcontrol.tts;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/** TTS konuşurken medyayı kısmak için transient-may-duck audio focus. */
public final class TtsAudioFocus {
    private static final String TAG = "TtsAudioFocus";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** Medya duck'ının oturması için konuşmadan önce beklenen süre. */
    public static final long DUCK_SETTLE_MS = 400L;

    private static AudioManager manager;
    private static AudioFocusRequest request;

    private TtsAudioFocus() {
    }

    public static synchronized boolean acquire(Context context, Runnable onLoss) {
        releaseInternal(true);
        Context app = context.getApplicationContext();
        AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            return false;
        }
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .build();
        AudioFocusRequest req = new AudioFocusRequest.Builder(
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(change -> {
                    if (change == AudioManager.AUDIOFOCUS_LOSS
                            || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        if (onLoss != null) {
                            MAIN.post(onLoss);
                        }
                    }
                })
                .build();
        int result = am.requestAudioFocus(req);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            manager = am;
            request = req;
            Log.i(TAG, "Audio focus alındı: TRANSIENT_MAY_DUCK");
            return true;
        }
        Log.w(TAG, "Audio focus reddedildi: " + result);
        return false;
    }

    public static synchronized void release() {
        releaseInternal(true);
    }

    private static void releaseInternal(boolean abandon) {
        if (manager != null && request != null && abandon) {
            try {
                manager.abandonAudioFocusRequest(request);
                Log.i(TAG, "Audio focus bırakıldı");
            } catch (Throwable ignored) {
            }
        }
        manager = null;
        request = null;
    }
}
