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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.vdb.IVDBusNotify;
import com.desaysv.ivi.vdb.client.VDBus;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;
import com.mapcontrol.R;
import com.mapcontrol.ui.cockpit.CockpitLegacyStyler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Salt okunur "önde araç" keşif ekranı.
 *
 * <p>CarInfo ID taraması ve ADAS event dinleme yapar. Hiçbir değer yazmaz;
 * dosya/CSV oluşturmaz. Yalnızca kullanıcı başlattığında çalışır.</p>
 */
public class FrontObjectProbeActivity extends Activity {

    private static final int[] MODULES = {
            VDEventCarInfo.MODULE_CAR_SETTING,
            VDEventCarInfo.MODULE_READONLY_INFO,
            VDEventCarInfo.MODULE_RADAR,
            VDEventCarInfo.MODULE_AVM,
            VDEventCarInfo.MODULE_NEW_ENERGY,
            VDEventCarInfo.MODULE_HVAC
    };
    private static final String[] MODULE_NAMES = {
            "CAR_SETTING", "READONLY", "RADAR", "AVM", "NEW_ENERGY", "HVAC"
    };

    private final Handler ui = new Handler(Looper.getMainLooper());
    private HandlerThread busThread;
    private Handler busHandler;

    private Spinner moduleSpinner;
    private TextView statusView;
    private TextView outputView;
    private Button scanButton;
    private Button refButton;
    private Button compareButton;
    private Button eventButton;

    private volatile boolean scanning;
    private volatile boolean scanStop;
    private volatile boolean eventListening;
    private final Map<String, String> reference = new ConcurrentHashMap<>();
    private final Map<String, Integer> eventCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> lastEventUi = new ConcurrentHashMap<>();
    private final StringBuilder output = new StringBuilder();

