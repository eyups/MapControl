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
    /** Overlay opaklığı: %20-100 (varsayılan 100). */
    public static final String PREF_OPACITY = "turnSignalCameraOpacityPct";
    /** Overlay boyutu: %50-150 (varsayılan 100). */
    public static final String PREF_SIZE = "turnSignalCameraSizePct";
    public static final int OPACITY_MIN = 20;
    public static final int OPACITY_MAX = 100;
    public static final int SIZE_MIN = 50;
    public static final int SIZE_MAX = 150;
    /** Harita kartlari sinyal kamerasinin onune gecsin mi? (varsayilan: hayir) */
    public static final String PREF_NAV_FRONT = "turnSignalCameraNavFront";
    /** Kamera gorundukten kac saniye sonra harita kartlari one gecsin. */
    public static final String PREF_NAV_DELAY_SEC = "turnSignalCameraNavDelaySec";
    public static final int NAV_DELAY_MIN = 1;
    public static final int NAV_DELAY_MAX = 30;
    public static final int NAV_DELAY_DEFAULT = 5;

    public static int clampOpacity(int pct) {
        return Math.max(OPACITY_MIN, Math.min(OPACITY_MAX, pct));
    }

    public static int clampSize(int pct) {
        return Math.max(SIZE_MIN, Math.min(SIZE_MAX, pct));
    }

    public static int getOpacity(android.content.Context context) {
        return clampOpacity(context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
                .getInt(PREF_OPACITY, OPACITY_MAX));
    }

    public static int getSize(android.content.Context context) {
        return clampSize(context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
                .getInt(PREF_SIZE, 100));
    }

    public static void setOpacity(android.content.Context context, int pct) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit()
                .putInt(PREF_OPACITY, clampOpacity(pct)).apply();
    }

    public static void setSize(android.content.Context context, int pct) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit()
                .putInt(PREF_SIZE, clampSize(pct)).apply();
    }

    public static boolean isNavFrontEnabled(android.content.Context context) {
        return context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
                .getBoolean(PREF_NAV_FRONT, false);
    }

    public static int getNavDelaySec(android.content.Context context) {
        return Math.max(NAV_DELAY_MIN, Math.min(NAV_DELAY_MAX,
                context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
                        .getInt(PREF_NAV_DELAY_SEC, NAV_DELAY_DEFAULT)));
    }

    public static long getNavDelayMs(android.content.Context context) {
        return getNavDelaySec(context) * 1000L;
    }
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
        if (PREF_ENABLED.equals(key)) {
            updateEnabled();
            return;
        }
        if (PREF_OPACITY.equals(key) || PREF_SIZE.equals(key)) {
            if (enabled) overlay.applyPreferences();
            return;
        }
        if (PREF_NAV_FRONT.equals(key) || PREF_NAV_DELAY_SEC.equals(key)) {
            // Yeni z-sirasi/gecikme ayari hemen uygulanabilsin: kamerayi tekrar one al;
            // nav overlay gecikme dolunca kendini tekrar one alir.
            if (enabled) overlay.onNavSettingsChanged();
        }
    }
}
