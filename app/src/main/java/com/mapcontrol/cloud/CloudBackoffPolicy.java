package com.mapcontrol.cloud;

/** Ağ hatalarında hızlı döngüyü engelleyen sabit üst sınırlı geri çekilme. */
final class CloudBackoffPolicy {

    static final long INITIAL_MS = 15_000L;
    static final long MAX_MS = 60_000L;

    private final long initialMs;
    private long nextMs;

    CloudBackoffPolicy() {
        this(INITIAL_MS);
    }

    CloudBackoffPolicy(long initialMs) {
        this.initialMs = Math.max(1_000L, initialMs);
        this.nextMs = this.initialMs;
    }

    long nextDelayMs() {
        long delay = nextMs;
        nextMs = Math.min(MAX_MS, nextMs * 2L);
        return delay;
    }

    void reset() {
        nextMs = initialMs;
    }
}
