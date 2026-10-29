package com.mapcontrol.cloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CloudTelemetryPolicyTest {

    @Test
    public void uploadIntervalIsClampedToTenThirtySeconds() {
        assertEquals(10, CloudTelemetryPolicy.uploadIntervalSeconds(0));
        assertEquals(10, CloudTelemetryPolicy.uploadIntervalSeconds(10));
        assertEquals(15, CloudTelemetryPolicy.uploadIntervalSeconds(15));
        assertEquals(30, CloudTelemetryPolicy.uploadIntervalSeconds(30));
        assertEquals(30, CloudTelemetryPolicy.uploadIntervalSeconds(600));
    }

    @Test
    public void liveUploadRequiresPowerOnFreshConnectedMetrics() {
        assertTrue(CloudTelemetryPolicy.liveUploadAllowed(2, true, 0L));
        assertTrue(CloudTelemetryPolicy.liveUploadAllowed(2, true, 60_000L));
        assertFalse(CloudTelemetryPolicy.liveUploadAllowed(1, true, 0L));
        assertFalse(CloudTelemetryPolicy.liveUploadAllowed(0, true, 0L));
        assertFalse(CloudTelemetryPolicy.liveUploadAllowed(2, false, 0L));
        assertFalse(CloudTelemetryPolicy.liveUploadAllowed(2, true, 60_001L));
    }

    @Test
    public void fuelPercentHandlesWholeAndTenthsRawValues() {
        assertEquals(50, CloudTelemetryPolicy.fuelPercent(50));
        assertEquals(100, CloudTelemetryPolicy.fuelPercent(100));
        assertEquals(35, CloudTelemetryPolicy.fuelPercent(348));
        assertEquals(100, CloudTelemetryPolicy.fuelPercent(1000));
        assertEquals(-1, CloudTelemetryPolicy.fuelPercent(-1));
    }

    @Test
    public void rangePlaceholderIsNotUploaded() {
        assertEquals(114, CloudTelemetryPolicy.rangeKm(114));
        assertEquals(1999, CloudTelemetryPolicy.rangeKm(1999));
        assertEquals(-1, CloudTelemetryPolicy.rangeKm(2000));
        assertEquals(-1, CloudTelemetryPolicy.rangeKm(2500));
        assertEquals(-1, CloudTelemetryPolicy.rangeKm(-1));
    }

    @Test
    public void batteryVoltageNormalizesMillivoltsAndTenths() {
        assertEquals(120, CloudTelemetryPolicy.batteryTenthsVolts(12000));
        assertEquals(127, CloudTelemetryPolicy.batteryTenthsVolts(127));
        assertEquals(-1, CloudTelemetryPolicy.batteryTenthsVolts(0));
    }

    @Test
    public void finalIsSentOnlyOnceForPowerOff() {
        assertTrue(CloudTelemetryPolicy.shouldSendFinal(0, false));
        assertTrue(CloudTelemetryPolicy.shouldSendFinal(1, false));
        assertFalse(CloudTelemetryPolicy.shouldSendFinal(0, true));
        assertFalse(CloudTelemetryPolicy.shouldSendFinal(2, false));
    }
}
