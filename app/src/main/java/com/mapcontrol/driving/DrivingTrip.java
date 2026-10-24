package com.mapcontrol.driving;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** One completed or in-progress drive, persisted locally. */
public final class DrivingTrip {

    public long id;
    public long startTimeMs;
    public long endTimeMs;
    public double distanceKm;
    public int durationSec;
    public int avgSpeedKmh;
    public int maxSpeedKmh;
    public double consumptionL100;
    public int harshBrakes;
    public int harshAccels;
    public int score;
    public String title = "";
    public String grade = "";
    public String description = "";
    public int sampleIntervalMs = 2000;
    /** Ayarlar > Sürüş Karnesi test verisi mi? */
    public boolean testData;
    public final List<Integer> speedSamples = new ArrayList<>();
    public final List<DrivingEvent> events = new ArrayList<>();

    public DrivingTrip copy() {
        DrivingTrip copy = new DrivingTrip();
        copy.id = id;
        copy.startTimeMs = startTimeMs;
        copy.endTimeMs = endTimeMs;
        copy.distanceKm = distanceKm;
        copy.durationSec = durationSec;
        copy.avgSpeedKmh = avgSpeedKmh;
        copy.maxSpeedKmh = maxSpeedKmh;
        copy.consumptionL100 = consumptionL100;
        copy.harshBrakes = harshBrakes;
        copy.harshAccels = harshAccels;
        copy.score = score;
        copy.title = title;
        copy.grade = grade;
        copy.description = description;
        copy.sampleIntervalMs = sampleIntervalMs;
        copy.testData = testData;
        copy.speedSamples.addAll(speedSamples);
        copy.events.addAll(events);
        return copy;
    }

    public JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            object.put("id", id);
            object.put("startTimeMs", startTimeMs);
            object.put("endTimeMs", endTimeMs);
            object.put("distanceKm", distanceKm);
            object.put("durationSec", durationSec);
            object.put("avgSpeedKmh", avgSpeedKmh);
            object.put("maxSpeedKmh", maxSpeedKmh);
            object.put("consumptionL100", consumptionL100);
            object.put("harshBrakes", harshBrakes);
            object.put("harshAccels", harshAccels);
            object.put("score", score);
            object.put("title", title);
            object.put("grade", grade);
            object.put("description", description);
            object.put("sampleIntervalMs", sampleIntervalMs);
            object.put("testData", testData);
            JSONArray samples = new JSONArray();
            for (Integer sample : speedSamples) {
                samples.put(sample == null ? 0 : sample);
            }
            object.put("speedSamples", samples);
            JSONArray eventArray = new JSONArray();
            for (DrivingEvent event : events) {
                if (event != null) {
                    eventArray.put(event.toJson());
                }
            }
            object.put("events", eventArray);
        } catch (JSONException ignored) {
        }
        return object;
    }

    public static DrivingTrip fromJson(JSONObject object) {
        if (object == null) {
            return null;
        }
        DrivingTrip trip = new DrivingTrip();
        trip.id = object.optLong("id");
        trip.startTimeMs = object.optLong("startTimeMs");
        if (trip.id == 0L) {
            trip.id = object.optLong("endTimeMs", trip.startTimeMs);
        }
        trip.endTimeMs = object.optLong("endTimeMs");
        trip.distanceKm = object.optDouble("distanceKm");
        trip.durationSec = object.optInt("durationSec");
        trip.avgSpeedKmh = object.optInt("avgSpeedKmh");
        trip.maxSpeedKmh = object.optInt("maxSpeedKmh");
        trip.consumptionL100 = object.optDouble("consumptionL100");
        trip.harshBrakes = object.optInt("harshBrakes");
        trip.harshAccels = object.optInt("harshAccels");
        trip.score = object.optInt("score");
        trip.title = object.optString("title");
        trip.grade = object.optString("grade");
        trip.description = object.optString("description");
        trip.sampleIntervalMs = Math.max(500, object.optInt("sampleIntervalMs", 2000));
        trip.testData = object.optBoolean("testData", false);
        JSONArray samples = object.optJSONArray("speedSamples");
        if (samples != null) {
            for (int i = 0; i < samples.length(); i++) {
                trip.speedSamples.add(samples.optInt(i));
            }
        }
        JSONArray eventArray = object.optJSONArray("events");
        if (eventArray != null) {
            for (int i = 0; i < eventArray.length(); i++) {
                DrivingEvent event = DrivingEvent.fromJson(eventArray.optJSONObject(i));
                if (event != null) {
                    trip.events.add(event);
                }
            }
        }
        return trip;
    }
}
