package com.eyups.roaddeck.cloud;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.util.Log;

/**
 * Park fotoğrafı tamamlandı sesi: yükselen iki nota (C6 → G6), yumuşak çan tınısı, ~0,55 sn.
 * Ses dosyası yok; PCM koda gömülü sentezlenir.
 *
 * <p><b>Neden USAGE_ALARM:</b> araçta kontak kapanınca `STREAM_MUSIC` seviyesi 0'a iniyor;
 * AudioFlinger "write muted data to hw" yazıyor ve medya akışı duyulmuyor. Alarm akışı
 * kontak kapalıyken de açık kalıyor. Track sesi 0,4 ile ölçeklenir; çok yüksek çalmaz.
 */
final class ParkChime {

    private static final String TAG = "ParkChime";
    static final int SAMPLE_RATE = 44_100;
    private static final double PEAK = 0.55;

    private ParkChime() {
    }

    /** 16 bit mono PCM; Android'e bağlı değildir (JVM testleri). */
    static short[] synthesize(int sampleRate) {
        double total = 0.56;
        int length = (int) (total * sampleRate);
        double[] mix = new double[length];
        addNote(mix, sampleRate, 1046.50, 0.00, 0.22, 0.075);
        addNote(mix, sampleRate, 1567.98, 0.11, 0.45, 0.150);
        double max = 1e-9;
        for (double value : mix) {
            max = Math.max(max, Math.abs(value));
        }
        double gain = PEAK / max;
        int fade = Math.max(1, sampleRate / 50);
        short[] pcm = new short[length];
        for (int i = 0; i < length; i++) {
            double sample = mix[i] * gain;
            if (i >= length - fade) {
                sample *= (length - 1 - i) / (double) fade;
            }
            pcm[i] = (short) Math.round(Math.max(-1.0, Math.min(1.0, sample)) * Short.MAX_VALUE);
        }
        return pcm;
    }

    private static void addNote(double[] mix, int sampleRate, double frequency,
            double startS, double durationS, double decayS) {
        int start = (int) (startS * sampleRate);
        int count = Math.min(mix.length - start, (int) (durationS * sampleRate));
        double attack = 0.006 * sampleRate;
        for (int i = 0; i < count; i++) {
            double t = i / (double) sampleRate;
            double envelope = Math.min(1.0, i / attack) * Math.exp(-t / decayS);
            double phase = 2.0 * Math.PI * frequency * t;
            // Temel + hafif 2. ve 3. harmonik: sert bip yerine yumuşak çan.
            double tone = Math.sin(phase) + 0.30 * Math.sin(2.0 * phase) + 0.10 * Math.sin(3.0 * phase);
            mix[start + i] += tone * envelope;
        }
    }

    /** Arka planda çalar; hata sessizce loglanır. */
    static void play(Context context) {
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            AudioTrack track = null;
            AudioManager audio = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            AudioFocusRequest focus = null;
            try {
                short[] pcm = synthesize(SAMPLE_RATE);
                if (audio != null) {
                    Log.i(TAG, "alarmVol=" + audio.getStreamVolume(AudioManager.STREAM_ALARM)
                            + " musicVol=" + audio.getStreamVolume(AudioManager.STREAM_MUSIC));
                }
                if (audio != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    focus = new AudioFocusRequest.Builder(
                            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                            .setAudioAttributes(attrs)
                            .build();
                    audio.requestAudioFocus(focus);
                }
                track = new AudioTrack.Builder()
                        .setAudioAttributes(attrs)
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .setBufferSizeInBytes(pcm.length * 2)
                        .build();
                track.write(pcm, 0, pcm.length);
                track.setVolume(0.4f);
                track.play();
                Thread.sleep(pcm.length * 1000L / SAMPLE_RATE + 120L);
            } catch (Throwable error) {
                Log.w(TAG, "ses çalınamadı: " + error.getClass().getSimpleName());
            } finally {
                if (track != null) {
                    try {
                        track.stop();
                    } catch (Throwable ignored) {
                    }
                    track.release();
                }
                if (audio != null && focus != null) {
                    audio.abandonAudioFocusRequest(focus);
                }
            }
        }, "RoadDeckParkChime").start();
    }
}
