package com.mapcontrol.vehicle.material;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.desaysv.ivi.vdb.client.VDBus;
import com.desaysv.ivi.vdb.client.bind.VDServiceDef;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;
import com.desaysv.ivi.vdb.event.id.device.VDEventVehicleDevice;

import java.lang.reflect.Method;

/**
 * OEM {@code EolConfig} / {@code CarConfigUtil} verisinin MapControl için sadeleştirilmiş okuyucusu.
 */
final class OemEolConfigReader {

    private static final String TAG = "OemEolConfig";
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;
    private static final long INCOMPLETE_CACHE_TTL_MS = 30 * 1000L;
    private static volatile OemEolConfigReader cached;
    private static volatile long cachedAtMs;

    private int[] carConfig3;
    private int[] carConfig5;
    private int[] comboConfig;

    void load(Context context) {
        OemEolConfigReader cache = cached;
        long age = System.currentTimeMillis() - cachedAtMs;
        if (cache != null && age >= 0 && age < cacheTtlMs(cache)) {
            copyFrom(cache);
            return;
        }
        Context app = context.getApplicationContext();
        VDBus vdbus = VDBus.getDefault();
        vdbus.init(app);
        ensureVehicleDeviceConnected(vdbus);

        carConfig3 = readHexConfig(vdbus, ReserveKeys.CAR_CONFIG_3);
        carConfig5 = readHexConfig(vdbus, ReserveKeys.CAR_CONFIG_5);
        comboConfig = readHexConfig(vdbus, ReserveKeys.COMBO_CONFIG);

        if (comboConfig == null) {
            comboConfig = hexStringToBytes(readSystemProperty(ReserveKeys.COMBO_CONFIG));
        }
        if (carConfig5 == null) {
            carConfig5 = hexStringToBytes(readSystemProperty(ReserveKeys.CAR_CONFIG_5));
        }
        if (carConfig3 == null) {
            carConfig3 = hexStringToBytes(readSystemProperty(ReserveKeys.CAR_CONFIG_3));
        }

        Log.i(TAG, "EOL yüklendi — modelCode=" + getModelCode()
                + " power=" + getPowerType()
                + " region=" + getCountryOrRegion()
                + " net=" + getTboxNetworkType()
                + " combo=" + (comboConfig != null ? comboConfig.length : 0)
                + " cfg5=" + (carConfig5 != null ? carConfig5.length : 0));
        cached = this;
        cachedAtMs = System.currentTimeMillis();
    }

    private static void ensureVehicleDeviceConnected(VDBus vdbus) {
        VDServiceDef.ServiceType type = VDServiceDef.ServiceType.VEHICLE_DEVICE;
        if (vdbus.isServiceConnected(type)) {
            return;
        }
        // Never block the main thread waiting for an OEM bind; properties below are
        // the safe fallback and the next settings refresh can retry.
        try {
            vdbus.bindService(type);
            Log.d(TAG, "VDBus VEHICLE_DEVICE bind istendi (asenkron)");
        } catch (Exception e) {
            Log.w(TAG, "VDBus VEHICLE_DEVICE bind hatası: " + e.getMessage());
        }
    }

    private static long cacheTtlMs(OemEolConfigReader value) {
        return value.comboConfig != null || value.carConfig5 != null || value.carConfig3 != null
                ? CACHE_TTL_MS : INCOMPLETE_CACHE_TTL_MS;
    }

    private void copyFrom(OemEolConfigReader other) {
        carConfig3 = other.carConfig3 != null ? other.carConfig3.clone() : null;
        carConfig5 = other.carConfig5 != null ? other.carConfig5.clone() : null;
        comboConfig = other.comboConfig != null ? other.comboConfig.clone() : null;
    }

    int getModelCode() {
        return comboConfig != null && comboConfig.length >= 2 ? comboConfig[1] : 0;
    }

    int getPowerType() {
        if (carConfig5 == null || carConfig5.length < 8) {
            return 0;
        }
        return (carConfig5[4] >> 4) & 3;
    }

