package com.mapcontrol.cloud;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.mapcontrol.BuildConfig;
import com.mapcontrol.vehicle.VehicleValueWatcher;

import org.json.JSONObject;

/** F2: seçili VDBus ID/değerlerini tek JSON anlık görüntüsü olarak yükler. */
final class VdbusLabUploader {

    interface Callback {
        void onResult(boolean ok, String message);
    }

    private final Context context;
    private final CloudConfig config;
    private final CloudHttp http;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    VdbusLabUploader(Context context, CloudConfig config, CloudHttp http) {
        this.context = context.getApplicationContext();
        this.config = config;
        this.http = http;
    }

    void upload(Callback callback) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("ts_ms", System.currentTimeMillis());
                body.put("app_version", BuildConfig.VERSION_NAME);
                body.put("readings", VehicleValueWatcher.snapshotJson());
                CloudHttp.Response response = http.postJson(
                        "/api/v1/car/vdbus", config.token(), body, 64 * 1024);
                boolean ok = response.code == 201;
                mainHandler.post(() -> callback.onResult(ok, "http_" + response.code));
            } catch (Exception error) {
                mainHandler.post(() -> callback.onResult(false, "error"));
            }
        }, "MapControlVdbusLab").start();
    }
}