    private final IVDBusNotify.Stub eventStub = new IVDBusNotify.Stub() {
        @Override
        public void onVDBusNotify(VDEvent event) {
            if (event == null) return;
            int id = event.getId();
            String payload = event.getPayload() == null ? "" : event.getPayload().toString();
            if (payload.length() > 800) payload = payload.substring(0, 800) + "…";
            int count = eventCounts.merge(Integer.toString(id), 1, Integer::sum);
            long now = System.currentTimeMillis();
            Long last = lastEventUi.get(Integer.toString(id));
            if (last == null || now - last >= 500L || count <= 3) {
                lastEventUi.put(Integer.toString(id), now);
                String line = "EVT " + id + " #" + count + "  " + payload;
                append(line);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        busThread = new HandlerThread("front-object-probe");
        busThread.start();
        busHandler = new Handler(busThread.getLooper());
        busHandler.post(() -> {
            try {
                CarInfoProxy.getInstance().init(getApplicationContext());
            } catch (Throwable ignored) {
            }
        });
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(14));
        root.setBackgroundColor(Color.rgb(18, 20, 24));

        TextView title = new TextView(this);
        title.setText(R.string.front_probe_title);
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.front_probe_subtitle);
        subtitle.setTextSize(12);
        subtitle.setTextColor(0xFFB8C4D4);
        subtitle.setPadding(0, dp(4), 0, dp(8));
        root.addView(subtitle);

        LinearLayout moduleRow = new LinearLayout(this);
        moduleRow.setOrientation(LinearLayout.HORIZONTAL);
        moduleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView moduleLabel = new TextView(this);
        moduleLabel.setText("Modül:");
        moduleLabel.setTextColor(0xFFE8EDF5);
        moduleLabel.setTextSize(13);
        moduleRow.addView(moduleLabel);
        moduleSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, MODULE_NAMES);
        moduleSpinner.setAdapter(adapter);
        LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        spinnerLp.leftMargin = dp(8);
        moduleRow.addView(moduleSpinner, spinnerLp);
        root.addView(moduleRow);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(buttons, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        scanButton = button("Tara (0–255)");
        scanButton.setOnClickListener(v -> startScan(ScanMode.SCAN));
        buttons.addView(scanButton, weighted());

        refButton = button("Referans al");
        refButton.setOnClickListener(v -> startScan(ScanMode.REFERENCE));
        buttons.addView(refButton, weightedWithStart());

        compareButton = button("Karşılaştır");
        compareButton.setOnClickListener(v -> startScan(ScanMode.COMPARE));
        buttons.addView(compareButton, weightedWithStart());

        eventButton = button("ADAS event dinle");
        eventButton.setOnClickListener(v -> toggleEvents());
        LinearLayout.LayoutParams eventLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        eventLp.topMargin = dp(6);
        root.addView(eventButton, eventLp);

        statusView = new TextView(this);
        statusView.setTextSize(12);
        statusView.setTextColor(0xFF8FA1B6);
        statusView.setPadding(0, dp(6), 0, dp(4));
        statusView.setText("Hazır.");
        root.addView(statusView);

        outputView = new TextView(this);
        outputView.setTextSize(11);
        outputView.setTypeface(Typeface.MONOSPACE);
        outputView.setTextColor(0xFFD7DEE8);
        outputView.setPadding(dp(8), dp(8), dp(8), dp(8));
        outputView.setTextIsSelectable(true);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(outputView, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        CockpitLegacyStyler.apply(root);
        setContentView(root);
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(0xFF37474F);
        b.setMinHeight(dp(44));
        return b;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout.LayoutParams weightedWithStart() {
        LinearLayout.LayoutParams lp = weighted();
        lp.leftMargin = dp(6);
        return lp;
    }

    private enum ScanMode {SCAN, REFERENCE, COMPARE}

    private int selectedModule() {
        int pos = moduleSpinner == null ? 0 : moduleSpinner.getSelectedItemPosition();
        return MODULES[Math.max(0, Math.min(pos, MODULES.length - 1))];
    }

    private String selectedModuleName() {
        int pos = moduleSpinner == null ? 0 : moduleSpinner.getSelectedItemPosition();
        return MODULE_NAMES[Math.max(0, Math.min(pos, MODULE_NAMES.length - 1))];
    }

    private void startScan(ScanMode mode) {
        if (scanning) return;
        scanning = true;
        scanStop = false;
        scanButton.setEnabled(false);
        refButton.setEnabled(false);
        compareButton.setEnabled(false);
        int module = selectedModule();
        String moduleName = selectedModuleName();
        statusView.setText(moduleName + " taranıyor…");
        output.setLength(0);
        outputView.setText("");
        busHandler.post(() -> scanModule(module, moduleName, mode));
    }

    private void scanModule(int module, String moduleName, ScanMode mode) {
        CarInfoProxy proxy = CarInfoProxy.getInstance();
        if (!proxy.isServiceConnnected()) {
            finishScan("CarInfo bağlı değil. Önce MapControl/Araç Bilgisi açılıp servis bağlanmalı.");
            return;
        }
        Map<String, String> found = new LinkedHashMap<>();
        for (int id = 0; id <= 255 && !scanStop; id++) {
            try {
                int[] values = proxy.getItemValues(module, id);
                if (values != null && values.length > 0) {
                    String text = join(values);
                    if (!allZero(values)) {
                        found.put(module + ":" + id, text);
                    }
                }
            } catch (Throwable ignored) {
            }
            if ((id & 15) == 0) {
                int p = id;
                ui.post(() -> statusView.setText(moduleName + " taranıyor… " + p + "/255"));
            }
            try {
                Thread.sleep(15L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (mode == ScanMode.REFERENCE) {
            reference.clear();
            reference.putAll(found);
            ui.post(() -> {
                output.setLength(0);
                output.append("REFERANS ").append(moduleName).append(": ")
                        .append(found.size()).append(" sıfır olmayan ID\n");
                for (Map.Entry<String, String> e : found.entrySet()) {
                    output.append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
                }
                outputView.setText(output.toString());
            });
            finishScan("Referans alındı: " + found.size() + " ID");
        } else if (mode == ScanMode.COMPARE) {
            ui.post(() -> {
                output.setLength(0);
                output.append("KARŞILAŞTIRMA ").append(moduleName).append("\n");
                int changes = 0;
                for (Map.Entry<String, String> e : found.entrySet()) {
                    String old = reference.get(e.getKey());
                    if (old == null || !old.equals(e.getValue())) {
                        changes++;
                        output.append("YENİ/DEĞİŞEN ").append(e.getKey())
                                .append(": ").append(old == null ? "—" : old)
                                .append(" -> ").append(e.getValue()).append('\n');
                    }
                }
                for (Map.Entry<String, String> e : reference.entrySet()) {
                    if (!found.containsKey(e.getKey())) {
                        changes++;
                        output.append("KAYBOLAN ").append(e.getKey())
                                .append(": ").append(e.getValue()).append(" -> —\n");
                    }
                }
                if (changes == 0) {
                    output.append("Değişen ID yok.\n");
                }
                outputView.setText(output.toString());
                finishScan("Karşılaştırma: " + changes + " değişiklik");
            });
        } else {
            ui.post(() -> {
                output.setLength(0);
                output.append(moduleName).append(" sıfır olmayan ID: ")
                        .append(found.size()).append('\n');
                for (Map.Entry<String, String> e : found.entrySet()) {
                    output.append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
                }
                outputView.setText(output.toString());
                finishScan("Tarama bitti: " + found.size() + " ID");
            });
        }
    }

    private void finishScan(String status) {
        scanning = false;
        ui.post(() -> {
            scanButton.setEnabled(true);
            refButton.setEnabled(true);
            compareButton.setEnabled(true);
            statusView.setText(status);
        });
    }

    private void toggleEvents() {
        if (eventListening) {
            stopEvents();
        } else {
            startEvents();
        }
    }

    private void startEvents() {
        eventListening = true;
        eventButton.setText("ADAS event dinlemeyi durdur");
        statusView.setText("ADAS event dinleniyor…");
        busHandler.post(() -> {
            try {
                VDBus.getDefault().init(getApplicationContext());
                for (int id : eventIds()) {
                    VDBus.getDefault().subscribe(new VDEvent(id), eventStub);
                }
                VDBus.getDefault().subscribeCommit();
                append("ADAS event aboneliği kuruldu.");
            } catch (Throwable t) {
                append("Event aboneliği hatası: " + t);
                eventListening = false;
            }
        });
    }

    private void stopEvents() {
        eventListening = false;
        eventButton.setText("ADAS event dinle");
        busHandler.post(() -> {
            try {
                for (int id : eventIds()) {
                    VDBus.getDefault().unsubscribe(new VDEvent(id), eventStub);
                }
            } catch (Throwable ignored) {
            }
            append("ADAS event dinleme durdu.");
        });
        statusView.setText("Event dinleme durdu.");
    }

    private static int[] eventIds() {
        int[] ids = new int[2 + 100 + 100];
        int i = 0;
        ids[i++] = 721697;
        ids[i++] = 722306;
        for (int id = 721796; id <= 721895; id++) ids[i++] = id;
        for (int id = 722196; id <= 722295; id++) ids[i++] = id;
        return ids;
    }

    private void append(String line) {
        ui.post(() -> {
            if (output.length() > 16000) {
                output.delete(0, output.length() - 12000);
            }
            output.append(line).append('\n');
            outputView.setText(output);
            ScrollView parent = (ScrollView) outputView.getParent();
            if (parent != null) parent.post(() -> parent.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    private static boolean allZero(int[] values) {
        for (int v : values) if (v != 0) return false;
        return true;
    }

    private static String join(int[] values) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) b.append('|');
            b.append(values[i]);
        }
        return b.toString();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        scanStop = true;
        if (eventListening) stopEvents();
        // once unsubscribe mesajlari islensin; sonra thread'i kapat.
        if (busThread != null) busThread.quitSafely();
        super.onDestroy();
    }
}
