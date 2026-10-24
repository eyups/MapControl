package com.mapcontrol.driving;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Settings > Sürüş Karnesi için gerçekçi, silinebilir test sürüşleri üretir. */
public final class DrivingTestData {

    public static final int DEFAULT_COUNT = 14;
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    private static final long HOUR_MS = 60L * 60L * 1000L;
    private static final long MINUTE_MS = 60L * 1000L;

    private DrivingTestData() {
    }

    /**
     * Önce önceki test verilerini siler, sonra {@code count} yeni test sürüşü ekler.
     * Gerçek sürüş kayıtları korunur.
     */
    public static int generate(Context context, int count) {
        if (context == null || count <= 0) {
            return 0;
        }
        DrivingTripStore.deleteTestTrips(context);

        Random random = new Random();
        List<DrivingTrip> trips = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            int durationSec = 420 + random.nextInt(3900); // 7-71 dk
            int avgSpeed = 18 + random.nextInt(74);        // 18-91 km/h
            double distance = Math.max(0.8,
                    avgSpeed * durationSec / 3600.0 * (0.88 + random.nextDouble() * 0.24));
            int maxSpeed = Math.min(180, avgSpeed + 12 + random.nextInt(48));

            double expected = DrivingScore.expectedConsumptionL100(avgSpeed);
            double consumption = Math.max(4.5,
                    expected + (random.nextDouble() * 4.2 - 1.6));

            boolean aggressive = random.nextInt(4) == 0;
            int harshBrakes = aggressive ? 3 + random.nextInt(6) : random.nextInt(5);
            int harshAccels = aggressive ? 3 + random.nextInt(5) : random.nextInt(4);
            if (random.nextInt(6) == 0) {
                harshBrakes = 0;
                harshAccels = 0;
            }

            long startTime = now - durationSec * 1000L
                    - random.nextInt(46) * DAY_MS
                    - random.nextInt(18) * HOUR_MS
                    - random.nextInt(60) * MINUTE_MS;

            DrivingTrip trip = new DrivingTrip();
            trip.startTimeMs = startTime;
            trip.endTimeMs = startTime + durationSec * 1000L;
            trip.id = trip.endTimeMs + i;
            trip.durationSec = durationSec;
            trip.distanceKm = distance;
            trip.avgSpeedKmh = avgSpeed;
            trip.maxSpeedKmh = maxSpeed;
            trip.consumptionL100 = consumption;
            trip.harshBrakes = harshBrakes;
            trip.harshAccels = harshAccels;
            trip.testData = true;

            int sampleCount = Math.max(12, Math.min(120, durationSec / 90));
            trip.sampleIntervalMs = Math.max(1000, durationSec * 1000 / sampleCount);
            double phase = random.nextDouble() * Math.PI * 2;
            for (int sample = 0; sample < sampleCount; sample++) {
                double wave = Math.sin(sample * 0.42 + phase) * (5 + random.nextDouble() * 11);
                int value = (int) Math.round(avgSpeed + wave + (random.nextDouble() - 0.5) * 14);
                if (sample == sampleCount / 3) {
                    value = maxSpeed;
                }
                trip.speedSamples.add(Math.max(0, Math.min(maxSpeed, value)));
            }

            for (int event = 0; event < harshBrakes; event++) {
                long elapsed = (long) (random.nextDouble() * durationSec * 1000L);
                int speed = 25 + random.nextInt(85);
                trip.events.add(new DrivingEvent(DrivingEvent.TYPE_HARSH_BRAKE,
                        elapsed, speed, -(2.8f + random.nextFloat() * 2.2f)));
            }
            for (int event = 0; event < harshAccels; event++) {
                long elapsed = (long) (random.nextDouble() * durationSec * 1000L);
                int speed = 15 + random.nextInt(70);
                trip.events.add(new DrivingEvent(DrivingEvent.TYPE_HARSH_ACCEL,
                        elapsed, speed, 2.2f + random.nextFloat() * 2.0f));
            }

            DrivingScore.evaluate(trip);
            trips.add(trip);
        }

        DrivingTripStore.saveAll(context, trips);
        return trips.size();
    }
}
