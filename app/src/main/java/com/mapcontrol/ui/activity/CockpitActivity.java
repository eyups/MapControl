package com.mapcontrol.ui.activity;

import android.annotation.SuppressLint;
import android.util.Log;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.mapcontrol.BuildConfig;
import com.mapcontrol.cloud.CloudManager;
import com.mapcontrol.driving.DrivingSessionRecorder;
import com.mapcontrol.driving.DrivingTrip;
import com.mapcontrol.profile.DriverProfileManager;
import com.mapcontrol.dvr.DvrController;
import com.mapcontrol.vehicle.VehicleMetricsFormatter;
import com.mapcontrol.vehicle.VehicleMetricsRepository;
import com.mapcontrol.vehicle.VehicleMetricsSnapshot;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


/**
 * Stitch cockpit template. The four primary screens are rendered from the
 * exported Stitch HTML, while feature tabs that are not part of the template
 * are opened in the existing native screen.
 */
public final class CockpitActivity extends AppCompatActivity implements VehicleMetricsRepository.Listener {

    public static final String EXTRA_PAGE = "com.mapcontrol.extra.COCKPIT_PAGE";
    private static final String ASSET_ROOT = "file:///android_asset/stitch/";
    private WebView webView;
    private String currentPage = "home";
    private VehicleMetricsRepository metricsRepository;
    private VehicleMetricsSnapshot metrics;
    private VehicleMetricsFormatter metricsFormatter;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        metricsFormatter = new VehicleMetricsFormatter(this);

