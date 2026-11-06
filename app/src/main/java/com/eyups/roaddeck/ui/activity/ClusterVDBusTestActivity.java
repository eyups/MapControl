package com.eyups.roaddeck.ui.activity;

import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import com.eyups.roaddeck.R;
import com.eyups.roaddeck.ui.cockpit.CockpitLegacyStyler;
import com.eyups.roaddeck.manager.ClusterVDBusBenchManager;
import com.eyups.roaddeck.ui.theme.UiStyles;
import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import com.eyups.roaddeck.vehicle.ClimateAutomationVdbusDiagnostics;
import com.eyups.roaddeck.vehicle.FakeVdbusBodyTest;
import com.eyups.roaddeck.vehicle.OutsideTemperatureVdbusTest;
import com.eyups.roaddeck.vehicle.SeatMemoryVdbusTest;
import com.eyups.roaddeck.vehicle.VehicleVdbusDiscoveryTest;

/**
 * Log sekmesinden açılır; araç / VDBus olaylarını bench eder.
 */
public class ClusterVDBusTestActivity extends AppCompatActivity implements ClusterVDBusBenchManager.UiActions {
    private static final String TAG = "ClusterVDBusTest";
    private ClusterVDBusBenchManager benchManager;
    private OutsideTemperatureVdbusTest temperatureTest;
    private ClimateAutomationVdbusDiagnostics automationDiagnostics;
    private SeatMemoryVdbusTest seatMemoryTest;
    private VehicleVdbusDiscoveryTest vehicleVdbusDiscoveryTest;
    private TextView tvOut;
    private Button temperatureTestButton;
    private Button automationDiagnosticsButton;
    private Button liveAutomationCaptureButton;
    private Button seatMemoryTestButton;
    private Button vehicleVdbusDiscoveryButton;
    private Button bodySpeedButton;
    private final List<BodyToggle> bodyToggles = new ArrayList<>();
    private boolean bodySpeedHigh;
    private boolean liveCaptureActive;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(UiStyles.color(this, R.color.backgroundPage));

        LinearLayout backBar = new LinearLayout(this);
        backBar.setOrientation(LinearLayout.HORIZONTAL);
        backBar.setBackgroundColor(UiStyles.color(this, R.color.surfaceBar));
        backBar.setPadding(16, 16, 16, 16);
        backBar.setGravity(Gravity.CENTER_VERTICAL);
        Button btnBack = new Button(this);
        btnBack.setText(R.string.cluster_test_back);
        btnBack.setTextColor(UiStyles.color(this, R.color.textPrimary));
        UiStyles.styleOemButton(btnBack, UiStyles.color(this, R.color.buttonPrimary));
        btnBack.setOnClickListener(v -> finish());
        backBar.addView(btnBack);
        root.addView(backBar);

