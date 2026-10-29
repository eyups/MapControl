package com.mapcontrol.cloud;

import android.content.Context;

import com.mapcontrol.driving.DrivingEvent;
import com.mapcontrol.driving.DrivingTrip;
import com.mapcontrol.driving.DrivingTripStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

/** Tamamlanmış sürüşleri idempotent uuid ile yükler; yüklenen kimlikler prefs'te tutulur. */
final class TripUploader {

    enum Result { OK, AUTH_FAILED, ERROR }

    private static final int MAX_TRIPS_PER_RUN = 20;
    private static final int MAX_SPEED_SAMPLES = 1800;
    private static final int MAX_REQUEST_BYTES = 256 * 1024;

    private final Context context;
    private final CloudConfig config;
    private final CloudHttp http;
    private final Set<Long> rejectedThisSession = new HashSet<>();

    TripUploader(Context context, CloudConfig config, CloudHttp http) {
        this.context = context.getApplicationContext();
        this.config = config;
        this.http = http;
    }

    Result uploadPending() {
        String token = config.token();
        if (token == null || token.isEmpty()) {
            return Result.AUTH_FAILED;
        }
        Set<Long> uploaded = config.uploadedTripIds();
        int attempted = 0;
        for (DrivingTrip trip : DrivingTripStore.loadHistory(context)) {
            if (attempted >= MAX_TRIPS_PER_RUN) {
                break;
            }
            if (trip == null || trip.id <= 0L || trip.endTimeMs <= 0L) {
                continue;
            }
            if (uploaded.contains(trip.id) || rejectedThisSession.contains(trip.id)) {
                continue;
            }
            JSONObject body = CloudTripMapper.build(trip);
            if (body == null) {
                continue;
            }
            attempted++;
            try {
                CloudHttp.Response response =
                        http.postJson("/api/v1/car/trips", token, body, MAX_REQUEST_BYTES);
                if (response.code == 200) {
                    config.markTripUploaded(trip.id);
                } else if (response.code == 401) {
                    return Result.AUTH_FAILED;
                } else if (response.code == 400 || response.code == 413) {
                    // Aynı bozuk kaydı her turda yeniden denememek için oturum boyunca atla.
                    rejectedThisSession.add(trip.id);
                }
            } catch (Exception ignored) {
                return Result.ERROR;
            }
        }
        return Result.OK;
    }

    static final class CloudTripMapper {

        private CloudTripMapper() {
        }

        static JSONObject build(DrivingTrip trip) {
            if (trip == null || trip.testData
                    || trip.id <= 0L || trip.startTimeMs <= 0L
                    || trip.endTimeMs <= 0L || trip.endTimeMs < trip.startTimeMs) {
                return null;
            }
            try {
                int durationSec = trip.durationSec > 0
                        ? trip.durationSec
                        : (int) ((trip.endTimeMs - trip.startTimeMs) / 1000L);
                durationSec = CloudTelemetryPolicy.clamp(durationSec, 0, 604_800);

                JSONObject object = new JSONObject();
                object.put("uuid", UUID.nameUUIDFromBytes(
                        ("mapcontrol-trip:" + trip.id).getBytes(StandardCharsets.UTF_8)).toString());
                object.put("start", isoUtc(trip.startTimeMs));
                object.put("end", isoUtc(trip.endTimeMs));
                object.put("distance_km", clampDouble(trip.distanceKm, 0d, 10_000d));
                object.put("duration_s", durationSec);

                putClamped(object, "avg_speed", trip.avgSpeedKmh, 0, 300);
                putClamped(object, "max_speed", trip.maxSpeedKmh, 0, 300);
                object.put("consumption_l100", clampDouble(trip.consumptionL100, 0d, 100d));
                putClamped(object, "harsh_brakes", trip.harshBrakes, 0, 100_000);
                putClamped(object, "harsh_accels", trip.harshAccels, 0, 100_000);
                putClamped(object, "score", trip.score, 0, 100);
                object.put("grade", truncate(trip.grade, 16));

                JSONArray samples = new JSONArray();
                int count = Math.min(trip.speedSamples.size(), MAX_SPEED_SAMPLES);
                int interval = Math.max(500, trip.sampleIntervalMs);
                for (int i = 0; i < count; i++) {
                    Integer speed = trip.speedSamples.get(i);
                    JSONObject sample = new JSONObject();
                    sample.put("ts_ms", trip.startTimeMs + (long) i * interval);
                    sample.put("speed", CloudTelemetryPolicy.clamp(
                            speed == null ? 0 : speed, 0, 300));
                    samples.put(sample);
                }
                object.put("speed_samples", samples);

                JSONArray events = new JSONArray();
                for (DrivingEvent event : trip.events) {
                    if (event == null) {
                        continue;
                    }
                    String type;
                    if (event.type == DrivingEvent.TYPE_HARSH_BRAKE) {
                        type = "harsh_brake";
                    } else if (event.type == DrivingEvent.TYPE_HARSH_ACCEL) {
                        type = "harsh_accel";
                    } else {
                        continue;
                    }
                    JSONObject item = new JSONObject();
                    item.put("type", type);
                    item.put("ts_ms", trip.startTimeMs + Math.max(0L, event.elapsedMs));
                    events.put(item);
                }
                object.put("events", events);
                return object;
            } catch (Exception ignored) {
                return null;
            }
        }

        private static String isoUtc(long epochMs) {
            SimpleDateFormat format = new SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            return format.format(new Date(epochMs));
        }

        private static void putClamped(
                JSONObject object, String key, int value, int min, int max) throws Exception {
            object.put(key, CloudTelemetryPolicy.clamp(value, min, max));
        }

        private static double clampDouble(double value, double min, double max) {
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return min;
            }
            return Math.max(min, Math.min(max, value));
        }

        private static String truncate(String value, int max) {
            if (value == null) {
                return "";
            }
            return value.length() <= max ? value : value.substring(0, max);
        }
    }
}
