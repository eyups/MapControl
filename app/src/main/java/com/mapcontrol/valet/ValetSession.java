package com.mapcontrol.valet;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Persisted state of one valet/young-driver session. */
public final class ValetSession {

    public boolean active;
    public long startTimeMs;
    public long endTimeMs;
    public int speedLimitKmh = 120;
    public int geofenceRadiusM = 500;
    public boolean curfewEnabled;
    public int curfewStartMinute = 22 * 60;
    public int curfewEndMinute = 6 * 60;
    public boolean hidePersonal = true;
    public double startLat;
    public double startLon;
    public double lastLat;
    public double lastLon;
    public double distanceKm;
    public int maxSpeedKmh;
    public final List<ValetAlert> alerts = new ArrayList<>();

    public void addAlert(ValetAlert alert) {
        if (alert == null) {
            return;
        }
        alerts.add(0, alert);
        while (alerts.size() > 60) {
            alerts.remove(alerts.size() - 1);
        }
    }

    public JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            object.put("active", active);
            object.put("startTimeMs", startTimeMs);
            object.put("endTimeMs", endTimeMs);
            object.put("speedLimitKmh", speedLimitKmh);
            object.put("geofenceRadiusM", geofenceRadiusM);
            object.put("curfewEnabled", curfewEnabled);
            object.put("curfewStartMinute", curfewStartMinute);
            object.put("curfewEndMinute", curfewEndMinute);
            object.put("hidePersonal", hidePersonal);
            object.put("startLat", startLat);
            object.put("startLon", startLon);
            object.put("lastLat", lastLat);
            object.put("lastLon", lastLon);
            object.put("distanceKm", distanceKm);
            object.put("maxSpeedKmh", maxSpeedKmh);
            JSONArray array = new JSONArray();
            for (ValetAlert alert : alerts) {
                array.put(alert.toJson());
            }
            object.put("alerts", array);
        } catch (Exception ignored) {
        }
        return object;
    }

    public static ValetSession fromJson(JSONObject object) {
        ValetSession session = new ValetSession();
        if (object == null) {
            return session;
        }
        session.active = object.optBoolean("active");
        session.startTimeMs = object.optLong("startTimeMs");
        session.endTimeMs = object.optLong("endTimeMs");
        session.speedLimitKmh = object.optInt("speedLimitKmh", 120);
        session.geofenceRadiusM = object.optInt("geofenceRadiusM", 500);
        session.curfewEnabled = object.optBoolean("curfewEnabled");
        session.curfewStartMinute = object.optInt("curfewStartMinute", 22 * 60);
        session.curfewEndMinute = object.optInt("curfewEndMinute", 6 * 60);
        session.hidePersonal = object.optBoolean("hidePersonal", true);
        session.startLat = object.optDouble("startLat");
        session.startLon = object.optDouble("startLon");
        session.lastLat = object.optDouble("lastLat");
        session.lastLon = object.optDouble("lastLon");
        session.distanceKm = object.optDouble("distanceKm");
        session.maxSpeedKmh = object.optInt("maxSpeedKmh");
        JSONArray array = object.optJSONArray("alerts");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                ValetAlert alert = ValetAlert.fromJson(array.optJSONObject(i));
                if (alert != null) {
                    session.alerts.add(alert);
                }
            }
        }
        return session;
    }
}
