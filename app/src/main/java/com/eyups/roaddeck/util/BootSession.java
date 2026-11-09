package com.eyups.roaddeck.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;

import com.eyups.roaddeck.service.BootReceiver;

/**
 * Aynı açılışta karşılama ve ana ekran bir kez açılır.
 * Açılış kimliği, platformun açılış sayacından okunur.
 */
public final class BootSession {

    private static final String KEY_BOOT = "bootGeneration";
    private static final String KEY_WELCOME = "welcomeGeneration";
    private static final String KEY_UI = "uiGeneration";
    private static final String KEY_UI_PENDING = "uiLaunchRequested";
    private static volatile String cachedBootId;

    private BootSession() {
    }

    public static void noteServiceStart(Context context) {
        SharedPreferences prefs = prefs(context);
        String current = currentId(context);
        String stored = prefs.getString(KEY_BOOT, null);
        SharedPreferences.Editor editor = prefs.edit().putString(KEY_BOOT, current);
        if (stored != null && !stored.equals(current)) {
            editor.remove(KEY_WELCOME).remove(KEY_UI);
        }
        editor.apply();
    }

    public static boolean isWelcomeShown(Context context) {
        return currentId(context).equals(prefs(context).getString(KEY_WELCOME, null));
    }

    public static void markWelcomeShown(Context context) {
        prefs(context).edit().putString(KEY_WELCOME, currentId(context)).apply();
    }

    public static void clearWelcomeShown(Context context) {
        prefs(context).edit().remove(KEY_WELCOME).apply();
    }

    public static void setUiPending(Context context, boolean pending) {
        prefs(context).edit().putBoolean(KEY_UI_PENDING, pending).apply();
    }

    public static boolean isUiPending(Context context) {
        return prefs(context).getBoolean(KEY_UI_PENDING, false);
    }

    public static boolean isUiLaunched(Context context) {
        return currentId(context).equals(prefs(context).getString(KEY_UI, null));
    }

    public static void markUiLaunched(Context context) {
        prefs(context).edit().putString(KEY_UI, currentId(context)).apply();
    }

    private static String currentId(Context context) {
        String cached = cachedBootId;
        if (cached != null) {
            return cached;
        }
        String value;
        try {
            int count = Settings.Global.getInt(
                    context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
            value = count >= 0
                    ? "boot-" + count
                    : "wall-" + (System.currentTimeMillis() - SystemClock.elapsedRealtime());
        } catch (Exception ignored) {
            value = "wall-" + (System.currentTimeMillis() - SystemClock.elapsedRealtime());
        }
        cachedBootId = value;
        return value;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(BootReceiver.PREFS_NAME, Context.MODE_PRIVATE);
    }
}
