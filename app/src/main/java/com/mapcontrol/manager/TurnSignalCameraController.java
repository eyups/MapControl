package com.mapcontrol.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import com.mapcontrol.R;
import com.mapcontrol.vehicle.VehicleMetricsRepository;
import com.mapcontrol.vehicle.VehicleMetricsSnapshot;

/** Sinyal durumunu izleyip cluster kamera overlay'ini yönetir. */
public final class TurnSignalCameraController implements VehicleMetricsRepository.Listener,
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "TurnSignalCamera";
    private static final String PREFS = "MapControlPrefs";
    public static final String PREF_ENABLED = "turnSignalCameraEnabled";
    private static final long OFF_HOLD_MS = 750L;
    private static final int CAM_RIGHT = 1;
    private static final int CAM_LEFT = 2;

    private final Context appContext;
    private final SharedPreferences prefs;
    private final VehicleMetricsRepository repository;
    private final TurnSignalCameraOverlay overlay;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable hideTask;

    private boolean started;
    private boolean enabled;
    private boolean hideScheduled;
    private int shownCam = -1;

    public TurnSignalCameraController(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        repository = VehicleMetricsRepository.getBackground(appContext);
        overlay = new TurnSignalCameraOverlay(appContext);
        hideTask = () -> {
            hideScheduled = false;
            overlay.hide();
            shownCam = -1;
        };
    }

    public void start() {
        if (started) return;
        started = true;
        prefs.registerOnSharedPreferenceChangeListener(this);
        updateEnabled();
    }

    public void stop() {
        if (!started) return;
        started = false;
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        repository.removeListener(this);
        main.removeCallbacks(hideTask);
        hideScheduled = false;
        overlay.hide();
        shownCam = -1;
        enabled = false;
    }

    private void updateEnabled() {
        boolean on = prefs.getBoolean(PREF_ENABLED, false);
        if (on == enabled) return;
        enabled = on;
        if (on) {
            repository.addListener(this);
            evaluate();
        } else {
            repository.removeListener(this);
            main.removeCallbacks(hideTask);
            hideScheduled = false;
            overlay.hide();
            shownCam = -1;
        }
    }

    @Override
    public void onMetricsUpdated(VehicleMetricsSnapshot snapshot) {
        if (enabled) evaluate();
    }

    private void evaluate() {
        if (!enabled) return;
        boolean left = isOn(ReadOnlyID.ID_DIRECTION_IND_LEFT)
                || isOn(ReadOnlyID.ID_LH_TURN_LIGHT_STS);
        boolean right = isOn(ReadOnlyID.ID_DIRECTION_IND_RIGHT)
                || isOn(ReadOnlyID.ID_RH_TURN_LIGHT_STS);
        boolean hazard = isOn(ReadOnlyID.ID_HAZARD_LIGHT);

        if (hazard || left == right) {
            scheduleHide();
            return;
        }
        hideScheduled = false;
        main.removeCallbacks(hideTask);
        int cam = left ? CAM_LEFT : CAM_RIGHT;
        shownCam = cam;
        overlay.show(cam, appContext.getString(
                left ? R.string.turn_signal_camera_left : R.string.turn_signal_camera_right));
    }

    private boolean isOn(int readOnlyId) {
        int raw = repository.getCombined(readOnlyId);
        return VehicleMetricsSnapshot.isValid(raw) && raw != 0;
    }

    private void scheduleHide() {
        if (shownCam < 0 || !enabled || hideScheduled) return;
        hideScheduled = true;
        main.postDelayed(hideTask, OFF_HOLD_MS);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        if (PREF_ENABLED.equals(key)) updateEnabled();
    }
}
