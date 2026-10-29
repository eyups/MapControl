package com.mapcontrol.vehicle;

import android.os.Handler;
import android.os.Looper;

import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.desaysv.ivi.extra.project.carinfo.HvacID;
import com.desaysv.ivi.extra.project.carinfo.RadarID;
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Seçili CarInfo değerlerini yoklar ve yalnızca değişimleri günlüğe yazar.
 * Araçta tuşa basıp hangi ID'nin hangi değere gittiğini görmek için geçici bir tanı aracı.
 */
public final class VehicleValueWatcher {

    private static final long POLL_DELAY_MS = 500L;
    /** CarSettingID kopyasında yok; OEM SettingQuickControl'den (perde durumu). */
    private static final int ID_CAR_SUNSHADE = 10;

    private static final class Item {
        final int module;
        final int id;
        final String label;

        Item(int module, int id, String label) {
            this.module = module;
            this.id = id;
            this.label = label;
        }
    }

    private static final int RO = VDEventCarInfo.MODULE_READONLY_INFO;
    private static final int CS = VDEventCarInfo.MODULE_CAR_SETTING;
    private static final int HV = VDEventCarInfo.MODULE_HVAC;
    private static final int RD = VDEventCarInfo.MODULE_RADAR;

    private static final Item[] ITEMS = {
            new Item(RO, ReadOnlyID.ID_CAR_SPEED, "hız"),
            new Item(RO, ReadOnlyID.ID_SPEED_GAUGE_DISPLAY, "gösterge hızı"),
            new Item(RO, ReadOnlyID.ID_GEARBOX_STATE, "vites"),
            new Item(RO, ReadOnlyID.ID_SYSTEM_POWER_MODE, "güç modu"),
            new Item(RO, ReadOnlyID.ID_RAIN_DETECTED, "yağmur algılandı"),
            new Item(RO, ReadOnlyID.ID_RAIN_FALL, "yağmur miktarı"),
            new Item(RO, ReadOnlyID.ID_TIRE_PRESSURE_WARNING, "lastik basınç uyarısı"),
            new Item(HV, HvacID.ID_AC_CIRCULATION_MODE, "iç sirkülasyon"),
            new Item(HV, HvacID.ID_AC_WORKING, "klima çalışıyor"),
            new Item(HV, HvacID.ID_AC_STATE, "klima durumu"),
            new Item(HV, HvacID.ID_AC_AUTO_STATE, "klima oto"),
            new Item(HV, HvacID.ID_AC_TEMPERATURE_CELSIUS_OUTSIDE, "dış sıcaklık"),
            new Item(HV, HvacID.ID_AC_TEMPERATURE_FAHRENHEIT_OUTSIDE, "dış sıcaklık (F)"),
            new Item(RO, ReadOnlyID.ID_ENGINE_STATE, "motor durumu"),
            new Item(CS, CarSettingID.ID_CAR_DRIVER_SEAT_HEAT, "sürücü koltuk ısıtma"),
            new Item(CS, CarSettingID.ID_CAR_COPILOT_SEAT_HEAT, "yolcu koltuk ısıtma"),
            new Item(CS, CarSettingID.ID_CAR_RL_SEAT_HEAT_STATUS, "arka sol koltuk ısıtma"),
            new Item(CS, CarSettingID.ID_CAR_RR_VR_SEAT_HEAT_STATUS, "arka sağ koltuk ısıtma"),
            new Item(CS, CarSettingID.ID_CAR_DRIVER_SEAT_VENTILATION, "sürücü koltuk havalandırma"),
            new Item(CS, CarSettingID.ID_CAR_COPILOT_SEAT_VENTILATION, "yolcu koltuk havalandırma"),
            new Item(CS, CarSettingID.ID_CAR_STEER_WHEEL_HEAT, "direksiyon ısıtma"),
            new Item(CS, CarSettingID.ID_CAR_PASS_SEAT_MASSAGE_MODE, "yolcu masaj modu"),
            new Item(CS, CarSettingID.ID_CAR_PASS_SEAT_MASSAGE_LEVEL, "yolcu masaj seviyesi"),
            new Item(CS, CarSettingID.ID_PASSENGER_SEAT_MASSAGE_MODE, "yolcu masaj modu (2)"),
            new Item(CS, CarSettingID.ID_PASSENGER_SEAT_MASSAGE_LEVEL, "yolcu masaj seviyesi (2)"),
            new Item(CS, CarSettingID.ID_CAR_SPF_OPERATE, "tavan operate"),
            new Item(CS, CarSettingID.ID_CAR_SPF_POSI, "tavan konum"),
            new Item(CS, ID_CAR_SUNSHADE, "güneşlik durumu"),
            new Item(CS, CarSettingID.ID_CAR_FRONT_DISTANCE, "ön mesafe"),
            new Item(CS, CarSettingID.ID_CAR_FRONT_BARRIER_TYPE, "ön engel türü"),
            new Item(CS, CarSettingID.ID_CAR_FRONT_BARRIER_WARNING, "ön engel uyarısı"),
            new Item(CS, CarSettingID.ID_CAR_FCM_FRM_TEXT_INFO, "FCM metin"),
            new Item(CS, CarSettingID.ID_CAR_FCM_WARNNING_TYPE, "FCM uyarı türü"),
            new Item(CS, CarSettingID.ID_CAR_FCM_TJAICA_TEXT_INFO, "TJA/ICA metin"),
            new Item(CS, CarSettingID.ID_CAR_ACC_LAST_DISTANCE, "ACC mesafe ayarı"),
            new Item(CS, CarSettingID.ID_CAR_ACC_LAST, "ACC son"),
            new Item(RD, RadarID.ID_RADAR_WORK_STATUS, "radar durumu"),
            new Item(RD, RadarID.ID_RADAR_LHF, "radar sol ön"),
            new Item(RD, RadarID.ID_RADAR_LHMF, "radar sol orta ön"),
            new Item(RD, RadarID.ID_RADAR_RHMF, "radar sağ orta ön"),
            new Item(RD, RadarID.ID_RADAR_RHF, "radar sağ ön"),
    };

