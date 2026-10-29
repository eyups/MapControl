package com.mapcontrol.cloud;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

import androidx.core.content.ContextCompat;

import java.util.List;

/** Konum paylaşımı açıksa son bilinen konumu okur; yeni abonelik/pil tüketimi başlatmaz. */
final class CloudLocationProvider {

    private static final long MAX_AGE_MS = 10 * 60 * 1000L;

    private CloudLocationProvider() {
    }

    static Location lastKnown(Context context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        try {
            LocationManager manager =
                    (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (manager == null) {
                return null;
            }
            Location best = null;
            List<String> providers = manager.getProviders(true);
            for (String provider : providers) {
                Location location = manager.getLastKnownLocation(provider);
                if (location == null) {
                    continue;
                }
                if (System.currentTimeMillis() - location.getTime() > MAX_AGE_MS) {
                    continue;
                }
                if (best == null || location.getTime() > best.getTime()) {
                    best = location;
                }
            }
            return best;
        } catch (SecurityException | IllegalArgumentException ignored) {
            return null;
        }
    }
}
