package com.eyups.roaddeck.cloud;

/**
 * Park fotoğrafı karar mantığı; Android'e bağlı değildir (JVM testleri).
 * Tasarım: mapcontrol-cloud/docs/parking_photo_quality.md
 */
final class ParkPhotoPolicy {

    static final int GEAR_PARK = 1;
    /** P + hız 0 bu kadar sürünce yakalanır (vites geçişindeki sıçramaları eler). */
    static final long PARK_STABLE_MS = 1_000L;

    static final int PREVIEW_QUALITY = 60;
    static final int PREVIEW_MAX_WIDTH = 640;
    /** Tam katman: önce q90, bütçeyi aşarsa sırayla düşer. */
    static final int[] FULL_QUALITY_LADDER = {90, 85, 80, 70};
    /** Sunucu sınırlarının altında pay bırakılır (640 KB / 2,5 MB istek). */
    static final int MAX_IMAGE_BYTES = 600 * 1024;
    static final int MAX_TIER_BYTES = 2_200 * 1024;

    /** Kontak kapandıktan sonra ağ denemeleri bu süre sürer; sonra bir sonraki kontağa kalır. */
    static final long RETRY_WINDOW_MS = 3 * 60_000L;
    private static final long[] RETRY_DELAYS_MS = {1_000L, 2_000L, 4_000L, 8_000L, 15_000L};

    enum Action { NONE, CAPTURE, DISCARD }

    private ParkPhotoPolicy() {
    }

    /** P bölümünü izler: P + hız 0'a girişte bir kez yakalama, P'den çıkışta taslağı bırakma. */
    static final class ParkEpisode {
        private long parkedSinceMs = -1L;
        private boolean captured;

        Action update(boolean gearValid, int gear, int speedKmh, int powerMode, long nowMs) {
            if (powerMode != 2) {
                return Action.NONE;
            }
            boolean inPark = gearValid && gear == GEAR_PARK && speedKmh <= 0;
            if (!inPark) {
                // Vites okunamıyorsa karar verme; geçerli ve P dışıysa bölüm biter.
                if (!gearValid && speedKmh <= 0) {
                    return Action.NONE;
                }
                boolean hadCapture = captured;
                parkedSinceMs = -1L;
                captured = false;
                return hadCapture ? Action.DISCARD : Action.NONE;
            }
            if (parkedSinceMs < 0L) {
                parkedSinceMs = nowMs;
            }
            if (!captured && nowMs - parkedSinceMs >= PARK_STABLE_MS) {
                captured = true;
                return Action.CAPTURE;
            }
            return Action.NONE;
        }

        /** Kontak kapandı: bir sonraki sürüşte yeni bölüm başlar. */
        void reset() {
            parkedSinceMs = -1L;
            captured = false;
        }

        boolean hasCaptured() {
            return captured;
        }
    }

    static int previewStep(int width) {
        return Math.max(1, (width + PREVIEW_MAX_WIDTH - 1) / PREVIEW_MAX_WIDTH);
    }

    static boolean fitsBudget(int[] sizes, int perImageMax, int totalMax) {
        if (sizes == null) {
            return false;
        }
        long total = 0L;
        for (int size : sizes) {
            if (size > perImageMax) {
                return false;
            }
            total += Math.max(0, size);
        }
        return total <= totalMax;
    }

    static long retryDelayMs(int attempt) {
        int index = Math.max(0, Math.min(attempt, RETRY_DELAYS_MS.length - 1));
        return RETRY_DELAYS_MS[index];
    }

    /** Araç açıkken ya da kontak kapanalı RETRY_WINDOW_MS'ten az olduysa ağ denenir. */
    static boolean inRetryWindow(int powerMode, long ignitionOffAtMs, long nowMs) {
        if (powerMode == 2) {
            return true;
        }
        return ignitionOffAtMs > 0L && nowMs - ignitionOffAtMs < RETRY_WINDOW_MS;
    }

    /** Ses yalnız park çıkışı bağlamında çalar; sonraki sürüşte tamamlanan geç yüklemede çalmaz. */
    static boolean shouldChime(boolean fullDone, boolean commitDone, boolean alreadyChimed,
            boolean chimeEnabled, long ignitionOffAtMs, long nowMs) {
        return fullDone && commitDone && !alreadyChimed && chimeEnabled
                && ignitionOffAtMs > 0L && nowMs - ignitionOffAtMs < RETRY_WINDOW_MS;
    }
}