        webView = new WebView(this);
        webView.setBackgroundColor(0xFF0B0F14);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                pushVehicleData();
                view.postDelayed(CockpitActivity.this::pushVehicleData, 900);
            }
        });
        webView.addJavascriptInterface(new Bridge(), "Android");
        setContentView(webView);
        hideSystemBars();

        metricsRepository = VehicleMetricsRepository.getInstance(getApplicationContext());
        metricsRepository.addListener(this);
        metricsRepository.refresh();

        String initialPage = getIntent() != null
                ? getIntent().getStringExtra(EXTRA_PAGE) : null;
        loadPage(initialPage == null ? "home" : initialPage);
    }

    private void loadPage(String page) {
        String normalized;
        switch (page == null ? "" : page) {
            case "comfort":
            case "driver":
            case "diagnostics":
                normalized = page;
                break;
            default:
                normalized = "home";
                break;
        }
        currentPage = normalized;
        webView.loadUrl(ASSET_ROOT + normalized + ".html");
    }

    private void openNative(String target) {
        int tab = -1;
        switch (target == null ? "" : target) {
            case "vehicle":
                tab = 9;
                break;
            case "camera":
                tab = 20;
                break;
            case "connectivity":
                tab = 0;
                break;
            case "cloud":
            case "settings":
                tab = 7;
                break;
            case "driving":
                tab = 14;
                break;
            case "driver":
                tab = 13;
                break;
            default:
                break;
        }
        if (tab < 0) {
            return;
        }
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_OPEN_TAB, tab);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }

    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String page = intent != null ? intent.getStringExtra(EXTRA_PAGE) : null;
        if (page != null) {
            loadPage(page);
        }
    }

    @Override
    public void onBackPressed() {
        if (!"home".equals(currentPage)) {
            loadPage("home");
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (metricsRepository != null) {
            metricsRepository.removeListener(this);
        }
        if (webView != null) {
            webView.removeJavascriptInterface("Android");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    public void onMetricsUpdated(VehicleMetricsSnapshot snapshot) {
        metrics = snapshot;
        pushVehicleData();
    }

    private void pushVehicleData() {
        if (webView == null) {
            return;
        }
        String json = buildVehicleJson();
        String js = "if(window.applyCockpitData){window.applyCockpitData(" + json + ");}";
        webView.evaluateJavascript(js, null);
    }

    private String buildVehicleJson() {
        JSONObject o = new JSONObject();
        try {
            VehicleMetricsSnapshot s = metrics;
            boolean connected = s != null && s.connected;
            o.put("connected", connected);
            o.put("time", new SimpleDateFormat("HH:mm", new Locale("tr", "TR")).format(new Date()));
            o.put("timeFull", new SimpleDateFormat("HH:mm:ss", new Locale("tr", "TR")).format(new Date()) + " TRT");
            o.put("appVersion", BuildConfig.VERSION_NAME);

            String range = connected ? clean(metricsFormatter.formatDashboardRange(s)) : "Veri yok";
            String fuel = connected ? clean(metricsFormatter.formatDashboardFuel(s)) : "Veri yok";
            String odo = connected ? clean(metricsFormatter.formatDashboardOdo(s)) : "Veri yok";
            int speed = connected ? s.preferredSpeed() : 0;
            String speedText = connected && speed > 0 ? speed + " km/s" : "Veri yok";
            String speedValue = connected ? String.valueOf(speed) : "Veri yok";
            String tpms;
            if (!connected) {
                tpms = "Veri yok";
            } else if (!VehicleMetricsSnapshot.isValid(s.tirePressureWarning)) {
                tpms = "TPMS Veri yok";
            } else if (s.tirePressureWarning != 0) {
                tpms = "TPMS UYARI";
            } else {
                tpms = "TPMS OK";
            }
            o.put("range", range);
            o.put("rangeValue", stripUnit(range));
            o.put("fuel", fuel);
            o.put("fuelShort", fuel.replace("% ", "%"));
            o.put("odo", odo);
            o.put("odoValue", stripUnit(odo));
            o.put("speed", speedText);
            o.put("speedValue", speedValue);
            o.put("tpms", tpms);
            o.put("tpmsLong", "TPMS OK".equals(tpms) ? "TPMS OK (basınç verisi yok)" : tpms);
            o.put("tire", "Veri yok");
            o.put("outside", "Veri yok");

            String doors;
            String trunk;
            if (!connected) {
                doors = "Veri yok";
                trunk = "Veri yok";
            } else {
                doors = s.isAnyDoorOpen() ? "Kapı açık" : "Kapılar kapalı";
                if (!s.hasTrunk()) trunk = "Veri yok";
                else trunk = s.isTrunkOpen() ? "Bagaj açık" : "Bagaj kapalı";
            }
            o.put("doors", doors);
            o.put("trunk", trunk);
            o.put("system", connected ? "SİSTEMLER HAZIR" : "VERİ YOK");
            o.put("driveMode", connected ? driveModeLabel(s.driveMode) : "Veri yok");

            String driver = "Veri yok";
            try {
                DriverProfileManager.Profile profile = DriverProfileManager.get(this).getActive();
                if (profile != null && profile.name != null && !profile.name.trim().isEmpty()) {
                    driver = profile.name;
                }
            } catch (Throwable ignored) {
            }
            o.put("driver", driver);
            o.put("driverInitials", initials(driver));

            DrivingTrip trip = null;
            try {
                DrivingSessionRecorder recorder = DrivingSessionRecorder.get(this);
                trip = recorder.isActive() ? recorder.getDisplayTrip() : recorder.getLastTrip();
            } catch (Throwable ignored) {
            }
            o.put("score", trip != null && trip.score > 0 ? String.valueOf(trip.score) : "Veri yok");
            o.put("scoreTitle", trip != null && trip.score > 0
                    ? (trip.title == null || trip.title.isEmpty() ? "Sürüş" : trip.title) + " (" + trip.score + " Puan)"
                    : "Veri yok");
            o.put("tripDistance", trip != null && trip.distanceKm > 0
                    ? String.format(Locale.getDefault(), "%.1f km", trip.distanceKm) : "Veri yok");
            o.put("tripConsumption", trip != null && trip.consumptionL100 > 0
                    ? String.format(Locale.getDefault(), "%.1f lt", trip.consumptionL100) : "Veri yok");
            o.put("tripDuration", trip != null && trip.durationSec > 0
                    ? (trip.durationSec / 60) + " dk" : "Veri yok");

            boolean recording = false;
            try {
                recording = DvrController.get(this).isRecording();
            } catch (Throwable ignored) {
            }
            o.put("recording", recording);
            o.put("recordingText", recording ? "● KAYIT DEVAM EDİYOR" : "KAYIT YOK");

            String cloud = "Veri yok";
            try {
                CloudManager cloudManager = CloudManager.get(this);
                if (cloudManager.isRunning()) cloud = "Çalışıyor";
                else if (cloudManager.isPaired()) cloud = "Eşleşti";
            } catch (Throwable ignored) {
            }
            o.put("cloud", cloud);
        } catch (Exception e) {
            // JSON is always valid even if one field is unavailable.
        }
        return o.toString();
    }

    private static String clean(String value) {
        if (value == null || value.trim().isEmpty() || "—".equals(value.trim())) {
            return "Veri yok";
        }
        return value.trim();
    }

    private static String stripUnit(String value) {
        if (value == null || value.endsWith("Veri yok")) return "Veri yok";
        return value.replace(" km", "").replace("%", "").trim();
    }

    private static String driveModeLabel(int mode) {
        switch (mode) {
            case 0: return "Eco";
            case 1: return "Normal";
            case 2: return "Sport";
            case 3: return "Kar";
            case 4: return "Çamur";
            case 5: return "Offroad";
            case 7: return "Kum";
            default: return "Veri yok";
        }
    }

    private static String initials(String name) {
        if (name == null || name.trim().isEmpty() || "Veri yok".equals(name)) return "—";
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) sb.append(Character.toUpperCase(part.charAt(0)));
            if (sb.length() == 2) break;
        }
        return sb.length() == 0 ? "—" : sb.toString();
    }

    private final class Bridge {
        @JavascriptInterface
        public void navigate(String page) {
            runOnUiThread(() -> loadPage(page));
        }

        @JavascriptInterface
        public void openNative(String target) {
            runOnUiThread(() -> CockpitActivity.this.openNative(target));
        }

        @JavascriptInterface
        public String getVehicleData() {
            return buildVehicleJson();
        }
    }
}
