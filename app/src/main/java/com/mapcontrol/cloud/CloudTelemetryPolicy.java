package com.mapcontrol.cloud;

/** Sunucu sözleşmesindeki telemetri aralıklarını tek yerde tutar. */
final class CloudTelemetryPolicy {

    static final int MIN_UPLOAD_INTERVAL_S = 10;
    static final int MAX_UPLOAD_INTERVAL_S = 30;
    static final long MAX_METRICS_AGE_MS = 60_000L;

    private CloudTelemetryPolicy() {
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static int uploadIntervalSeconds(int serverValue) {
        return clamp(serverValue, MIN_UPLOAD_INTERVAL_S, MAX_UPLOAD_INTERVAL_S);
    }

    static boolean shouldSendFinal(int powerMode, boolean finalAlreadySent) {
        return (powerMode == 0 || powerMode == 1) && !finalAlreadySent;
    }

    /** Canlı telemetri yalnız kontak ON ve taze metriklerle gönderilir. */
    static boolean liveUploadAllowed(int powerMode, boolean connected, long ageMs) {
        return connected && powerMode == 2 && ageMs >= 0L && ageMs <= MAX_METRICS_AGE_MS;
    }

    /** OEM yakıt: 0–100 tam yüzde; >100 ise onda bir (348 -> %34.8). */
    static int fuelPercent(int raw) {
        if (raw < 0) {
            return -1;
        }
        int percent = raw <= 100 ? raw : Math.round(raw / 10f);
        return clamp(percent, 0, 100);
    }

    /** IBS voltajı mV ise 0.1 V'a çevir; zaten 0–255 ise olduğu gibi bırak. */
    static int batteryTenthsVolts(int raw) {
        if (raw <= 0) {
            return -1;
        }
        if (raw <= 255) {
            return raw;
        }
        return clamp(Math.round(raw / 100f), 0, 255);
    }

    /** Bu araçta 2000 km, menzil modülünün başlatılmamış placeholder değeri. */
    static int rangeKm(int raw) {
        if (raw < 0 || raw >= 2000) {
            return -1;
        }
        return clamp(raw, 0, 1999);
    }
}
