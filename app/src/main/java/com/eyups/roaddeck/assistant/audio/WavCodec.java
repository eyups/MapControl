package com.eyups.roaddeck.assistant.audio;

/** PCM16 mono → WAV (bellekte; diske yazılmaz). */
public final class WavCodec {

    private WavCodec() {
    }

    public static byte[] toWav(short[] pcm, int length, int sampleRate) {
        int samples = pcm == null ? 0 : Math.max(0, Math.min(length, pcm.length));
        int dataSize = samples * 2;
        byte[] out = new byte[44 + dataSize];
        writeAscii(out, 0, "RIFF");
        writeInt(out, 4, 36 + dataSize);
        writeAscii(out, 8, "WAVE");
        writeAscii(out, 12, "fmt ");
        writeInt(out, 16, 16);          // PCM chunk size
        writeShort(out, 20, 1);         // PCM format
        writeShort(out, 22, 1);         // mono
        writeInt(out, 24, sampleRate);
        writeInt(out, 28, sampleRate * 2);
        writeShort(out, 32, 2);         // block align
        writeShort(out, 34, 16);        // bits per sample
        writeAscii(out, 36, "data");
        writeInt(out, 40, dataSize);
        int offset = 44;
        for (int i = 0; i < samples; i++) {
            short value = pcm[i];
            out[offset++] = (byte) (value & 0xFF);
            out[offset++] = (byte) ((value >> 8) & 0xFF);
        }
        return out;
    }

    /** Yaklaşık süre (ms). */
    public static long durationMs(int samples, int sampleRate) {
        if (sampleRate <= 0) {
            return 0L;
        }
        return (long) samples * 1000L / sampleRate;
    }

    private static void writeAscii(byte[] out, int offset, String text) {
        for (int i = 0; i < 4 && i < text.length(); i++) {
            out[offset + i] = (byte) text.charAt(i);
        }
    }

    private static void writeInt(byte[] out, int offset, int value) {
        out[offset] = (byte) (value & 0xFF);
        out[offset + 1] = (byte) ((value >> 8) & 0xFF);
        out[offset + 2] = (byte) ((value >> 16) & 0xFF);
        out[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    private static void writeShort(byte[] out, int offset, int value) {
        out[offset] = (byte) (value & 0xFF);
        out[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }
}
