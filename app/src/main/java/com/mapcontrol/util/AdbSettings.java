package com.mapcontrol.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;

import com.desaysv.ivi.vdb.client.VDBus;
import com.desaysv.ivi.vdb.client.bind.VDServiceDef;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.id.cabin.VDEventCabinLan;
import com.desaysv.ivi.vdb.event.id.cabin.bean.VDCLCommonMessage;
import com.desaysv.ivi.vdb.event.id.vehicle.VDKeyVehicleHal;

import java.lang.reflect.Method;

/**
 * USB/Wi-Fi ADB ayarları.
 *
 * Android katmanı: Settings.Global.adb_enabled / adb_wifi_enabled.
 * OEM katmanı: persist.sv.debug.adb_enable; USB rolünü init belirler.
 * OEM katmanı normal uygulamadan yazılamaz. Mümkünse VDBus
 * CABIN_LAN {"AdbAdjustAction":1|0} ile EngMode plugin'ine iletilir.
 */
public final class AdbSettings {
    private static final String KEY_ADB = "adb_enabled";
    private static final String KEY_ADB_WIFI = "adb_wifi_enabled";
    private static final String OEM_ADB_PROP = "persist.sv.debug.adb_enable";
    private static final int CABIN_LAN_MSG_TYPE = 4101;

    private AdbSettings() {
    }

    public static final class UsbResult {
        public final boolean settingOk;
        public final boolean oemRequested;
        public final boolean oemSent;
        public final String oemState;

        UsbResult(boolean settingOk, boolean oemRequested, boolean oemSent, String oemState) {
            this.settingOk = settingOk;
            this.oemRequested = oemRequested;
            this.oemSent = oemSent;
            this.oemState = oemState;
        }
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

    /** OEM USB rolü: 1 peripheral/ADB, 0 host, ? okunamadı. */
    public static String oemUsbState() {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method get = sp.getMethod("get", String.class, String.class);
            Object value = get.invoke(null, OEM_ADB_PROP, "?");
            return value == null ? "?" : String.valueOf(value);
        } catch (Throwable ignored) {
            return "?";
        }
    }

    public static UsbResult setUsbEnabled(Context context, boolean on) {
        boolean settingOk = write(context, KEY_ADB, on);
        if (!settingOk) {
            return new UsbResult(false, false, false, oemUsbState());
        }
        // OEM property'yi doğrudan yazamayız; EngMode plugin'i VDBus ile dene.
        boolean sent = requestOemAdbAdjust(on);
        return new UsbResult(true, true, sent, oemUsbState());
    }

    public static boolean setWifiEnabled(Context context, boolean on) {
        return write(context, KEY_ADB_WIFI, on);
    }

    private static boolean requestOemAdbAdjust(boolean on) {
        boolean sent = false;
        try {
            if (VDBus.getDefault().isServiceConnected(VDServiceDef.ServiceType.CABIN_LAN)) {
                VDCLCommonMessage message = new VDCLCommonMessage();
                message.setMsgType(CABIN_LAN_MSG_TYPE);
                message.setMessage("{\"AdbAdjustAction\":" + (on ? 1 : 0) + "}");
                VDBus.getDefault().set(VDCLCommonMessage.createGetEvent(
                        VDEventCabinLan.CABIN_LAN_MSG_COMMON, message));
                sent = true;
            }
        } catch (Throwable ignored) {
        }
        // EngMode AJSDispatcher aynı isteği SV_ENG_SWITCH_ADBMODE (33365) ile de kabul ediyor.
        try {
            Bundle payload = new Bundle();
            payload.putIntArray(VDKeyVehicleHal.INT_VECTOR, new int[]{on ? 1 : 2});
            VDBus.getDefault().set(new VDEvent(33365, payload));
            sent = true;
        } catch (Throwable ignored) {
        }
        return sent;
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