        TextView title = new TextView(this);
        title.setText(R.string.cluster_test_title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTextColor(UiStyles.color(this, R.color.textPrimary));
        title.setPadding(16, 12, 16, 4);
        root.addView(title);

        tvOut = new TextView(this);
        tvOut.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvOut.setTextColor(UiStyles.color(this, R.color.textMuted));
        tvOut.setPadding(16, 0, 16, 8);
        tvOut.setText(R.string.cluster_test_output);
        root.addView(tvOut);

        benchManager = new ClusterVDBusBenchManager(this, this, line -> {
            Log.d(TAG, line);
            runOnUiThread(() -> {
                CharSequence cur = tvOut.getText();
                String next = (cur != null ? cur.toString() : "") + "\n" + line;
                if (next.length() > 4000) {
                    next = next.substring(next.length() - 3500);
                }
                tvOut.setText(next.trim());
            });
            MainActivity.appendBenchLogToMainIfActive(line);
        });
        temperatureTest = new OutsideTemperatureVdbusTest(this, this::appendTemperatureTestLine);
        automationDiagnostics = new ClimateAutomationVdbusDiagnostics(
                this, this::appendAutomationDiagnosticsLine);
        seatMemoryTest = new SeatMemoryVdbusTest(this, this::appendSeatMemoryTestLine);
        vehicleVdbusDiscoveryTest = new VehicleVdbusDiscoveryTest(
                this, this::appendVehicleVdbusDiscoveryLine);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(16, 8, 16, 24);

        int gap = UiStyles.dimenPx(this, R.dimen.spacing_small);
        int btnColor = UiStyles.color(this, R.color.buttonSecondaryMuted);

        addBodyTestSection(col, gap, btnColor);

        addBenchButton(col, "Nav toggle (26/4 yolu)", () -> benchManager.benchNavToggle(), btnColor, gap);
        addBenchButton(col, "Uyarı sesi (26/1)", () -> benchManager.benchAlertTone(), btnColor, gap);
        addBenchButton(col, "Cluster aç (UI / manager)", () -> benchManager.benchClusterOpenUi(), btnColor, gap);
        addBenchButton(col, "Cluster kapat (UI / manager)", () -> benchManager.benchClusterCloseUi(), btnColor, gap);
        addBenchButton(col, "Cluster aç (servis yolu)", () -> benchManager.benchClusterOpenService(), btnColor, gap);
        addBenchButton(col, "Cluster kapat (servis yolu)", () -> benchManager.benchClusterCloseService(), btnColor, gap);

        temperatureTestButton = new Button(this);
        temperatureTestButton.setText(R.string.cluster_test_temperature_button);
        temperatureTestButton.setTextColor(UiStyles.color(this, R.color.textPrimary));
        temperatureTestButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        UiStyles.styleOemButton(temperatureTestButton, UiStyles.color(this, R.color.buttonPrimary));
        temperatureTestButton.setOnClickListener(v -> {
            temperatureTestButton.setEnabled(false);
            temperatureTestButton.setText(R.string.cluster_test_temperature_running);
            if (!temperatureTest.start()) {
                temperatureTestButton.setEnabled(true);
                temperatureTestButton.setText(R.string.cluster_test_temperature_button);
            }
        });
        LinearLayout.LayoutParams temperatureButtonLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        temperatureButtonLp.setMargins(0, 0, 0, gap);
        col.addView(temperatureTestButton, temperatureButtonLp);

        automationDiagnosticsButton = new Button(this);
        automationDiagnosticsButton.setText(R.string.cluster_test_automation_button);
        automationDiagnosticsButton.setTextColor(UiStyles.color(this, R.color.textPrimary));
        automationDiagnosticsButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        UiStyles.styleOemButton(automationDiagnosticsButton,
                UiStyles.color(this, R.color.buttonSecondaryMuted));
        automationDiagnosticsButton.setOnClickListener(v -> {
            automationDiagnosticsButton.setEnabled(false);
            automationDiagnosticsButton.setText(R.string.cluster_test_automation_running);
            automationDiagnostics.collect();
        });
        LinearLayout.LayoutParams automationButtonLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        automationButtonLp.setMargins(0, 0, 0, gap);
        col.addView(automationDiagnosticsButton, automationButtonLp);

        liveAutomationCaptureButton = new Button(this);
        liveAutomationCaptureButton.setText(R.string.cluster_test_live_button);
        liveAutomationCaptureButton.setTextColor(UiStyles.color(this, R.color.textPrimary));
        liveAutomationCaptureButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        UiStyles.styleOemButton(liveAutomationCaptureButton,
                UiStyles.color(this, R.color.buttonSecondaryMuted));
        liveAutomationCaptureButton.setOnClickListener(v -> {
            if (liveCaptureActive) {
                automationDiagnostics.stopLiveCapture();
                liveAutomationCaptureButton.setEnabled(false);
                liveAutomationCaptureButton.setText(R.string.cluster_test_live_stopping);
                return;
            }
            liveCaptureActive = true;
            liveAutomationCaptureButton.setText(R.string.cluster_test_live_stop);
            if (!automationDiagnostics.startLiveCapture()) {
                liveCaptureActive = false;
                liveAutomationCaptureButton.setText(R.string.cluster_test_live_button);
            }
        });
        LinearLayout.LayoutParams liveCaptureLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        liveCaptureLp.setMargins(0, 0, 0, gap);
        col.addView(liveAutomationCaptureButton, liveCaptureLp);

        seatMemoryTestButton = new Button(this);
        seatMemoryTestButton.setText(R.string.cluster_test_seat_memory_button);
        seatMemoryTestButton.setTextColor(UiStyles.color(this, R.color.textPrimary));
        seatMemoryTestButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        UiStyles.styleOemButton(seatMemoryTestButton,
                UiStyles.color(this, R.color.buttonSecondaryMuted));
        seatMemoryTestButton.setOnClickListener(v -> {
            if (seatMemoryTestButton.isSelected()) {
                seatMemoryTest.stop();
                seatMemoryTestButton.setSelected(false);
                seatMemoryTestButton.setText(R.string.cluster_test_seat_memory_button);
            } else {
                seatMemoryTestButton.setSelected(true);
                seatMemoryTestButton.setText(R.string.cluster_test_seat_memory_running);
                if (!seatMemoryTest.start()) {
                    seatMemoryTestButton.setSelected(false);
                    seatMemoryTestButton.setText(R.string.cluster_test_seat_memory_button);
                }
            }
        });
        LinearLayout.LayoutParams seatMemoryLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        seatMemoryLp.setMargins(0, 0, 0, gap);
        col.addView(seatMemoryTestButton, seatMemoryLp);

        vehicleVdbusDiscoveryButton = new Button(this);
        vehicleVdbusDiscoveryButton.setText(R.string.cluster_test_vdbus_button);
        vehicleVdbusDiscoveryButton.setTextColor(UiStyles.color(this, R.color.textPrimary));
        vehicleVdbusDiscoveryButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        UiStyles.styleOemButton(vehicleVdbusDiscoveryButton,
                UiStyles.color(this, R.color.buttonPrimary));
        vehicleVdbusDiscoveryButton.setOnClickListener(v -> {
            if (vehicleVdbusDiscoveryButton.isSelected()) {
                vehicleVdbusDiscoveryTest.stop();
                vehicleVdbusDiscoveryButton.setSelected(false);
                vehicleVdbusDiscoveryButton.setText(R.string.cluster_test_vdbus_button);
            } else {
                vehicleVdbusDiscoveryButton.setSelected(true);
                vehicleVdbusDiscoveryButton.setText(R.string.cluster_test_vdbus_running);
                if (!vehicleVdbusDiscoveryTest.start()) {
                    vehicleVdbusDiscoveryButton.setSelected(false);
                    vehicleVdbusDiscoveryButton.setText(R.string.cluster_test_vdbus_button);
                }
            }
        });
        LinearLayout.LayoutParams vdbusDiscoveryLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        vdbusDiscoveryLp.setMargins(0, 0, 0, gap);
        col.addView(vehicleVdbusDiscoveryButton, vdbusDiscoveryLp);

        scroll.addView(col);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f);
        root.addView(scroll, scrollLp);

