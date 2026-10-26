package com.mapcontrol.cloud;

import android.content.Context;

import org.json.JSONObject;

/** Sağlık raporunu 5 dakikada bir, ayrı ağ çağrısıyla yükler. */
final class HealthUploader {

    private final Context context;
    private final CloudConfig config;
    private final CloudHttp http;

    HealthUploader(Context context, CloudConfig config, CloudHttp http) {
        this.context = context.getApplicationContext();
        this.config = config;
        this.http = http;
    }

    CloudHttp.Response upload() throws Exception {
        JSONObject payload = HealthCollector.collect(context);
        return http.postJson("/api/v1/car/health", config.token(), payload, 16 * 1024);
    }
}
