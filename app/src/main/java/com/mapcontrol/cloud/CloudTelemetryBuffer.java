package com.mapcontrol.cloud;

import java.util.ArrayDeque;

/** Disk yazmayan, en fazla 200 kayıt tutan bellek içi telemetri tamponu. */
final class CloudTelemetryBuffer<T> {

    static final int MAX_RECORDS = 200;

    private final ArrayDeque<T> items = new ArrayDeque<>();

    synchronized void add(T item) {
        if (item == null) {
            return;
        }
        items.addLast(item);
        while (items.size() > MAX_RECORDS) {
            items.removeFirst();
        }
    }

    synchronized T peek() {
        return items.peekFirst();
    }

    synchronized void removeFirst() {
        items.pollFirst();
    }

    synchronized int size() {
        return items.size();
    }

    synchronized void clear() {
        items.clear();
    }
}