        CockpitLegacyStyler.apply(root);
        setContentView(root);
    }

    private void addBodyTestSection(LinearLayout col, int gap, int btnColor) {
        TextView heading = new TextView(this);
        heading.setText("Gövde testi · fake VDBus");
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        heading.setTextColor(UiStyles.color(this, R.color.textPrimary));
        heading.setPadding(0, 0, 0, 2);
        col.addView(heading);

        TextView hint = new TextView(this);
        hint.setText("Yalnızca emülatördeki fake VDBus ControlReceiver'a sinyal gönderir; gerçek araç actuator'ına komut gitmez.");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        hint.setTextColor(UiStyles.color(this, R.color.textMuted));
        hint.setPadding(0, 0, 0, gap);
        col.addView(hint);

        addBodyToggle(col, "Sol ön kapı", open -> FakeVdbusBodyTest.setDoor(
                this, ReadOnlyID.ID_DRIVE_DOOR_STATE, open), btnColor, gap);
        addBodyToggle(col, "Sağ ön kapı", open -> FakeVdbusBodyTest.setDoor(
                this, ReadOnlyID.ID_PASSENGER_DOOR_STATE, open), btnColor, gap);
        addBodyToggle(col, "Sol arka kapı", open -> FakeVdbusBodyTest.setDoor(
                this, ReadOnlyID.ID_LFFT_BEHIND_DOOR_STATE, open), btnColor, gap);
        addBodyToggle(col, "Sağ arka kapı", open -> FakeVdbusBodyTest.setDoor(
                this, ReadOnlyID.ID_RIGHT_BEHIND_DOOR_STATE, open), btnColor, gap);
        addBodyToggle(col, "Sol ön cam", open -> FakeVdbusBodyTest.setWindow(
                this, CarSettingID.ID_CAR_FL_WINDOW, open), btnColor, gap);
        addBodyToggle(col, "Sağ ön cam", open -> FakeVdbusBodyTest.setWindow(
                this, CarSettingID.ID_CAR_FR_WINDOW, open), btnColor, gap);
        addBodyToggle(col, "Sol arka cam", open -> FakeVdbusBodyTest.setWindow(
                this, CarSettingID.ID_CAR_RL_WINDOW, open), btnColor, gap);
        addBodyToggle(col, "Sağ arka cam", open -> FakeVdbusBodyTest.setWindow(
                this, CarSettingID.ID_CAR_RR_WINDOW, open), btnColor, gap);
        addBodyToggle(col, "Bagaj", open -> FakeVdbusBodyTest.setTrunk(this, open),
                btnColor, gap);
        addBodyToggle(col, "Kaput", open -> FakeVdbusBodyTest.setHood(this, open),
                btnColor, gap);
        addBodyToggle(col, "Cam tavan", open -> FakeVdbusBodyTest.setSunroof(this, open),
                btnColor, gap);
        addBodyToggle(col, "Güneşlik", open -> FakeVdbusBodyTest.setSunshade(this, open),
                btnColor, gap);

        bodySpeedButton = new Button(this);
        bodySpeedButton.setText("Hız: 0 km/s");
        bodySpeedButton.setTextColor(UiStyles.color(this, R.color.textPrimary));
        bodySpeedButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        UiStyles.styleOemButton(bodySpeedButton, btnColor);
        bodySpeedButton.setOnClickListener(v -> {
            bodySpeedHigh = !bodySpeedHigh;
            FakeVdbusBodyTest.setSpeed(this, bodySpeedHigh ? 30 : 0);
            bodySpeedButton.setText(bodySpeedHigh ? "Hız: 30 km/s" : "Hız: 0 km/s");
            appendOutputLine("Fake VDBus: hız=" + (bodySpeedHigh ? "30" : "0"));
        });
        LinearLayout.LayoutParams speedLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        speedLp.setMargins(0, 0, 0, gap);
        col.addView(bodySpeedButton, speedLp);

        Button closeAll = new Button(this);
        closeAll.setText("Tüm gövdeyi kapat");
        closeAll.setTextColor(UiStyles.color(this, R.color.textPrimary));
        closeAll.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        UiStyles.styleOemButton(closeAll, UiStyles.color(this, R.color.buttonPrimary));
        closeAll.setOnClickListener(v -> {
            FakeVdbusBodyTest.closeAll(this);
            bodySpeedHigh = false;
            bodySpeedButton.setText("Hız: 0 km/s");
            for (BodyToggle toggle : bodyToggles) {
                toggle.setOpen(false);
            }
            appendOutputLine("Fake VDBus: tüm gövde kapatıldı");
        });
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        closeLp.setMargins(0, 0, 0, gap * 2);
        col.addView(closeAll, closeLp);
    }

    private void addBodyToggle(LinearLayout col, String label, BodyOpenSetter setter,
            int color, int gap) {
        BodyToggle toggle = new BodyToggle(label, setter);
        toggle.button.setText(label + ": kapalı");
        toggle.button.setTextColor(UiStyles.color(this, R.color.textPrimary));
        toggle.button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        UiStyles.styleOemButton(toggle.button, color);
        toggle.button.setOnClickListener(v -> {
            boolean open = !toggle.open;
            toggle.setOpen(open);
            setter.set(open);
            appendOutputLine("Fake VDBus: " + label + "=" + (open ? "açık" : "kapalı"));
        });
        bodyToggles.add(toggle);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, gap);
        col.addView(toggle.button, lp);
    }

    private final class BodyToggle {
        final Button button = new Button(ClusterVDBusTestActivity.this);
        final String label;
        final BodyOpenSetter setter;
        boolean open;

        BodyToggle(String label, BodyOpenSetter setter) {
            this.label = label;
            this.setter = setter;
        }

        void setOpen(boolean open) {
            this.open = open;
            button.setText(label + (open ? ": açık" : ": kapalı"));
        }
    }

    private interface BodyOpenSetter {
        void set(boolean open);
    }

    private void appendTemperatureTestLine(String line) {
        Log.i(TAG, line);
        runOnUiThread(() -> {
            if (tvOut == null || isFinishing() || isDestroyed()) {
                return;
            }
            CharSequence current = tvOut.getText();
            String next = (current != null ? current.toString() : "") + "\n" + line;
            if (next.length() > 8000) {
                next = next.substring(next.length() - 7000);
            }
            tvOut.setText(next.trim());
            MainActivity.appendBenchLogToMainIfActive(line);
            if (line.equals(getString(R.string.cluster_test_temperature_done))) {
                temperatureTestButton.setEnabled(true);
                temperatureTestButton.setText(R.string.cluster_test_temperature_button);
            }
        });
    }

    private void appendAutomationDiagnosticsLine(String line) {
        Log.i(TAG, line);
        runOnUiThread(() -> {
            if (tvOut == null || isFinishing() || isDestroyed()) {
                return;
            }
            appendOutputLine(line);
            MainActivity.appendBenchLogToMainIfActive(line);
            if (line.equals(getString(R.string.cluster_test_automation_done))) {
                automationDiagnosticsButton.setEnabled(true);
                automationDiagnosticsButton.setText(R.string.cluster_test_automation_button);
            }
            if (line.equals(getString(R.string.cluster_test_live_done))) {
                liveCaptureActive = false;
                liveAutomationCaptureButton.setEnabled(true);
                liveAutomationCaptureButton.setText(R.string.cluster_test_live_button);
            }
        });
    }

    private void appendSeatMemoryTestLine(String line) {
        Log.i(TAG, line);
        runOnUiThread(() -> {
            if (tvOut == null || isFinishing() || isDestroyed()) {
                return;
            }
            appendOutputLine(line);
            MainActivity.appendBenchLogToMainIfActive(line);
            if (line.equals(getString(R.string.cluster_test_seat_memory_done))) {
                seatMemoryTestButton.setSelected(false);
                seatMemoryTestButton.setText(R.string.cluster_test_seat_memory_button);
            }
        });
    }

    private void appendVehicleVdbusDiscoveryLine(String line) {
        Log.i(TAG, line);
        runOnUiThread(() -> {
            if (tvOut == null || isFinishing() || isDestroyed()) {
                return;
            }
            appendOutputLine(line);
            MainActivity.appendBenchLogToMainIfActive(line);
            if (line.equals(getString(R.string.cluster_test_vdbus_done))) {
                vehicleVdbusDiscoveryButton.setSelected(false);
                vehicleVdbusDiscoveryButton.setText(R.string.cluster_test_vdbus_button);
            }
        });
    }

    private void appendOutputLine(String line) {
        CharSequence current = tvOut.getText();
        String next = (current != null ? current.toString() : "") + "\n" + line;
        if (next.length() > 8000) {
            next = next.substring(next.length() - 7000);
        }
        tvOut.setText(next.trim());
    }

    private void addBenchButton(LinearLayout col, String label, Runnable r, int color, int gapBottom) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(UiStyles.color(this, R.color.textPrimary));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        UiStyles.styleOemButton(b, color);
        b.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, gapBottom);
        col.addView(b, lp);
    }

    @Override
    protected void onDestroy() {
        if (temperatureTest != null) {
            temperatureTest.stop();
        }
        if (automationDiagnostics != null) {
            automationDiagnostics.stop();
        }
        if (seatMemoryTest != null) {
            seatMemoryTest.shutdown();
        }
        if (vehicleVdbusDiscoveryTest != null) {
            vehicleVdbusDiscoveryTest.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public void navKeyToggleLikeVDBus() {
        MainActivity.benchNavKeyToggle();
    }

    @Override
    public void alertToneLikeVDBus() {
        MainActivity.benchAlertTone();
    }

    @Override
    public void clusterOpenDirectUi() {
        MainActivity.benchClusterOpenDirect();
    }

    @Override
    public void clusterCloseDirectUi() {
        MainActivity.benchClusterCloseDirect();
    }
}
