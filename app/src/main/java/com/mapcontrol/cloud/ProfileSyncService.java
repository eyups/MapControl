package com.mapcontrol.cloud;

import android.content.Context;
import android.util.Log;

import com.mapcontrol.profile.DriverProfileManager;
import com.mapcontrol.vehicle.FaceProfileStore;

import org.json.JSONObject;

/** F5: sürücü profili ve güvenli sahne tercihlerini buluta yedekler/geri yükler. */
final class ProfileSyncService {

    private final Context context;
    private final CloudConfig config;
    private final CloudHttp http;
    private volatile boolean audioOk = true;
    private volatile String lastError = "";

    ProfileSyncService(Context context, CloudConfig config, CloudHttp http) {
        this.context = context.getApplicationContext();
        this.config = config;
        this.http = http;
    }

    boolean backup() throws Exception {
        audioOk = true;
        lastError = "";
        DriverProfileManager manager = DriverProfileManager.get(context);
        JSONObject profiles = new JSONObject(manager.exportBackup());
        if (!put("profiles", profiles)) {
            return false;
        }
        if (!put("prefs", manager.exportPreferences())) {
            return false;
        }
        if (config.backupFaceData()
                && !put("faces", FaceProfileStore.exportBackup(context))) {
            return false;
        }
        try {
            audioOk = ProfileAudioBackup.upload(context, config, http);
        } catch (Exception error) {
            audioOk = false;
            lastError = "audio";
        }
        if (!audioOk) {
            lastError = "audio";
        }
        return true;
    }

    boolean isAudioOk() {
        return audioOk;
    }

    String lastError() {
        return lastError;
    }

    boolean clearFaces() throws Exception {
        return put("faces", FaceProfileStore.emptyBackup());
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
        if (config.backupFaceData()) {
            JSONObject faces = get("faces");
            if (faces != null && faces.optJSONArray("profiles") != null
                    && faces.optJSONArray("profiles").length() > 0
                    && !FaceProfileStore.importBackup(context, faces)) {
                return false;
            }
        }
        return ProfileAudioBackup.restore(context, config, http);
    }

    private boolean put(String key, JSONObject data) throws Exception {
        JSONObject body = new JSONObject();
        body.put("payload", data);
        CloudHttp.Response response = http.putJson(
                "/api/v1/car/sync/" + key, config.token(), body, 512 * 1024);
        if (response.code != 200) {
            lastError = key + "_" + response.code;
            Log.w("CloudBackup", key + " code=" + response.code);
        }
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
