package com.mapcontrol.cloud;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CloudBackoffPolicyTest {

    @Test
    public void growsThenCapsAtSixtySeconds() {
        CloudBackoffPolicy policy = new CloudBackoffPolicy();
        assertEquals(15_000L, policy.nextDelayMs());
        assertEquals(30_000L, policy.nextDelayMs());
        assertEquals(60_000L, policy.nextDelayMs());
        assertEquals(60_000L, policy.nextDelayMs());
    }

    @Test
    public void resetReturnsToInitialDelay() {
        CloudBackoffPolicy policy = new CloudBackoffPolicy();
        policy.nextDelayMs();
        policy.nextDelayMs();
        policy.reset();
        assertEquals(15_000L, policy.nextDelayMs());
    }
}
