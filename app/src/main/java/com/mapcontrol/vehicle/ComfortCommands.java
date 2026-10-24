package com.mapcontrol.vehicle;

import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.desaysv.ivi.extra.project.carinfo.HvacID;
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import com.desaysv.ivi.extra.project.carinfo.VrID;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;

/**
 * Cam tavan, güneşlik ve iç sirkülasyon komutları. Değerler araçta logcat ve OEM kodundan doğrulandı.
 * Tüm çağrılar CarInfoProxy bağlıyken yapılmalı; okumalar bloklayabilir, ana thread'de çağırma.
 */
public final class ComfortCommands {

    /** Cam tavan açılış konumu: -1 hiçbiri, {@link #SUNROOF_VENT} veya {@link #SUNROOF_OPEN}. */
    public static final String PREF_SUNROOF = "comfortSunroofSetting";
    /** Güneşlik: -1 hiçbiri, {@link #SUNSHADE_OPEN} veya {@link #SUNSHADE_CLOSE}. */
    public static final String PREF_SUNSHADE = "comfortSunshadeSetting";

    public static final int SUNROOF_CLOSED = 1;
    public static final int SUNROOF_OPEN = 11;
    public static final int SUNROOF_VENT = 12;

    public static final int SUNSHADE_OPEN = 1;
    public static final int SUNSHADE_CLOSE = 2;

    private static final int RECIRC_ON_COMMAND = 2;
    private static final int RECIRC_OFF_COMMAND = 1;

    private ComfortCommands() {
    }

    public static boolean isConnected() {
        return CarInfoProxy.getInstance().isServiceConnnected();
    }

    public static void sendSunroofPosition(int position) {
        CarInfoProxy.getInstance().sendItemValue(
                VDEventCarInfo.MODULE_VR, VrID.ID_VR_SRF_POSI, position);
    }

    public static void sendSunshade(int command) {
        CarInfoProxy.getInstance().sendItemValue(
                VDEventCarInfo.MODULE_VR, VrID.ID_VR_SUN_SHADE, command);
    }

    public static int readSunroofPosition() {
        return read(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_SPF_POSI);
    }

    /** Klima çalışırken true; OEM de iç sirkülasyon komutunu yalnızca bu durumda gönderir. */
    public static boolean isHvacRunning() {
        return read(VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_WORKING) == 1;
    }

    /** 1 iç sirkülasyon, 0 dış hava. */
    public static boolean isRecirculationOn() {
        return read(VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_CIRCULATION_MODE) == 1;
    }

    public static void sendRecirculation(boolean on) {
        CarInfoProxy.getInstance().sendItemValue(
                VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_CIRCULATION_MODE,
                on ? RECIRC_ON_COMMAND : RECIRC_OFF_COMMAND);
    }

    /** km/h; gösterge hızı yoksa gerçek hız. Okunamazsa {@code -1}. */
    public static int readSpeedKmh() {
        int display = read(VDEventCarInfo.MODULE_READONLY_INFO, ReadOnlyID.ID_SPEED_GAUGE_DISPLAY);
        if (display > 0) {
            return display;
        }
        int actual = read(VDEventCarInfo.MODULE_READONLY_INFO, ReadOnlyID.ID_CAR_SPEED);
        return actual >= 0 ? actual : -1;
    }

    public static final int INVALID_TEMP = Integer.MIN_VALUE;
    /** Dış sıcaklık okunamadığında dönen değer. */
    public static final float INVALID_TEMP_C = OutsideTemperature.INVALID;
    /** Koltuk ısıtma/havalandırma seviyesi: 0 kapalı, 1-3 açık. */
    public static final int SEAT_LEVEL_MAX = 3;

    /** OEM koltuk ısıtma/havalandırmayı yalnızca motor çalışırken kabul ediyor. */
    public static boolean isEngineRunning() {
        return read(VDEventCarInfo.MODULE_READONLY_INFO, ReadOnlyID.ID_ENGINE_STATE) == 1;
    }

    /** Dış sıcaklık °C; okunamazsa {@link #INVALID_TEMP_C}. Ölçek: 0.5°C adım, -55°C offset. */
    public static float readOutsideTempC() {
        int[] raw = CarInfoProxy.getInstance().getItemValues(
                VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_TEMPERATURE_CELSIUS_OUTSIDE);
        int combined = VehicleMetricsCache.combineRaw(raw);
        return combined == VehicleMetricsCache.INVALID
                ? INVALID_TEMP_C : OutsideTemperature.fromRaw(combined);
    }

