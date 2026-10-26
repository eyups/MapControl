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
    public void finalIsSentOnlyOnceForPowerOff() {
        assertTrue(CloudTelemetryPolicy.shouldSendFinal(0, false));
        assertFalse(CloudTelemetryPolicy.shouldSendFinal(0, true));
        assertFalse(CloudTelemetryPolicy.shouldSendFinal(2, false));
    }
}