    /** Seçili VDBus değerlerinin tek seferlik JSON anlık görüntüsü. */
    public static JSONArray snapshotJson() {
        JSONArray readings = new JSONArray();
        try {
            CarInfoProxy proxy = CarInfoProxy.getInstance();
            for (Item item : ITEMS) {
                int[] values = proxy.getItemValues(item.module, item.id);
                JSONObject entry = new JSONObject();
                entry.put("module", item.module);
                entry.put("id", item.id);
                entry.put("label", item.label);
                JSONArray valueArray = new JSONArray();
                if (values != null) {
                    for (int value : values) {
                        valueArray.put(value);
                    }
                }
                entry.put("values", valueArray);
                readings.put(entry);
            }
        } catch (Throwable ignored) {
        }
        return readings;
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, String> last = new HashMap<>();
    private ScheduledExecutorService executor;

    public synchronized boolean isRunning() {
        return executor != null;
    }

    public synchronized void start(Consumer<String> log) {
        if (executor != null) {
            return;
        }
        last.clear();
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleWithFixedDelay(() -> poll(log), 0, POLL_DELAY_MS, TimeUnit.MILLISECONDS);
        post(log, "[İzleyici] Başladı, yalnızca değişimler yazılır");
    }

    public synchronized void stop(Consumer<String> log) {
        if (executor == null) {
            return;
        }
        executor.shutdownNow();
        executor = null;
        post(log, "[İzleyici] Durdu");
    }

    private void poll(Consumer<String> log) {
        try {
            if (!ComfortCommands.isConnected()) {
                return;
            }
            CarInfoProxy proxy = CarInfoProxy.getInstance();
            for (Item item : ITEMS) {
                String value = Arrays.toString(proxy.getItemValues(item.module, item.id));
                String key = item.module + "/" + item.id;
                String previous = last.put(key, value);
                if (value.equals(previous)) {
                    continue;
                }
                post(log, "[İzleyici] " + item.label + " (" + key + ") = " + value
                        + (previous == null ? "" : "  (önceki " + previous + ")"));
            }
        } catch (Throwable ignored) {
        }
    }

    private void post(Consumer<String> log, String message) {
        mainHandler.post(() -> log.accept(message));
    }
}
