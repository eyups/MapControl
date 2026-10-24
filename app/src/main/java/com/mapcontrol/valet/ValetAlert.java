package com.mapcontrol.valet;

import org.json.JSONObject;

/** One valet/young-driver alert. */
public final class ValetAlert {

    public static final String TYPE_SPEED = "speed";
    public static final String TYPE_GEOFENCE = "geofence";
    public static final String TYPE_CURFEW = "curfew";

    public String type = TYPE_SPEED;
    public long timeMs;
    public int speedKmh;
    public double distanceM;
    public String message = "";

    public ValetAlert() {
    }

    public ValetAlert(String type, long timeMs, int speedKmh, double distanceM, String message) {
        this.type = type;
        this.timeMs = timeMs;
        this.speedKmh = speedKmh;
        this.distanceM = distanceM;
        this.message = message;
    }

    public JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            object.put("type", type);
            object.put("timeMs", timeMs);
            object.put("speedKmh", speedKmh);
            object.put("distanceM", distanceM);
            object.put("message", message);
        } catch (Exception ignored) {
        }
        return object;
    }

    public static ValetAlert fromJson(JSONObject object) {
        if (object == null) {
            return null;
        }
        ValetAlert alert = new ValetAlert();
        alert.type = object.optString("type", TYPE_SPEED);
        alert.timeMs = object.optLong("timeMs");
        alert.speedKmh = object.optInt("speedKmh");
        alert.distanceM = object.optDouble("distanceM");
        alert.message = object.optString("message");
        return alert;
    }
}
