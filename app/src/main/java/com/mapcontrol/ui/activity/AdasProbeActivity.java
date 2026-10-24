package com.mapcontrol.ui.activity;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.vehicle.material.OemEolConfigReader;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Root gerektirmeyen ADAS teşhis ekranı.
 *
 * <p>Önce CarLanService'in SOME/IP ADAS hattını başlatıp başlatmadığını belirleyen
 * EOL config 112/147 değerlerini gösterir; ardından ADAS event aralıklarını manuel
 * olarak dinler. Hiçbir arka plan kaydı veya otomatik başlatma yapmaz.</p>
 */
public class AdasProbeActivity extends Activity {

    private static final int[] EVENT_IDS = buildEventIds();

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService configExecutor = Executors.newSingleThreadExecutor();
    private final StringBuilder eventLog = new StringBuilder();

    private TextView configView;
    private TextView statusView;
    private TextView eventView;
    private Button startButton;
    private Button stopButton;

    private boolean scanning;
    private int subscribedCount;
    private int subscribeFailures;

    private final IVDBusNotify.Stub eventStub = new IVDBusNotify.Stub() {
        @Override
        public void onVDBusNotify(VDEvent event) {
            if (event == null) {
                return;
            }
            String payload;
            try {
                payload = event.getPayload() == null ? "" : event.getPayload().toString();
            } catch (Throwable t) {
                payload = "<payload error: " + t.getClass().getSimpleName() + ">";
            }
            if (payload.length() > 700) {
                payload = payload.substring(0, 700) + "…";
            }
            appendEvent("EVT " + event.getId() + "  " + payload);
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

        setContentView(root);
        loadConfig();
        updateStatus();
    }

    private void loadConfig() {
        configView.setText(R.string.adas_probe_loading);
        configExecutor.execute(() -> {
            try {
                VDBus.getDefault().init(getApplicationContext());
                VDBus.getDefault().bindService(VDServiceDef.ServiceType.VEHICLE_DEVICE);
            } catch (Throwable ignored) {
            }

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
            ui.post(() -> configView.setText(shown));
        });
    }

    private void startScan() {
        if (scanning) {
            return;
        }
        eventLog.setLength(0);
        eventView.setText("");
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
        updateStatus();
        appendEvent(getString(R.string.adas_probe_subscribed, subscribedCount, subscribeFailures));
    }

    private void stopScan() {
        if (!scanning) {
            return;
        }
        scanning = false;
        for (int id : EVENT_IDS) {
            try {
                VDBus.getDefault().unsubscribe(new VDEvent(id), eventStub);
            } catch (Throwable ignored) {
            }
        }
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        updateStatus();
        appendEvent(getString(R.string.adas_probe_scan_stop));
    }

    private void updateStatus() {
        if (statusView == null) {
            return;
        }
        statusView.setText(scanning
                ? getString(R.string.adas_probe_status_running, EVENT_IDS.length)
                : getString(R.string.adas_probe_status_idle));
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
        int[] ids = new int[16 + 18];
        int index = 0;
        for (int id = 721796; id <= 721811; id++) {
            ids[index++] = id;
        }
        for (int id = 722196; id <= 722213; id++) {
            ids[index++] = id;
        }
        return ids;
    }

    @Override
    protected void onDestroy() {
        stopScan();
        configExecutor.shutdownNow();
        super.onDestroy();
    }
}
