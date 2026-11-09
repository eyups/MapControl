package com.eyups.roaddeck.cloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ParkPhotoPolicyTest {

    private static final int P = ParkPhotoPolicy.GEAR_PARK;
    private static final int D = 4;

    @Test
    public void capturesOnceAfterStableParkAndOnlyWhenPoweredOn() {
        ParkPhotoPolicy.ParkEpisode episode = new ParkPhotoPolicy.ParkEpisode();

        assertEquals(ParkPhotoPolicy.Action.NONE, episode.update(true, P, 0, 2, 1_000L));
        assertEquals(ParkPhotoPolicy.Action.NONE, episode.update(true, P, 0, 2, 1_500L));
        assertEquals(ParkPhotoPolicy.Action.CAPTURE, episode.update(true, P, 0, 2, 2_000L));
        assertEquals(ParkPhotoPolicy.Action.NONE, episode.update(true, P, 0, 2, 9_000L));
        assertEquals(ParkPhotoPolicy.Action.NONE, episode.update(true, P, 0, 0, 10_000L));
    }

    @Test
    public void leavingParkAfterCaptureDiscardsAndStartsNewEpisode() {
        ParkPhotoPolicy.ParkEpisode episode = new ParkPhotoPolicy.ParkEpisode();
        episode.update(true, P, 0, 2, 0L);
        episode.update(true, P, 0, 2, 1_000L);

        assertEquals(ParkPhotoPolicy.Action.DISCARD, episode.update(true, D, 0, 2, 5_000L));
        assertEquals(ParkPhotoPolicy.Action.NONE, episode.update(true, D, 12, 2, 6_000L));
        episode.update(true, P, 0, 2, 20_000L);
        assertEquals(ParkPhotoPolicy.Action.CAPTURE, episode.update(true, P, 0, 2, 21_000L));
    }

    @Test
    public void briefParkShorterThanStableWindowDoesNotCapture() {
        ParkPhotoPolicy.ParkEpisode episode = new ParkPhotoPolicy.ParkEpisode();
        episode.update(true, P, 0, 2, 0L);

        assertEquals(ParkPhotoPolicy.Action.NONE, episode.update(true, D, 0, 2, 600L));
        assertEquals(ParkPhotoPolicy.Action.NONE, episode.update(true, P, 0, 2, 900L));
        assertEquals(ParkPhotoPolicy.Action.NONE, episode.update(true, P, 0, 2, 1_800L));
        assertEquals(ParkPhotoPolicy.Action.CAPTURE, episode.update(true, P, 0, 2, 1_900L));
    }

    @Test
    public void invalidGearWhileStoppedKeepsStateButMovingResets() {
        ParkPhotoPolicy.ParkEpisode episode = new ParkPhotoPolicy.ParkEpisode();
        episode.update(true, P, 0, 2, 0L);
        episode.update(true, P, 0, 2, 1_000L);

        assertEquals(ParkPhotoPolicy.Action.NONE, episode.update(false, -1, 0, 2, 2_000L));
        assertTrue(episode.hasCaptured());
        assertEquals(ParkPhotoPolicy.Action.DISCARD, episode.update(false, -1, 30, 2, 3_000L));
        assertFalse(episode.hasCaptured());
    }

    @Test
    public void resetAfterIgnitionOffAllowsNextTripCapture() {
        ParkPhotoPolicy.ParkEpisode episode = new ParkPhotoPolicy.ParkEpisode();
        episode.update(true, P, 0, 2, 0L);
        episode.update(true, P, 0, 2, 1_000L);
        episode.reset();

        episode.update(true, P, 0, 2, 50_000L);
        assertEquals(ParkPhotoPolicy.Action.CAPTURE, episode.update(true, P, 0, 2, 51_000L));
    }

    @Test
    public void budgetRejectsLargeImageOrTotal() {
        int max = ParkPhotoPolicy.MAX_IMAGE_BYTES;
        int total = ParkPhotoPolicy.MAX_TIER_BYTES;

        assertTrue(ParkPhotoPolicy.fitsBudget(new int[] {400_000, 400_000, 400_000, 400_000}, max, total));
        assertFalse(ParkPhotoPolicy.fitsBudget(new int[] {max + 1}, max, total));
        assertFalse(ParkPhotoPolicy.fitsBudget(new int[] {600_000, 600_000, 600_000, 600_000}, max, total));
        assertFalse(ParkPhotoPolicy.fitsBudget(null, max, total));
    }

    @Test
    public void qualityLadderStartsAtNinetyAndDescends() {
        int[] ladder = ParkPhotoPolicy.FULL_QUALITY_LADDER;
        assertEquals(90, ladder[0]);
        for (int i = 1; i < ladder.length; i++) {
            assertTrue(ladder[i] < ladder[i - 1]);
        }
    }

    @Test
    public void previewStepTargetsAboutSixHundredFortyPixels() {
        assertEquals(2, ParkPhotoPolicy.previewStep(1280));
        assertEquals(1, ParkPhotoPolicy.previewStep(640));
        assertEquals(3, ParkPhotoPolicy.previewStep(1920));
    }

    @Test
    public void retryWindowCoversDrivingAndThreeMinutesAfterIgnitionOff() {
        assertTrue(ParkPhotoPolicy.inRetryWindow(2, -1L, 10L));
        assertTrue(ParkPhotoPolicy.inRetryWindow(0, 1_000L, 1_000L + 60_000L));
        assertFalse(ParkPhotoPolicy.inRetryWindow(0, 1_000L, 1_000L + ParkPhotoPolicy.RETRY_WINDOW_MS));
        assertFalse(ParkPhotoPolicy.inRetryWindow(0, -1L, 10L));
        assertEquals(1_000L, ParkPhotoPolicy.retryDelayMs(0));
        assertEquals(15_000L, ParkPhotoPolicy.retryDelayMs(99));
    }

    @Test
    public void chimeOnlyWhenFullAndCommitDoneAfterIgnitionOff() {
        long off = 10_000L;
        assertTrue(ParkPhotoPolicy.shouldChime(true, true, false, true, off, off + 5_000L));
        assertFalse(ParkPhotoPolicy.shouldChime(true, false, false, true, off, off + 5_000L));
        assertFalse(ParkPhotoPolicy.shouldChime(false, true, false, true, off, off + 5_000L));
        assertFalse(ParkPhotoPolicy.shouldChime(true, true, true, true, off, off + 5_000L));
        assertFalse(ParkPhotoPolicy.shouldChime(true, true, false, false, off, off + 5_000L));
        // Kontak kapanmadan (P'de beklerken) ya da ertesi sürüşte geç tamamlanınca çalmaz.
        assertFalse(ParkPhotoPolicy.shouldChime(true, true, false, true, -1L, 5_000L));
        assertFalse(ParkPhotoPolicy.shouldChime(true, true, false, true, off,
                off + ParkPhotoPolicy.RETRY_WINDOW_MS + 1L));
    }
}
