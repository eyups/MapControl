package com.mapcontrol.assistant.tools;

/** Asistan karar tablosunun saf (Android'siz) araç bağlamı. */
public final class VehicleContext {

    public static final int GEAR_PARK = 1;
    public static final int GEAR_REVERSE = 2;
    public static final int GEAR_NEUTRAL = 3;

    public final boolean connected;
    /** READONLY/14: 0 OFF, 1 standby, 2 ON, -1 bilinmiyor. */
    public final int powerMode;
    public final int speedKmh;
    public final int gear;
    public final boolean engineRunning;
    public final float outsideTempC;

    public VehicleContext(boolean connected, int powerMode, int speedKmh, int gear,
            boolean engineRunning, float outsideTempC) {
        this.connected = connected;
        this.powerMode = powerMode;
        this.speedKmh = Math.max(0, speedKmh);
        this.gear = gear;
        this.engineRunning = engineRunning;
        this.outsideTempC = outsideTempC;
    }

    public static VehicleContext unknown() {
        return new VehicleContext(false, -1, 0, -1, false, Float.NaN);
    }

    public boolean isOn() {
        return powerMode == 2;
    }

    public boolean isParked() {
        return gear == GEAR_PARK || gear == GEAR_NEUTRAL;
    }

    public boolean isReverse() {
        return gear == GEAR_REVERSE;
    }

    public boolean isMoving() {
        return speedKmh > 0;
    }

    public String gearLabel() {
        switch (gear) {
            case GEAR_PARK:
                return "P";
            case GEAR_REVERSE:
                return "R";
            case GEAR_NEUTRAL:
                return "N";
            case -1:
                return "?";
            default:
                return "D" + (gear - 3);
        }
    }
}
