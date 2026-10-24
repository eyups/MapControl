package com.mapcontrol.vehicle;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OutsideTemperatureTest {

    @Test
    public void mapsVehicleRaw145To17Point5C() {
        assertEquals(17.5f, OutsideTemperature.fromRaw(145), 0.001f);
    }

    @Test
    public void mapsKnownRange() {
        assertEquals(-45f, OutsideTemperature.fromRaw(20), 0.001f);
        assertEquals(17.5f, OutsideTemperature.fromRaw(145), 0.001f);
        assertEquals(70f, OutsideTemperature.fromRaw(250), 0.001f);
    }

    @Test
    public void rejectsOutOfRangeAndMissingValues() {
        assertTrue(Float.isNaN(OutsideTemperature.fromRaw(Integer.MIN_VALUE)));
        assertTrue(Float.isNaN(OutsideTemperature.fromRaw(-1)));
        assertTrue(Float.isNaN(OutsideTemperature.fromRaw(251)));
        assertTrue(Float.isNaN(OutsideTemperature.fromRaw(0xFFFF)));
    }
}
