package com.mapcontrol.cloud;

import android.content.Context;

import com.mapcontrol.valet.ValetAlert;
import com.mapcontrol.valet.ValetModeManager;
import com.mapcontrol.valet.ValetSession;

import org.json.JSONArray;
import org.json.JSONObject;

/** C4/D5: valet oturumunun son durumunu ve uyarılarını buluta taşır. */
final class ValetUploader {

    private final Context context;
    private final CloudConfig config;
    private final CloudHttp http;
    private boolean wasActive;

    ValetUploader(Context context, CloudConfig config, CloudHttp http) {
        this.context = context.getApplicationContext();
        this.config = config;
        this.http = http;
    }

    /** null dönerse gönderilecek yeni durum yoktur. */
    CloudHttp.Response upload() throws Exception {
        ValetSession session = ValetModeManager.getSession(context);
        if (!session.active && !wasActive) {
            return null;
        }
        JSONObject body = new JSONObject();
        body.put("active", session.active);
        if (session.startTimeMs > 0) {
            body.put("started_at_ms", session.startTimeMs);
        }
        if (session.endTimeMs > 0) {
            body.put("ended_at_ms", session.endTimeMs);
        }
        body.put("speed_limit_kmh", session.speedLimitKmh);
        body.put("geofence_radius_m", session.geofenceRadiusM);
        body.put("curfew_enabled", session.curfewEnabled);
        body.put("curfew_start_minute", session.curfewStartMinute);
        body.put("curfew_end_minute", session.curfewEndMinute);
        body.put("distance_km", session.distanceKm);
        body.put("max_speed_kmh", session.maxSpeedKmh);
        JSONArray alerts = new JSONArray();
        int end = Math.min(20, session.alerts.size());
        for (int i = 0; i < end; i++) {
            ValetAlert alert = session.alerts.get(i);
            JSONObject item = new JSONObject();
            item.put("type", alert.type);
            item.put("time_ms", alert.timeMs);
            item.put("speed_kmh", alert.speedKmh);
            item.put("distance_m", alert.distanceM);
            item.put("message", alert.message);
            alerts.put(item);
        }
        body.put("alerts", alerts);
        CloudHttp.Response response = http.postJson(
                "/api/v1/car/valet", config.token(), body, 32 * 1024);
        if (response.code == 204) {
            wasActive = session.active;
        }
        return response;
    }
}
