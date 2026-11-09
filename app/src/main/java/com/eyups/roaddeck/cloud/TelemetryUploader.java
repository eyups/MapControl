package com.eyups.roaddeck.cloud;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import com.eyups.roaddeck.vehicle.VehicleMetricsRepository;
import com.eyups.roaddeck.vehicle.VehicleMetricsSnapshot;

import org.json.JSONObject;

/** VehicleMetricsRepository aboneliğini yönetir ve sözleşmeye uygun telemetri üretir. */
final class TelemetryUploader implements VehicleMetricsRepository.Listener {

    private static final String TAG = "CloudTelemetry";

    private final Context context;
    private final CloudConfig config;
    private final VehicleMetricsRepository repository;
    private final CloudTelemetryBuffer<JSONObject> pending = new CloudTelemetryBuffer<>();

    private volatile VehicleMetricsSnapshot latest;
    private volatile boolean finalSent;
    private volatile boolean sawPoweredOn;
    private volatile long lastMetricsAtMs;
    private int lastLoggedPower = Integer.MIN_VALUE;
    private int lastLoggedSystem = Integer.MIN_VALUE;
    private int lastLoggedCar = Integer.MIN_VALUE;

    TelemetryUploader(Context context, CloudConfig config) {
        this.context = context.getApplicationContext();
        this.config = config;
        this.repository = VehicleMetricsRepository.getBackground(this.context);
    }

    void start() {
        finalSent = false;
        sawPoweredOn = false;
        lastMetricsAtMs = 0L;
        repository.addListener(this);
    }

    void stop() {
        repository.removeListener(this);
        latest = null;
        lastMetricsAtMs = 0L;
        pending.clear();
    }

    @Override
    public void onMetricsUpdated(VehicleMetricsSnapshot snapshot) {
        if (snapshot != null) {
            latest = snapshot;
            lastMetricsAtMs = android.os.SystemClock.elapsedRealtime();
        }
    }

    int powerMode() {
        VehicleMetricsSnapshot snapshot = latest;
        if (snapshot == null) {
            logPowerMode(-1, -1, -1, false);
            return -1;
        }
        int carPowerMode = repository.getCombined(ReadOnlyID.ID_CAR_POWER_MODE);
        int mode = effectivePowerMode(snapshot, carPowerMode);
        if (mode != 2) {
            logPowerMode(mode, snapshot.powerMode, carPowerMode, snapshot.connected);
        }
        return mode;
    }

    VehicleMetricsSnapshot latestSnapshot() {
        return latest;
    }

    int speedKmh() {
        VehicleMetricsSnapshot snapshot = latest;
        return snapshot == null ? 0 : snapshot.preferredSpeed();
    }

    private int effectivePowerMode(VehicleMetricsSnapshot snapshot, int carPowerMode) {
        int systemPowerMode = snapshot.powerMode;
        // CAR_POWER_MODE bazı araçlarda yalnız OFF'ta anlamlı. Sistem ON/ACC
        // derken 0 değerini yok say; aksi hâlde heartbeat ve telemetri kesiliyor.
        if (VehicleMetricsSnapshot.isValid(systemPowerMode)) {
            return systemPowerMode;
        }
        return VehicleMetricsSnapshot.isValid(carPowerMode) ? carPowerMode : -1;
    }

    private void logPowerMode(int mode, int system, int car, boolean connected) {
        if (mode == lastLoggedPower && system == lastLoggedSystem && car == lastLoggedCar) {
            return;
        }
        lastLoggedPower = mode;
        lastLoggedSystem = system;
        lastLoggedCar = car;
        Log.i(TAG, "power mode=" + mode + " system=" + system + " car=" + car
                + " connected=" + connected);
    }

