package com.mapcontrol.ui.activity;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.mapcontrol.R;
import com.mapcontrol.profile.DriverProfileManager;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.vehicle.ComfortCommands;
import com.mapcontrol.vehicle.IdScanner;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Log sekmesinden açılır. "Önce" ve "Sonra" anlık görüntülerinin farkını gösterir; kaydedici
 * değişiklikleri CSV'ye yazar. Değişen CAR_SETTING vb. değerler profil hafızasına eklenebilir.
 */
public class IdDiscoveryActivity extends AppCompatActivity {

    private static final int MAX_ROWS = 300;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<Integer, CheckBox> moduleChecks = new LinkedHashMap<>();
    private volatile boolean cancelRequested;
    private volatile boolean recording;
    private volatile boolean busy;

    private IdScanner.Snapshot before;
    private List<IdScanner.Change> lastChanges = new ArrayList<>();
    private TextView status;
    private LinearLayout results;
    private Button btnRecord;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(UiStyles.color(this, R.color.backgroundPage));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(UiStyles.color(this, R.color.surfaceBar));
        bar.setPadding(16, 16, 16, 16);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        Button back = button(R.string.cluster_test_back, R.color.buttonPrimary);
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        TextView title = new TextView(this);
        title.setText(R.string.discovery_title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTextColor(UiStyles.color(this, R.color.textPrimary));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(24, 0, 0, 0);
        bar.addView(title);
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(16, 12, 16, 24);
        scroll.addView(col);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        col.addView(text(getString(R.string.discovery_help), 13, R.color.textHint, false));

        LinearLayout moduleRow = new LinearLayout(this);
        moduleRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout moduleRow2 = new LinearLayout(this);
        moduleRow2.setOrientation(LinearLayout.HORIZONTAL);
        int index = 0;
        for (IdScanner.Module module : IdScanner.modules()) {
            CheckBox box = new CheckBox(this);
            box.setText(module.label);
            box.setChecked(module.core);
            box.setTextColor(UiStyles.color(this, R.color.textPrimary));
            box.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            moduleChecks.put(module.id, box);
            (index++ < 9 ? moduleRow : moduleRow2).addView(box);
        }
        col.addView(moduleRow);
        col.addView(moduleRow2);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, 8, 0, 8);
        Button btnBefore = button(R.string.discovery_before, R.color.buttonPrimary);
        btnBefore.setOnClickListener(v -> takeBefore());
        Button btnAfter = button(R.string.discovery_after, R.color.buttonPrimary);
        btnAfter.setOnClickListener(v -> showDiff());
        btnRecord = button(R.string.discovery_record_start, R.color.buttonSecondaryMuted);
        btnRecord.setOnClickListener(v -> toggleRecording());
        Button btnCsv = button(R.string.discovery_save_csv, R.color.buttonSecondaryMuted);
        btnCsv.setOnClickListener(v -> saveCsv());
        for (Button b : new Button[]{btnBefore, btnAfter, btnRecord, btnCsv}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 12, 0);
            buttons.addView(b, lp);
        }
        col.addView(buttons);

        status = text(getString(R.string.discovery_status_idle), 14, R.color.textSecondary, true);
        col.addView(status);

        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        col.addView(results);

        setContentView(root);
    }

    @Override
    protected void onDestroy() {
        cancelRequested = true;
        recording = false;
        executor.shutdownNow();
        super.onDestroy();
    }

    private List<Integer> selectedModules() {
        List<Integer> ids = new ArrayList<>();
        for (Map.Entry<Integer, CheckBox> e : moduleChecks.entrySet()) {
            if (e.getValue().isChecked()) {
                ids.add(e.getKey());
            }
        }
        return ids;
    }

    private boolean precheck() {
        if (!ComfortCommands.isConnected()) {
            setStatus(getString(R.string.discovery_not_connected));
            return false;
        }
        if (busy) {
            setStatus(getString(R.string.discovery_busy));
            return false;
        }
        if (selectedModules().isEmpty()) {
            setStatus(getString(R.string.discovery_no_module));
            return false;
        }
        return true;
    }

    private void takeBefore() {
        if (!precheck()) {
            return;
        }
        List<Integer> modules = selectedModules();
        busy = true;
        cancelRequested = false;
        executor.execute(() -> {
            IdScanner.Snapshot snap = IdScanner.scan(modules,
                    p -> setStatus(getString(R.string.discovery_scanning, p)), () -> cancelRequested);
            before = snap;
            busy = false;
            setStatus(getString(R.string.discovery_before_done, snap.size(), time(snap.timeMs)));
        });
    }

    private void showDiff() {
        if (before == null) {
            setStatus(getString(R.string.discovery_need_before));
            return;
        }
        if (!precheck()) {
            return;
        }
        List<Integer> modules = selectedModules();
        busy = true;
        cancelRequested = false;
        executor.execute(() -> {
            IdScanner.Snapshot after = IdScanner.scan(modules,
                    p -> setStatus(getString(R.string.discovery_scanning, p)), () -> cancelRequested);
            lastChanges = IdScanner.diff(before, after);
            busy = false;
            runOnUiThread(() -> renderChanges(lastChanges));
            setStatus(getString(R.string.discovery_diff_done, lastChanges.size()));
        });
    }

    private void toggleRecording() {
        if (recording) {
            recording = false;
            cancelRequested = true;
            btnRecord.setText(R.string.discovery_record_start);
            return;
        }
        if (!precheck()) {
            return;
        }
        List<Integer> modules = selectedModules();
        File file = outputFile("idlog");
        if (file == null) {
            setStatus(getString(R.string.discovery_save_failed));
            return;
        }
        recording = true;
        busy = true;
        cancelRequested = false;
        btnRecord.setText(R.string.discovery_record_stop);
        executor.execute(() -> {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write("time,module,module_id,id,name,before,after\n");
                IdScanner.Snapshot previous = IdScanner.scan(modules, null, () -> !recording);
                int total = 0;
                while (recording) {
                    IdScanner.Snapshot current = IdScanner.scan(modules, null, () -> !recording);
                    if (!recording) {
                        break;
                    }
                    for (IdScanner.Change c : IdScanner.diff(previous, current)) {
                        writer.write(time(current.timeMs) + "," + IdScanner.moduleLabel(c.module) + ","
                                + c.module + "," + c.id + "," + c.name + ",\""
                                + IdScanner.format(c.before) + "\",\"" + IdScanner.format(c.after) + "\"\n");
                        total++;
                    }
                    writer.flush();
                    previous = current;
                    setStatus(getString(R.string.discovery_recording, total, file.getName()));
                }
            } catch (IOException e) {
                setStatus(getString(R.string.discovery_save_failed));
            } finally {
                busy = false;
                recording = false;
                runOnUiThread(() -> btnRecord.setText(R.string.discovery_record_start));
                setStatus(getString(R.string.discovery_saved, file.getAbsolutePath()));
            }
        });
    }

    private void saveCsv() {
        File file = outputFile("idscan");
        if (file == null) {
            setStatus(getString(R.string.discovery_save_failed));
            return;
        }
        String csv = lastChanges.isEmpty() && before != null
                ? IdScanner.snapshotCsv(before) : IdScanner.changesCsv(lastChanges);
        executor.execute(() -> {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(csv);
                setStatus(getString(R.string.discovery_saved, file.getAbsolutePath()));
            } catch (IOException e) {
                setStatus(getString(R.string.discovery_save_failed));
            }
        });
    }

    private File outputFile(String prefix) {
        File dir = getExternalFilesDir(null);
        if (dir == null) {
            return null;
        }
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return new File(dir, prefix + "_" + stamp + ".csv");
    }

    private void renderChanges(List<IdScanner.Change> changes) {
        results.removeAllViews();
        int shown = 0;
        for (IdScanner.Change c : changes) {
            if (shown++ >= MAX_ROWS) {
                break;
            }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, 6, 0, 6);
            TextView label = text(IdScanner.describe(c), 13, R.color.textPrimary, false);
            label.setTypeface(android.graphics.Typeface.MONOSPACE);
            row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            if (c.writable() && c.after.length == 1) {
                Button add = button(R.string.discovery_add_to_profile, R.color.buttonSecondaryMuted);
                add.setOnClickListener(v -> addToProfile(c));
                row.addView(add);
            }
            results.addView(row);
        }
    }

    private void addToProfile(IdScanner.Change change) {
        DriverProfileManager manager = DriverProfileManager.get(this);
        DriverProfileManager.Profile profile = manager.getActive();
        if (profile == null) {
            return;
        }
        String label = change.name.isEmpty()
                ? IdScanner.moduleLabel(change.module) + " " + change.id : change.name;
        manager.addOrUpdateItem(profile.id, new DriverProfileManager.MemoryItem(
                change.module, change.id, label, change.after[0], false));
        Toast.makeText(this, getString(R.string.discovery_added, profile.name), Toast.LENGTH_LONG).show();
    }

    private void setStatus(String message) {
        runOnUiThread(() -> status.setText(message));
    }

    private static String time(long ms) {
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(ms));
    }

    private Button button(int textRes, int colorRes) {
        Button b = new Button(this);
        b.setText(textRes);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setTextColor(UiStyles.color(this, R.color.textPrimary));
        UiStyles.styleOemButton(b, UiStyles.color(this, colorRes));
        b.setPadding(16, 10, 16, 10);
        return b;
    }

    private TextView text(String value, int sp, int colorRes, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(UiStyles.color(this, colorRes));
        if (bold) {
            t.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        t.setPadding(0, 8, 0, 8);
        return t;
    }
}
