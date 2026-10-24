package com.mapcontrol.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;

/** USB ve Wi-Fi ADB ayarlarını okur/yazar; yazma için WRITE_SECURE_SETTINGS gerekir. */
public final class AdbSettings {
    private static final String KEY_ADB = "adb_enabled";
    private static final String KEY_ADB_WIFI = "adb_wifi_enabled";

    private AdbSettings() {
    }

    public static boolean canWrite(Context context) {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isUsbEnabled(Context context) {
        return read(context, KEY_ADB);
    }

    public static boolean isWifiEnabled(Context context) {
        return read(context, KEY_ADB_WIFI);
    }

    public static boolean setUsbEnabled(Context context, boolean on) {
        return write(context, KEY_ADB, on);
    }

    public static boolean setWifiEnabled(Context context, boolean on) {
        return write(context, KEY_ADB_WIFI, on);
    }

    private static boolean read(Context context, String key) {
        return Settings.Global.getInt(context.getContentResolver(), key, 0) == 1;
    }

    /** Yazıp geri okuyarak doğrular; izin yoksa veya sistem değeri değiştirdiyse false döner. */
    private static boolean write(Context context, String key, boolean on) {
        try {
            Settings.Global.putInt(context.getContentResolver(), key, on ? 1 : 0);
        } catch (SecurityException e) {
            return false;
        }
        return read(context, key) == on;
    }
}