    /** Motor suyu sıcaklığı °C; okunamazsa {@link #INVALID_TEMP_C}. Çağrı ana thread'de yapılmamalı. */
    public static float readWaterTempC() {
        int[] raw = CarInfoProxy.getInstance().getItemValues(
                VDEventCarInfo.MODULE_READONLY_INFO, ReadOnlyID.ID_WATER_TEMPERATURE);
        if (raw == null || raw.length == 0) {
            return INVALID_TEMP_C;
        }
        int combined = raw.length == 1 ? raw[0] : 0;
        if (raw.length > 1) {
            for (int value : raw) {
                combined = (combined << 8) | (value & 0xFF);
            }
        }
        if (combined == Integer.MIN_VALUE || combined <= 0) {
            return INVALID_TEMP_C;
        }
        return combined > 200 ? combined / 10f : combined;
    }

    /** CAR_SETTING koltuk kimliği: 0 kapalı, 1-3 seviye (OEM kodundan). */
    public static int readSeatLevel(int seatCommandId) {
        return read(VDEventCarInfo.MODULE_CAR_SETTING, seatCommandId);
    }

    /** Yazma değeri seviye+1'dir: 1 kapat, 2-4 seviye 1-3 (OEM SeatHvacController). */
    public static void sendSeatLevel(int seatCommandId, int level) {
        int clamped = Math.max(0, Math.min(SEAT_LEVEL_MAX, level));
        CarInfoProxy.getInstance().sendItemValue(
                VDEventCarInfo.MODULE_CAR_SETTING, seatCommandId, clamped + 1);
    }

    /** OEM koltuk + dikiz aynası hafıza slotları: 1-3. 52 = slotu geri yükle, 54 = konumu slota kaydet. */
    public static final int SEAT_MEMORY_SLOTS = 3;
    private static final int SEAT_MEMORY_RESTORE_ID = 52;
    private static final int SEAT_MEMORY_SAVE_ID = 54;
    private static final int GEAR_PARK = 1;
    private static final int GEAR_NEUTRAL = 3;

    /** OEM, koltuk ayarını yalnızca özellik aktifken ve vites P/N iken kabul ediyor (SeatAdjustPresenter). */
    public static boolean isSeatMemoryAvailable() {
        int[] state = CarInfoProxy.getInstance().getItemValues(
                VDEventCarInfo.MODULE_CAR_SETTING, SEAT_MEMORY_RESTORE_ID);
        int gear = read(VDEventCarInfo.MODULE_READONLY_INFO, ReadOnlyID.ID_GEARBOX_STATE);
        return state != null && state.length > 0 && state[0] == 1
                && (gear == GEAR_PARK || gear == GEAR_NEUTRAL);
    }

    public static void restoreSeatMemory(int slot) {
        CarInfoProxy.getInstance().sendItemValue(
                VDEventCarInfo.MODULE_CAR_SETTING, SEAT_MEMORY_RESTORE_ID, slot);
    }

    public static void saveSeatMemory(int slot) {
        CarInfoProxy.getInstance().sendItemValue(
                VDEventCarInfo.MODULE_CAR_SETTING, SEAT_MEMORY_SAVE_ID, slot);
    }

    /** Profil hafıza maddeleri için ham değer; okunamazsa {@link #INVALID_TEMP}. */
    public static int readRaw(int module, int id) {
        int[] raw = CarInfoProxy.getInstance().getItemValues(module, id);
        int combined = VehicleMetricsCache.combineRaw(raw);
        return combined == VehicleMetricsCache.INVALID ? INVALID_TEMP : combined;
    }

    public static void writeRaw(int module, int id, int value) {
        CarInfoProxy.getInstance().sendItemValue(module, id, value);
    }

    public static boolean isSteeringHeatOn() {
        return read(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_STEER_WHEEL_HEAT) == 1;
    }

    /** OEM hem açma hem kapatma için 1 gönderiyor (düğme gibi); önce durumu okuyup çağır. */
    public static void toggleSteeringHeat() {
        CarInfoProxy.getInstance().sendItemValue(
                VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_STEER_WHEEL_HEAT, 1);
    }

    private static int read(int module, int id) {
        int[] raw = CarInfoProxy.getInstance().getItemValues(module, id);
        int combined = VehicleMetricsCache.combineRaw(raw);
        return combined == VehicleMetricsCache.INVALID ? -1 : combined;
    }
}
