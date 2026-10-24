package com.mapcontrol.vehicle;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.desaysv.ivi.extra.project.carinfo.HvacID;
import com.desaysv.ivi.extra.project.carinfo.RadarID;
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.vdb.IVDBusNotify;
import com.desaysv.ivi.vdb.client.VDBus;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.id.carlan.VDEventCarLan;
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDADSDynamicObjectInfo;
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDAdasObjectInfo;
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDADSPerceptionResult;
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDADSLocationRelativePoint;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;
import com.mapcontrol.driving.DrivingSessionRecorder;
import com.mapcontrol.driving.DrivingTrip;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * İç sirkülasyon / ön araç davranışı geliştirmesi için ham veri kaydı.
 * CarInfo ön mesafe + radar + klima değerlerini ve anlık Sürüş Karnesi
 * metriklerini 500 ms'de bir CSV'ye yazar.
 */
public final class DriveFollowLogger {

    private static final String TAG = "DriveFollowLog";
    private static final long POLL_MS = 500L;
    private static final int RO = VDEventCarInfo.MODULE_READONLY_INFO;
    private static final int CS = VDEventCarInfo.MODULE_CAR_SETTING;
    private static final int HV = VDEventCarInfo.MODULE_HVAC;
    private static final int RD = VDEventCarInfo.MODULE_RADAR;
    private static final int ADAS_OBJECT_EVENT = VDEventCarLan.ADAS_DISPLAY_OBJECT_INFO;
    private static final int ADAS_PERCEPTION_EVENT =
            VDEventCarLan.HIGH_PERCISION_MAP_ADS_DRIVING_FUNCTION_PERCEPTION_RESULT;

