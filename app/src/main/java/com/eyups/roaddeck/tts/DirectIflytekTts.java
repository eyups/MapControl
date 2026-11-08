package com.eyups.roaddeck.tts;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.SystemClock;
import android.util.Log;

import com.eyups.roaddeck.BuildConfig;
import com.iflytek.speech.ITtsListener;
import com.iflytek.speech.NativeTtsHandle;
import com.iflytek.speech.libisstts;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * iFlytek xTTS'i servis/AIDL olmadan doğrudan JNI ile çalıştırır.
 *
 * <p>Flavor seçimi BuildConfig ile yapılır:
 * {@code tts30 -> libissxtts30.so + /vr/iflytek/res/xTTSRes},
 * {@code tts40 -> libissxtts40.so + /vr/iflytek/resvr/xTTS40Res}.
 * Her iki flavor aynı JNI sembolünü export ettiği için aynı process'te
 * yalnızca biri yüklenebilir.</p>
 */
final class DirectIflytekTts {
    private static final String TAG = "DirectIflytekTts";
    private static final String BENCH = "TtsBench";
    private static volatile long REQUEST_AT;
    private static final int SAMPLE_RATE = 24000;
    private static final int MODE_LOCAL = 0;
    private static final int ERR_NO_DATA = 10118;
    private static final int ERR_NO_ENOUGH_BUFFER = 10117;
    private static final int ERR_END = 10004;

    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "iflytek-direct-tts"));
    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static final Object LOCK = new Object();

    private static NativeTtsHandle handle;
    private static boolean initialized;
    private static String initError;
    private static volatile boolean endOfData;

    private static volatile int activeGeneration;
    private static volatile IflytekTts.ResultListener activeListener;
    private static volatile Runnable activeFinished;
    private static final AtomicBoolean finished = new AtomicBoolean(true);

    private static AudioTrack track;

    private DirectIflytekTts() {
    }

    static void speak(Context context, String text, IflytekTts.ResultListener listener,
                      Runnable onFinished) {
        if (context == null || text == null || text.trim().isEmpty()) {
            if (listener != null) {
                listener.onResult(false, "Metin boş.");
            }
            if (onFinished != null) {
                onFinished.run();
            }
            return;
        }
        REQUEST_AT = SystemClock.elapsedRealtime();
        Log.i(BENCH, "flavor=" + BuildConfig.TTS_FLAVOR + " phase=request");
        Context app = context.getApplicationContext();
        int generation = GENERATION.incrementAndGet();
        activeGeneration = generation;
        activeListener = listener;
        activeFinished = onFinished;
        finished.set(false);
        endOfData = false;
        EXECUTOR.execute(() -> run(app, text.trim(), generation, listener, onFinished));
    }

    static void stop(Context context, IflytekTts.ResultListener listener) {
        GENERATION.incrementAndGet();
        stopInternal();
        report(listener, true, "Doğrudan iFlytek TTS durduruldu.");
        finishOnce(null);
    }

    private static void run(Context context, String text, int generation,
                            IflytekTts.ResultListener listener, Runnable onFinished) {
        long benchStart = SystemClock.elapsedRealtime();
        if (generation != activeGeneration) {
            finishOnce(onFinished);
            return;
        }
        boolean initOk = ensureInitialized(context);
        Log.i(BENCH, "flavor=" + BuildConfig.TTS_FLAVOR + " phase=init ms="
                + (SystemClock.elapsedRealtime() - REQUEST_AT) + " ok=" + initOk);
        if (!initOk) {
            report(listener, false, initError == null ? "TTS başlatılamadı." : initError);
            finishOnce(onFinished);
            return;
        }
        boolean focus = false;
        try {
            synchronized (LOCK) {
                if (generation != activeGeneration) {
                    finishOnce(onFinished);
                    return;
                }
                libisstts.stop(handle);
            }
            focus = TtsAudioFocus.acquireTts(context, () -> stop(context, null));
            if (focus) {
                try {
                    Thread.sleep(TtsAudioFocus.DUCK_SETTLE_MS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            AudioTrack output = ensureTrack();
            if (output == null) {
                report(listener, false, "AudioTrack açılamadı.");
                finishOnce(onFinished);
                return;
            }
            output.play();
            int started;
            long engineStart = SystemClock.elapsedRealtime();
            synchronized (LOCK) {
                if (generation != activeGeneration) {
                    finishOnce(onFinished);
                    return;
                }
                started = libisstts.start(handle, text);
            }
            Log.i(BENCH, "flavor=" + BuildConfig.TTS_FLAVOR + " phase=start ms="
                    + (SystemClock.elapsedRealtime() - engineStart) + " ret=" + started);
            if (started != 0) {
                report(listener, false, "iFlytek start hatası: " + started);
                finishOnce(onFinished);
                return;
            }
            report(listener, true, null);

            byte[] buffer = new byte[8192];
            int[] info = new int[1];
            long lastData = 0L;
            long began = SystemClock.elapsedRealtime();
            boolean anyData = false;
            long bytesTotal = 0L;
            boolean firstLogged = false;
            while (generation == activeGeneration && !finished.get()) {
                info[0] = 0;
                int ret = libisstts.getAudioData(handle, buffer, buffer.length, info);
                if (info[0] > 0) {
                    try {
                        output.write(buffer, 0, info[0]);
                    } catch (Throwable t) {
                        Log.w(TAG, "AudioTrack write hatası", t);
                        break;
                    }
                    if (!firstLogged) {
                        Log.i(BENCH, "flavor=" + BuildConfig.TTS_FLAVOR
                                + " phase=first_audio ms="
                                + (SystemClock.elapsedRealtime() - REQUEST_AT));
                        firstLogged = true;
                    }
                    anyData = true;
                    bytesTotal += info[0];
                    lastData = SystemClock.elapsedRealtime();
                }
                long now = SystemClock.elapsedRealtime();
                if (ret == ERR_NO_DATA || ret == ERR_NO_ENOUGH_BUFFER) {
                    if (anyData && now - lastData > 2000L) {
                        break;
                    }
                    if (!anyData && now - began > 15000L) {
                        break;
                    }
                    Thread.sleep(20L);
                    continue;
                }
                if (ret == ERR_END) {
                    Log.w(TAG, "getAudioData end ret=" + ret + " bytes=" + bytesTotal);
                    break;
                }
                if (ret != 0) {
                    Log.w(TAG, "getAudioData ret=" + ret + " err=" + handle.err_ret);
                    break;
                }
                if (endOfData && info[0] == 0) {
                    break;
                }
                if (anyData && now - lastData > 2000L) {
                    break;
                }
                if (info[0] == 0) {
                    Thread.sleep(10L);
                }
            }
            Log.i(BENCH, "flavor=" + BuildConfig.TTS_FLAVOR + " phase=last_audio ms="
                    + (SystemClock.elapsedRealtime() - REQUEST_AT) + " bytes=" + bytesTotal);
            Log.i(BENCH, "flavor=" + BuildConfig.TTS_FLAVOR + " phase=done ms="
                    + (SystemClock.elapsedRealtime() - REQUEST_AT) + " bytes=" + bytesTotal);
            synchronized (LOCK) {
                if (handle != null) {
                    libisstts.stop(handle);
                }
            }
            try {
                output.stop();
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            Log.e(TAG, "Doğrudan TTS hatası", t);
        } finally {
            releaseTrack();
            if (focus) {
                TtsAudioFocus.release();
            }
            finishOnce(onFinished);
        }
    }

    private static boolean ensureInitialized(Context context) {
        synchronized (LOCK) {
            if (initialized) {
                return true;
            }
            if (initError != null) {
                return false;
            }
            String path = resolveResourcePath();
            if (path == null) {
                initError = "iFlytek TTS kaynak dizini bulunamadı: " + BuildConfig.TTS_RES_PATH;
                Log.e(TAG, initError);
                return false;
            }
            try {
                Log.i(TAG, "initRes " + path + " flavor=" + BuildConfig.TTS_FLAVOR);
                int r = libisstts.initRes(path, 1);
                if (r != 0 && r != 10110) {
                    initError = "initRes hatası: " + r + " (" + path + ")";
                    Log.e(TAG, initError);
                    return false;
                }
                handle = new NativeTtsHandle();
                int c = libisstts.create(handle, CALLBACK);
                if (c != 0) {
                    initError = "create hatası: " + c;
                    Log.e(TAG, initError);
                    return false;
                }
                libisstts.setParam(handle, libisstts.ISS_TTS_PARAM_MODE, MODE_LOCAL);
                libisstts.setParam(handle, libisstts.ISS_TTS_PARAM_VOICE_SPEED, 0);
                libisstts.setParam(handle, libisstts.ISS_TTS_PARAM_VOICE_PITCH, 0);
                libisstts.setParam(handle, libisstts.ISS_TTS_PARAM_VOLUME, 0);
                int role = RoadDeckSpeech.iflytekRole(context);
                if (role > 0) {
                    int speakerRet = libisstts.setParam(
                            handle, libisstts.ISS_TTS_PARAM_SPEAKER, role);
                    Log.i(TAG, "speaker=" + role + " ret=" + speakerRet);
                }
                initialized = true;
                return true;
            } catch (Throwable t) {
                initError = t.getClass().getSimpleName() + ": " + t.getMessage();
                Log.e(TAG, "TTS init hatası", t);
                return false;
            }
        }
    }

    private static String resolveResourcePath() {
        String configured = BuildConfig.TTS_RES_PATH;
        if (configured != null && !configured.isEmpty() && new File(configured).isDirectory()) {
            return configured;
        }
        String[] fallbacks = BuildConfig.TTS_IS_40
                ? new String[]{"/data/local/tmp/tts40/xTTS40Res", "/data/local/tmp/xTTS40Res"}
                : new String[]{"/data/local/tmp/tts30/xTTSRes", "/data/local/tmp/xTTSRes"};
        for (String path : fallbacks) {
            if (new File(path).isDirectory()) {
                return path;
            }
        }
        return null;
    }

    private static AudioTrack ensureTrack() {
        synchronized (LOCK) {
            if (track != null) {
                return track;
            }
            int min = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) {
                min = SAMPLE_RATE * 2;
            }
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .build();
            AudioFormat format = new AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();
            try {
                track = new AudioTrack.Builder()
                        .setAudioAttributes(attrs)
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(Math.max(min * 4, SAMPLE_RATE * 2))
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build();
            } catch (Throwable t) {
                Log.e(TAG, "AudioTrack oluşturulamadı", t);
                track = null;
            }
            return track;
        }
    }

    private static void releaseTrack() {
        synchronized (LOCK) {
            if (track == null) {
                return;
            }
            try {
                track.pause();
                track.flush();
                track.release();
            } catch (Throwable ignored) {
            }
            track = null;
        }
    }

    private static void stopInternal() {
        endOfData = true;
        synchronized (LOCK) {
            if (handle != null) {
                try {
                    libisstts.stop(handle);
                } catch (Throwable ignored) {
                }
            }
        }
        releaseTrack();
        TtsAudioFocus.release();
    }

    private static void finishOnce(Runnable onFinished) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        Runnable callback = activeFinished;
        activeFinished = null;
        if (callback != null) {
            callback.run();
        } else if (onFinished != null) {
            onFinished.run();
        }
    }

    private static void report(IflytekTts.ResultListener listener, boolean ok, String message) {
        if (listener != null) {
            try {
                listener.onResult(ok, message);
            } catch (Throwable t) {
                Log.w(TAG, "TTS listener hatası", t);
            }
        }
    }

    private static final ITtsListener CALLBACK = new ITtsListener() {
        @Override
        public void onProgress(int index, int progress) {
        }

        @Override
        public void onTtsMsgProc(int msg, int value, String json) {
            if (msg == 20002) {
                endOfData = true;
            }
        }
    };
}
