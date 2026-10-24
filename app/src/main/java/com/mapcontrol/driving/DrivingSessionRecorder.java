package com.mapcontrol.driving;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import com.mapcontrol.vehicle.VehicleMetricsRepository;
import com.mapcontrol.vehicle.VehicleMetricsSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Background trip recorder. It keeps the CarInfo snapshot alive through
 * {@link VehicleMetricsRepository}, integrates speed into distance and turns
 * speed changes into harsh brake / harsh acceleration events.
 *
 * Read-only: no vehicle setting is written.
 */
public final class DrivingSessionRecorder implements VehicleMetricsRepository.Listener {

    private static final String TAG = "DrivingRecorder";
    private static volatile DrivingSessionRecorder instance;

    private static final long TICK_MS = 1000L;
    private static final long SAMPLE_MS = 2000L;
    private static final long AUTO_STOP_ZERO_MS = 180000L;
    private static final double HARSH_BRAKE_MPS2 = -3.5;
    private static final double HARSH_ACCEL_MPS2 = 3.0;
    private static final long HARSH_COOLDOWN_MS = 4000L;
    /** Hız sinyali sıçramalarını gerçek ivme sanmamak için en az pencere. */
    private static final long MIN_ACCEL_WINDOW_MS = 500L;
    private static final long MAX_ACCEL_WINDOW_MS = 3000L;
    /** Fiziksel olarak mümkün olmayan sıçramaları yok say. */
    private static final double MAX_PLAUSIBLE_ACCEL_MPS2 = 10.0;

    private final Context appContext;
    private final VehicleMetricsRepository repository;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Integer> samples = new ArrayList<>();
    private final List<DrivingEvent> events = new ArrayList<>();

