package com.mapcontrol.cloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class CloudTelemetryBufferTest {

    @Test
    public void keepsNewestTwoHundredRecords() {
        CloudTelemetryBuffer<Integer> buffer = new CloudTelemetryBuffer<>();
        for (int i = 0; i < 205; i++) {
            buffer.add(i);
        }

        assertEquals(200, buffer.size());
        assertEquals(Integer.valueOf(5), buffer.peek());
    }

    @Test
    public void removesOnlyTheOldestRecord() {
        CloudTelemetryBuffer<Integer> buffer = new CloudTelemetryBuffer<>();
        buffer.add(10);
        buffer.add(20);

        buffer.removeFirst();

        assertEquals(1, buffer.size());
        assertEquals(Integer.valueOf(20), buffer.peek());
    }

    @Test
    public void clearLeavesBufferEmpty() {
        CloudTelemetryBuffer<Integer> buffer = new CloudTelemetryBuffer<>();
        buffer.add(1);
        buffer.clear();

        assertEquals(0, buffer.size());
        assertNull(buffer.peek());
    }
}
