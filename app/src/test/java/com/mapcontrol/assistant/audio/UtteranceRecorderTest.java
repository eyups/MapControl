package com.mapcontrol.assistant.audio;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class UtteranceRecorderTest {

    private static final int FRAME = EnergyGate.FRAME_SAMPLES;
    private static final int STEP_MS = 20;

    private static short[] speech() {
        short[] data = new short[FRAME];
        for (int i = 0; i < FRAME; i++) {
            data[i] = (short) (i % 2 == 0 ? 4000 : -4000);
        }
        return data;
    }

    private static short[] silence() {
        return new short[FRAME];
    }

    @Test
    public void completesAfterSilence() {
        final short[][] captured = new short[1][];
        final boolean[] started = {false};
        UtteranceRecorder recorder = new UtteranceRecorder(
                new EnergyGate(EnergyGate.SENSITIVITY_MEDIUM),
                new UtteranceRecorder.Listener() {
                    @Override
                    public void onSpeechStart() {
                        started[0] = true;
                    }

                    @Override
                    public void onComplete(short[] pcm, int length) {
                        captured[0] = pcm;
                    }

                    @Override
                    public void onCancelled(String reason) {
                    }
                });
        long now = 0L;
        recorder.start(now);
        for (int i = 0; i < 25; i++) {
            now += STEP_MS;
            recorder.feed(speech(), FRAME, now);
        }
        assertTrue(started[0]);
        int silenceFrames = (int) (UtteranceRecorder.SILENCE_END_MS / STEP_MS) + 2;
        for (int i = 0; i < silenceFrames; i++) {
            now += STEP_MS;
            recorder.feed(silence(), FRAME, now);
        }
        assertFalse(recorder.isActive());
        assertNotNull(captured[0]);
        assertTrue(captured[0].length > 0);
    }

    @Test
    public void cancelsWhenNoSpeech() {
        final String[] reason = new String[1];
        UtteranceRecorder recorder = new UtteranceRecorder(
                new EnergyGate(EnergyGate.SENSITIVITY_MEDIUM),
                new UtteranceRecorder.Listener() {
                    @Override
                    public void onSpeechStart() {
                    }

                    @Override
                    public void onComplete(short[] pcm, int length) {
                    }

                    @Override
                    public void onCancelled(String value) {
                        reason[0] = value;
                    }
                });
        long now = 0L;
        recorder.start(now);
        for (int i = 0; i < 260; i++) {
            now += STEP_MS;
            recorder.feed(silence(), FRAME, now);
        }
        assertEquals("no_speech", reason[0]);
        assertFalse(recorder.isActive());
    }

    @Test
    public void stopsAtMaxDuration() {
        final int[] length = new int[1];
        UtteranceRecorder recorder = new UtteranceRecorder(
                new EnergyGate(EnergyGate.SENSITIVITY_MEDIUM),
                new UtteranceRecorder.Listener() {
                    @Override
                    public void onSpeechStart() {
                    }

                    @Override
                    public void onComplete(short[] pcm, int count) {
                        length[0] = count;
                    }

                    @Override
                    public void onCancelled(String reason) {
                    }
                });
        long now = 0L;
        recorder.start(now);
        int frames = (int) (UtteranceRecorder.MAX_DURATION_MS / STEP_MS) + 5;
        for (int i = 0; i < frames; i++) {
            now += STEP_MS;
            recorder.feed(speech(), FRAME, now);
        }
        assertFalse(recorder.isActive());
        assertTrue(length[0] >= EnergyGate.SAMPLE_RATE * 7);
    }

    @Test
    public void emptyStopCancels() {
        final String[] reason = new String[1];
        UtteranceRecorder recorder = new UtteranceRecorder(
                new EnergyGate(EnergyGate.SENSITIVITY_MEDIUM),
                new UtteranceRecorder.Listener() {
                    @Override
                    public void onSpeechStart() {
                    }

                    @Override
                    public void onComplete(short[] pcm, int count) {
                    }

                    @Override
                    public void onCancelled(String value) {
                        reason[0] = value;
                    }
                });
        recorder.start(0L);
        recorder.stopAndComplete();
        assertEquals("empty", reason[0]);
    }
}
