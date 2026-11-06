package com.eyups.roaddeck.assistant.audio;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EnergyGateTest {

    private static short[] tone(int amplitude, int length) {
        short[] data = new short[length];
        for (int i = 0; i < length; i++) {
            data[i] = (short) (i % 2 == 0 ? amplitude : -amplitude);
        }
        return data;
    }

    private static short[] silence(int length) {
        return new short[length];
    }

    @Test
    public void silenceIsRejected() {
        EnergyGate gate = new EnergyGate(EnergyGate.SENSITIVITY_MEDIUM);
        for (int i = 0; i < 20; i++) {
            assertFalse(gate.process(silence(EnergyGate.FRAME_SAMPLES),
                    EnergyGate.FRAME_SAMPLES));
        }
    }

    @Test
    public void loudSpeechLikeFrameTriggers() {
        EnergyGate gate = new EnergyGate(EnergyGate.SENSITIVITY_MEDIUM);
        assertTrue(gate.process(tone(4000, EnergyGate.FRAME_SAMPLES),
                EnergyGate.FRAME_SAMPLES));
    }

    @Test
    public void lowSensitivityNeedsMoreEnergy() {
        EnergyGate low = new EnergyGate(EnergyGate.SENSITIVITY_LOW);
        EnergyGate high = new EnergyGate(EnergyGate.SENSITIVITY_HIGH);
        short[] soft = tone(300, EnergyGate.FRAME_SAMPLES);
        assertFalse(low.process(soft, EnergyGate.FRAME_SAMPLES));
        assertTrue(high.process(soft, EnergyGate.FRAME_SAMPLES));
    }

    @Test
    public void noiseFloorAdaptsToQuietRoom() {
        EnergyGate gate = new EnergyGate(EnergyGate.SENSITIVITY_MEDIUM);
        float before = gate.noiseFloor();
        for (int i = 0; i < 100; i++) {
            gate.process(tone(60, EnergyGate.FRAME_SAMPLES), EnergyGate.FRAME_SAMPLES);
        }
        assertTrue(gate.noiseFloor() < before);
        assertTrue(gate.noiseFloor() >= 20f);
    }

    @Test
    public void rmsOfKnownSignal() {
        assertEquals(1000f, EnergyGate.rms(tone(1000, 400), 400), 0.5f);
        assertEquals(0f, EnergyGate.rms(silence(400), 400), 0.01f);
        assertEquals(0f, EnergyGate.rms(null, 400), 0.01f);
    }

    @Test
    public void resetRestoresFloor() {
        EnergyGate gate = new EnergyGate(EnergyGate.SENSITIVITY_MEDIUM);
        gate.process(silence(EnergyGate.FRAME_SAMPLES), EnergyGate.FRAME_SAMPLES);
        gate.reset();
        assertEquals(120f, gate.noiseFloor(), 0.01f);
        assertFalse(gate.isSpeech());
    }
}
