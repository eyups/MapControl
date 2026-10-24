package com.mapcontrol.driving;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Local, newest-first driving history plus the most recent trip report. */
public final class DrivingTripStore {

    private static final String TAG = "DrivingStore";
    private static final String PREFS = "MapControlPrefs";
    private static final String KEY_LAST_TRIP = "drivingLastTrip";
    private static final String KEY_HISTORY = "drivingHistory";
    private static final int MAX_HISTORY = 100;

    private DrivingTripStore() {
    }

    public static void save(Context context, DrivingTrip trip) {
        if (trip == null) {
            return;
        }
        List<DrivingTrip> one = new ArrayList<>();
        one.add(trip);
        saveAll(context, one);
    }

    /** Birden fazla sürüşü tek seferde ekler; mevcut gerçek sürüşler korunur. */
    public static void saveAll(Context context, List<DrivingTrip> newTrips) {
        if (context == null || newTrips == null || newTrips.isEmpty()) {
            return;
        }
        List<DrivingTrip> trips = loadHistory(context);
        Log.i(TAG, "saveAll incoming=" + newTrips.size() + " existing=" + trips.size());
        for (DrivingTrip trip : newTrips) {
            if (trip == null) {
                continue;
            }
            if (trip.id <= 0L) {
                trip.id = trip.endTimeMs > 0L
                        ? trip.endTimeMs
                        : (trip.startTimeMs > 0L ? trip.startTimeMs : System.currentTimeMillis());
            }
            for (int i = trips.size() - 1; i >= 0; i--) {
                if (trips.get(i).id == trip.id) {
                    trips.remove(i);
                }
            }
            trips.add(trip);
        }
        writeHistory(context, trips);
    }

    /** Sadece testData=true olan kayıtları siler; gerçek sürüşlere dokunmaz. */
    public static int deleteTestTrips(Context context) {
        if (context == null) {
            return 0;
        }
        List<DrivingTrip> trips = loadHistory(context);
        int removed = 0;
        for (int i = trips.size() - 1; i >= 0; i--) {
            if (trips.get(i).testData) {
                trips.remove(i);
                removed++;
            }
        }
        if (removed > 0) {
            writeHistory(context, trips);
        }
        return removed;
    }

    public static int countTestTrips(Context context) {
        int count = 0;
        for (DrivingTrip trip : loadHistory(context)) {
            if (trip.testData) count++;
        }
        return count;
    }

    private static void writeHistory(Context context, List<DrivingTrip> trips) {
        sortNewestFirst(trips);
        if (trips.size() > MAX_HISTORY) {
            trips = new ArrayList<>(trips.subList(0, MAX_HISTORY));
        }
        JSONArray array = new JSONArray();
        for (DrivingTrip item : trips) {
            array.put(item.toJson());
        }
        SharedPreferences.Editor editor = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_HISTORY, array.toString());
        if (trips.isEmpty()) {
            editor.remove(KEY_LAST_TRIP);
        } else {
            editor.putString(KEY_LAST_TRIP, trips.get(0).toJson().toString());
        }
        editor.apply();
        Log.i(TAG, "writeHistory count=" + trips.size() + " json=" + array.length()
                + " last=" + (trips.isEmpty() ? "none" : trips.get(0).id));
    }

    /** Newest first. Also migrates the old single-trip preference. */
    public static List<DrivingTrip> loadHistory(Context context) {
        List<DrivingTrip> trips = readHistory(context);
        if (trips.isEmpty()) {
            DrivingTrip last = readLast(context);
            if (last != null) {
                trips.add(last);
            }
        }
        sortNewestFirst(trips);
        for (DrivingTrip trip : trips) {
            if (trip.distanceKm >= 0.1) {
                DrivingScore.evaluate(trip);
            }
        }
        return trips;
    }

    public static DrivingTrip load(Context context) {
        List<DrivingTrip> trips = loadHistory(context);
        return trips.isEmpty() ? null : trips.get(0);
    }

    public static DrivingTrip find(Context context, long tripId) {
        if (tripId <= 0L) {
            return null;
        }
        for (DrivingTrip trip : loadHistory(context)) {
            if (trip.id == tripId) {
                return trip;
            }
        }
        return null;
    }

    private static List<DrivingTrip> readHistory(Context context) {
        List<DrivingTrip> trips = new ArrayList<>();
        if (context == null) {
            return trips;
        }
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_HISTORY, null);
        if (raw == null || raw.isEmpty()) {
            Log.i(TAG, "readHistory empty");
            return trips;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                DrivingTrip trip = DrivingTrip.fromJson(array.optJSONObject(i));
                if (trip != null) {
                    trips.add(trip);
                }
            }
            Log.i(TAG, "readHistory parsed=" + trips.size() + " json=" + array.length()
                    + " rawLen=" + raw.length());
        } catch (Exception e) {
            Log.e(TAG, "readHistory parse failed rawLen=" + raw.length(), e);
        }
        return trips;
    }

    private static DrivingTrip readLast(Context context) {
        if (context == null) {
            return null;
        }
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_LAST_TRIP, null);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            DrivingTrip trip = DrivingTrip.fromJson(new JSONObject(raw));
            if (trip != null && trip.distanceKm >= 0.1) {
                DrivingScore.evaluate(trip);
            }
            return trip;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void sortNewestFirst(List<DrivingTrip> trips) {
        Collections.sort(trips, new Comparator<DrivingTrip>() {
            @Override
            public int compare(DrivingTrip a, DrivingTrip b) {
                int byStart = Long.compare(b.startTimeMs, a.startTimeMs);
                if (byStart != 0) {
                    return byStart;
                }
                return Long.compare(b.endTimeMs, a.endTimeMs);
            }
        });
    }
}
