package com.mapcontrol.vehicle;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import com.mapcontrol.util.TargetPackageStore;

import java.util.ArrayDeque;
import java.util.function.Consumer;

/**
 * Yoğun, dur-kalk trafikte iç sirkülasyonu açar; yol açılınca veya süre dolunca dış havaya döner.
 * Önde araç sensörü doğrulanmadığı için karar yalnızca hız örüntüsüne dayanır.
 * {@link #tick} servisin arka plan thread'inde yaklaşık 2 sn'de bir çağrılır.
 */
public final class RecirculationAutomation {

    public static final String PREF_ENABLED = "recircAutoEnabled";

    private static final int WINDOW_SAMPLES = 45;
    private static final int MIN_SAMPLES = 30;
    /** Bu ortalamanın altı ve en az bir yavaş örnek → trafik. */
    private static final int TRAFFIC_AVG_KMH = 25;
    private static final int CRAWL_KMH = 8;
    /** Pencerede bundan hızlı en az bir örnek olmalı; park halinde beklemek trafik sayılmaz. */
    private static final int MOVING_KMH = 6;
    /** Bu ortalamanın üstü → yol açık, dış havaya dön. */
    private static final int FREE_FLOW_AVG_KMH = 45;
    private static final long MAX_ON_MS = 12L * 60_000L;
    private static final long TIMEOUT_COOLDOWN_MS = 5L * 60_000L;
    private static final long USER_OVERRIDE_COOLDOWN_MS = 10L * 60_000L;
    private static final long ACTION_COOLDOWN_MS = 60_000L;

    private final Context appContext;
    private final Consumer<String> log;
    private final ArrayDeque<Integer> speeds = new ArrayDeque<>();
    private boolean owned;
    private long ownedSinceMs;
    private long cooldownUntilMs;

    public RecirculationAutomation(Context context, Consumer<String> log) {
        this.appContext = context.getApplicationContext();
        this.log = log;
    }

    public void tick(int powerMode) {
        if (!isEnabled() || powerMode != 2 || !ComfortCommands.isConnected()) {
            reset();
            return;
        }
        int speed = ComfortCommands.readSpeedKmh();
        if (speed < 0) {
            return;
        }
        speeds.addLast(speed);
        while (speeds.size() > WINDOW_SAMPLES) {
            speeds.removeFirst();
        }
        if (!ComfortCommands.isHvacRunning()) {
            return;
        }
        boolean recircOn = ComfortCommands.isRecirculationOn();
        long now = SystemClock.elapsedRealtime();

        if (owned) {
            if (!recircOn) {
                owned = false;
                cooldownUntilMs = now + USER_OVERRIDE_COOLDOWN_MS;
                log.accept("[Sirkülasyon] Kullanıcı kapattı, otomasyon 10 dk bekliyor");
                return;
            }
            boolean freeFlow = speeds.size() >= MIN_SAMPLES && average() >= FREE_FLOW_AVG_KMH;
            boolean timedOut = now - ownedSinceMs >= MAX_ON_MS;
            if (freeFlow || timedOut) {
                ComfortCommands.sendRecirculation(false);
                owned = false;
                cooldownUntilMs = now + (timedOut ? TIMEOUT_COOLDOWN_MS : ACTION_COOLDOWN_MS);
                log.accept("[Sirkülasyon] Dış havaya dönüldü (" + (timedOut ? "süre doldu" : "yol açık")
                        + ", ort. hız " + average() + " km/h)");
            }
            return;
        }

        if (recircOn || now < cooldownUntilMs || speeds.size() < MIN_SAMPLES) {
            return;
        }
        if (average() <= TRAFFIC_AVG_KMH && minimum() <= CRAWL_KMH && maximum() >= MOVING_KMH) {
            ComfortCommands.sendRecirculation(true);
            owned = true;
            ownedSinceMs = now;
            log.accept("[Sirkülasyon] Trafik algılandı, iç sirkülasyon açıldı (ort. hız " + average() + " km/h)");
        }
    }

    private boolean isEnabled() {
        SharedPreferences prefs = TargetPackageStore.prefs(appContext);
        return prefs.getBoolean(PREF_ENABLED, false);
    }

    private void reset() {
        speeds.clear();
        owned = false;
    }

    private int average() {
        int sum = 0;
        for (int s : speeds) {
            sum += s;
        }
        return speeds.isEmpty() ? 0 : sum / speeds.size();
    }

    private int minimum() {
        int min = Integer.MAX_VALUE;
        for (int s : speeds) {
            min = Math.min(min, s);
        }
        return min;
    }

    private int maximum() {
        int max = 0;
        for (int s : speeds) {
            max = Math.max(max, s);
        }
        return max;
    }
}
