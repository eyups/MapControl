package com.eyups.roaddeck.assistant.audio;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioRecordingConfiguration;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import java.util.List;

import com.eyups.roaddeck.BuildConfig;

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
    private int captureRate = SAMPLE_RATE;
    private int captureChannels = 1;
    private int captureFrameSamples = FRAME_SAMPLES;
    private long startedAtMs;
    private boolean silentProbeDone;
    private long probeEnergy;
    private boolean released;
    /** OEM VR mikrofonu bu kayıt için bırakıldı mı. */
    private boolean micArbiterHeld;
    /** Debug: emülatör mikrofonu sessizse PCM dosyasını gerçek zamanlı besle. */
    private static final String DEBUG_MIC_SETTING = "va_debug_mic";
    private short[] debugPcm;
    private int debugIndex;
    private boolean debugConsumed;

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
        if (BuildConfig.DEBUG && startDebugSource()) {
            return;
        }
        // Emulator HAL'i 16 kHz mono capture'i reddediyor/sessiz birakiyor;
        // 48 kHz stereo acip 16 kHz mono'ya indiriyoruz. Aracta 16 kHz mono kalir.
        boolean emulator = Build.FINGERPRINT.contains("generic")
                || Build.HARDWARE.contains("ranchu")
                || Build.HARDWARE.contains("goldfish")
                || Build.PRODUCT.contains("sdk");
        captureRate = emulator ? 48000 : SAMPLE_RATE;
        captureChannels = emulator ? 2 : 1;
        captureFrameSamples = emulator ? 960 : FRAME_SAMPLES;
        int channelConfig = captureChannels == 2
                ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO;
        int minBuffer = AudioRecord.getMinBufferSize(captureRate,
                channelConfig, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuffer <= 0) {
            minBuffer = captureFrameSamples * 2 * captureChannels * 8;
        }
        int bufferSize = Math.max(minBuffer, captureFrameSamples * 2 * captureChannels * 4);
        // OEM VR mikrofonu sürekli tutuyorsa yeni kayıt sessiz kalır; önce bırak.
        if (!micArbiterHeld && OemMicArbiter.release(appContext)) {
            micArbiterHeld = true;
            awaitOemCaptureRelease(600L);
        }
        record = createRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, bufferSize);
        if (record == null) {
            record = createRecord(MediaRecorder.AudioSource.MIC, bufferSize);
        }
        if (record == null) {
            notifyError("Mikrofon açılamadı.");
            releaseRecord();
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
        Log.i(TAG, "AudioRecord başladı (source=" + record.getAudioSource()
                + ", " + captureRate + " Hz, " + captureChannels + " ch)");
        startedAtMs = SystemClock.elapsedRealtime();
        silentProbeDone = false;
        probeEnergy = 0L;
        handler.post(this::readLoop);
    }

    /**
     * Yalnız debug: {@code settings put global va_debug_mic <pcm-yolu>} ile
     * 16 kHz mono PCM16 dosyası, AudioRecord yerine 20 ms'lik çerçevelerle
     * gerçek zamanlı beslenir. Emülatörün sanal mikrofonu sessiz kaldığında
     * uyandırma + komut hattını deterministik test etmek içindir.
     */
    private boolean startDebugSource() {
        String path;
        try {
            path = Settings.Global.getString(appContext.getContentResolver(),
                    DEBUG_MIC_SETTING);
        } catch (Throwable t) {
            return false;
        }
        if (path == null || path.trim().isEmpty() || debugConsumed) {
            return false;
        }
        try {
            java.io.File file = new java.io.File(path.trim());
            if (!file.isFile()) {
                return false;
            }
            byte[] data = new byte[(int) file.length()];
            java.io.FileInputStream in = new java.io.FileInputStream(file);
            try {
                int offset = 0;
                while (offset < data.length) {
                    int read = in.read(data, offset, data.length - offset);
                    if (read <= 0) {
                        break;
                    }
                    offset += read;
                }
            } finally {
                in.close();
            }
            int samples = data.length / 2;
            if (samples < FRAME_SAMPLES) {
                return false;
            }
            short[] pcm = new short[samples];
            for (int i = 0; i < samples; i++) {
                int lo = data[i * 2] & 0xFF;
                int hi = data[i * 2 + 1];
                pcm[i] = (short) ((hi << 8) | lo);
            }
            debugPcm = pcm;
            debugIndex = 0;
            running = true;
            silentProbeDone = true;
            startedAtMs = SystemClock.elapsedRealtime();
            handler.post(this::debugLoop);
            Log.i(TAG, "Debug PCM mikrofon kaynağı: " + path + " (" + samples + " örnek)");
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "Debug PCM kaynağı okunamadı", t);
            return false;
        }
    }

    private void debugLoop() {
        long nextFrameAt = SystemClock.elapsedRealtime();
        while (running && debugPcm != null && debugIndex < debugPcm.length) {
            int count = Math.min(FRAME_SAMPLES, debugPcm.length - debugIndex);
            short[] frame = new short[count];
            System.arraycopy(debugPcm, debugIndex, frame, 0, count);
            debugIndex += count;
            long now = SystemClock.elapsedRealtime();
            Listener current = listener;
            if (current != null) {
                current.onFrame(frame, count, now);
            }
            nextFrameAt += 20L;
            long delay = nextFrameAt - SystemClock.elapsedRealtime();
            if (delay > 0L) {
                SystemClock.sleep(delay);
            } else {
                nextFrameAt = SystemClock.elapsedRealtime();
            }
        }
        if (debugPcm != null && debugIndex >= debugPcm.length) {
            debugConsumed = true;
        }
        running = false;
        debugPcm = null;
    }

    private AudioRecord createRecord(int source, int bufferSize) {
        try {
            int channelConfig = captureChannels == 2
                    ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO;
            AudioRecord created = new AudioRecord(source, captureRate,
                    channelConfig, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
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

    /** 48k stereo -> 16k mono (sol kanal, 3:1); 16k mono oldugu gibi. */
    private int toMono16k(short[] source, int read, short[] out) {
        if (read <= 0) return 0;
        if (captureRate == SAMPLE_RATE && captureChannels == 1) {
            int n = Math.min(read, out.length);
            System.arraycopy(source, 0, out, 0, n);
            return n;
        }
        int frames = read / captureChannels;
        int n = 0;
        for (int i = 0; i < frames && n < out.length; i += 3) {
            out[n++] = source[i * captureChannels];
        }
        return n;
    }

    private void readLoop() {
        short[] source = new short[captureFrameSamples * captureChannels];
        short[] frame = new short[FRAME_SAMPLES];
        while (running && record != null) {
            int read;
            try {
                read = record.read(source, 0, source.length);
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
            int length = toMono16k(source, read, frame);
            if (length <= 0) {
                continue;
            }
            if (!silentProbeDone) {
                for (int i = 0; i < length; i++) {
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
                current.onFrame(frame, length, now);
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
        if (micArbiterHeld) {
            OemMicArbiter.restore(appContext);
            micArbiterHeld = false;
        }
    }

    /** OEM VR kaydı gerçekten bırakana kadar kısa süre bekle (en fazla timeout). */
    private void awaitOemCaptureRelease(long timeoutMs) {
        AudioManager am = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            SystemClock.sleep(120L);
            return;
        }
        long end = SystemClock.elapsedRealtime() + timeoutMs;
        while (SystemClock.elapsedRealtime() < end) {
            try {
                List<AudioRecordingConfiguration> active = am.getActiveRecordingConfigurations();
                if (active == null || active.isEmpty()) {
                    return;
                }
            } catch (Throwable t) {
                SystemClock.sleep(120L);
                return;
            }
            SystemClock.sleep(50L);
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
