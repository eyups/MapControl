package com.mapcontrol.assistant.audio;

/**
 * Uyanma sonrası komut kaydı: konuşma bitince 800 ms sessizlikte durur,
 * hiç konuşma yoksa 5 sn sonra iptal eder, en fazla 8 sn kaydeder.
 * Saf Java; zaman dışarıdan verilir (JVM test edilebilir).
 */
public final class UtteranceRecorder {

    public interface Listener {
        void onSpeechStart();

        void onComplete(short[] pcm, int length);

        void onCancelled(String reason);
    }

    public static final long SILENCE_END_MS = 800L;
    public static final long NO_SPEECH_TIMEOUT_MS = 5000L;
    public static final long MAX_DURATION_MS = 8000L;

    private final EnergyGate gate;
    private final Listener listener;
    private final int maxSamples;

    private short[] buffer;
    private int length;
    private long startedAtMs;
    private long lastSpeechAtMs;
    private boolean speechStarted;
    private int speechRun;
    private boolean active;

    public UtteranceRecorder(EnergyGate gate, Listener listener) {
        this.gate = gate;
        this.listener = listener;
        this.maxSamples = (int) (EnergyGate.SAMPLE_RATE * MAX_DURATION_MS / 1000L);
        this.buffer = new short[EnergyGate.SAMPLE_RATE * 2];
    }

    public boolean isActive() {
        return active;
    }

    public void start(long nowMs) {
        buffer = new short[EnergyGate.SAMPLE_RATE * 2];
        length = 0;
        startedAtMs = nowMs;
        lastSpeechAtMs = nowMs;
        speechStarted = false;
        speechRun = 0;
        active = true;
        if (gate != null) {
            gate.reset();
        }
    }

    public void feed(short[] pcm, int count, long nowMs) {
        if (!active || pcm == null || count <= 0) {
            return;
        }
        long elapsed = nowMs - startedAtMs;
        boolean speech = gate != null && gate.process(pcm, count);
        if (speech) {
            if (speechRun < 6) {
                speechRun++;
            }
            // Tek karelik tık/bip/TTS kuyruğu konuşma sayılmasın; en az 150 ms.
            if (!speechStarted && speechRun >= 6) {
                speechStarted = true;
                if (listener != null) {
                    listener.onSpeechStart();
                }
            }
            if (speechStarted) {
                lastSpeechAtMs = nowMs;
            }
        } else {
            speechRun = 0;
        }

        // Konuşma başlamadan önce gelen çerçeveler de tampona girer (başlangıç kırpılmasın).
        int room = maxSamples - length;
        int copy = Math.min(count, Math.max(0, room));
        if (copy > 0) {
            ensureCapacity(length + copy);
            System.arraycopy(pcm, 0, buffer, length, copy);
            length += copy;
        }

        if (!speechStarted && elapsed >= NO_SPEECH_TIMEOUT_MS) {
            cancelInternal("no_speech");
            return;
        }
        if (elapsed >= MAX_DURATION_MS) {
            complete();
            return;
        }
        if (speechStarted && nowMs - lastSpeechAtMs >= SILENCE_END_MS) {
            complete();
        }
    }

    /** Kullanıcı butonu bıraktı / iptal etti. */
    public void stopAndComplete() {
        if (!active) {
            return;
        }
        if (!speechStarted && length < EnergyGate.SAMPLE_RATE / 4) {
            cancelInternal("empty");
            return;
        }
        complete();
    }

    public void cancel(String reason) {
        if (!active) {
            return;
        }
        cancelInternal(reason);
    }

    private void complete() {
        active = false;
        short[] result = new short[length];
        System.arraycopy(buffer, 0, result, 0, length);
        int resultLength = length;
        reset();
        if (listener != null) {
            listener.onComplete(result, resultLength);
        }
    }

    private void cancelInternal(String reason) {
        active = false;
        reset();
        if (listener != null) {
            listener.onCancelled(reason);
        }
    }

    private void reset() {
        buffer = new short[EnergyGate.SAMPLE_RATE / 2];
        length = 0;
        speechStarted = false;
    }

    private void ensureCapacity(int needed) {
        if (needed <= buffer.length) {
            return;
        }
        int next = Math.min(maxSamples, Math.max(needed, buffer.length * 2));
        short[] grown = new short[next];
        System.arraycopy(buffer, 0, grown, 0, length);
        buffer = grown;
    }
}
