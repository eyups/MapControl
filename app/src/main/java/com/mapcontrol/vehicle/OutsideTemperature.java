package com.mapcontrol.vehicle;

/**
 * HVAC dış sıcaklık ham değeri.
 * Araçta 145 ham değeri 17.5 °C ekran değeriyle eşleşti:
 * {@code °C = raw * 0.5 - 55}. Makul aralık dışındaki değerler geçersiz sayılır.
 */
public final class OutsideTemperature {

    public static final float INVALID = Float.NaN;

    private static final float SCALE = 0.5f;
    private static final float OFFSET = 55f;
    private static final float MIN_C = -50f;
    private static final float MAX_C = 70f;

    private OutsideTemperature() {
    }

    public static float fromRaw(int raw) {
        if (raw == Integer.MIN_VALUE) {
            return INVALID;
        }
        float celsius = raw * SCALE - OFFSET;
        return celsius >= MIN_C && celsius <= MAX_C ? celsius : INVALID;
    }
}
