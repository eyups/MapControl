package com.mapcontrol.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.SparseLongArray;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.desaysv.ivi.vdb.IVDBusNotify;
import com.desaysv.ivi.vdb.client.VDBus;
import com.desaysv.ivi.vdb.client.bind.VDServiceDef;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;
import com.desaysv.ivi.vdb.event.id.cabin.VDEventCabinLan;
import com.desaysv.ivi.vdb.event.id.cabin.bean.VDCLCommonMessage;
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDADSPerceptionResult;
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDAdasObjectInfo;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;
import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.extra.project.carinfo.proxy.Constants;
import com.mapcontrol.R;
import com.mapcontrol.ui.cockpit.CockpitLegacyStyler;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.util.SystemLogBuffer;
import com.mapcontrol.vehicle.material.OemEolConfigReader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;


/**
 * Root gerektirmeyen ADAS teşhis ekranı.
 *
 * <p>Önce CarLanService'in SOME/IP ADAS hattını başlatıp başlatmadığını belirleyen
 * EOL config 112/147 değerlerini gösterir; ardından ADAS event aralıklarını manuel
 * olarak dinler. Hiçbir arka plan kaydı veya otomatik başlatma yapmaz.</p>
 */
public class AdasProbeActivity extends Activity {

    private static final String TAG = "AdasProbe";
    /** ADAS/FCM araç okumaları güvenlik nedeniyle devre dışı. */
    private static final boolean ENABLE_VEHICLE_READS = false;
    /** Cluster/AR-HUD nesne listesinin Android->QNX CabinLAN mesajı. */
    private static final int CABIN_LAN_OBJECT_EVENT = VDEventCabinLan.CABIN_LAN_MSG_COMMON;
    private static final int CABIN_LAN_AR_HUD_MSG_TYPE = 4105;
    private static final int MAX_CABIN_EVENT_CHARS = 1600;
    private static final int[] EVENT_IDS = buildEventIds();
    // ADAS getOnce snapshot kaldirildi: arac calisirken aktif ADAS sorgusu
    // yerine yalniz pasif event aboneligi kullaniliyor.

    private final Handler ui = new Handler(Looper.getMainLooper());
    private HandlerThread busThread;
    private Handler busHandler;
    private final StringBuilder eventLog = new StringBuilder();
    private final SparseIntArray eventCounts = new SparseIntArray();
    private final SparseLongArray lastAppendMs = new SparseLongArray();
    private final SparseArray<String> lastPayload = new SparseArray<>();
    private final Object eventLock = new Object();
    private final int[] fcmIds = {
            CarSettingID.ID_CAR_FCM_WARNNING_TYPE,
            CarSettingID.ID_CAR_FCM_FRM_TEXT_INFO,
            CarSettingID.ID_CAR_FCM_TJAICA_TEXT_INFO,
            CarSettingID.ID_CAR_FRONT_DISTANCE,
            CarSettingID.ID_CAR_ACC_LAST_DISTANCE,
            CarSettingID.ID_CAR_FCW_WARNING,
            CarSettingID.ID_CAR_FRONT_BARRIER_TYPE,
            CarSettingID.ID_CAR_FRONT_BARRIER_WARNING
    };
    private final String[] lastFcm = new String[fcmIds.length];
    private final Object fcmLock = new Object();
    private final Object fcmLogLock = new Object();
    private BufferedWriter fcmLogWriter;
    private File fcmLogFile;
    private long fcmLogBytes;
    private boolean leadAwayActive;
    private static final long MAX_FCM_LOG_BYTES = 256L * 1024L;
    private final IVDBusNotify.Stub fcmStub = new IVDBusNotify.Stub() {
        @Override
        public void onVDBusNotify(VDEvent event) {
            if (event == null || event.getId() != VDEventCarInfo.MODULE_CAR_SETTING
                    || event.getPayload() == null) {
                return;
            }
            int cmd = event.getPayload().getInt(Constants.CMD_ID, -1);
            int[] values = event.getPayload().getIntArray(Constants.VALUE);
            int index = -1;
            for (int i = 0; i < fcmIds.length; i++) {
                if (fcmIds[i] == cmd) { index = i; break; }
            }
            if (index >= 0 && values != null && values.length > 0) {
                applyFcmValues(index, values);
            }
        }
    };
    /** Yalnizca tarama basinda bir kez ilk degerleri okur; periyodik getOnce yok. */
    private final Runnable fcmSnapshot = new Runnable() {
        @Override
        public void run() {
            if (!scanning) return;
            try {
                CarInfoProxy proxy = CarInfoProxy.getInstance();
                if (!proxy.isServiceConnnected()) {
                    ui.post(() -> fcmView.setText("CAR_INFO bekleniyor…"));
                    return;
                }
                for (int i = 0; i < fcmIds.length; i++) {
                    int[] values = proxy.getItemValues(VDEventCarInfo.MODULE_CAR_SETTING, fcmIds[i]);
                    if (values != null && values.length > 0) {
                        applyFcmValues(i, values);
                    }
                }
                updateFcmUi();
            } catch (Throwable t) {
                Log.w(TAG, "FCM snapshot failed", t);
            }
        }
    };