    int getCountryOrRegion() {
        if (carConfig5 == null || carConfig5.length < 8) {
            return 0;
        }
        return carConfig5[6] & 255;
    }

    int getTboxNetworkType() {
        if (carConfig5 == null || carConfig5.length < 8) {
            return 0;
        }
        return (carConfig5[3] >> 2) & 3;
    }

    int getFaceStyleConfig() {
        if (carConfig3 == null || carConfig3.length < 8) {
            return 0;
        }
        return (carConfig3[4] >> 5) & 1;
    }

    int getBrandConfig() {
        if (comboConfig == null || comboConfig.length < 14) {
            return 0;
        }
        return comboConfig[13];
    }

    boolean isInternational() {
        return getCountryOrRegion() != 0;
    }

    boolean isNet() {
        return getTboxNetworkType() != 0;
    }

    boolean isEv() {
        return getPowerType() == 2;
    }

    boolean isPhev() {
        return getPowerType() == 1;
    }

    boolean isT19CevInt() {
        return getModelCode() == 7 && isInternational() && isEv();
    }

    boolean isT19cInt() {
        return getModelCode() == 7 && isInternational() && !isEv();
    }

    boolean isT18fl3Int() {
        return getModelCode() == 10 && isInternational();
    }

    boolean isT26Int() {
        return getModelCode() == 1 && isInternational();
    }

    boolean isM1ePhevInt() {
        return getModelCode() == 5 && isInternational() && isPhev();
    }

    boolean isT1eflInt() {
        return getModelCode() == 3 && isInternational();
    }

    boolean isT19flInt() {
        return getModelCode() == 12 && isInternational();
    }

    boolean isM1eInt() {
        return getModelCode() == 5 && isInternational() && !isPhev();
    }

    boolean hasSignal() {
        return getModelCode() != 0 || getCountryOrRegion() != 0;
    }

    String resolveCarDrawableBaseName() {
        final String base = "img_car_energy_conservation";
        if (isT19flInt()) {
            return base + "_face" + ((getBrandConfig() == 3 ? 1 : 0) + 1);
        }
        if (isT1eflInt()) {
            return base + "_face" + ((getFaceStyleConfig() == 1 ? 1 : 0) + 1);
        }
        return base;
    }

    private static int[] readHexConfig(VDBus vdbus, String key) {
        try {
            Bundle bundle = new Bundle();
            bundle.putString(VDKey.TYPE, key);
            VDEvent event = vdbus.getOnce(new VDEvent(VDEventVehicleDevice.PROJECT_RESERVE_CONFIGS, bundle));
            if (event == null || event.getPayload() == null) {
                return null;
            }
            String value = event.getPayload().getString("value");
            if (value == null || value.isEmpty()) {
                return null;
            }
            return hexStringToBytes(value);
        } catch (RuntimeException e) {
            Log.w(TAG, "EOL VDBus okunamadı: " + key, e);
            return null;
        }
    }

    private static String readSystemProperty(String key) {
        try {
            Class<?> cls = Class.forName("android.os.SystemProperties");
            Method get = cls.getMethod("get", String.class, String.class);
            Object value = get.invoke(null, key, "");
            return value != null ? value.toString() : "";
        } catch (ReflectiveOperationException e) {
            Log.w(TAG, "SystemProperties okunamadı: " + key, e);
            return "";
        }
    }

    private static int[] hexStringToBytes(String hex) {
        if (hex == null || hex.length() < 2) {
            return null;
        }
        int length = hex.length() / 2;
        int[] out = new int[length];
        for (int i = 0; i < hex.length() - 1; i += 2) {
            try {
                out[i / 2] = Integer.parseInt(hex.substring(i, i + 2), 16) & 255;
            } catch (NumberFormatException ignored) {
                out[i / 2] = 0;
            }
        }
        return out;
    }

    private static final class ReserveKeys {
        static final String CAR_CONFIG_3 = "vehicle.persist.project.ext.configs3";
        static final String CAR_CONFIG_5 = "vehicle.persist.project.ext.configs5";
        static final String COMBO_CONFIG = "vehicle.persist.combo.config";

        private ReserveKeys() {
        }
    }
}
