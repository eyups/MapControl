package com.mapcontrol.util;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Uygulama ici "Sistem Kayitlari" ekraninin ortak, sinirli log tamponu.
 * Farkli Activity'ler ayni tampona yazabilir; MainActivity dinleyici olarak
 * baglanip TextView'i gunceller. Tampon 200 KB ile sinirlidir.
 */
public final class SystemLogBuffer {

    private static final int MAX_CHARS = 200_000;
    private static final int TRIM_TO = 150_000;
    private static final StringBuilder BUFFER = new StringBuilder();
    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    public interface Listener {
        void onSystemLog(String text);
    }

    private SystemLogBuffer() {
    }

    public static void append(String line) {
        if (line == null || line.isEmpty()) return;
        String text;
        synchronized (BUFFER) {
            BUFFER.append(line);
            if (BUFFER.length() > MAX_CHARS) {
                BUFFER.delete(0, BUFFER.length() - TRIM_TO);
            }
            text = BUFFER.toString();
        }
        for (Listener listener : LISTENERS) {
            try {
                listener.onSystemLog(text);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void clear() {
        synchronized (BUFFER) {
            BUFFER.setLength(0);
        }
        for (Listener listener : LISTENERS) {
            try {
                listener.onSystemLog("");
            } catch (Throwable ignored) {
            }
        }
    }

    public static String snapshot() {
        synchronized (BUFFER) {
            return BUFFER.toString();
        }
    }

    public static void addListener(Listener listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    public static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }
}
