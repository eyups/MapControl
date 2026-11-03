package com.mapcontrol.assistant.audio;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

/**
 * Tek {@link AudioRecord} sahibi. 16 kHz mono PCM16 okur, çerçeveleri
 * {@code va-audio} HandlerThread'inde dinleyiciye verir.
 *
 * <p>Asistan kapalıyken hiçbir örnek oluşturulmaz. Kaynak: önce
 * {@code VOICE_RECOGNITION}, başarısızsa {@code MIC}. Açılıştan sonraki 1 sn
 * tamamen sıfır veri gelirse mikrofon başka uygulamada kabul edilir ve
 * {@link Listener#onSilentInput()} çağrılır.</p>
 */
public final class AudioCaptureEngine {

    private static final String TAG = "VaAudio";
    public static final int SAMPLE_RATE = 16000;
    private static final int FRAME_SAMPLES = 320; // 20 ms
    private static final long SILENCE_PROBE_MS = 1000L;

    public interface Listener {
        void onFrame(short[] pcm, int length, long timeMs);

        void onError(String message);

        void onSilentInput();
    }

    private final Context appContext;
    private final HandlerThread thread;
    private final Handler handler;

    private volatile Listener listener;
    private volatile boolean running;
    private AudioRecord record;
    private long startedAtMs;
    private boolean silentProbeDone;
    private long probeEnergy;
    private boolean released;

    public AudioCaptureEngine(Context context) {
        this.appContext = context.getApplicationContext();
        this.thread = new HandlerThread("va-audio");
        this.thread.start();
        this.handler = new Handler(thread.getLooper());
    }

    public void setListener(Listener value) {
        this.listener = value;
    }

    public boolean isRunning() {
        return running;
    }

    /** HandlerThread üzerinde çalışır; bloklayabilir. */
    public void start() {
        handler.post(this::startInternal);
    }

    public void stop() {
        handler.post(this::stopInternal);
    }

    public void release() {
        try {
            handler.post(this::stopInternal);
            handler.post(() -> {
                released = true;
                thread.quitSafely();
            });
        } catch (Throwable ignored) {
        }
    }

    private void startInternal() {
        if (running || released) {
            return;
        }
        int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuffer <= 0) {
            minBuffer = FRAME_SAMPLES * 2 * 8;
        }
        int bufferSize = Math.max(minBuffer, FRAME_SAMPLES * 2 * 4);
        record = createRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, bufferSize);
        if (record == null) {
            record = createRecord(MediaRecorder.AudioSource.MIC, bufferSize);
        }
        if (record == null) {
            notifyError("Mikrofon açılamadı.");
            return;
        }
        try {
            record.startRecording();
        } catch (Throwable t) {
            notifyError("Mikrofon başlatılamadı: " + t.getClass().getSimpleName());
            releaseRecord();
            return;
        }
        if (record.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            notifyError("Mikrofon kayıt durumuna geçmedi.");
            releaseRecord();
            return;
        }
        running = true;
        startedAtMs = SystemClock.elapsedRealtime();
        silentProbeDone = false;
        probeEnergy = 0L;
        handler.post(this::readLoop);
    }

    private AudioRecord createRecord(int source, int bufferSize) {
        try {
            AudioRecord created = new AudioRecord(source, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
            if (created.getState() == AudioRecord.STATE_INITIALIZED) {
                return created;
            }
            try {
                created.release();
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            Log.w(TAG, "AudioRecord oluşturulamadı: " + t.getClass().getSimpleName());
        }
        return null;
    }

    private void readLoop() {
        short[] frame = new short[FRAME_SAMPLES];
        while (running && record != null) {
            int read;
            try {
                read = record.read(frame, 0, frame.length);
            } catch (Throwable t) {
                notifyError("Mikrofon okuma hatası.");
                stopInternal();
                return;
            }
            if (read <= 0) {
                if (read == AudioRecord.ERROR_INVALID_OPERATION
                        || read == AudioRecord.ERROR_DEAD_OBJECT) {
                    notifyError("Mikrofon bağlantısı koptu.");
                    stopInternal();
                    return;
                }
                continue;
            }
            long now = SystemClock.elapsedRealtime();
            if (!silentProbeDone) {
                for (int i = 0; i < read; i++) {
                    probeEnergy += Math.abs(frame[i]);
                }
                if (now - startedAtMs >= SILENCE_PROBE_MS) {
                    silentProbeDone = true;
                    if (probeEnergy == 0L) {
                        Listener current = listener;
                        if (current != null) {
                            current.onSilentInput();
                        }
                        stopInternal();
                        return;
                    }
                }
            }
            Listener current = listener;
            if (current != null) {
                current.onFrame(frame, read, now);
            }
        }
    }

    private void stopInternal() {
        running = false;
        releaseRecord();
    }

    private void releaseRecord() {
        AudioRecord current = record;
        record = null;
        if (current != null) {
            try {
                if (current.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    current.stop();
                }
            } catch (Throwable ignored) {
            }
            try {
                current.release();
            } catch (Throwable ignored) {
            }
        }
    }

    private void notifyError(final String message) {
        final Listener current = listener;
        if (current != null) {
            new Handler(Looper.getMainLooper()).post(() -> current.onError(message));
        }
    }

    /** Bluetooth/telefon gibi nedenlerle mikrofonu geçici bırakmak için. */
    public Context context() {
        return appContext;
    }
}
