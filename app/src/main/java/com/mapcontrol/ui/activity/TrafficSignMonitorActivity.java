package com.mapcontrol.ui.activity;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.extra.project.carinfo.proxy.Constants;
import com.desaysv.ivi.vdb.IVDBusNotify;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;
import com.mapcontrol.R;
import com.mapcontrol.util.SystemLogBuffer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Salt okunur trafik levhası / TSR monitörü.
 *
 * <p>Araca hiçbir değer yazmaz. Yalnızca CarInfo CAR_SETTING kanalındaki
 * TSR, trafik ışığı, hız limiti ve uyarı alanlarını okur. Değerler mümkünse
 * event callback ile, yoksa yalnızca başlangıçta tek seferlik snapshot ile gelir.</p>
 */
public class TrafficSignMonitorActivity extends Activity {

    private static final String TAG = "TrafficSignMonitor";
    private static final int MODULE = VDEventCarInfo.MODULE_CAR_SETTING;
    private static final int[] IDS = {
            CarSettingID.ID_CAR_FCM_TRAFFIC_LOGO_IDENTIFY_SYSTEM,   // 105
            CarSettingID.ID_CAR_FCM_TRAFFIC_SIGNAL_IDENTIFY_SYSTEM, // 106
            CarSettingID.ID_CAR_LIMIT_ASSISTANCE,                  // 32
            CarSettingID.ID_IDCU_SPEEDLMTNMBERE,                   // 207
            CarSettingID.ID_IHU_SPEEDLMTNMBERAB,                   // 208
            CarSettingID.ID_NAV_SPEED_LIMIT,                       // 86
            CarSettingID.ID_NAV_SPEED_LIMIT_STATUS,                // 85
            CarSettingID.ID_CAR_OVER_SPEED_WARNING,                // 13
            CarSettingID.ID_CAR_SPD_LIMIT_WARN_SET                 // 201
    };

    private final Handler ui = new Handler(Looper.getMainLooper());
    private HandlerThread busThread;
    private Handler busHandler;
    private final Map<Integer, String> values = new ConcurrentHashMap<>();
    private final Map<Integer, String> labels = new LinkedHashMap<>();

    private TextView statusView;
    private TextView dataView;
    private TextView lastChangeView;
    private Button startButton;
    private Button stopButton;

    private volatile boolean running;
    private volatile boolean registered;
    private int connectAttempts;

    private final IVDBusNotify.Stub callback = new IVDBusNotify.Stub() {
        @Override
        public void onVDBusNotify(VDEvent event) {
            if (event == null || event.getId() != MODULE || event.getPayload() == null) {
                return;
            }
            int id = event.getPayload().getInt(Constants.CMD_ID, -1);
            int[] raw = event.getPayload().getIntArray(Constants.VALUE);
            if (id < 0 || raw == null || raw.length == 0) return;
            String text = join(raw);
            values.put(id, text);
            String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
            ui.post(() -> {
                lastChangeView.setText("Son değişim: " + time + "  " + id + "=" + text);
                renderValues();
            });
            SystemLogBuffer.append("[" + time + "] TSR " + id + "=" + text + "\n");
        }
    };

