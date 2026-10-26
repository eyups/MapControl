package com.mapcontrol.cloud;

/** Ağ hatalarında hızlı döngüyü engelleyen sabit üst sınırlı geri çekilme. */
final class CloudBackoffPolicy {

    static final long INITIAL_MS = 15_000L;
    static final long MAX_MS = 60_000L;

    private long nextMs = INITIAL_MS;

    long nextDelayMs() {
        long delay = nextMs;
        nextMs = Math.min(MAX_MS, nextMs * 2L);
        return delay;
    }

    void reset() {
        nextMs = INITIAL_MS;
    }
}
