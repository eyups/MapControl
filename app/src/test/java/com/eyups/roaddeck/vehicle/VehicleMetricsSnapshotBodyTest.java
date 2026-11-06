package com.eyups.roaddeck.vehicle;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VehicleMetricsSnapshotBodyTest {

    private static VehicleMetricsSnapshot snapshot(int doorLf, int doorRf, int doorLr,
            int doorRr, int trunk, int hood, int windowFl, int windowFr, int windowRl,
            int windowRr, int sunroofOperate, int sunroofPosi, int sunshade) {
        int invalid = VehicleMetricsCache.INVALID;
        return new VehicleMetricsSnapshot(
                true,
                invalid, invalid, invalid, invalid, invalid, invalid, invalid, invalid,
                invalid,
                doorLf, doorRf, doorLr, doorRr, trunk, hood,
                windowFl, windowFr, windowRl, windowRr,
                sunroofOperate, sunroofPosi, sunshade,
                invalid, invalid, invalid, invalid);
    }

    @Test
    public void rawOneMarksBodyPartOpen() {
        VehicleMetricsSnapshot value = snapshot(1, 0, 0, 1, 1, 1,
                1, 0, 1, 0, 3, 2, 2);

        assertTrue(value.isDoorLfOpen());
        assertFalse(value.isDoorRfOpen());
        assertFalse(value.isDoorLrOpen());
        assertTrue(value.isDoorRrOpen());
        assertTrue(value.isTrunkOpen());
        assertTrue(value.isHoodOpen());
        assertTrue(value.isAnyDoorOpen());
        assertTrue(value.isWindowFlOpen());
        assertFalse(value.isWindowFrOpen());
        assertTrue(value.isSunroofOpen());
        assertTrue(value.isSunshadeOpen());
    }

    @Test
    public void missingPartIsNotReportedAsOpen() {
        int invalid = VehicleMetricsCache.INVALID;
        VehicleMetricsSnapshot value = snapshot(
                invalid, invalid, invalid, invalid, invalid, invalid,
                invalid, invalid, invalid, invalid, invalid, invalid, invalid);

        assertFalse(value.hasDoorLf());
        assertFalse(value.hasTrunk());
        assertFalse(value.hasHood());
        assertFalse(value.isAnyDoorOpen());
        assertFalse(value.hasWindowFl());
        assertFalse(value.hasSunroof());
    }
}