    private TextView configView;
    private TextView statusView;
    private TextView fcmView;
    private TextView cabinView;
    private TextView eventView;
    private Button startButton;
    private Button stopButton;
    private Button cabinButton;

    private boolean scanning;
    private int subscribedCount;
    private int subscribeFailures;
    private boolean cabinScanning;
    private int cabinEventCount;
    private long lastCabinEventMs;
    private String lastCabinSummary = "";
    private final Object cabinLock = new Object();

    private final IVDBusNotify.Stub cabinStub = new IVDBusNotify.Stub() {
        @Override
        public void onVDBusNotify(VDEvent event) {
            if (event == null || event.getId() != CABIN_LAN_OBJECT_EVENT) {
                return;
            }
            try {
                VDCLCommonMessage message = VDCLCommonMessage.getValue(event);
                if (message == null || message.getMsgType() != CABIN_LAN_AR_HUD_MSG_TYPE) {
                    return;
                }
                String summary = summarizeObjectList(message.getContent());
                int count;
                boolean show;
                synchronized (cabinLock) {
                    count = ++cabinEventCount;
                    lastCabinSummary = summary;
                    long now = SystemClock.uptimeMillis();
                    show = count <= 3 || now - lastCabinEventMs >= 500L;
                    if (show) {
                        lastCabinEventMs = now;
                    }
                }
                String line = "CABIN 4105 #" + count + "  subtype=" + message.getSubtype()
                        + "  " + summary;
                if (show) {
                    Log.i(TAG, line);
                    appendEvent(line);
                }
                ui.post(() -> cabinView.setText(
                        "CabinLAN 1114113 → 4105\n#" + count + "  " + summary));
            } catch (Throwable t) {
                Log.w(TAG, "CabinLAN payload failed", t);
            }
        }
    };

    private final IVDBusNotify.Stub eventStub = new IVDBusNotify.Stub() {
        @Override
        public void onVDBusNotify(VDEvent event) {
            if (event == null) {
                return;
            }
            String payload = formatPayload(event);
            if (payload.length() > 1500) {
                payload = payload.substring(0, 1500) + "…";
            }
            int id = event.getId();
            String line = null;
            synchronized (eventLock) {
                int count = eventCounts.get(id, 0) + 1;
                eventCounts.put(id, count);
                lastPayload.put(id, payload);
                long now = SystemClock.uptimeMillis();
                // Yuksek frekansli ADAS frame'leri UI'yi bogmasin: ilk 3 olay ve
                // sonrasinda en fazla 2 satir/sn.
                boolean show = count <= 3 || now - lastAppendMs.get(id, 0L) >= 500L;
                if (show) {
                    lastAppendMs.put(id, now);
                    line = "EVT " + id + " #" + count + "  " + payload;
                } else if (count == 4) {
                    Log.i(TAG, "EVT " + id + " rate-limited; counting only");
                }
            }
            if (line != null) {
                Log.i(TAG, line);
                appendEvent(line);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));
        root.setBackgroundColor(Color.rgb(18, 20, 24));

        TextView title = new TextView(this);
        title.setText(R.string.adas_probe_title);
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.adas_probe_subtitle);
        subtitle.setTextSize(12);
        subtitle.setTextColor(0xFFB8C4D4);
        subtitle.setPadding(0, dp(4), 0, dp(10));
        root.addView(subtitle);