    private final Runnable connectRetry = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            CarInfoProxy proxy = CarInfoProxy.getInstance();
            if (proxy.isServiceConnnected()) {
                beginCallbacks(proxy);
                return;
            }
            if (++connectAttempts > 20) {
                statusView.setText("CarInfo bağlanamadı.");
                stopMonitor();
                return;
            }
            statusView.setText("CarInfo bağlanıyor… (" + connectAttempts + "/20)");
            busHandler.post(() -> {
                try {
                    proxy.init(getApplicationContext());
                } catch (Throwable ignored) {
                }
            });
            ui.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        busThread = new HandlerThread("tsr-monitor-bus");
        busThread.start();
        busHandler = new Handler(busThread.getLooper());
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));
        root.setBackgroundColor(Color.rgb(18, 20, 24));

        TextView title = new TextView(this);
        title.setText(R.string.tsr_monitor_title);
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.tsr_monitor_subtitle);
        subtitle.setTextSize(12);
        subtitle.setTextColor(0xFFB8C4D4);
        subtitle.setPadding(0, dp(4), 0, dp(10));
        root.addView(subtitle);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(buttons, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        startButton = new Button(this);
        startButton.setText(R.string.tsr_monitor_start);
        styleButton(startButton, 0xFF1565C0);
        startButton.setOnClickListener(v -> startMonitor());
        buttons.addView(startButton, weighted());

        stopButton = new Button(this);
        stopButton.setText(R.string.tsr_monitor_stop);
        styleButton(stopButton, 0xFF37474F);
        stopButton.setEnabled(false);
        stopButton.setOnClickListener(v -> stopMonitor());
        LinearLayout.LayoutParams stopLp = weighted();
        stopLp.leftMargin = dp(8);
        buttons.addView(stopButton, stopLp);

        Button clearButton = new Button(this);
        clearButton.setText(R.string.tsr_monitor_clear);
        styleButton(clearButton, 0xFF37474F);
        clearButton.setOnClickListener(v -> {
            values.clear();
            lastChangeView.setText("Son değişim: —");
            renderValues();
        });
        LinearLayout.LayoutParams clearLp = weighted();
        clearLp.leftMargin = dp(8);
        buttons.addView(clearButton, clearLp);

        statusView = new TextView(this);
        statusView.setTextSize(12);
        statusView.setTextColor(0xFF8FA1B6);
        statusView.setPadding(0, dp(8), 0, dp(4));
        statusView.setText(R.string.tsr_monitor_idle);
        root.addView(statusView);

        lastChangeView = new TextView(this);
        lastChangeView.setTextSize(12);
        lastChangeView.setTextColor(0xFFFFD180);
        lastChangeView.setPadding(dp(8), dp(4), dp(8), dp(4));
        lastChangeView.setText("Son değişim: —");
        root.addView(lastChangeView);

        dataView = new TextView(this);
        dataView.setTextSize(14);
        dataView.setTypeface(Typeface.MONOSPACE);
        dataView.setTextColor(0xFFE8EDF5);
        dataView.setPadding(dp(8), dp(8), dp(8), dp(8));
        dataView.setTextIsSelectable(true);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(dataView, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        labels.put(CarSettingID.ID_CAR_FCM_TRAFFIC_LOGO_IDENTIFY_SYSTEM,
                "105  Levha tanıma");
        labels.put(CarSettingID.ID_CAR_FCM_TRAFFIC_SIGNAL_IDENTIFY_SYSTEM,
                "106  Trafik ışığı tanıma");
        labels.put(CarSettingID.ID_CAR_LIMIT_ASSISTANCE,
                " 32  Hız sınırı yardımı");
        labels.put(CarSettingID.ID_IDCU_SPEEDLMTNMBERE,
                "207  ICU hız limiti (aday)");
        labels.put(CarSettingID.ID_IHU_SPEEDLMTNMBERAB,
                "208  IHU hız limiti (aday)");
        labels.put(CarSettingID.ID_NAV_SPEED_LIMIT,
                " 86  Navigasyon hız limiti");
        labels.put(CarSettingID.ID_NAV_SPEED_LIMIT_STATUS,
                " 85  Navigasyon limit durumu");
        labels.put(CarSettingID.ID_CAR_OVER_SPEED_WARNING,
                " 13  Aşırı hız uyarı eşiği");
        labels.put(CarSettingID.ID_CAR_SPD_LIMIT_WARN_SET,
                "201  Hız uyarı ayarı");

        setContentView(root);
        renderValues();
    }

    private void startMonitor() {
        if (running) return;
        running = true;
        connectAttempts = 0;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        statusView.setText(R.string.tsr_monitor_connecting);
        CarInfoProxy proxy = CarInfoProxy.getInstance();
        if (proxy.isServiceConnnected()) {
            beginCallbacks(proxy);
        } else {
            busHandler.post(() -> {
                try {
                    proxy.init(getApplicationContext());
                } catch (Throwable ignored) {
                }
            });
            ui.postDelayed(connectRetry, 1000L);
        }
    }

    private void beginCallbacks(CarInfoProxy proxy) {
        if (!running || registered) return;
        try {
            proxy.regCallBack(MODULE, callback, IDS);
            registered = true;
            statusView.setText(R.string.tsr_monitor_running);
            busHandler.post(this::snapshot);
        } catch (Throwable t) {
            statusView.setText("Callback kaydı başarısız.");
            stopMonitor();
        }
    }

    /** Yalnızca bir kez, arka planda ilk değerleri okur; periyodik polling yok. */
    private void snapshot() {
        CarInfoProxy proxy = CarInfoProxy.getInstance();
        for (int id : IDS) {
            try {
                int[] raw = proxy.getItemValues(MODULE, id);
                if (raw != null && raw.length > 0) {
                    values.put(id, join(raw));
                }
            } catch (Throwable ignored) {
            }
        }
        ui.post(this::renderValues);
    }

    private void stopMonitor() {
        running = false;
        ui.removeCallbacks(connectRetry);
        if (registered) {
            try {
                CarInfoProxy.getInstance().unRegCallBack(MODULE, callback);
            } catch (Throwable ignored) {
            }
            registered = false;
        }
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        statusView.setText(R.string.tsr_monitor_idle);
    }

    private void renderValues() {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<Integer, String> entry : labels.entrySet()) {
            int id = entry.getKey();
            String raw = values.get(id);
            b.append(entry.getValue()).append(": ")
                    .append(format(id, raw)).append('\n');
        }
        b.append("\n").append(getString(R.string.tsr_monitor_note));
        dataView.setText(b.toString());
    }

    private static String format(int id, String raw) {
        if (raw == null || raw.isEmpty()) return "—";
        if (id == 105 || id == 106 || id == 32) {
            if ("1".equals(raw)) return "AÇIK";
            if ("2".equals(raw)) return "KAPALI";
            return raw;
        }
        if (id == 207 || id == 208 || id == 86) {
            try {
                int v = Integer.parseInt(raw.split("\\|")[0]);
                return v > 0 ? v + " km/h" : "—";
            } catch (Throwable ignored) {
                return raw;
            }
        }
        return raw;
    }

    private static String join(int[] values) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) b.append('|');
            b.append(values[i]);
        }
        return b.toString();
    }

    private void styleButton(Button button, int color) {
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setMinHeight(dp(44));
        button.setBackgroundColor(color);
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        stopMonitor();
        if (busHandler != null) {
            busHandler.removeCallbacksAndMessages(null);
        }
        if (busThread != null) {
            busThread.quitSafely();
        }
        super.onDestroy();
    }
}