    /** Gönderilecek gövde yoksa null döner. */
    JSONObject buildPayload(long tsMs, boolean serverAllowsLocation) {
        VehicleMetricsSnapshot snapshot = latest;
        if (snapshot == null) {
            return null;
        }
        int powerMode = effectivePowerMode(snapshot, repository.getCombined(ReadOnlyID.ID_CAR_POWER_MODE));
        long ageMs = lastMetricsAtMs == 0L
                ? -1L : android.os.SystemClock.elapsedRealtime() - lastMetricsAtMs;
        if (powerMode == 2) {
            sawPoweredOn = true;
            finalSent = false;
        }
        boolean finalFlag = CloudTelemetryPolicy.shouldSendFinal(powerMode, finalSent)
                && sawPoweredOn;
        if (!finalFlag && !CloudTelemetryPolicy.liveUploadAllowed(
                powerMode, snapshot.connected, ageMs)) {
            return null;
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("ts_ms", tsMs);
            payload.put("final", finalFlag);

            if (snapshot.isValid(snapshot.displaySpeed) || snapshot.isValid(snapshot.actualSpeed)) {
                payload.put("speed", CloudTelemetryPolicy.clamp(snapshot.preferredSpeed(), 0, 300));
            }
            if (snapshot.isValid(snapshot.gearSignal)) {
                payload.put("gear", CloudTelemetryPolicy.clamp(snapshot.gearSignal, -1, 10));
            }
            if (snapshot.isValid(powerMode)) {
                payload.put("power_mode", CloudTelemetryPolicy.clamp(powerMode, 0, 10));
            }
            if (snapshot.isValid(snapshot.odoKm)) {
                payload.put("odo_km", CloudTelemetryPolicy.clamp(snapshot.odoKm, 0, 2_000_000));
            }
            if (snapshot.isValid(snapshot.fuelPercent)) {
                int fuelPercent = CloudTelemetryPolicy.fuelPercent(snapshot.fuelPercent);
                if (fuelPercent >= 0) {
                    payload.put("fuel_pct", fuelPercent);
                }
            }
            if (snapshot.isValid(snapshot.rangeKm)) {
                int rangeKm = CloudTelemetryPolicy.rangeKm(snapshot.rangeKm);
                if (rangeKm >= 0) {
                    payload.put("range_km", rangeKm);
                }
            }
            if (snapshot.isValid(snapshot.batteryRaw)) {
                int battery = CloudTelemetryPolicy.batteryTenthsVolts(snapshot.batteryRaw);
                if (battery > 0) {
                    payload.put("battery_raw", battery);
                }
            }
            if (snapshot.isValid(snapshot.tirePressureWarning)) {
                payload.put("tpms_warning", CloudTelemetryPolicy.clamp(
                        snapshot.tirePressureWarning, 0, 255));
            }

            JSONObject doors = new JSONObject();
            putDoor(doors, "lf", snapshot.doorLf);
            putDoor(doors, "rf", snapshot.doorRf);
            putDoor(doors, "lr", snapshot.doorLr);
            putDoor(doors, "rr", snapshot.doorRr);
            putDoor(doors, "trunk", snapshot.trunk);
            if (doors.length() > 0) {
                payload.put("doors", doors);
            }

            if (snapshot.hasSunroof()) {
                JSONObject sunroof = new JSONObject();
                Boolean open = snapshot.sunroofOpenOrNull();
                if (open != null) {
                    sunroof.put("open", open.booleanValue());
                }
                if (snapshot.isValid(snapshot.sunroofPosi) && snapshot.sunroofPosi > 0) {
                    sunroof.put("pos", sunroofPercent(snapshot.sunroofPosi));
                }
                if (sunroof.length() > 0) {
                    payload.put("sunroof", sunroof);
                }
            }

            if (snapshot.isValid(snapshot.driveMode)) {
                payload.put("drive_mode", CloudTelemetryPolicy.clamp(snapshot.driveMode, 0, 10));
            }

            if (serverAllowsLocation && config.shareLocation()) {
                Location location = CloudLocationProvider.lastKnown(context);
                if (location != null) {
                    JSONObject loc = new JSONObject();
                    loc.put("lat", location.getLatitude());
                    loc.put("lon", location.getLongitude());
                    if (location.hasAccuracy()) {
                        loc.put("acc_m", CloudTelemetryPolicy.clamp(
                                Math.round(location.getAccuracy()), 0, 10_000));
                    }
                    payload.put("loc", loc);
                }
            }
            return payload;
        } catch (Exception ignored) {
            return null;
        }
    }

    void markFinalSent() {
        finalSent = true;
        pending.clear();
    }

    /** Kontak kapanışını 15 sn yükleme aralığını beklemeden yakalar. */
    JSONObject captureFinal(long tsMs, boolean serverAllowsLocation) {
        VehicleMetricsSnapshot snapshot = latest;
        if (snapshot == null) {
            return null;
        }
        int powerMode = effectivePowerMode(snapshot, repository.getCombined(ReadOnlyID.ID_CAR_POWER_MODE));
        if (powerMode == 2) {
            sawPoweredOn = true;
            finalSent = false;
            return null;
        }
        if (!sawPoweredOn || finalSent) {
            return null;
        }
        if (powerMode != 0 && powerMode != 1) {
            return null;
        }
        return buildPayload(tsMs, serverAllowsLocation);
    }

    /** Normal örneği tampona ekler; final örneğini hemen gönderilmek üzere döndürür. */
    JSONObject capture(long tsMs, boolean serverAllowsLocation) {
        JSONObject payload = buildPayload(tsMs, serverAllowsLocation);
        if (payload == null) {
            return null;
        }
        if (payload.optBoolean("final", false)) {
            return payload;
        }
        pending.add(payload);
        return null;
    }

    JSONObject pendingPayload() {
        return pending.peek();
    }

    void removePending() {
        pending.removeFirst();
    }

    int pendingCount() {
        return pending.size();
    }

    private static void putDoor(JSONObject doors, String key, int value) {
        if (VehicleMetricsSnapshot.isValid(value) && value >= 0 && value <= 3) {
            try {
                doors.put(key, value);
            } catch (Exception ignored) {
                // Eksik alan göndermemek daha güvenli.
            }
        }
    }

    /** OEM 1 kapalı, 2–11 kademe, 12 tilt; sunucu 0–100 yüzde bekler. */
    private static int sunroofPercent(int oemPosition) {
        if (oemPosition <= 1) {
            return 0;
        }
        if (oemPosition >= 11) {
            return 100;
        }
        return CloudTelemetryPolicy.clamp(10 + ((oemPosition - 2) * 90) / 9, 0, 100);
    }
}