        configView = new TextView(this);
        configView.setTextSize(13);
        configView.setTextColor(0xFFE8EDF5);
        configView.setTypeface(Typeface.MONOSPACE);
        configView.setPadding(0, 0, 0, dp(10));
        root.addView(configView);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(buttons, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        startButton = new Button(this);
        startButton.setText(R.string.adas_probe_start);
        styleButton(startButton, UiStyles.color(this, R.color.buttonPrimary));
        startButton.setOnClickListener(v -> startScan());
        buttons.addView(startButton, weighted());

        stopButton = new Button(this);
        stopButton.setText(R.string.adas_probe_stop);
        styleButton(stopButton, UiStyles.color(this, R.color.buttonSecondaryMuted));
        stopButton.setEnabled(false);
        stopButton.setOnClickListener(v -> stopScan());
        LinearLayout.LayoutParams stopLp = weighted();
        stopLp.leftMargin = dp(8);
        buttons.addView(stopButton, stopLp);

        Button clearButton = new Button(this);
        clearButton.setText(R.string.adas_probe_clear);
        styleButton(clearButton, UiStyles.color(this, R.color.buttonSecondaryMuted));
        clearButton.setOnClickListener(v -> {
            eventLog.setLength(0);
            eventView.setText("");
        });
        LinearLayout.LayoutParams clearLp = weighted();
        clearLp.leftMargin = dp(8);
        buttons.addView(clearButton, clearLp);

        statusView = new TextView(this);
        statusView.setTextSize(12);
        statusView.setTextColor(0xFF8FA1B6);
        statusView.setPadding(0, dp(8), 0, dp(4));
        root.addView(statusView);

        fcmView = new TextView(this);
        fcmView.setTextSize(13);
        fcmView.setTypeface(Typeface.MONOSPACE);
        fcmView.setTextColor(0xFFFFD180);
        fcmView.setPadding(dp(10), dp(6), dp(10), dp(6));
        fcmView.setBackgroundColor(0xFF20242B);
        fcmView.setText("FCM bekleniyor…");
        root.addView(fcmView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        cabinButton = new Button(this);
        cabinButton.setText(R.string.adas_probe_cabin_start);
        styleButton(cabinButton, UiStyles.color(this, R.color.buttonPrimary));
        cabinButton.setOnClickListener(v -> toggleCabinProbe());
        cabinButton.setEnabled(false);
        LinearLayout.LayoutParams cabinButtonLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cabinButtonLp.topMargin = dp(6);
        root.addView(cabinButton, cabinButtonLp);

        cabinView = new TextView(this);
        cabinView.setTextSize(12);
        cabinView.setTypeface(Typeface.MONOSPACE);
        cabinView.setTextColor(0xFF9CDCFE);
        cabinView.setPadding(dp(10), dp(6), dp(10), dp(6));
        cabinView.setBackgroundColor(0xFF1B2530);
        cabinView.setText(R.string.adas_probe_cabin_idle);
        root.addView(cabinView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        eventView = new TextView(this);
        eventView.setTextSize(11);
        eventView.setTextColor(0xFFD7DEE8);
        eventView.setTypeface(Typeface.MONOSPACE);
        eventView.setPadding(dp(8), dp(8), dp(8), dp(8));
        eventView.setTextIsSelectable(true);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(eventView, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        CockpitLegacyStyler.apply(root);
        setContentView(root);
        if (!ENABLE_VEHICLE_READS) {
            configView.setText("ADAS/FCM araç okumaları güvenli modda devre dışı.\n"
                    + "CabinLAN 1114113 / 4105 probe kullanılabilir.");
            fcmView.setText("Araç hattı okunmuyor.");
        } else {
            configView.setText(R.string.adas_probe_loading);
        }
        busThread = new HandlerThread("adas-probe-bus");
        busThread.start();
        busHandler = new Handler(busThread.getLooper());
        // VDBus init Looper'li bir thread'de yapilmali; aksi halde SDK Handler
        // olusturamiyor ve EOL config okunamıyor.
        busHandler.post(() -> {
            try {
                VDBus.getDefault().init(getApplicationContext());
                Log.i(TAG, "VDBus init complete (CabinLAN bind on demand)");
                if (ENABLE_VEHICLE_READS) {
                    boolean bound = VDBus.getDefault().bindService(
                            VDServiceDef.ServiceType.VEHICLE_DEVICE);
                    boolean carLan = VDBus.getDefault().bindService(
                            VDServiceDef.ServiceType.CAR_LAN);
                    Log.i(TAG, "VEHICLE_DEVICE bind=" + bound + " CAR_LAN bind=" + carLan);
                    CarInfoProxy.getInstance().init(getApplicationContext());
                }
            } catch (Throwable t) {
                Log.w(TAG, "VDBus init failed", t);
            }
            if (ENABLE_VEHICLE_READS) {
                loadConfigOnBus();
            }
            ui.post(() -> {
                if (cabinButton != null) {
                    cabinButton.setEnabled(true);
                }
            });
        });
        updateStatus();
    }

    private void loadConfigOnBus() {
            int[] c4 = null;
            int[] c5 = null;
            for (int attempt = 0; attempt < 16; attempt++) {
                try {
                    c4 = OemEolConfigReader.readConfig4Bytes();
                } catch (Throwable ignored) {
                }
                try {
                    c5 = OemEolConfigReader.readConfig5Bytes();
                } catch (Throwable ignored) {
                }
                if (c4 != null || c5 != null) {
                    break;
                }
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            String text;
            if (c4 == null && c5 == null) {
                text = getString(R.string.adas_probe_config_missing);
            } else {
                Integer c112 = c4 != null && c4.length > 5
                        ? ((c4[5] >> 7) & 1) : null;
                Integer c147 = c5 != null && c5.length > 3
                        ? ((c5[3] >> 2) & 3) : null;
                String raw4 = c4 != null && c4.length > 5
                        ? "0x" + Integer.toHexString(c4[5] & 0xFF) : "—";
                String raw5 = c5 != null && c5.length > 3
                        ? "0x" + Integer.toHexString(c5[3] & 0xFF) : "—";
                text = getString(R.string.adas_probe_config_format,
                        value(c112), value(c147), raw4, raw5,
                        c112 != null && c112 == 1 ? "YES" : "NO",
                        c147 != null && c147 == 2 ? "YES" : "NO");
            }
            String shown = text;
            Log.i(TAG, shown.replace("\n", " | "));
            ui.post(() -> configView.setText(shown));
    }

    private void toggleCabinProbe() {
        if (cabinScanning) {
            stopCabinProbe();
        } else {
            startCabinProbe();
        }
    }

    /**
     * Yalnız 1114113 / msgType 4105 dinler. ADAS/FCM aboneliklerini veya
     * CarInfo okumalarını başlatmaz; manuel ve tek abonelik.
     */
    private void startCabinProbe() {
        if (cabinScanning) {
            return;
        }
        boolean bound;
        try {
            // VDBus.init onCreate'te HandlerThread'de yapıldı; UI thread'de tekrar init yok.
            bound = VDBus.getDefault().isServiceConnected(
                    VDServiceDef.ServiceType.CABIN_LAN);
            if (!bound) {
                bound = VDBus.getDefault().bindService(VDServiceDef.ServiceType.CABIN_LAN);
            }
            VDBus.getDefault().subscribe(new VDEvent(CABIN_LAN_OBJECT_EVENT), cabinStub);
        } catch (Throwable t) {
            cabinView.setText("CabinLAN abone olamadı: " + t.getClass().getSimpleName());
            Log.w(TAG, "CABIN_LAN subscribe failed", t);
            return;
        }
        // subscribe() doğrudan subscribeCustomizedEvent çağırır. Bu SDK
        // sürümünde subscribeCommit() decompile edilememiş ve
        // UnsupportedOperationException atıyor; aboneliği buna bağlama.
        boolean committed = true;
        try {
            VDBus.getDefault().subscribeCommit();
        } catch (Throwable t) {
            committed = false;
            Log.w(TAG, "CABIN_LAN subscribeCommit unavailable: "
                    + t.getClass().getSimpleName());
        }
        synchronized (cabinLock) {
            cabinEventCount = 0;
            lastCabinEventMs = 0L;
            lastCabinSummary = "";
        }
        cabinScanning = true;
        cabinButton.setText(R.string.adas_probe_cabin_stop);
        cabinView.setText("CabinLAN 1114113 → 4105\nbind=" + bound
                + ", commit=" + committed + "\n4105 bekleniyor…");
        appendEvent("CABIN_LAN subscribe 1114113 (4105), bind=" + bound
                + " commit=" + committed);
        updateStatus();
        Log.i(TAG, "CABIN_LAN subscribe=1114113 msgType=4105 bind=" + bound
                + " commit=" + committed);
    }

    private void stopCabinProbe() {
        if (!cabinScanning) {
            return;
        }
        cabinScanning = false;
        try {
            VDBus.getDefault().unsubscribe(new VDEvent(CABIN_LAN_OBJECT_EVENT), cabinStub);
            VDBus.getDefault().subscribeCommit();
        } catch (Throwable ignored) {
        }
        cabinButton.setText(R.string.adas_probe_cabin_start);
        appendEvent("CABIN_LAN unsubscribe 1114113");
        updateStatus();
        Log.i(TAG, "CABIN_LAN unsubscribe=1114113 count=" + cabinEventCount);
    }

    /** Gson ile serileştirilen VDObjectList içeriğini kısa, okunur özete çevirir. */
    private static String summarizeObjectList(String content) {
        if (content == null || content.isEmpty()) {
            return "content empty";
        }
        try {
            JSONObject root = new JSONObject(content);
            JSONArray list = root.optJSONArray("ObjectList");
            if (list == null) {
                return "json=" + shorten(content);
            }
            StringBuilder b = new StringBuilder("n=").append(list.length());
            int max = Math.min(list.length(), 8);
            for (int i = 0; i < max; i++) {
                JSONObject object = list.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                int type = object.optInt("TVT", -1);
                b.append(" | ").append(objectTypeName(type))
                        .append('(').append(type).append(')')
                        .append(" x=").append(number(object.optDouble("TLC", Double.NaN)))
                        .append(" y=").append(number(object.optDouble("THC", Double.NaN)));
                if (object.optInt("TV", 0) == 1) {
                    b.append(" LEAD");
                }
            }
            if (list.length() > max) {
                b.append(" …");
            }
            return b.toString();
        } catch (Throwable t) {
            return "raw=" + shorten(content);
        }
    }

    private static String objectTypeName(int type) {
        switch (type) {
            case 1: return "araba";
            case 2: return "kamyon";
            case 3: return "otobüs";
            case 4: return "yaya";
            case 5: return "motosiklet";
            case 6: return "üçteker";
            case 7: return "konik";
            default: return type == 0 ? "yok" : "?";
        }
    }

    private static String number(double value) {
        return Double.isNaN(value) ? "—" : String.format(Locale.US, "%.1f", value);
    }

    private static String shorten(String value) {
        if (value == null) {
            return "";
        }
        String oneLine = value.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() <= MAX_CABIN_EVENT_CHARS
                ? oneLine
                : oneLine.substring(0, MAX_CABIN_EVENT_CHARS) + "…";
    }

    private void startScan() {
        if (!ENABLE_VEHICLE_READS) {
            appendEvent("ADAS/FCM araç okumaları devre dışı.");
            return;
        }
        if (scanning) {
            return;
        }
        eventLog.setLength(0);
        eventView.setText("");
        synchronized (eventLock) {
            eventCounts.clear();
            lastAppendMs.clear();
            lastPayload.clear();
        }
        scanning = true;
        subscribedCount = 0;
        subscribeFailures = 0;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        updateStatus();
        appendEvent(getString(R.string.adas_probe_scan_start));

        try {
            VDBus.getDefault().init(getApplicationContext());
        } catch (Throwable ignored) {
        }

        for (int id : EVENT_IDS) {
            try {
                VDBus.getDefault().subscribe(new VDEvent(id), eventStub);
                subscribedCount++;
            } catch (Throwable ignored) {
                subscribeFailures++;
            }
        }
        try {
            VDBus.getDefault().subscribeCommit();
        } catch (Throwable ignored) {
        }
        Log.i(TAG, "services CAR_LAN="
                + VDBus.getDefault().isServiceConnected(VDServiceDef.ServiceType.CAR_LAN)
                + " CAR_INFO=" + VDBus.getDefault().isServiceConnected(
                        VDServiceDef.ServiceType.CAR_INFO)
                + " VEHICLE_DEVICE=" + VDBus.getDefault().isServiceConnected(
                        VDServiceDef.ServiceType.VEHICLE_DEVICE));
        updateStatus();
        String subscribed = getString(R.string.adas_probe_subscribed, subscribedCount, subscribeFailures);
        Log.i(TAG, subscribed);
        appendEvent(subscribed);
        synchronized (fcmLock) {
            for (int i = 0; i < lastFcm.length; i++) {
                lastFcm[i] = null;
            }
            leadAwayActive = false;
        }
        if (fcmView != null) {
            fcmView.setText("FCM okunuyor…");
        }
        startFcmLog();
        appendAwayToSystemLog("SCAN_START", readSpeed(CarInfoProxy.getInstance()));
        try {
            CarInfoProxy.getInstance().regCallBack(VDEventCarInfo.MODULE_CAR_SETTING,
                    fcmStub, fcmIds);
        } catch (Throwable t) {
            Log.w(TAG, "FCM callback register failed", t);
        }
        busHandler.removeCallbacks(fcmSnapshot);
        busHandler.post(fcmSnapshot);
    }

    private void stopScan() {
        if (!scanning) {
            return;
        }
        scanning = false;
        if (busHandler != null) {
            busHandler.removeCallbacks(fcmSnapshot);
        }
        try {
            CarInfoProxy.getInstance().unRegCallBack(VDEventCarInfo.MODULE_CAR_SETTING, fcmStub);
        } catch (Throwable ignored) {
        }
        if (leadAwayActive) {
            int speed = readSpeed(CarInfoProxy.getInstance());
            writeFcmEvent("AWAY_END", speed);
            appendAwayToSystemLog("AWAY_END", speed);
            leadAwayActive = false;
        }
        appendAwayToSystemLog("SCAN_STOP", readSpeed(CarInfoProxy.getInstance()));
        stopFcmLog();
        for (int id : EVENT_IDS) {
            try {
                VDBus.getDefault().unsubscribe(new VDEvent(id), eventStub);
            } catch (Throwable ignored) {
            }
        }
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        updateStatus();
        String stopped = getString(R.string.adas_probe_scan_stop);
        synchronized (eventLock) {
            Log.i(TAG, stopped + " counts=" + eventCounts);
        }
        appendEvent(stopped);
    }

    private void updateStatus() {
        if (statusView == null) {
            return;
        }
        String status = scanning
                ? getString(R.string.adas_probe_status_running, EVENT_IDS.length)
                : getString(R.string.adas_probe_status_idle);
        if (cabinScanning) {
            status += "  |  CabinLAN 1114113 / 4105";
        }
        statusView.setText(status);
    }

    /** Bilinen ADAS payload'larini okunabilir hale getirir; bilinmeyenlerde bundle toString. */
    private static String formatPayload(VDEvent event) {
        if (event == null || event.getPayload() == null) return "";
        String out = null;
        try {
            int id = event.getId();
            if (id == 721797) {
                ArrayList<VDAdasObjectInfo> list = VDAdasObjectInfo.getList(event);
                if (list != null) {
                    out = "objects=" + list.size()
                            + (list.isEmpty() ? "" : " first=" + list.get(0));
                }
            } else if (id == 722206) {
                VDADSPerceptionResult r = VDADSPerceptionResult.getValue(event);
                if (r != null) {
                    out = "perception=" + r;
                }
            }
            if (out == null) {
                Bundle b = event.getPayload();
                if (b.containsKey(VDKey.INFO)) {
                    Object info = b.get(VDKey.INFO);
                    out = "INFO=" + info + (info == null ? "" : " ["
                            + info.getClass().getSimpleName() + "]");
                }
            }
            if (out == null) {
                Bundle b = event.getPayload();
                // Bundle lazy parse; keySet/toString ile gercek anahtar ve degerleri ac.
                b.keySet();
                out = b.toString();
            }
        } catch (Throwable t) {
            out = "<payload error: " + t.getClass().getSimpleName() + ">";
        }
        if (out.length() > 1500) out = out.substring(0, 1500) + "…";
        return out;
    }

    private void startFcmLog() {
        stopFcmLog();
        try {
            File dir = getExternalFilesDir("adas_fcm");
            if (dir == null) dir = new File(getFilesDir(), "adas_fcm");
            if (!dir.exists() && !dir.mkdirs()) {
                Log.w(TAG, "FCM log dir failed: " + dir);
                return;
            }
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            fcmLogFile = new File(dir, "fcm_events_" + stamp + ".csv");
            fcmLogBytes = fcmLogFile.length();
            fcmLogWriter = new BufferedWriter(new FileWriter(fcmLogFile, true));
            fcmLogWriter.write("event,time,speed,fcm48,fcm104,fcm112,fcw126,front129,acc34,barrier125,barrier127");
            fcmLogWriter.newLine();
            fcmLogWriter.flush();
            Log.i(TAG, "FCM log=" + fcmLogFile.getAbsolutePath());
            writeFcmEvent("SCAN_START", readSpeed(CarInfoProxy.getInstance()));
        } catch (Throwable t) {
            Log.w(TAG, "FCM log start failed", t);
            stopFcmLog();
        }
    }

    private void stopFcmLog() {
        synchronized (fcmLogLock) {
            if (fcmLogWriter == null) return;
            try {
                fcmLogWriter.write("SCAN_STOP" + eventColumns(0));
                fcmLogWriter.newLine();
                fcmLogWriter.flush();
            } catch (Throwable ignored) {
            }
            try {
                fcmLogWriter.close();
            } catch (Throwable ignored) {
            }
            fcmLogWriter = null;
        }
    }

    private void appendAwayToSystemLog(String event, int speed) {
        String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
        SystemLogBuffer.append("[" + time + "] FCM " + event
                + "  speed=" + speed
                + "  48=" + value(lastFcm[1])
                + "  104=" + value(lastFcm[0])
                + "  112=" + value(lastFcm[2])
                + "  126=" + value(lastFcm[5]) + "\n");
    }

    private void writeFcmEvent(String event, int speed) {
        synchronized (fcmLogLock) {
            if (fcmLogWriter == null) return;
            try {
                if (fcmLogBytes >= MAX_FCM_LOG_BYTES) {
                    Log.w(TAG, "FCM log size limit reached");
                    return;
                }
                String line = event + "," + new SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
                        .format(new Date()) + "," + speed + eventColumns(0);
                fcmLogWriter.write(line);
                fcmLogWriter.newLine();
                fcmLogWriter.flush();
                fcmLogBytes += line.length() + 2L;
            } catch (Throwable t) {
                Log.w(TAG, "FCM log write failed", t);
            }
        }
    }

    /** CSV alanlari: 48,104,112,126,129,34,125,127. */
    private String eventColumns(int ignored) {
        StringBuilder b = new StringBuilder();
        int[] ids = {48, 104, 112, 126, 129, 34, 125, 127};
        for (int id : ids) {
            String v = id == 48 ? lastFcm[1]
                    : id == 104 ? lastFcm[0]
                    : id == 112 ? lastFcm[2]
                    : id == 126 ? lastFcm[5]
                    : id == 129 ? lastFcm[3]
                    : id == 34 ? lastFcm[4]
                    : id == 125 ? lastFcm[6]
                    : lastFcm[7];
            b.append(',').append(v == null ? "" : v);
        }
        return b.toString();
    }

    private int readSpeed(CarInfoProxy proxy) {
        try {
            int[] v = proxy.getItemValues(VDEventCarInfo.MODULE_READONLY_INFO,
                    ReadOnlyID.ID_SPEED_GAUGE_DISPLAY);
            return v == null || v.length == 0 ? 0 : Math.max(0, v[0]);
        } catch (Throwable t) {
            return 0;
        }
    }

    private void applyFcmValues(int index, int[] values) {
        String text = join(values);
        boolean changed;
        boolean away;
        boolean previousAway;
        synchronized (fcmLock) {
            previousAway = leadAwayActive;
            changed = !text.equals(lastFcm[index]);
            lastFcm[index] = text;
            away = isAwayText(lastFcm[1]);
            leadAwayActive = away;
        }
        if (changed) {
            Log.i(TAG, "FCM " + fcmIds[index] + "=" + text);
        }
        updateFcmUi();
        if (away != previousAway) {
            int speed = readSpeed(CarInfoProxy.getInstance());
            String event = away ? "AWAY_START" : "AWAY_END";
            writeFcmEvent(event, speed);
            appendAwayToSystemLog(event, speed);
        }
    }

    private void updateFcmUi() {
        if (fcmView == null) return;
        String line1 = "FCM 104=" + value(lastFcm[0])
                + "  48=" + value(lastFcm[1])
                + "  112=" + value(lastFcm[2]);
        String line2 = "FRONT 129=" + value(lastFcm[3])
                + "  ACC 34=" + value(lastFcm[4])
                + "  FCW 126=" + value(lastFcm[5]);
        String line3 = "BARRIER 125=" + value(lastFcm[6])
                + " / 127=" + value(lastFcm[7]);
        ui.post(() -> fcmView.setText(line1 + "\n" + line2 + "\n" + line3));
    }

    private static String value(String s) {
        return s == null || s.isEmpty() ? "—" : s;
    }

    private static boolean isAwayText(String text) {
        return "12".equals(text) || (text != null && text.startsWith("12|"));
    }

    private static String join(int[] values) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) b.append('|');
            b.append(values[i]);
        }
        return b.toString();
    }

    private void appendEvent(String line) {
        if (line == null) {
            return;
        }
        ui.post(() -> {
            if (eventView == null) {
                return;
            }
            if (eventLog.length() > 12000) {
                eventLog.delete(0, eventLog.length() - 12000);
            }
            eventLog.append(line).append('\n');
            eventView.setText(eventLog);
        });
    }

    private void styleButton(Button button, int color) {
        UiStyles.styleOemButton(button, color);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setMinHeight(dp(44));
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private String value(Integer value) {
        return value == null ? "—" : Integer.toString(value);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int[] buildEventIds() {
        // Sadece pasif, ADAS ile ilgili eventler. AR-HUD OTA/settings ve
        // komut eventleri abone edilmiyor; arac hattina gereksiz yuk binmesin.
        return new int[]{
                721697, // NAVIGATION_LANE_INFO
                721797, // ADAS_DISPLAY_OBJECT_INFO
                721798, // ADAS_DISPLAY_LANE_INFO
                721799, // ADAS_PLANNING_PATH
                722204, // lane changing info
                722205, // lane changing result
                722206, // ADS perception result
                722207, // ADS state tips
                722208, // ADS running state
                722209, // ADS vehicle speed
                722306  // SOUND_CURRENT_ADAS_OPEN_STATUS
        };
    }

    @Override
    public void onBackPressed() {
        // Tarama acikken Activity'yi bitirme; Sistem Kayitlari'na don ve
        // FCM izleyiciyi arka planda devam ettir. Tarama durdurulduysa normal cikis.
        if (!scanning && !cabinScanning) {
            super.onBackPressed();
            return;
        }
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_OPEN_LOG_TAB, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        stopCabinProbe();
        stopScan();
        stopFcmLog();
        if (busHandler != null) {
            busHandler.removeCallbacksAndMessages(null);
        }
        if (busThread != null) {
            busThread.quitSafely();
        }
        super.onDestroy();
    }
}
