package com.mapcontrol.cloud;

/** Sunucu sözleşmesindeki telemetri aralıklarını tek yerde tutar. */
final class CloudTelemetryPolicy {

    static final int MIN_UPLOAD_INTERVAL_S = 10;
    static final int MAX_UPLOAD_INTERVAL_S = 30;

    private CloudTelemetryPolicy() {
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static int uploadIntervalSeconds(int serverValue) {
        return clamp(serverValue, MIN_UPLOAD_INTERVAL_S, MAX_UPLOAD_INTERVAL_S);
    }

    static boolean shouldSendFinal(int powerMode, boolean finalAlreadySent) {
        return powerMode == 0 && !finalAlreadySent;
    }
}
