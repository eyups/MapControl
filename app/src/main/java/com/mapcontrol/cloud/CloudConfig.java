package com.mapcontrol.cloud;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Yalnızca küçük tercihleri tutar; token ve eşleştirme bilgisi loglanmaz. */
final class CloudConfig {

    static final String BASE_URL = "https://ctrl.eyups.com";

    private static final String PREFS = "MapControlPrefs";
    private static final String KEY_DEVICE_TOKEN = "cloudDeviceToken";
    private static final String KEY_DEVICE_ID = "cloudDeviceId";
    private static final String KEY_TOKEN_EXPIRES_AT = "cloudTokenExpiresAt";
    private static final String KEY_SHARE_LOCATION = "cloudShareLocation";
    private static final String KEY_UPLOADED_TRIPS = "cloudUploadedTripIds";
    private static final int MAX_UPLOADED_TRIPS = 500;

    private final SharedPreferences prefs;

    CloudConfig(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    boolean isPaired() {
        String token = token();
        return token != null && !token.isEmpty();
    }

    String token() {
        return prefs.getString(KEY_DEVICE_TOKEN, "");
    }

    String deviceId() {
        return prefs.getString(KEY_DEVICE_ID, "");
    }

    void savePair(String deviceId, String token, String tokenExpiresAt) {
        prefs.edit()
                .putString(KEY_DEVICE_ID, deviceId == null ? "" : deviceId)
                .putString(KEY_DEVICE_TOKEN, token == null ? "" : token)
                .putString(KEY_TOKEN_EXPIRES_AT, tokenExpiresAt == null ? "" : tokenExpiresAt)
                .apply();
    }

    void clearPair() {
        prefs.edit()
                .remove(KEY_DEVICE_TOKEN)
                .remove(KEY_DEVICE_ID)
                .remove(KEY_TOKEN_EXPIRES_AT)
                .apply();
    }

    boolean shareLocation() {
        return prefs.getBoolean(KEY_SHARE_LOCATION, false);
    }

    void setShareLocation(boolean enabled) {
        prefs.edit().putBoolean(KEY_SHARE_LOCATION, enabled).apply();
    }

    Set<Long> uploadedTripIds() {
        Set<String> stored = prefs.getStringSet(KEY_UPLOADED_TRIPS, Collections.emptySet());
        Set<Long> ids = new LinkedHashSet<>();
        if (stored == null) {
            return ids;
        }
        for (String value : stored) {
            try {
                ids.add(Long.parseLong(value));
            } catch (NumberFormatException ignored) {
                // Bozuk eski kayıt yeni yüklemeyi engellemesin.
            }
        }
        return ids;
    }

    synchronized void markTripUploaded(long tripId) {
        if (tripId <= 0L) {
            return;
        }
        Set<String> values = new LinkedHashSet<>(
                prefs.getStringSet(KEY_UPLOADED_TRIPS, Collections.emptySet()));
        values.add(Long.toString(tripId));
        while (values.size() > MAX_UPLOADED_TRIPS) {
            String first = values.iterator().next();
            values.remove(first);
        }
        prefs.edit().putStringSet(KEY_UPLOADED_TRIPS, values).apply();
    }
}
