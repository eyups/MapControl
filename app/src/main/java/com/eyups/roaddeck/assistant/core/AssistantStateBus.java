package com.eyups.roaddeck.assistant.core;

import android.os.Handler;
import android.os.Looper;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Asistan durum yayını. Dinleyiciler her zaman ana thread'de çağrılır; durum
 * geçişleri (log satırı hariç) döngü içinde yayınlanmaz.
 */
public final class AssistantStateBus {

    public interface Listener {
        void onAssistantState(AssistantState state, String detail);
    }

    private static volatile AssistantStateBus instance;

    public static AssistantStateBus get() {
        if (instance == null) {
            synchronized (AssistantStateBus.class) {
                if (instance == null) {
                    instance = new AssistantStateBus();
                }
            }
        }
        return instance;
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private volatile AssistantState state = AssistantState.DISABLED;
    private volatile String detail = "";

    private AssistantStateBus() {
    }

    public void addListener(Listener listener) {
        if (listener == null) {
            return;
        }
        listeners.add(listener);
        final AssistantState current = state;
        final String currentDetail = detail;
        main.post(() -> listener.onAssistantState(current, currentDetail));
    }

    public void removeListener(Listener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public AssistantState state() {
        return state;
    }

    public String detail() {
        return detail;
    }

    public void set(AssistantState next) {
        set(next, "");
    }

    public void set(AssistantState next, String message) {
        if (next == null) {
            return;
        }
        state = next;
        detail = message == null ? "" : message;
        final AssistantState snapshotState = next;
        final String snapshotDetail = detail;
        main.post(() -> {
            for (Listener listener : listeners) {
                listener.onAssistantState(snapshotState, snapshotDetail);
            }
        });
    }
}
