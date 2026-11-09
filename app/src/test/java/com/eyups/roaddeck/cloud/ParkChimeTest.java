package com.eyups.roaddeck.cloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ParkChimeTest {

    @Test
    public void chimeIsShortAndDoesNotClip() {
        short[] pcm = ParkChime.synthesize(ParkChime.SAMPLE_RATE);

        double seconds = pcm.length / (double) ParkChime.SAMPLE_RATE;
        assertTrue("süre " + seconds, seconds > 0.4 && seconds < 0.7);
        int peak = 0;
        for (short sample : pcm) {
            peak = Math.max(peak, Math.abs(sample));
        }
        assertTrue("tepe " + peak, peak > Short.MAX_VALUE * 0.45 && peak < Short.MAX_VALUE * 0.6);
    }

    @Test
    public void chimeStartsAndEndsSilentlyWithoutClick() {
        short[] pcm = ParkChime.synthesize(ParkChime.SAMPLE_RATE);

        assertEquals(0, pcm[0]);
        assertEquals(0, pcm[pcm.length - 1]);
    }

    @Test
    public void secondNoteIsHigherThanFirst() {
        short[] pcm = ParkChime.synthesize(ParkChime.SAMPLE_RATE);
        int rate = ParkChime.SAMPLE_RATE;

        double first = zeroCrossingHz(pcm, (int) (0.02 * rate), (int) (0.08 * rate), rate);
        double second = zeroCrossingHz(pcm, (int) (0.25 * rate), (int) (0.35 * rate), rate);

        assertTrue("ilk nota " + first, first > 900 && first < 1200);
        assertTrue("ikinci nota " + second, second > 1400 && second < 1750);
    }

    private static double zeroCrossingHz(short[] pcm, int from, int to, int rate) {
        int crossings = 0;
        for (int i = from + 1; i < to; i++) {
            if ((pcm[i - 1] < 0) != (pcm[i] < 0)) {
                crossings++;
            }
        }
        return crossings / 2.0 / ((to - from) / (double) rate);
    }
}
