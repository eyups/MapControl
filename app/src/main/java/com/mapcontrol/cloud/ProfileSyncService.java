package com.mapcontrol.cloud;

import android.content.Context;

import com.mapcontrol.profile.DriverProfileManager;

import org.json.JSONObject;

/** F5: sürücü profili ve güvenli sahne tercihlerini buluta yedekler/geri yükler. */
final class ProfileSyncService {

    private final Context context;
    private final CloudConfig config;
    private final CloudHttp http;

    ProfileSyncService(Context context, CloudConfig config, CloudHttp http) {
        this.context = context.getApplicationContext();
        this.config = config;
        this.http = http;
    }

    boolean backup() throws Exception {
        DriverProfileManager manager = DriverProfileManager.get(context);
        JSONObject profiles = new JSONObject(manager.exportBackup());
        if (!put("profiles", profiles)) {
            return false;
        }
        return put("prefs", manager.exportPreferences());
    }

    boolean restore() throws Exception {
        JSONObject profiles = get("profiles");
        if (profiles == null) {
            return false;
        }
        DriverProfileManager manager = DriverProfileManager.get(context);
        if (!manager.importBackup(profiles.toString())) {
            return false;
        }
        JSONObject preferences = get("prefs");
        if (preferences != null) {
            manager.importPreferences(preferences);
        }
        return true;
    }

    private boolean put(String key, JSONObject data) throws Exception {
        JSONObject body = new JSONObject();
        body.put("payload", data);
        CloudHttp.Response response = http.putJson(
                "/api/v1/car/sync/" + key, config.token(), body, 80 * 1024);
        return response.code == 200;
    }

    private JSONObject get(String key) throws Exception {
        CloudHttp.Response response = http.getJson(
                "/api/v1/car/sync/" + key, config.token(), 15_000);
        if (response.code == 204) {
            return null;
        }
        if (response.code != 200) {
            throw new Exception("http_" + response.code);
        }
        return new JSONObject(response.body).optJSONObject("data");
    }
}
