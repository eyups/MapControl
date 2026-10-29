package com.mapcontrol.driving;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;
import com.mapcontrol.tts.MapControlSpeech;
import com.mapcontrol.vehicle.VehicleMetricsRepository;
import com.mapcontrol.vehicle.VehicleMetricsSnapshot;

import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Canli surus kocu. Yalnizca kullanici ayarlardan acarsa ve bir surus
 * oturumu aktifken calisir; boot/acilis aninda konusmaz.
 */
public final class DrivingCoach {
    private static final String TAG = "DrivingCoach";
    public static final String PREFS = "MapControlPrefs";
    public static final String KEY_ENABLED = "drivingCoachEnabled";

    private static final int PRIORITY_SAFETY = 0;
    private static final int PRIORITY_LIMIT = 1;
    private static final long DEDUPE_MS = 10000L;
    private static final long LIMIT_COOLDOWN_MS = 30000L;
    private static final long POLL_MS = 1000L;

    private static volatile DrivingCoach instance;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final PriorityQueue<Message> queue = new PriorityQueue<>(
            Comparator.comparingInt((Message m) -> m.priority).thenComparingLong(m -> m.seq));
    private final long[] lastSpokenAt = new long[16];
    private long seq;
    private boolean enabled;
    private boolean tripActive;
    private boolean speaking;
    private boolean polling;
    private long overLimitSinceMs;
    private long lastLimitAlertMs;

    private DrivingCoach(Context context) {
        app = context.getApplicationContext();
        enabled = prefs().getBoolean(KEY_ENABLED, false);
    }

    public static DrivingCoach get(Context context) {
        if (instance == null) {
            synchronized (DrivingCoach.class) {
                if (instance == null) {
                    instance = new DrivingCoach(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public static boolean isEnabled(Context context) {
        return context != null && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean value) {
        if (context == null) {
            return;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, value).apply();
        DrivingCoach coach = get(context);
        coach.enabled = value;
        if (!value) {
            coach.onTripEnded();
        } else if (coach.tripActive) {
            coach.startPolling();
        }
    }

    /** DrivingSessionRecorder tarafindan cagrilir. */
    public void onTripStarted() {
        tripActive = true;
        if (enabled) {
            startPolling();
        }
    }

    /** DrivingSessionRecorder tarafindan cagrilir. */
    public void onTripEnded() {
        tripActive = false;
        polling = false;
        overLimitSinceMs = 0L;
        main.removeCallbacks(pollRunnable);
        queue.clear();
    }

    public void onDrivingEvent(DrivingEvent event) {
        if (!enabled || !tripActive || event == null) {
            return;
        }
        if (event.isBrake()) {
            enqueue(2, PRIORITY_SAFETY, "Sert fren. Takip mesafesini artır.");
        } else if (event.type == DrivingEvent.TYPE_HARSH_ACCEL) {
            enqueue(3, PRIORITY_SAFETY, "Sert gaz. Daha yumuşak hızlan.");
        }
    }

    private void startPolling() {
        if (polling || !enabled || !tripActive) {
            return;
        }
        polling = true;
        main.removeCallbacks(pollRunnable);
        main.postDelayed(pollRunnable, POLL_MS);
    }

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!enabled || !tripActive) {
                polling = false;
                return;
            }
            try {
                pollSpeedLimit();
            } catch (Throwable t) {
                Log.w(TAG, "coach poll failed", t);
            }
            main.postDelayed(this, POLL_MS);
        }
    };

    private void pollSpeedLimit() {
        CarInfoProxy proxy = CarInfoProxy.getInstance();
        if (proxy == null || !proxy.isServiceConnnected()) {
            return;
        }
        int limit = read(proxy, VDEventCarInfo.MODULE_CAR_SETTING,
                CarSettingID.ID_NAV_SPEED_LIMIT);
        if (limit < 20 || limit > 160) {
            limit = read(proxy, VDEventCarInfo.MODULE_CAR_SETTING,
                    CarSettingID.ID_IDCU_SPEEDLMTNMBERE);
        }
        if (limit < 20 || limit > 160) {
            overLimitSinceMs = 0L;
            return;
        }
        VehicleMetricsSnapshot snapshot = VehicleMetricsRepository
                .getBackground(app).currentSnapshot();
        int speed = snapshot == null ? 0 : snapshot.preferredSpeed();
        if (speed <= 0) {
            overLimitSinceMs = 0L;
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (speed > limit + 5) {
            if (overLimitSinceMs == 0L) {
                overLimitSinceMs = now;
            } else if (now - overLimitSinceMs >= 2000L
                    && now - lastLimitAlertMs >= LIMIT_COOLDOWN_MS) {
                lastLimitAlertMs = now;
                enqueue(4, PRIORITY_LIMIT,
                        "Hız limiti " + limit + ". Biraz yavaşla.");
            }
        } else {
            overLimitSinceMs = 0L;
        }
    }

    private void enqueue(int kind, int priority, String text) {
        long now = SystemClock.elapsedRealtime();
        if (kind >= 0 && kind < lastSpokenAt.length
                && now - lastSpokenAt[kind] < DEDUPE_MS) {
            return;
        }
        if (queue.size() > 6) {
            queue.poll();
        }
        queue.add(new Message(priority, seq++, kind, text));
        pump();
    }

    private void pump() {
        if (speaking || queue.isEmpty()) {
            return;
        }
        if (MapControlSpeech.isSpeaking()) {
            main.postDelayed(this::pump, 500L);
            return;
        }
        Message message = queue.poll();
        if (message == null) {
            return;
        }
        if (message.kind >= 0 && message.kind < lastSpokenAt.length) {
            lastSpokenAt[message.kind] = SystemClock.elapsedRealtime();
        }
        speaking = true;
        MapControlSpeech.speak(app, message.text, message.text, 0L, () -> {
            speaking = false;
            main.post(this::pump);
        });
    }

    private static int read(CarInfoProxy proxy, int module, int id) {
        try {
            int[] values = proxy.getItemValues(module, id);
            if (values == null || values.length == 0) {
                return -1;
            }
            return values[0];
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private SharedPreferences prefs() {
        return app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static final class Message {
        final int priority;
        final long seq;
        final int kind;
        final String text;

        Message(int priority, long seq, int kind, String text) {
            this.priority = priority;
            this.seq = seq;
            this.kind = kind;
            this.text = text;
        }
    }
}