    private volatile VehicleMetricsSnapshot lastSnapshot;
    private boolean running;
    private boolean active;
    private long startElapsedMs;
    private long startWallMs;
    private long lastTickElapsedMs;
    private long lastSpeedChangeMs;
    private long lastBrakeMs;
    private long lastHarshAccelMs;
    private long zeroSinceMs;
    private long lastSampleMs;
    private int lastSpeed;
    private int lastAccelSpeed;
    private long lastAccelWindowMs;
    private int maxSpeed;
    private double distanceKm;
    private int harshBrakes;
    private int harshAccels;
    private int startFuelUsedMl;
    private int lastAverageConsumptionRaw;
    private DrivingTrip lastSaved;
    private long lastMetricsLogMs;
    private long lastTickLogMs;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            tickInternal();
            handler.postDelayed(this, TICK_MS);
        }
    };

    private DrivingSessionRecorder(Context context) {
        appContext = context.getApplicationContext();
        repository = VehicleMetricsRepository.getBackground(appContext);
        lastSaved = DrivingTripStore.load(appContext);
    }

    public static DrivingSessionRecorder get(Context context) {
        if (instance == null) {
            synchronized (DrivingSessionRecorder.class) {
                if (instance == null) {
                    instance = new DrivingSessionRecorder(context);
                }
            }
        }
        return instance;
    }

    public void start() {
        if (running) {
            Log.i(TAG, "start: zaten çalışıyor");
            return;
        }
        running = true;
        repository.addListener(this);
        handler.removeCallbacks(tick);
        handler.post(tick);
        Log.i(TAG, "start: dinleme başladı");
    }

    public void stop() {
        if (active) {
            finishAndSave();
        }
        running = false;
        handler.removeCallbacks(tick);
        repository.removeListener(this);
    }

    public boolean isActive() {
        return active;
    }

    public void startNow() {
        if (!running) {
            start();
        }
        if (!active) {
            startTrip(lastSnapshot != null ? lastSnapshot : repository.currentSnapshot());
        }
    }

    public DrivingTrip finishNow() {
        if (active) {
            return finishAndSave();
        }
        return lastSaved;
    }

    public DrivingTrip getDisplayTrip() {
        if (active) {
            return currentTrip();
        }
        return lastSaved != null ? lastSaved : DrivingTripStore.load(appContext);
    }

    public DrivingTrip getLastTrip() {
        if (lastSaved == null) {
            lastSaved = DrivingTripStore.load(appContext);
        }
        return lastSaved;
    }

    /** Test verisi üretme/silme sonrası bellekteki son sürüşü tazele. */
    public void reloadLastTrip() {
        lastSaved = DrivingTripStore.load(appContext);
    }

    @Override
    public void onMetricsUpdated(VehicleMetricsSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        try {
            lastSnapshot = snapshot;
            int speed = snapshot.preferredSpeed();
            long now = SystemClock.elapsedRealtime();
            if (now - lastMetricsLogMs >= 5000L) {
                lastMetricsLogMs = now;
                Log.i(TAG, "metrics connected=" + snapshot.connected
                        + " speed=" + speed + " display=" + snapshot.displaySpeed
                        + " actual=" + snapshot.actualSpeed + " gear=" + snapshot.gearSignal
                        + " active=" + active + " dist=" + String.format(java.util.Locale.US, "%.3f", distanceKm)
                        + " samples=" + samples.size() + " events=" + events.size());
            }

            if (!active && speed >= 5) {
                startTrip(snapshot);
            }
            if (!active) {
                return;
            }

            int accelPrevSpeed = lastAccelSpeed;
            long accelDt = now - lastAccelWindowMs;
            if (accelDt >= MIN_ACCEL_WINDOW_MS) {
                if (accelDt <= MAX_ACCEL_WINDOW_MS) {
                    double accel = (speed - accelPrevSpeed) / 3.6 / (accelDt / 1000.0);
                    if (Math.abs(accel) > MAX_PLAUSIBLE_ACCEL_MPS2) {
                        Log.w(TAG, "accel ignored dt=" + accelDt + " raw=" + accel
                                + " from=" + accelPrevSpeed + " to=" + speed);
                    } else if (accel <= HARSH_BRAKE_MPS2
                            && now - lastBrakeMs >= HARSH_COOLDOWN_MS) {
                        harshBrakes++;
                        lastBrakeMs = now;
                        addEvent(DrivingEvent.TYPE_HARSH_BRAKE, now, speed, accel);
                    } else if (accel >= HARSH_ACCEL_MPS2
                            && now - lastHarshAccelMs >= HARSH_COOLDOWN_MS) {
                        harshAccels++;
                        lastHarshAccelMs = now;
                        addEvent(DrivingEvent.TYPE_HARSH_ACCEL, now, speed, accel);
                    }
                }
                lastAccelSpeed = speed;
                lastAccelWindowMs = now;
            }
            lastSpeed = speed;
            lastSpeedChangeMs = now;
            maxSpeed = Math.max(maxSpeed, speed);
            if (speed > 0) {
                zeroSinceMs = 0;
            } else if (zeroSinceMs == 0) {
                zeroSinceMs = now;
            }
        } catch (Throwable t) {
            Log.e(TAG, "metrics işlenemedi", t);
        }
    }

    private void startTrip(VehicleMetricsSnapshot snapshot) {
        active = true;
        long now = SystemClock.elapsedRealtime();
        startElapsedMs = now;
        startWallMs = System.currentTimeMillis();
        lastTickElapsedMs = now;
        lastSpeedChangeMs = now;
        lastBrakeMs = 0;
        lastHarshAccelMs = 0;
        lastAccelWindowMs = now;
        zeroSinceMs = 0;
        lastSampleMs = now;
        lastSpeed = snapshot != null ? snapshot.preferredSpeed() : 0;
        lastAccelSpeed = lastSpeed;
        lastAccelWindowMs = now;
        maxSpeed = lastSpeed;
        distanceKm = 0;
        harshBrakes = 0;
        harshAccels = 0;
        samples.clear();
        samples.add(lastSpeed);
        events.clear();
        startFuelUsedMl = repository.getCombined(ReadOnlyID.ID_SUM_FUEL);
        lastAverageConsumptionRaw = repository.getCombined(ReadOnlyID.ID_AVG_FUEL_CONS);
        Log.i(TAG, "trip START speed=" + lastSpeed + " fuelMl=" + startFuelUsedMl
                + " avgFuelRaw=" + lastAverageConsumptionRaw);
    }

    private void tickInternal() {
        if (!active) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        long dt = Math.max(0, now - lastTickElapsedMs);
        lastTickElapsedMs = now;
        if (lastSpeed > 0) {
            distanceKm += lastSpeed / 3600.0 * (dt / 1000.0);
        }
        if (now - lastSampleMs >= SAMPLE_MS) {
            samples.add(lastSpeed);
            if (samples.size() > 600) {
                samples.remove(1);
            }
            lastSampleMs = now;
        }
        if (now - lastTickLogMs >= 10000L) {
            lastTickLogMs = now;
            Log.i(TAG, "trip tick speed=" + lastSpeed + " dist="
                    + String.format(java.util.Locale.US, "%.3f", distanceKm)
                    + " max=" + maxSpeed + " samples=" + samples.size()
                    + " brakes=" + harshBrakes + " accels=" + harshAccels);
        }
        if (zeroSinceMs > 0 && now - zeroSinceMs >= AUTO_STOP_ZERO_MS && distanceKm >= 0.05) {
            finishAndSave();
        }
    }

    private DrivingTrip currentTrip() {
        long now = SystemClock.elapsedRealtime();
        int duration = (int) Math.max(0, (now - startElapsedMs) / 1000L);
        DrivingTrip trip = new DrivingTrip();
        trip.startTimeMs = startWallMs;
        trip.endTimeMs = System.currentTimeMillis();
        trip.distanceKm = distanceKm;
        trip.durationSec = duration;
        trip.maxSpeedKmh = maxSpeed;
        trip.avgSpeedKmh = duration > 0
                ? (int) Math.round(distanceKm / (duration / 3600.0))
                : 0;
        trip.consumptionL100 = estimateConsumption();
        trip.harshBrakes = harshBrakes;
        trip.harshAccels = harshAccels;
        trip.sampleIntervalMs = (int) SAMPLE_MS;
        trip.speedSamples.addAll(samples);
        trip.events.addAll(events);
        DrivingScore.evaluate(trip);
        return trip;
    }

    private DrivingTrip finishAndSave() {
        if (!active) {
            return lastSaved;
        }
        long now = SystemClock.elapsedRealtime();
        int duration = (int) Math.max(1, (now - startElapsedMs) / 1000L);
        int avg = duration > 0 ? (int) Math.round(distanceKm / (duration / 3600.0)) : 0;
        double consumption = estimateConsumption();

        DrivingTrip trip = new DrivingTrip();
        trip.startTimeMs = startWallMs;
        trip.endTimeMs = System.currentTimeMillis();
        trip.distanceKm = distanceKm;
        trip.durationSec = duration;
        trip.avgSpeedKmh = Math.max(0, Math.min(250, avg));
        trip.maxSpeedKmh = maxSpeed;
        trip.consumptionL100 = consumption;
        trip.harshBrakes = harshBrakes;
        trip.harshAccels = harshAccels;
        trip.sampleIntervalMs = (int) SAMPLE_MS;
        trip.speedSamples.addAll(samples);
        trip.events.addAll(events);
        if (distanceKm >= 0.05) {
            DrivingScore.evaluate(trip);
            DrivingTripStore.save(appContext, trip);
            lastSaved = trip;
            Log.i(TAG, "trip SAVED dist=" + String.format(java.util.Locale.US, "%.3f", distanceKm)
                    + " score=" + trip.score + " grade=" + trip.grade
                    + " brakes=" + trip.harshBrakes + " accels=" + trip.harshAccels);
        } else {
            Log.w(TAG, "trip DISCARDED dist=" + String.format(java.util.Locale.US, "%.3f", distanceKm));
        }
        active = false;
        return lastSaved;
    }

    private void addEvent(int type, long now, int speed, double acceleration) {
        if (events.size() >= 200) {
            return;
        }
        events.add(new DrivingEvent(type, Math.max(0L, now - startElapsedMs),
                speed, (float) acceleration));
        Log.i(TAG, "event type=" + type + " elapsedMs=" + (now - startElapsedMs)
                + " speed=" + speed + " accel=" + acceleration);
    }

    private double estimateConsumption() {
        int endFuel = repository.getCombined(ReadOnlyID.ID_SUM_FUEL);
        if (startFuelUsedMl > 0 && endFuel > 0 && endFuel >= startFuelUsedMl && distanceKm >= 0.05) {
            double liters = (endFuel - startFuelUsedMl) / 1000.0;
            double calculated = liters / distanceKm * 100.0;
            if (calculated > 0 && calculated < 40) {
                return calculated;
            }
        }
        int avgRaw = repository.getCombined(ReadOnlyID.ID_AVG_FUEL_CONS);
        if (avgRaw <= 0 || avgRaw > 400) {
            avgRaw = lastAverageConsumptionRaw;
        }
        // 16711684 gibi OEM sentinel/packed değerleri 40 L/100 km sanıp
        // puanı mahvetmesin. Geçerli aralık 0..400 → 0.0..40.0 L/100 km.
        if (avgRaw > 0 && avgRaw <= 400) {
            return avgRaw / 10.0;
        }
        return 0.0;
    }
}
