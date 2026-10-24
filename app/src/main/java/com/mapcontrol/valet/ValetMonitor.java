package com.mapcontrol.valet;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.mapcontrol.vehicle.VehicleMetricsRepository;
import com.mapcontrol.vehicle.VehicleMetricsSnapshot;

import java.util.Calendar;

/** Monitors an active valet session: speed, geofence and curfew. */
public final class ValetMonitor implements VehicleMetricsRepository.Listener {

    private static volatile ValetMonitor instance;
    private static final long SPEED_ALERT_COOLDOWN_MS = 60_000L;
    private static final long GEOFENCE_ALERT_COOLDOWN_MS = 5 * 60_000L;
    private static final long CURFEW_ALERT_COOLDOWN_MS = 30 * 60_000L;
    private static final long PERSIST_INTERVAL_MS = 10_000L;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final VehicleMetricsRepository repository;
    private final LocationManager locationManager;
    private boolean running;
    private ValetSession session;
    private int lastSpeed;
    private long lastTickElapsed;
    private long lastPersistElapsed;
    private long lastSpeedAlertElapsed;
    private long lastGeofenceAlertElapsed;
    private long lastCurfewAlertElapsed;

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            if (session == null || !session.active || location == null) {
                return;
            }
            if (session.startLat == 0.0 && session.startLon == 0.0) {
                session.startLat = location.getLatitude();
                session.startLon = location.getLongitude();
            }
            session.lastLat = location.getLatitude();
            session.lastLon = location.getLongitude();
            if (session.startLat != 0.0 || session.startLon != 0.0) {
                float[] result = new float[1];
                Location.distanceBetween(session.startLat, session.startLon,
                        location.getLatitude(), location.getLongitude(), result);
                if (session.geofenceRadiusM > 0
                        && result[0] > session.geofenceRadiusM
                        && SystemClock.elapsedRealtime() - lastGeofenceAlertElapsed
                        >= GEOFENCE_ALERT_COOLDOWN_MS) {
                    lastGeofenceAlertElapsed = SystemClock.elapsedRealtime();
                    addAlert(ValetAlert.TYPE_GEOFENCE, Math.round(result[0]),
                            "Alan sınırı aşıldı: başlangıçtan "
                                    + Math.round(result[0] / 1000.0) + " km uzakta.");
                }
            }
            persist();
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            long now = SystemClock.elapsedRealtime();
            long dt = Math.max(0L, now - lastTickElapsed);
            lastTickElapsed = now;
            if (session != null && session.active) {
                if (lastSpeed > 0) {
                    session.distanceKm += lastSpeed / 3600.0 * (dt / 1000.0);
                }
                session.maxSpeedKmh = Math.max(session.maxSpeedKmh, lastSpeed);
                checkCurfew();
                if (now - lastPersistElapsed >= PERSIST_INTERVAL_MS) {
                    lastPersistElapsed = now;
                    persist();
                }
            }
            handler.postDelayed(this, 1000L);
        }
    };

    private ValetMonitor(Context context) {
        this.context = context.getApplicationContext();
        this.repository = VehicleMetricsRepository.getBackground(this.context);
        this.locationManager = (LocationManager) this.context.getSystemService(Context.LOCATION_SERVICE);
    }

    public static ValetMonitor get(Context context) {
        if (instance == null) {
            synchronized (ValetMonitor.class) {
                if (instance == null) {
                    instance = new ValetMonitor(context);
                }
            }
        }
        return instance;
    }

    public void startIfActive() {
        if (!ValetModeManager.isActive(context)) {
            return;
        }
        start();
    }

    public void start() {
        if (running) {
            return;
        }
        session = ValetModeManager.getSession(context);
        if (!session.active) {
            return;
        }
        running = true;
        lastTickElapsed = SystemClock.elapsedRealtime();
        lastPersistElapsed = lastTickElapsed;
        lastSpeed = 0;
        repository.addListener(this);
        startLocationUpdates();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(tick);
        repository.removeListener(this);
        stopLocationUpdates();
        if (session != null) {
            // Do not resurrect a session that the UI has just ended.
            ValetSession persisted = ValetModeManager.getSession(context);
            if (persisted.active) {
                ValetModeManager.saveSession(context, session);
            }
            session = null;
        }
    }

    @Override
    public void onMetricsUpdated(VehicleMetricsSnapshot snapshot) {
        if (!running || session == null || snapshot == null || !session.active) {
            return;
        }
        int speed = snapshot.preferredSpeed();
        lastSpeed = speed;
        session.maxSpeedKmh = Math.max(session.maxSpeedKmh, speed);
        if (session.speedLimitKmh > 0 && speed > session.speedLimitKmh
                && SystemClock.elapsedRealtime() - lastSpeedAlertElapsed
                >= SPEED_ALERT_COOLDOWN_MS) {
            lastSpeedAlertElapsed = SystemClock.elapsedRealtime();
            addAlert(ValetAlert.TYPE_SPEED, speed,
                    "Hız sınırı aşıldı: " + speed + " km/h (limit "
                            + session.speedLimitKmh + ").");
        }
    }

    private void startLocationUpdates() {
        if (locationManager == null
                || context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    5000L, 10f, locationListener, Looper.getMainLooper());
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                    5000L, 10f, locationListener, Looper.getMainLooper());
            Location last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last == null) {
                last = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (last != null) {
                locationListener.onLocationChanged(last);
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
        }
    }

    private void stopLocationUpdates() {
        if (locationManager == null) {
            return;
        }
        try {
            locationManager.removeUpdates(locationListener);
        } catch (SecurityException ignored) {
        }
    }

    private void addAlert(String type, int speed, String message) {
        if (session == null) {
            return;
        }
        session.addAlert(new ValetAlert(type, System.currentTimeMillis(), speed,
                session.distanceKm * 1000.0, message));
        persist();
    }

    private void checkCurfew() {
        if (session == null || !session.curfewEnabled) {
            return;
        }
        Calendar now = Calendar.getInstance();
        int minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int start = session.curfewStartMinute;
        int end = session.curfewEndMinute;
        boolean inside = start <= end
                ? minute >= start && minute < end
                : minute >= start || minute < end;
        if (inside && SystemClock.elapsedRealtime() - lastCurfewAlertElapsed
                >= CURFEW_ALERT_COOLDOWN_MS) {
            lastCurfewAlertElapsed = SystemClock.elapsedRealtime();
            addAlert(ValetAlert.TYPE_CURFEW, lastSpeed, "Saat kısıtı sırasında sürüş.");
        }
    }

    private void persist() {
        if (session != null) {
            ValetModeManager.saveSession(context, session);
        }
    }
}
