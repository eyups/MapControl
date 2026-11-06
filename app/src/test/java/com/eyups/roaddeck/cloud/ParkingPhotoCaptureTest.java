package com.eyups.roaddeck.cloud;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import java.nio.ByteBuffer;

import org.junit.Test;

public class ParkingPhotoCaptureTest {

    @Test
    public void yuyvToNv21SamplingKeepsLumaAndUsesVuOrder() {
        byte[] yuyv = syntheticFrame();

        byte[] nv21 = ParkingPhotoCapture.YuyvJpeg.toNv21(yuyv, 4, 4, 2);

        assertNotNull(nv21);
        assertEquals(6, nv21.length);
        assertEquals(10, nv21[0] & 0xff);
        assertEquals(12, nv21[1] & 0xff);
        assertEquals(12, nv21[2] & 0xff);
        assertEquals(14, nv21[3] & 0xff);
        assertEquals(200, nv21[4] & 0xff);
        assertEquals(100, nv21[5] & 0xff);
    }

    @Test
    public void copyMissingDoesNotRecopyFrames() {
        ByteBuffer[] frames = new ByteBuffer[4];
        int[] widths = {2, 2, 2, 2};
        int[] heights = {2, 2, 2, 2};
        for (int cam = 0; cam < 4; cam++) {
            frames[cam] = ByteBuffer.wrap(new byte[] {(byte) cam, (byte) (cam + 10)});
        }
        ParkingPhotoCapture.FrameSet set = new ParkingPhotoCapture.FrameSet();

        assertEquals(4, ParkingPhotoCapture.copyMissing(set, frames, widths, heights));
        byte[] first = set.data[0];
        for (int cam = 0; cam < 4; cam++) {
            frames[cam] = ByteBuffer.wrap(new byte[] {9, 9});
        }
        ParkingPhotoCapture.copyMissing(set, frames, widths, heights);

        assertSame(first, set.data[0]);
        assertArrayEquals(new byte[] {0, 10}, set.data[0]);
    }

    @Test
    public void tenRoundsOfFrameConversionStayStable() {
        byte[] frame = syntheticFrame();
        long started = System.nanoTime();
        for (int round = 0; round < 10; round++) {
            byte[] nv21 = ParkingPhotoCapture.YuyvJpeg.toNv21(frame, 4, 4, 1);
            assertNotNull(nv21);
            assertEquals(24, nv21.length);
            assertEquals(10, nv21[0] & 0xff);
        }
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        System.out.println("10 frame conversion rounds: " + elapsedMs + " ms");
    }

    @Test
    public void yuyvToNv21FullResolutionKeepsAllLuma() {
        byte[] nv21 = ParkingPhotoCapture.YuyvJpeg.toNv21(syntheticFrame(), 4, 4, 1);

        assertNotNull(nv21);
        assertEquals(24, nv21.length);
        assertArrayEquals(
                new byte[] {10, 11, 12, 13},
                new byte[] {nv21[0], nv21[1], nv21[2], nv21[3]});
        assertEquals(200, nv21[16] & 0xff);
        assertEquals(100, nv21[17] & 0xff);
    }

    private static byte[] syntheticFrame() {
        byte[] frame = new byte[4 * 4 * 2];
        for (int row = 0; row < 4; row++) {
            int base = row * 8;
            frame[base] = (byte) (10 + row);
            frame[base + 1] = (byte) (100 + row);
            frame[base + 2] = (byte) (11 + row);
            frame[base + 3] = (byte) (200 + row);
            frame[base + 4] = (byte) (12 + row);
            frame[base + 5] = (byte) (101 + row);
            frame[base + 6] = (byte) (13 + row);
            frame[base + 7] = (byte) (201 + row);
        }
        return frame;
    }
}