    private static final String[] HEADER = {
            "timestamp_ms", "time", "speed_kmh", "speed_raw", "gear",
            "front_distance", "front_barrier_type", "front_barrier_warning",
            "acc_distance", "acc_last", "fcm_warning", "fcw_warning", "aeb", "fcm_text", "tja_text",
            "radar_lhf", "radar_lhmf", "radar_rhf", "radar_all", "radar_work",
            "recirc", "hvac_working", "aq_quality", "pm25_in", "pm25_out", "outside_raw",
            "trip_active", "trip_distance_km", "trip_duration_s", "trip_avg_kmh",
            "trip_max_kmh", "trip_brakes", "trip_accels", "trip_score", "trip_consumption",
            "adas_count", "lead_id", "lead_type", "lead_x", "lead_y", "lead_speed", "lead_risk",
            "obj_count", "obj_tdd", "obj_tv", "obj_tvt", "obj_tlc", "obj_thc",
            "obj_trlav", "obj_trlov", "obj_ttw", "obj_tth"
    };

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ScheduledExecutorService executor;
    private BufferedWriter writer;
    private File file;
    private long lastPrintMs;
    private long lastAdasPollMs;
    private volatile VDADSPerceptionResult adasResult;
    private volatile ArrayList<VDAdasObjectInfo> adasObjects;
    private final IVDBusNotify.Stub adasStub = new IVDBusNotify.Stub() {
        @Override
        public void onVDBusNotify(VDEvent event) {
            if (event == null || (event.getId() != ADAS_OBJECT_EVENT
                    && event.getId() != ADAS_PERCEPTION_EVENT)) {
                return;
            }
            try {
                if (event.getId() == ADAS_OBJECT_EVENT) {
                    ArrayList<VDAdasObjectInfo> list = null;
                    try {
                        list = VDAdasObjectInfo.getList(event);
                    } catch (Throwable ignored) {
                    }
                    if (list == null) {
                        try {
                            VDAdasObjectInfo single = VDAdasObjectInfo.getValue(event);
                            if (single != null) {
                                list = new ArrayList<>();
                                list.add(single);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                    if (list != null) {
                        adasObjects = list;
                        Log.i(TAG, "ADAS object list count=" + list.size()
                                + (list.isEmpty() ? "" : " first=" + list.get(0)));
                        return;
                    }
                }
                VDADSPerceptionResult result = VDADSPerceptionResult.getValue(event);
                if (result == null) {
                    return;
                }
                adasResult = result;
                VDADSDynamicObjectInfo[] objects = result.getAdsDynamicObjectInfo();
                Log.i(TAG, "ADAS perception count=" + result.getObjectCnt()
                        + " objects=" + (objects == null ? 0 : objects.length)
                        + (objects != null && objects.length > 0 ? " first=" + objects[0] : ""));
            } catch (Throwable t) {
                Log.w(TAG, "ADAS event parse failed", t);
            }
        }
    };

    public DriveFollowLogger(Context context) {
        this.app = context.getApplicationContext();
    }

    public synchronized boolean isRunning() {
        return executor != null;
    }

    public String getFilePath() {
        return file == null ? "" : file.getAbsolutePath();
    }

    public synchronized void start(Consumer<String> log) {
        if (executor != null) {
            return;
        }
        try {
            File dir = app.getExternalFilesDir("front_follow_logs");
            if (dir == null) {
                dir = new File(app.getFilesDir(), "front_follow_logs");
            }
            if (!dir.exists() && !dir.mkdirs()) {
                post(log, "[Ön araç logu] Klasör açılamadı: " + dir);
                return;
            }
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            file = new File(dir, "front_follow_" + stamp + ".csv");
            writer = new BufferedWriter(new FileWriter(file, true));
            writer.write(String.join(",", HEADER));
            writer.newLine();
            writer.flush();
            lastPrintMs = 0L;
            try {
                VDBus.getDefault().init(app);
                VDBus.getDefault().subscribe(new VDEvent(ADAS_OBJECT_EVENT), adasStub);
                VDBus.getDefault().subscribe(new VDEvent(ADAS_PERCEPTION_EVENT), adasStub);
                VDBus.getDefault().subscribeCommit();
                Log.i(TAG, "ADAS subscribed " + ADAS_OBJECT_EVENT + "," + ADAS_PERCEPTION_EVENT);
            } catch (Throwable t) {
                Log.w(TAG, "ADAS subscribe failed", t);
            }
            executor = Executors.newSingleThreadScheduledExecutor();
            executor.scheduleWithFixedDelay(this::poll, 0L, POLL_MS, TimeUnit.MILLISECONDS);
            post(log, "[Ön araç logu] Başladı: " + file.getAbsolutePath());
            Log.i(TAG, "start " + file.getAbsolutePath());
        } catch (Throwable t) {
            closeQuietly();
            Log.e(TAG, "start failed", t);
            post(log, "[Ön araç logu] Başlatılamadı: " + t.getClass().getSimpleName());
        }
    }

    public synchronized void stop(Consumer<String> log) {
        if (executor == null) {
            return;
        }
        ScheduledExecutorService old = executor;
        executor = null;
        old.shutdownNow();
        try {
            VDBus.getDefault().unsubscribe(new VDEvent(ADAS_OBJECT_EVENT), adasStub);
            VDBus.getDefault().unsubscribe(new VDEvent(ADAS_PERCEPTION_EVENT), adasStub);
        } catch (Throwable ignored) {
        }
        String path = getFilePath();
        closeQuietly();
        post(log, "[Ön araç logu] Durdu: " + path);
        Log.i(TAG, "stop " + path);
    }

    private void poll() {
        try {
            if (!CarInfoProxy.getInstance().isServiceConnnected()) {
                return;
            }
            CarInfoProxy proxy = CarInfoProxy.getInstance();
            long now = System.currentTimeMillis();
            if (now - lastAdasPollMs >= 2000L) {
                lastAdasPollMs = now;
                pollAdasOnce();
            }
            DrivingSessionRecorder recorder = DrivingSessionRecorder.get(app);
            DrivingTrip trip = recorder.getDisplayTrip();
            boolean active = recorder.isActive();

            int displaySpeed = combined(proxy, RO, ReadOnlyID.ID_SPEED_GAUGE_DISPLAY);
            int actualSpeed = combined(proxy, RO, ReadOnlyID.ID_CAR_SPEED);
            int speed = displaySpeed > 0 ? displaySpeed : Math.max(0, actualSpeed);
            String rawSpeed = read(proxy, RO, ReadOnlyID.ID_SPEED_GAUGE_DISPLAY)
                    + "|" + read(proxy, RO, ReadOnlyID.ID_CAR_SPEED);
            String row = now + ","
                    + new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date(now)) + ","
                    + speed + ","
                    + rawSpeed + ","
                    + read(proxy, RO, ReadOnlyID.ID_GEARBOX_STATE) + ","
                    + read(proxy, CS, CarSettingID.ID_CAR_FRONT_DISTANCE) + ","
                    + read(proxy, CS, CarSettingID.ID_CAR_FRONT_BARRIER_TYPE) + ","
                    + read(proxy, CS, CarSettingID.ID_CAR_FRONT_BARRIER_WARNING) + ","
                    + read(proxy, CS, CarSettingID.ID_CAR_ACC_LAST_DISTANCE) + ","
                    + read(proxy, CS, CarSettingID.ID_CAR_ACC_LAST) + ","
                    + read(proxy, CS, CarSettingID.ID_CAR_FCM_WARNNING_TYPE) + ","
                    + read(proxy, CS, CarSettingID.ID_CAR_FCW_WARNING) + ","
                    + read(proxy, CS, CarSettingID.ID_CAR_AEB) + ","
                    + read(proxy, CS, CarSettingID.ID_CAR_FCM_FRM_TEXT_INFO) + ","
                    + read(proxy, CS, CarSettingID.ID_CAR_FCM_TJAICA_TEXT_INFO) + ","
                    + read(proxy, RD, RadarID.ID_RADAR_LHF) + ","
                    + read(proxy, RD, RadarID.ID_RADAR_LHMF) + ","
                    + read(proxy, RD, RadarID.ID_RADAR_RHF) + ","
                    + radarAll(proxy) + ","
                    + read(proxy, RD, RadarID.ID_RADAR_WORK_STATUS) + ","
                    + read(proxy, HV, HvacID.ID_AC_CIRCULATION_MODE) + ","
                    + read(proxy, HV, HvacID.ID_AC_WORKING) + ","
                    + read(proxy, HV, HvacID.ID_AC_AQ_QUALITY) + ","
                    + read(proxy, HV, HvacID.ID_AC_PM25_INCAR) + ","
                    + read(proxy, HV, HvacID.ID_AC_PM25_OUTCAR) + ","
                    + read(proxy, HV, HvacID.ID_AC_TEMPERATURE_CELSIUS_OUTSIDE) + ","
                    + (active ? 1 : 0) + ","
                    + (trip == null ? "" : trim(trip.distanceKm)) + ","
                    + (trip == null ? "" : trip.durationSec) + ","
                    + (trip == null ? "" : trip.avgSpeedKmh) + ","
                    + (trip == null ? "" : trip.maxSpeedKmh) + ","
                    + (trip == null ? "" : trip.harshBrakes) + ","
                    + (trip == null ? "" : trip.harshAccels) + ","
                    + (trip == null ? "" : trip.score) + ","
                    + (trip == null ? "" : trim(trip.consumptionL100)) + ","
                    + adasCsv();

            BufferedWriter out;
            synchronized (this) {
                out = writer;
            }
            if (out != null) {
                out.write(row);
                out.newLine();
                out.flush();
            }

            if (now - lastPrintMs >= 2000L) {
                lastPrintMs = now;
                Log.i(TAG, row);
            }
        } catch (Throwable t) {
            Log.w(TAG, "poll failed", t);
        }
    }

    private void pollAdasOnce() {
        try {
            VDEvent objectEvent = VDBus.getDefault().getOnce(new VDEvent(ADAS_OBJECT_EVENT));
            if (objectEvent != null && objectEvent.getPayload() != null) {
                ArrayList<VDAdasObjectInfo> list = VDAdasObjectInfo.getList(objectEvent);
                if (list != null) {
                    adasObjects = list;
                    Log.i(TAG, "ADAS getOnce object list=" + list.size()
                            + (list.isEmpty() ? "" : " first=" + list.get(0)));
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            VDEvent event = VDBus.getDefault().getOnce(new VDEvent(ADAS_PERCEPTION_EVENT));
            if (event == null || event.getPayload() == null) {
                return;
            }
            VDADSPerceptionResult result = VDADSPerceptionResult.getValue(event);
            if (result != null) {
                adasResult = result;
                VDADSDynamicObjectInfo[] objects = result.getAdsDynamicObjectInfo();
                Log.i(TAG, "ADAS getOnce perception=" + result.getObjectCnt()
                        + " objects=" + (objects == null ? 0 : objects.length)
                        + (objects != null && objects.length > 0 ? " first=" + objects[0] : ""));
            }
        } catch (Throwable t) {
            Log.w(TAG, "ADAS getOnce failed", t);
        }
    }

    private String adasCsv() {
        String[] perception = new String[7];
        for (int i = 0; i < perception.length; i++) perception[i] = "";
        VDADSPerceptionResult result = adasResult;
        if (result != null) {
            perception[0] = Integer.toString(result.getObjectCnt());
            VDADSDynamicObjectInfo lead = null;
            VDADSDynamicObjectInfo[] objects = result.getAdsDynamicObjectInfo();
            if (objects != null) {
                for (VDADSDynamicObjectInfo object : objects) {
                    if (object == null) continue;
                    VDADSLocationRelativePoint point = object.getAdsLocationRelativePoint();
                    if (point != null && point.getY() > 0
                            && (lead == null || Math.abs(point.getX())
                            < Math.abs(lead.getAdsLocationRelativePoint().getX()))) {
                        lead = object;
                    }
                }
                if (lead == null && objects.length > 0) {
                    lead = objects[0];
                }
            }
            if (lead != null) {
                VDADSLocationRelativePoint point = lead.getAdsLocationRelativePoint();
                perception[1] = Integer.toString(lead.getObjectId());
                perception[2] = Integer.toString(lead.getDynamicObjectType());
                perception[3] = point == null ? "" : Integer.toString(point.getX());
                perception[4] = point == null ? "" : Integer.toString(point.getY());
                perception[5] = trim(lead.getSpeed());
                perception[6] = Integer.toString(lead.getCollisionRisk() & 0xFF);
            }
        }

        String[] objectFields = new String[10];
        for (int i = 0; i < objectFields.length; i++) objectFields[i] = "";
        ArrayList<VDAdasObjectInfo> list = adasObjects;
        if (list != null) {
            objectFields[0] = Integer.toString(list.size());
            VDAdasObjectInfo lead = null;
            for (VDAdasObjectInfo object : list) {
                if (object == null) continue;
                if (lead == null || (object.getTDD() > 0
                        && (lead.getTDD() <= 0 || object.getTDD() < lead.getTDD()))) {
                    lead = object;
                }
            }
            if (lead != null) {
                objectFields[1] = Integer.toString(lead.getTDD());
                objectFields[2] = Integer.toString(lead.getTV());
                objectFields[3] = Integer.toString(lead.getTVT());
                objectFields[4] = trim(lead.getTLC());
                objectFields[5] = trim(lead.getTHC());
                objectFields[6] = trim(lead.getTRLaV());
                objectFields[7] = trim(lead.getTRLoV());
                objectFields[8] = trim(lead.getTTW());
                objectFields[9] = trim(lead.getTTH());
            }
        }

        StringBuilder csv = new StringBuilder();
        for (String value : perception) csv.append(',').append(value);
        for (String value : objectFields) csv.append(',').append(value);
        return csv.toString();
    }

    private static String radarAll(CarInfoProxy proxy) {
        StringBuilder text = new StringBuilder();
        for (int id = RadarID.ID_RADAR_LHF; id <= RadarID.ID_RADAR_RHR; id++) {
            if (text.length() > 0) text.append('|');
            text.append(read(proxy, RD, id));
        }
        return text.toString();
    }

    private static int combined(CarInfoProxy proxy, int module, int id) {
        try {
            int[] values = proxy.getItemValues(module, id);
            return VehicleMetricsCache.combineRaw(values);
        } catch (Throwable t) {
            return VehicleMetricsCache.INVALID;
        }
    }

    private static String read(CarInfoProxy proxy, int module, int id) {
        try {
            int[] values = proxy.getItemValues(module, id);
            if (values == null || values.length == 0) {
                return "";
            }
            if (values.length == 1) {
                return Integer.toString(values[0]);
            }
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < values.length; i++) {
                if (i > 0) text.append('|');
                text.append(values[i]);
            }
            return text.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String trim(double value) {
        return String.format(Locale.US, "%.3f", value);
    }

    private void post(Consumer<String> log, String message) {
        if (log == null) return;
        main.post(() -> log.accept(message));
    }

    private synchronized void closeQuietly() {
        if (writer != null) {
            try {
                writer.close();
            } catch (Throwable ignored) {
            }
            writer = null;
        }
    }
}
