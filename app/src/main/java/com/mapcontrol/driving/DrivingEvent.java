package com.mapcontrol.driving;

import org.json.JSONException;
import org.json.JSONObject;

/** A harsh brake or acceleration event, stored relative to trip start. */
public final class DrivingEvent {

    public static final int TYPE_HARSH_BRAKE = 1;
    public static final int TYPE_HARSH_ACCEL = 2;

    public int type;
    public long elapsedMs;
    public int speedKmh;
    public float accelerationMps2;

    public DrivingEvent() {
    }

    public DrivingEvent(int type, long elapsedMs, int speedKmh, float accelerationMps2) {
        this.type = type;
        this.elapsedMs = elapsedMs;
        this.speedKmh = speedKmh;
        this.accelerationMps2 = accelerationMps2;
    }

    public boolean isBrake() {
        return type == TYPE_HARSH_BRAKE;
    }

    public JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            object.put("type", type);
            object.put("elapsedMs", elapsedMs);
            object.put("speedKmh", speedKmh);
            object.put("accelerationMps2", accelerationMps2);
        } catch (JSONException ignored) {
        }
        return object;
    }

    public static DrivingEvent fromJson(JSONObject object) {
        if (object == null) {
            return null;
        }
        return new DrivingEvent(
                object.optInt("type"),
                object.optLong("elapsedMs"),
                object.optInt("speedKmh"),
                (float) object.optDouble("accelerationMps2"));
    }
}
