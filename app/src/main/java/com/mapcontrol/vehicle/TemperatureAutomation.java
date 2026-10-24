package com.mapcontrol.vehicle;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.mapcontrol.R;
import com.mapcontrol.util.TargetPackageStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Dış sıcaklığa göre koltuk ısıtma/havalandırma ve direksiyon ısıtmasını kontak sonrası bir kez açar.
 * Kullanıcı sonradan kapatırsa aynı oturumda tekrar açmaz. {@link #tick} servis thread'inde ~2 sn'de çağrılır.
 */
public final class TemperatureAutomation {

    public static final String PREF_ENABLED = "tempAutoEnabled";

    public enum Kind { SEAT_HEAT, SEAT_VENT, STEERING_HEAT }

    /** Tek bir otomasyon kuralı; tercih anahtarları {@code tempAuto_<key>_*}. */
    public static final class Rule {
        public final String key;
        public final int titleRes;
        public final Kind kind;
        public final int commandId;
        /** Aynı koltuğun karşı fonksiyonu (ısıtma↔havalandırma); yoksa -1. */
        public final int oppositeId;
        public final int defaultThreshold;
        public final int defaultLevel;

        Rule(String key, int titleRes, Kind kind, int commandId, int oppositeId,
                int defaultThreshold, int defaultLevel) {
            this.key = key;
            this.titleRes = titleRes;
            this.kind = kind;
            this.commandId = commandId;
            this.oppositeId = oppositeId;
            this.defaultThreshold = defaultThreshold;
            this.defaultLevel = defaultLevel;
        }

        /** Isıtma kuralları eşiğin altında, havalandırma eşiğin üstünde çalışır. */
        public boolean isHeating() {
            return kind != Kind.SEAT_VENT;
        }

        public boolean hasLevel() {
            return kind != Kind.STEERING_HEAT;
        }

        public String prefEnabled() {
            return "tempAuto_" + key + "_on";
        }

        public String prefThreshold() {
            return "tempAuto_" + key + "_threshold";
        }

        public String prefLevel() {
            return "tempAuto_" + key + "_level";
        }

        public String prefDelay() {
            return "tempAuto_" + key + "_delay";
        }
    }

    public static final List<Rule> RULES = Collections.unmodifiableList(Arrays.asList(
            new Rule("driverHeat", R.string.automation_rule_driver_heat, Kind.SEAT_HEAT,
                    CarSettingID.ID_CAR_DRIVER_SEAT_HEAT, CarSettingID.ID_CAR_DRIVER_SEAT_VENTILATION, 18, 2),
            new Rule("passengerHeat", R.string.automation_rule_passenger_heat, Kind.SEAT_HEAT,
                    CarSettingID.ID_CAR_COPILOT_SEAT_HEAT, CarSettingID.ID_CAR_COPILOT_SEAT_VENTILATION, 18, 2),
            new Rule("rearLeftHeat", R.string.automation_rule_rear_left_heat, Kind.SEAT_HEAT,
                    CarSettingID.ID_CAR_RL_SEAT_HEAT_STATUS, -1, 15, 1),
            new Rule("rearRightHeat", R.string.automation_rule_rear_right_heat, Kind.SEAT_HEAT,
                    CarSettingID.ID_CAR_RR_VR_SEAT_HEAT_STATUS, -1, 15, 1),
            new Rule("driverVent", R.string.automation_rule_driver_vent, Kind.SEAT_VENT,
                    CarSettingID.ID_CAR_DRIVER_SEAT_VENTILATION, CarSettingID.ID_CAR_DRIVER_SEAT_HEAT, 27, 2),
            new Rule("passengerVent", R.string.automation_rule_passenger_vent, Kind.SEAT_VENT,
                    CarSettingID.ID_CAR_COPILOT_SEAT_VENTILATION, CarSettingID.ID_CAR_COPILOT_SEAT_HEAT, 27, 2),
            new Rule("steeringHeat", R.string.automation_rule_steering_heat, Kind.STEERING_HEAT,
                    CarSettingID.ID_CAR_STEER_WHEEL_HEAT, -1, 12, 1)));

    private static final int MIN_PLAUSIBLE_C = -50;
    private static final int MAX_PLAUSIBLE_C = 70;
    /** Gecikme dolduktan sonra koşul bu süre içinde sağlanmazsa kural bu oturum için bırakılır. */
    private static final long TRIGGER_WINDOW_MS = 10L * 60_000L;
    private static final long VERIFY_AFTER_MS = 3_000L;

    private final Context appContext;
    private final Consumer<String> log;
    private final Set<String> finished = new HashSet<>();
    private final Set<String> warned = new HashSet<>();
    private final Map<String, Long> verifyAt = new HashMap<>();
    private long sessionStartMs;

    public TemperatureAutomation(Context context, Consumer<String> log) {
        this.appContext = context.getApplicationContext();
        this.log = log;
    }

    public void tick(int powerMode) {
        SharedPreferences prefs = TargetPackageStore.prefs(appContext);
        if (!prefs.getBoolean(PREF_ENABLED, false) || powerMode != 2 || !ComfortCommands.isConnected()) {
            reset();
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (sessionStartMs == 0) {
            sessionStartMs = now;
        }
        verifyPending(now);

        List<Rule> pending = new ArrayList<>();
        for (Rule rule : RULES) {
            if (prefs.getBoolean(rule.prefEnabled(), false) && !finished.contains(rule.key)) {
                pending.add(rule);
            }
        }
        if (pending.isEmpty()) {
            return;
        }

        float temp = ComfortCommands.readOutsideTempC();
        if (Float.isNaN(temp) || temp < MIN_PLAUSIBLE_C || temp > MAX_PLAUSIBLE_C) {
            warnOnce("temp", "[Sıcaklık] Dış sıcaklık okunamadı veya geçersiz (" + temp + ")");
            return;
        }
        long elapsedMs = now - sessionStartMs;
        boolean engine = ComfortCommands.isEngineRunning();

        for (Rule rule : pending) {
            long delayMs = prefs.getInt(rule.prefDelay(), 0) * 1000L;
            if (elapsedMs < delayMs) {
                continue;
            }
            if (elapsedMs > delayMs + TRIGGER_WINDOW_MS) {
                finish(rule, "koşul süre içinde oluşmadı");
                continue;
            }
            int threshold = prefs.getInt(rule.prefThreshold(), rule.defaultThreshold);
            boolean met = rule.isHeating() ? temp < threshold : temp > threshold;
            if (!met) {
                continue;
            }
            if (rule.kind != Kind.STEERING_HEAT && !engine) {
                warnOnce("engine_" + rule.key, "[Sıcaklık] " + title(rule) + ": motor çalışmıyor, bekleniyor");
                continue;
            }
            apply(rule, prefs, temp);
        }
    }

    private void apply(Rule rule, SharedPreferences prefs, float temp) {
        if (rule.kind == Kind.STEERING_HEAT) {
            if (ComfortCommands.isSteeringHeatOn()) {
                finish(rule, "direksiyon ısıtma zaten açık");
                return;
            }
            ComfortCommands.toggleSteeringHeat();
        } else {
            if (ComfortCommands.readSeatLevel(rule.commandId) >= 1) {
                finish(rule, "zaten açık");
                return;
            }
            if (rule.oppositeId > 0 && ComfortCommands.readSeatLevel(rule.oppositeId) >= 1) {
                finish(rule, "karşı fonksiyon açık");
                return;
            }
            int level = Math.max(1, Math.min(ComfortCommands.SEAT_LEVEL_MAX,
                    prefs.getInt(rule.prefLevel(), rule.defaultLevel)));
            ComfortCommands.sendSeatLevel(rule.commandId, level);
            log.accept("[Sıcaklık] " + title(rule) + " açıldı (dış " + temp + "°C, seviye " + level + ")");
            finished.add(rule.key);
            verifyAt.put(rule.key, SystemClock.elapsedRealtime() + VERIFY_AFTER_MS);
            return;
        }
        log.accept("[Sıcaklık] " + title(rule) + " açıldı (dış " + temp + "°C)");
        finished.add(rule.key);
        verifyAt.put(rule.key, SystemClock.elapsedRealtime() + VERIFY_AFTER_MS);
    }

    private void verifyPending(long now) {
        if (verifyAt.isEmpty()) {
            return;
        }
        for (Rule rule : RULES) {
            Long at = verifyAt.get(rule.key);
            if (at == null || now < at) {
                continue;
            }
            verifyAt.remove(rule.key);
            boolean on = rule.kind == Kind.STEERING_HEAT
                    ? ComfortCommands.isSteeringHeatOn()
                    : ComfortCommands.readSeatLevel(rule.commandId) >= 1;
            log.accept("[Sıcaklık] " + title(rule) + (on ? ": doğrulandı" : ": komut uygulanmadı (araç kabul etmedi)"));
        }
    }

    private void finish(Rule rule, String reason) {
        finished.add(rule.key);
        log.accept("[Sıcaklık] " + title(rule) + ": " + reason);
    }

    private void warnOnce(String key, String message) {
        if (warned.add(key)) {
            log.accept(message);
        }
    }

    private String title(Rule rule) {
        return appContext.getString(rule.titleRes);
    }

    private void reset() {
        if (sessionStartMs != 0 || !finished.isEmpty()) {
            finished.clear();
            warned.clear();
            verifyAt.clear();
            sessionStartMs = 0;
        }
    }
}
