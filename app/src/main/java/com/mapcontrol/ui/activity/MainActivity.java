package com.mapcontrol.ui.activity;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Display;
import android.view.KeyEvent;
import android.view.View;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.desaysv.ivi.vdb.client.bind.VDServiceDef;
import com.desaysv.ivi.vdb.client.listener.VDBindListener;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.base.VDKey;
import com.desaysv.ivi.vdb.event.id.carlan.VDEventCarLan;
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviDisplayArea;
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviDisplayCluster;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayList;
import android.content.pm.PackageInfo;
import android.content.SharedPreferences;
import androidx.core.content.FileProvider;
import android.app.ActivityManager;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoHelper;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;
import com.desaysv.ivi.extra.project.carinfo.NewEnergyID;
import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import android.content.pm.PackageManager;
import com.mapcontrol.R;
import com.mapcontrol.manager.ClusterDisplayManager;
import com.mapcontrol.manager.MapControlVDBusKeyBridge;
import com.mapcontrol.util.AlertSoundHelper;
import com.mapcontrol.util.ApkSessionInstaller;
import com.mapcontrol.manager.WebServerManager;
import com.mapcontrol.service.GlobalBackService;
import com.mapcontrol.service.ServiceInitializer;
import com.mapcontrol.ui.builder.AppsTabBuilder;
import com.mapcontrol.ui.builder.AssistTabBuilder;
import com.mapcontrol.ui.builder.DriveModeTabBuilder;

import com.mapcontrol.service.DmsFaceRecognitionManager;
import com.mapcontrol.cloud.CloudTabBuilder;
import com.mapcontrol.ui.builder.Camera360TabBuilder;
import com.mapcontrol.ui.builder.ClimateAutomationTabBuilder;
import com.mapcontrol.ui.builder.SunroofAutomationTabBuilder;
import com.mapcontrol.ui.builder.DmsFaceRecognitionTabBuilder;
import com.mapcontrol.ui.builder.EqualizerTabBuilder;
import com.mapcontrol.ui.builder.FileUploadTabBuilder;
import com.mapcontrol.ui.builder.HomeTabBuilder;
import com.mapcontrol.ui.builder.LauncherTabBuilder;
import com.mapcontrol.ui.builder.DriverProfilesTabBuilder;
import com.mapcontrol.ui.builder.DrivingHistoryTabBuilder;
import com.mapcontrol.ui.builder.LogTabBuilder;
import com.mapcontrol.ui.builder.ProjectionTabBuilder;
import com.mapcontrol.ui.builder.SettingsTabBuilder;
import com.mapcontrol.ui.builder.SplitScreenTabBuilder;
import com.mapcontrol.ui.builder.SideRailBuilder;
import com.mapcontrol.ui.builder.ValetTabBuilder;
import com.mapcontrol.ui.builder.TopBarBuilder;
import com.mapcontrol.ui.builder.VehicleInfoTabBuilder;
import com.mapcontrol.ui.builder.WelcomeSoundTabBuilder;
import com.mapcontrol.ui.builder.WifiTabBuilder;
import com.mapcontrol.ui.cockpit.CockpitLegacyStyler;
import com.mapcontrol.ui.cockpit.CockpitModal;
import com.mapcontrol.ui.cockpit.CockpitNavModel;
import com.mapcontrol.ui.cockpit.CockpitPageHeader;
import com.mapcontrol.ui.cockpit.CockpitSubTabBar;
import com.mapcontrol.ui.cockpit.CockpitTokens;
import com.mapcontrol.util.DialogHelper;
import com.mapcontrol.util.OnboardingHelper;
import com.mapcontrol.util.DisplayHelper;
import com.mapcontrol.nav.GoogleMapsNavNotificationCoordinator;
import com.mapcontrol.util.ClusterNavigationState;
import com.mapcontrol.util.ImmersiveFullscreenHelper;
import com.mapcontrol.util.LauncherModeManager;
import com.mapcontrol.util.LauncherSwipeStore;
import com.mapcontrol.util.TargetPackageStore;
import com.mapcontrol.vehicle.VehicleMetricsRepository;
import com.mapcontrol.valet.ValetModeManager;
import com.mapcontrol.vehicle.VehicleQuickControls;
import com.mapcontrol.ui.theme.ThemeManager;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.ui.widget.CyberScanlineView;
import com.mapcontrol.ui.widget.MaxWidthFrameLayout;
import com.mapcontrol.ui.widget.SystemStatsOverlay;
import com.mapcontrol.util.SystemLogBuffer;

public class MainActivity extends AppCompatActivity {

    /** Yüzen yansıtma çubuğundan hedef uygulama seçiciyi açmak için {@link Intent} extra anahtarı. */
    public static final String EXTRA_OPEN_PROJECTION_TARGET_PICKER = "com.mapcontrol.extra.OPEN_PROJECTION_TARGET_PICKER";
    public static final String EXTRA_OPEN_LOG_TAB = "com.mapcontrol.extra.OPEN_LOG_TAB";
    private static final String EXTRA_OPEN_SETTINGS_AFTER_THEME = "com.mapcontrol.extra.OPEN_SETTINGS_AFTER_THEME";
    /** Araç Launcher Modu "Ana Ekran" karşılama sekmesinin {@code switchTab} indeksi. */
    private static final int TAB_LAUNCHER = 10;
    private static final int TAB_EQUALIZER = 11;
    private static final int TAB_DRIVER_PROFILES = 13;
    private static final int TAB_DRIVING_HISTORY = 14;
    private static final int TAB_VALET = 16;
    private static final int TAB_HOME = 17;
    public static final int TAB_CLIMATE_AUTOMATION = 18;
    public static final int TAB_FACE_RECOGNITION = 19;
    public static final int TAB_CAMERA_360 = 20;
    public static final int TAB_SPLIT_SCREEN = 21;
    public static final int TAB_SUNROOF_AUTOMATION = 22;
    private ScrollView climateAutomationScrollView;
    private ScrollView faceRecognitionScrollView;
    private ScrollView sunroofAutomationScrollView;
    private ClimateAutomationTabBuilder climateAutomationTabBuilder;
    private SunroofAutomationTabBuilder sunroofAutomationTabBuilder;
    private DmsFaceRecognitionTabBuilder dmsFaceRecognitionTabBuilder;
    private boolean faceRecognitionStatusBroadcastRegistered;
    private final BroadcastReceiver faceRecognitionStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (dmsFaceRecognitionTabBuilder != null) {
                dmsFaceRecognitionTabBuilder.refresh();
            }
        }
    };
    private TextView tvLogs;
    private ScrollView scrollView;
    private final Handler systemLogHandler = new Handler(Looper.getMainLooper());
    private final SystemLogBuffer.Listener systemLogListener = text ->
            systemLogHandler.post(() -> renderSystemLogs(text));
    private Handler handler;
    private volatile boolean isNavigationOpen = false; // Navigasyon durumu
    private String targetPackage = ""; // Seçilen uygulama paketi
    private ClusterDisplayManager clusterDisplayManager;
    private TextView targetAppLabel; // Seçilen uygulamayı gösteren TextView
    private FrameLayout tabContentArea; // Tab içerik alanı
    private LinearLayout settingsTabContent; // Ayarlar tab içeriği
    private ScrollView settingsScrollView; // Ayarlar tab ScrollView
    private LinearLayout projectionTabContent; // Yansıtma tab içeriği
    private ScrollView projectionScrollView; // Yansıtma tab ScrollView
    private LinearLayout wifiTabContent; // Wi-Fi tab içeriği
    private WifiTabBuilder wifiTabBuilder;
    private LinearLayout logTabContent; // LOG tab içeriği
    private LinearLayout appsTabContent; // Uygulamalar tab içeriği
    private AppsTabBuilder appsTabBuilder;
    private LinearLayout driveModeTabContent; // Hafıza Modu tab içeriği
    private ScrollView driveModeScrollView; // Hafıza Modu tab ScrollView
    private LinearLayout fileUploadTabContent; // Web Yönetimi tab içeriği
    private ScrollView fileUploadScrollView; // Web Yönetimi tab ScrollView
    private FileUploadTabBuilder fileUploadTabBuilder;
    private WelcomeSoundTabBuilder welcomeSoundTabBuilder;
    private final ActivityResultLauncher<Intent> welcomeAudioPicker =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (welcomeSoundTabBuilder != null) {
                            welcomeSoundTabBuilder.onAudioPicked(result);
                        }
                    });
    private EqualizerTabBuilder equalizerTabBuilder;
    private DriverProfilesTabBuilder driverProfilesTabBuilder;
    private VehicleInfoTabBuilder vehicleInfoTabBuilder;
    private DrivingHistoryTabBuilder drivingHistoryTabBuilder;
    private ScrollView drivingHistoryScrollView;
    private ValetTabBuilder valetTabBuilder;
    private HomeTabBuilder homeTabBuilder;
    private ScrollView homeScrollView;
    private VehicleMetricsRepository vehicleMetricsRepository;
    private ScrollView vehicleInfoScrollView;
    private ScrollView launcherScrollView; // Araç Launcher Modu "Ana Ekran" karşılama sekmesi
    private LauncherTabBuilder launcherTabBuilder;
    private SettingsTabBuilder settingsTabBuilder;
    private DriveModeTabBuilder driveModeTabBuilder;
    private AssistTabBuilder assistTabBuilder;
    private LogTabBuilder logTabBuilder;
    private Camera360TabBuilder camera360TabBuilder;
    private android.view.View camera360TabContent;
    private SplitScreenTabBuilder splitScreenTabBuilder;
    private CloudTabBuilder roadmateTabBuilder;
    /** Bu görev, Android'in Ana Ekran (HOME) isteğiyle mi başlatıldı? */
    private boolean launchedAsHome;
    private int currentTab = 17; // 17=Ana Sayfa, 0=Wi-Fi, 1=Web, 2=Profil, 3=Yansıtma, 4=LOG, 5=Uygulamalar, 6=Hafıza, 7=Ayarlar, 8=Açılış Sesi, 9=Araç Bilgi, 10=Ana Ekran (Launcher), 11=Ekolayzer
    private WebServerManager webServerManager; // HTTP Server Manager
    private Button btnWebServerToggle; // Web Server aç/kapat butonu
    private TextView webServerStatusText; // Web Server durum metni
    private android.widget.ImageView qrCodeImageView; // QR kod görseli
    private ServiceInitializer serviceInitializer;
    private SideRailBuilder sideRailBuilder;
    /** {@link ClusterVDBusTestActivity} bench — yalnızca görünürken; {@link #onResume()} atanır. */
    private static volatile MainActivity sBenchHost;
    /** Oturumda LOG sekmesine ilk geçişte hoşgeldin TTS bir kez */
    private boolean logWelcomeTtsDone;
    private TextToSpeech systemTts;
    private String pendingTtsText;
    private LinearLayout topBarButtonsContainer; // Üst bar'daki buton container'ı (dinamik)
    private TextView topBarTitle; // Üst bar başlığı (dinamik)
    private FrameLayout mainRootContainer;
    private SystemStatsOverlay systemStatsOverlay;
    private TextView cameraRecordingBadge;
    private final Runnable cameraRecordingBadgeTicker = new Runnable() {
        @Override
        public void run() {
            updateCameraRecordingBadge();
            if (handler != null) {
                handler.postDelayed(this, 1000);
            }
        }
    };
    /** Gece/gündüz uiMode — Activity recreate etmeden soft tema yenilemek için. */
    private int lastNightModeUiBits = -1;
    /** Son uygulanan yön; configChanges orientation activity'yi yeniden kurmaz. */
    private int lastOrientation = -1;
    private LinearLayout shellHost;
    private boolean portraitShell;
    private LinearLayout sideRail; // Sol kenar çubuğu kök view'ı (Launcher modunda gizlenebilir)
    private LinearLayout mainContent; // Ana içerik kök view'ı (Launcher modunda tam genişlik olur)
    private LinearLayout.LayoutParams mainContentParams;
    private int sidebarWidthPx;
    private boolean contentWide;
    private int screenWidthPx;
    private TopBarBuilder topBarBuilder;
    private CockpitSubTabBar subTabBar;
    private boolean railExpanded;
    /** Menü yumuşatması: içerik alfa’sı değil, tema renkli örtü. İptal edilen geçiş burada durur. */
    private final IdentityHashMap<View, ValueAnimator> transitionVeils = new IdentityHashMap<>();
    private ProjectionTabBuilder projectionTabBuilder;
    /** Yüzen kontrolden veya tekilleştirilmiş intent ile hedef uygulama diyaloğu ertelenmiş. */
    private boolean deferredOpenProjectionTargetPicker;
    private boolean targetPackageBroadcastRegistered;
    private boolean navigationClusterBroadcastRegistered;
    private boolean googleMapsNavBroadcastRegistered;
    private final BroadcastReceiver navigationClusterStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null
                    || !ClusterNavigationState.ACTION_NAVIGATION_CLUSTER_STATE.equals(intent.getAction())) {
                return;
            }
            boolean open = intent.getBooleanExtra(ClusterNavigationState.EXTRA_IS_OPEN, false);
            if (handler != null) {
                handler.post(() -> applyNavigationClusterOpenFromBus(open));
            } else {
                applyNavigationClusterOpenFromBus(open);
            }
        }
    };
    private final BroadcastReceiver googleMapsNavSnapshotReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null
                    || !GoogleMapsNavNotificationCoordinator.ACTION_GOOGLE_MAPS_NAV_SNAPSHOT
                            .equals(intent.getAction())) {
                return;
            }
            Runnable apply = () -> {
                if (projectionTabBuilder != null) {
                    projectionTabBuilder.updateGoogleMapsNavSummary(
                            GoogleMapsNavNotificationCoordinator.getLastSnapshot());
                }
            };
            if (handler != null) {
                handler.post(apply);
            } else {
                apply.run();
            }
        }
    };
    private final BroadcastReceiver targetPackageUpdatedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (handler != null) {
                handler.post(() -> {
                    loadTargetPackage();
                    updateTargetLabel();
                });
            }
        }
    };

    private View launchSplashRoot;
    private ApkSessionInstaller.Listener apkSessionListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);

        handler = new Handler(Looper.getMainLooper());
        launchedAsHome = LauncherModeManager.isHomeIntent(getIntent());
        if (savedInstanceState == null && getIntent() != null
                && getIntent().getBooleanExtra(EXTRA_OPEN_PROJECTION_TARGET_PICKER, false)) {
            deferredOpenProjectionTargetPicker = true;
            getIntent().removeExtra(EXTRA_OPEN_PROJECTION_TARGET_PICKER);
        }
        ApkSessionInstaller.handleSessionResultIntent(this, getIntent());

        // Cold start: önce splash boyansın — initializeApp uzun sürer, siyah frame olmasın
        launchSplashRoot = DisplayHelper.createAppLaunchSplashView(this);
        setContentView(launchSplashRoot);

        SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
        boolean disclaimerAccepted = prefs.getBoolean("disclaimerAccepted", false);

        if (!disclaimerAccepted) {
            handler.post(this::showLegalDisclaimer);
        } else if (!prefs.getBoolean(OnboardingHelper.PREF_KEY_COMPLETED, false)) {
            handler.post(this::showOnboarding);
        } else {
            handler.post(this::initializeApp);
        }
    }

    private boolean vdbusBridgeAcquired;

    private void showOnboarding() {
        DisplayHelper.stopAppLaunchSplashAnimations();
        launchSplashRoot = OnboardingHelper.createView(this, () -> {
            getSharedPreferences("MapControlPrefs", MODE_PRIVATE).edit()
                    .putBoolean(OnboardingHelper.PREF_KEY_COMPLETED, true).apply();
            initializeApp();
        });
        setContentView(launchSplashRoot);
    }

    /**
     * Yasal uyarı ve onay ekranını gösterir
     */
    private void showLegalDisclaimer() {
        DialogHelper.showLegalDisclaimer(this, () -> {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("disclaimerAccepted", true);
            editor.apply();
            showOnboarding();
        }, this::finish);
    }

    /**
     * Uygulama Yönetimi için yasal uyarı ve onay ekranını gösterir
     */
    private void showAppManagementDisclaimer() {
        DialogHelper.showAppManagementDisclaimer(this, () -> {
            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            prefs.edit().putBoolean("appManagementDisclaimerAccepted", true).apply();
            switchTab(5);
            if (topBarTitle != null) topBarTitle.setText(R.string.topbar_app_management);
            if (sideRailBuilder != null) {
                sideRailBuilder.setSelectionForTabIndex(5);
            }
        }, () -> {});
    }

    /**
     * Uygulamayı başlatır (onCreate'in geri kalanı)
     */
    private void initializeApp() {
        // SharedPreferences (tüm bölümler için ortak)
        SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
        railExpanded = prefs.getBoolean(CockpitNavModel.PREF_RAIL_EXPANDED, false);
        
        
        // SharedPreferences'tan targetPackage'ı yükle
        loadTargetPackage();

        clusterDisplayManager = new ClusterDisplayManager(this,
                new ClusterDisplayManager.ClusterCallback() {
                    @Override
                    public void onNavigationStateChanged(boolean isOpen) {
                        isNavigationOpen = isOpen;
                        ClusterNavigationState.setLastKnownOpen(isOpen);
                        if (projectionTabBuilder != null) {
                            projectionTabBuilder.refreshProjectionStatusUi();
                        }
                    }

                    @Override
                    public String getTargetPackage() {
                        return targetPackage;
                    }

                    @Override
                    public void log(String message) {
                        MainActivity.this.log(message);
                    }
                });
        isNavigationOpen = ClusterNavigationState.getLastKnownOpen();

        // Servisleri başlat ve log alıcıyı kaydet
        serviceInitializer = new ServiceInitializer(this,
                new ServiceInitializer.ServiceCallback() {
                    @Override
                    public void onLogReceived(String message) {
                        MainActivity.this.log(message);
                    }

                    @Override
                    public void log(String msg) {
                        MainActivity.this.log(msg);
                    }
                });
        serviceInitializer.onCreate();
        

        // FrameLayout (Ana container - overlay için)
        mainRootContainer = new FrameLayout(this);
        mainRootContainer.setBackgroundColor(CockpitTokens.color(this, R.color.ck_bg_base));

        // Sol sabit kenar çubuğu (Builder)
        sideRailBuilder = new SideRailBuilder(this, prefs,
                new SideRailBuilder.SideRailCallback() {
                    @Override
                    public void onTabSelected(int tabIndex, String title) {
                        crossfadeContent(() -> {
                            switchTab(tabIndex);
                            if (topBarTitle != null) {
                                topBarTitle.setText(CockpitNavModel.title(MainActivity.this, tabIndex));
                            }
                            if (tabIndex == TAB_DRIVING_HISTORY && drivingHistoryTabBuilder != null) {
                                drivingHistoryTabBuilder.onTabVisible();
                            }
                        });
                    }

                    @Override
                    public void onAppManagementRequested() {
                        showAppManagementDisclaimer();
                    }

                    @Override
                    public void log(String msg) {
                        MainActivity.this.log(msg);
                    }

                    @Override
                    public void onRailWidthChanged(boolean expanded) {
                        applyRailExpanded(expanded);
                    }
                });

        // Ana içerik alanı (yatayda sağ kolon, dikeyde şeridin altı)
        LinearLayout mainContent = new LinearLayout(this);
        mainContent.setOrientation(LinearLayout.VERTICAL);
        mainContent.setBackgroundColor(UiStyles.color(this, R.color.transparent));
        this.mainContent = mainContent;
        
        // Üst başlık bar (Builder)
        topBarBuilder = new TopBarBuilder(this,
                new TopBarBuilder.TopBarCallback() {
                    @Override
                    public void onLogTabToggle(boolean show) {
                        if (logTabContent != null) {
                            logTabContent.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
                        }
                        if (show) {
                            switchTab(4);
                        } else if (currentTab == 4) {
                            switchTab(TAB_HOME);
                        }
                    }

                    @Override
                    public void log(String msg) {
                        MainActivity.this.log(msg);
                    }
                });
        LinearLayout topBar = topBarBuilder.build();
        topBarTitle = topBarBuilder.getTitleView();
        topBarButtonsContainer = subTabBar != null ? subTabBar.getActionHost()
                : topBarBuilder.getButtonsContainer();
        topBarBuilder.setLauncherBackButtonListener(v -> returnToLauncherHome());
        
        mainContent.addView(topBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        subTabBar = new CockpitSubTabBar(this, prefs, this::onSubTabSelected);
        mainContent.addView(subTabBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                CockpitTokens.dim(this, R.dimen.ck_subtab_h)));
        topBarButtonsContainer = subTabBar.getActionHost();

        // Tab içerik alanı (FrameLayout: launcher park ederken SurfaceView GONE olmasın)
        tabContentArea = new FrameLayout(this);
        tabContentArea.setPadding(0, 0, 0, 0);
        tabContentArea.setBackgroundColor(0x00000000);
        LinearLayout.LayoutParams tabContentParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f);
        mainContent.addView(tabContentArea, tabContentParams);
        mountShell(isPortraitConfiguration());
        
        // WebServerManager'ı başlat
        webServerManager = new WebServerManager(this);
        webServerManager.setListener(new WebServerManager.WebServerListener() {
            @Override
            public void onServerStarted(int port, String localIp) {
                handler.post(() -> {
                    String serverUrl = "http://" + localIp + ":" + port;
                    if (webServerStatusText != null) {
                        webServerStatusText.setText(serverUrl);
                        webServerStatusText.setTextColor(UiStyles.color(MainActivity.this, R.color.accentHighlight));
                    }
                    if (btnWebServerToggle != null) {
                        btnWebServerToggle.setText(R.string.web_server_stop);
                    }
                    // QR kod oluştur
                    if (fileUploadTabBuilder != null) fileUploadTabBuilder.generateQRCode(serverUrl);
                    log("Web Server başlatıldı: " + serverUrl);
                });
            }

            @Override
            public void onServerStopped() {
                handler.post(() -> {
                    if (webServerStatusText != null) {
                        webServerStatusText.setText(R.string.web_server_stopped);
                        webServerStatusText.setTextColor(UiStyles.color(MainActivity.this, R.color.textDialogButtonSecondary));
                    }
                    if (fileUploadTabBuilder != null) {
                        fileUploadTabBuilder.setQrVisible(false);
                    }
                    if (btnWebServerToggle != null) {
                        btnWebServerToggle.setText(R.string.web_server_start);
                    }
                    log("Web Server durduruldu");
                });
            }

            @Override
            public void onError(String error) {
                handler.post(() -> {
                    if (webServerStatusText != null) {
                        webServerStatusText.setText(getString(R.string.common_error_prefix, error));
                        webServerStatusText.setTextColor(UiStyles.color(MainActivity.this, R.color.statusErrorBright));
                    }
                    log("Web Server hatası: " + error);
                });
            }

            @Override
            public void onInstallApk(String fileName) {
                handler.post(() -> {
                    if (appsTabBuilder != null) appsTabBuilder.installApkFile(fileName);
                });
            }

            @Override
            public void onDeleteApp(String packageName) {
                handler.post(() -> {
                    if (appsTabBuilder != null) appsTabBuilder.deleteApp(packageName);
                });
            }

            @Override
            public void onLaunchApp(String packageName) {
                handler.post(() -> {
                    if (appsTabBuilder != null) appsTabBuilder.launchApp(packageName);
                });
            }

            @Override
            public void onLog(String message) {
                handler.post(() -> {
                    log(message);
                });
            }

            @Override
            public void onOpenMapUrl(String url) {
                handler.post(() -> {
                    try {
                        String u = url != null ? url.trim() : "";
                        if (u.isEmpty()) {
                            return;
                        }
                        Uri uri = Uri.parse(u);
                        Intent i = new Intent(Intent.ACTION_VIEW, uri);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        String scheme = uri.getScheme();
                        if (scheme != null
                                && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                            i.addCategory(Intent.CATEGORY_BROWSABLE);
                        }
                        if (shouldShowChooserToPickBrowserOrMaps(uri)) {
                            // Yandex /maps, goo.gl harita kısası: önce tek hedef uyg. sessizce çalışıp
                            // ekran göstermeyebiliyor; listeden tarayıcı seçilebilir.
                            Intent chooser = Intent.createChooser(i, "Linki aç (tarayıcı önerilir)");
                            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(chooser);
                        } else {
                            try {
                                startActivity(i);
                            } catch (android.content.ActivityNotFoundException noHandler) {
                                Intent chooser = Intent.createChooser(i, "Bağlantıyı aç");
                                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(chooser);
                            }
                        }
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this,
                                "Link açılamadı: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        log("Link açma hatası: " + e.getMessage());
                    }
                });
            }

            @Override
            public void onTypeKeyboardText(String text, boolean showDeviceFeedback) {
                handler.post(() -> {
                    if (!GlobalBackService.isRegisteredInSystemAccessibilitySettings(MainActivity.this)) {
                        if (showDeviceFeedback) {
                            Toast.makeText(MainActivity.this,
                                    "Ayarlar > Erişilebilirlik: Global Back servisini açın; diğer uygulamada girdi alanının odaklı olması gerekir.",
                                    Toast.LENGTH_LONG).show();
                        }
                        return;
                    }
                    if (GlobalBackService.typeIntoFocusedField(MainActivity.this, text)) {
                        if (showDeviceFeedback) {
                            Toast.makeText(MainActivity.this, R.string.web_text_sent, Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        if (showDeviceFeedback) {
                            Toast.makeText(MainActivity.this,
                                    "Metin eklenemedi. Hedef uygulamada arama/Metin alanına bir kez dokunup odağın açık olduğundan emin olun (bazı uygulamalar ağaçta desteklemez).",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        });

        // Web Yönetimi tab içeriği
        fileUploadTabBuilder = new FileUploadTabBuilder(this, webServerManager);
        fileUploadScrollView = fileUploadTabBuilder.getScrollView();
        btnWebServerToggle = fileUploadTabBuilder.getToggleButton();
        webServerStatusText = fileUploadTabBuilder.getStatusText();
        qrCodeImageView = fileUploadTabBuilder.getQrImageView();
        fileUploadTabContent = fileUploadTabBuilder.getFileUploadTabContent();

        // Yansıtma tab içeriği (Builder)
        projectionTabBuilder = new ProjectionTabBuilder(this, prefs,
                new ProjectionTabBuilder.ProjectionCallback() {
                    @Override
                    public void onOpenCluster() {
                        clusterDisplayManager.openClusterDisplay();
                    }

                    @Override
                    public void onCloseCluster() {
                        clusterDisplayManager.closeClusterDisplay(true);
                    }

                    @Override
                    public void onSavePowerMode(int mode) {
                        savePrefInt("powerModeSetting", mode);
                    }

                    @Override
                    public void onStartKeyEventListener() {
                        MapControlVDBusKeyBridge.start();
                    }

                    @Override
                    public void onStopKeyEventListener() {
                        MapControlVDBusKeyBridge.stop();
                    }

                    @Override
                    public String getTargetPackage() {
                        return targetPackage;
                    }

                    @Override
                    public void onTargetPackageSelected(String packageName) {
                        saveTargetPackage(packageName);
                        updateTargetLabel();
                        log("Seçilen uygulama: " + targetPackage);
                    }

                    @Override
                    public boolean isSystemOrPrivApp(ApplicationInfo appInfo) {
                        return MainActivity.this.isSystemOrPrivApp(appInfo);
                    }

                    @Override
                    public void log(String msg) {
                        MainActivity.this.log(msg);
                    }

                    @Override
                    public void onBringToMainDisplayCheckClusterSplash() {
                        if (clusterDisplayManager != null) {
                            clusterDisplayManager.showBootSplashOnClusterIfNoForegroundApp();
                        }
                    }
                });
        projectionScrollView = projectionTabBuilder.getScrollView();
        projectionTabContent = projectionTabBuilder.getProjectionTabContent();
        targetAppLabel = projectionTabBuilder.getTargetAppLabel();
        tryConsumeDeferredProjectionTargetPicker();

        // === HAFIZA MODU TAB İÇERİĞİ ===
        driveModeTabBuilder = new DriveModeTabBuilder(this, prefs,
                new DriveModeTabBuilder.DriveModeCallback() {
                    @Override
                    public void onModeSelected(int modeValue) {
                        savePrefInt("driveModeSetting", modeValue);
                        log("Hafıza modu kaydedildi: " + modeValue);
                    }

                    @Override
                    public void log(String msg) {
                        MainActivity.this.log(msg);
                    }
                });
        driveModeScrollView = driveModeTabBuilder.getScrollView();
        driveModeTabContent = driveModeTabBuilder.getTabContent();

        assistTabBuilder = new AssistTabBuilder(this, prefs,
                new AssistTabBuilder.AssistCallback() {
                    @Override
                    public void onSettingChanged(String key, int value) {
                        saveAssistSetting(key, value);
                    }

                    @Override
                    public void onSafetyWarningRequired(String key, int value, Runnable onUserConfirmed) {
                        DialogHelper.showSafetyWarningDialog(MainActivity.this, key, value, onUserConfirmed);
                    }

                    @Override
                    public void log(String message) {
                        MainActivity.this.log(message);
                    }
                });


        wifiTabBuilder = new WifiTabBuilder(this, msg -> MainActivity.this.log(msg));
        wifiTabContent = wifiTabBuilder.getTabContent();

        logTabBuilder = new LogTabBuilder(this, new LogTabBuilder.LogCallback() {
            @Override
            public void onClearLogs() {
                SystemLogBuffer.clear();
                if (logTabBuilder != null && logTabBuilder.getLogsTextView() != null) {
                    logTabBuilder.getLogsTextView().setText("");
                }
                log("Loglar temizlendi");
            }

            @Override
            public void log(String msg) {
                MainActivity.this.log(msg);
            }

            @Override
            public void onReadAloud(String text) {
                speakTtsText(text);
            }

            @Override
            public void onWelcomeTts() {
                speakTtsText(getString(R.string.log_tts_welcome_phrase));
            }
        });
        logTabContent = logTabBuilder.getTabContent();
        tvLogs = logTabBuilder.getLogsTextView();
        SystemLogBuffer.addListener(systemLogListener);
        renderSystemLogs(SystemLogBuffer.snapshot());
        scrollView = logTabBuilder.getScrollView();

        welcomeSoundTabBuilder = new WelcomeSoundTabBuilder(this, prefs, welcomeAudioPicker);
        equalizerTabBuilder = new EqualizerTabBuilder(this);
        valetTabBuilder = new ValetTabBuilder(this, msg -> MainActivity.this.log(msg),
                this::onValetStateChanged);
        driverProfilesTabBuilder = new DriverProfilesTabBuilder(this, msg -> MainActivity.this.log(msg),
                this::onDriverProfileChanged);
        camera360TabBuilder = new Camera360TabBuilder(this, () -> {
            try {
                if (vehicleMetricsRepository == null) return 0;
                // Alinacak hiz: DisVehSpd (gosterge hizi); yedek ActVehSpd.
                int g = vehicleMetricsRepository.getCombined(
                        com.desaysv.ivi.extra.project.carinfo.ReadOnlyID.ID_SPEED_GAUGE_DISPLAY);
                if (com.mapcontrol.vehicle.VehicleMetricsSnapshot.isValid(g)) {
                    return Math.max(g, 0);
                }
                int c = vehicleMetricsRepository.getCombined(
                        com.desaysv.ivi.extra.project.carinfo.ReadOnlyID.ID_CAR_SPEED);
                return com.mapcontrol.vehicle.VehicleMetricsSnapshot.isValid(c) ? Math.max(c, 0) : 0;
            } catch (Throwable t) {
                return 0;
            }
        }, () -> {
            // Vites: ID_GEARBOX_STATE (1=P 2=R 3=N, 4.. = ileri vites 1.. -> D1..)
            try {
                if (vehicleMetricsRepository == null) return "";
                int g = vehicleMetricsRepository.getCombined(
                        com.desaysv.ivi.extra.project.carinfo.ReadOnlyID.ID_GEARBOX_STATE);
                if (!com.mapcontrol.vehicle.VehicleMetricsSnapshot.isValid(g) || g <= 0) return "";
                switch (g) {
                    case 1: return "P";
                    case 2: return "R";
                    case 3: return "N";
                    default: return "D" + (g - 3);
                }
            } catch (Throwable t) {
                return "";
            }
        });
        camera360TabContent = camera360TabBuilder.getTabContent();
        camera360TabBuilder.setWideListener(this::setContentWide);

        splitScreenTabBuilder = new SplitScreenTabBuilder(this,
                msg -> MainActivity.this.log(msg));
        roadmateTabBuilder = new CloudTabBuilder(this, msg -> MainActivity.this.log("[Roadmate] " + msg));

        climateAutomationTabBuilder = new ClimateAutomationTabBuilder(this, prefs,
                msg -> MainActivity.this.log(msg));
        rebuildClimateAutomationTab();

        dmsFaceRecognitionTabBuilder = new DmsFaceRecognitionTabBuilder(this, prefs,
                msg -> MainActivity.this.log(msg));
        faceRecognitionScrollView = new ScrollView(this);
        faceRecognitionScrollView.setFillViewport(true);
        faceRecognitionScrollView.addView(dmsFaceRecognitionTabBuilder.build(),
                new ScrollView.LayoutParams(
                        ScrollView.LayoutParams.MATCH_PARENT,
                        ScrollView.LayoutParams.WRAP_CONTENT));

        vehicleMetricsRepository = new VehicleMetricsRepository(getApplicationContext());
        // Onbellek yalnizca dinleyici varken doluyordu; hiz overlay'i (ve launcher)
        // icin izlemeyi bastan acik tut (VDBus olay gudumlu, hafif).
        vehicleMetricsRepository.startMonitoring();

        homeTabBuilder = new HomeTabBuilder(this, vehicleMetricsRepository,
                new HomeTabBuilder.HomeCallback() {
                    @Override
                    public void onNavigate(int tabIndex, String title) {
                        switchTab(tabIndex);
                        if (topBarTitle != null && title != null) {
                            topBarTitle.setText(title);
                        }
                        if (sideRailBuilder != null) {
                            sideRailBuilder.setSelectionForTabIndex(tabIndex);
                        }
                    }

                    @Override
                    public void log(String message) {
                        MainActivity.this.log(message);
                    }
                });
        homeScrollView = homeTabBuilder.getScrollView();
        vehicleInfoTabBuilder = new VehicleInfoTabBuilder(this, vehicleMetricsRepository,
                msg -> MainActivity.this.log(msg));
        vehicleInfoScrollView = vehicleInfoTabBuilder.getScrollView();
        drivingHistoryTabBuilder = new DrivingHistoryTabBuilder(this);
        drivingHistoryScrollView = drivingHistoryTabBuilder.getScrollView();

        appsTabBuilder = new AppsTabBuilder(this, new AppsTabBuilder.AppsCallback() {
            @Override
            public boolean isSystemOrPrivApp(ApplicationInfo appInfo) {
                return MainActivity.this.isSystemOrPrivApp(appInfo);
            }

            @Override
            public boolean isSystemOrPrivApp(String packageName) {
                return MainActivity.this.isSystemOrPrivApp(packageName);
            }

            @Override
            public void log(String msg) {
                MainActivity.this.log(msg);
            }
        });
        appsTabContent = MainActivity.this.appsTabBuilder.getTabContent();
        apkSessionListener = (status, message) -> handler.post(() -> onApkSessionStatus(status, message));
        ApkSessionInstaller.setListener(apkSessionListener);

        // Ayarlar tab içeriği (Builder) — initializeApp içinde bir kez oluşturulur
        settingsTabBuilder = new SettingsTabBuilder(this, prefs,
                new SettingsTabBuilder.SettingsCallback() {
                    @Override
                    public void log(String msg) {
                        MainActivity.this.log(msg);
                    }

                    @Override
                    public String getCarToken() {
                        return prefs.getString("cloudDeviceToken", null);
                    }

                    @Override
                    public void onLauncherModeChanged(boolean enabled) {
                        crossfadeLauncherTransition(() -> {
                            if (enabled) {
                                enterLauncherMode();
                            } else {
                                exitLauncherMode();
                            }
                        });
                    }

                    @Override
                    public void onLocaleChanged() {
                        MainActivity.this.recreate();
                    }

                    @Override
                    public void onThemeChanged() {
                        try {
                            com.mapcontrol.manager.FloatingBackButtonManager
                                    .getInstance(MainActivity.this).refreshTheme();
                        } catch (RuntimeException ignored) {
                        }
                        getIntent().putExtra(EXTRA_OPEN_SETTINGS_AFTER_THEME, true);
                        MainActivity.this.recreate();
                    }

                    @Override
                    public void onSystemStatsChanged(boolean enabled) {
                        if (systemStatsOverlay != null) {
                            systemStatsOverlay.setEnabled(enabled);
                        }
                    }
                });
        settingsScrollView = settingsTabBuilder.getScrollView();
        settingsTabContent = settingsTabBuilder.getSettingsTabContent();

        // Araç Launcher Modu "Ana Ekran" karşılama sekmesi (Builder) — mevcut switchTab akışını kullanır
        launcherTabBuilder = new LauncherTabBuilder(this, vehicleMetricsRepository,
                new LauncherTabBuilder.LauncherCallback() {
                    @Override
                    public void onShortcutSelected(int tabIndex, String title) {
                        crossfadeLauncherTransition(() -> {
                            switchTab(tabIndex);
                            if (topBarTitle != null) topBarTitle.setText(title);
                            if (sideRailBuilder != null) sideRailBuilder.setSelectionForTabIndex(tabIndex);
                        });
                    }

                    @Override
                    public void onExitLauncherRequested() {
                        crossfadeLauncherTransition(() -> exitLauncherMode());
                    }

                    @Override
                    public void onAppLaunchRequested(String packageName) {
                        if (appsTabBuilder != null) {
                            appsTabBuilder.launchApp(packageName);
                        }
                    }

                    @Override
                    public void onClusterProjectionRequested(String action) {
                        if (clusterDisplayManager == null) {
                            return;
                        }
                        if (LauncherSwipeStore.ACTION_CLUSTER_CLOSE.equals(action)
                                || (LauncherSwipeStore.ACTION_CLUSTER_TOGGLE.equals(action)
                                && isNavigationOpen)) {
                            clusterDisplayManager.closeClusterDisplay(false);
                        } else {
                            clusterDisplayManager.openClusterDisplay();
                        }
                    }
                });
        launcherScrollView = launcherTabBuilder.build();

        presentMainUi(mainRootContainer);
        lastOrientation = getResources().getConfiguration().orientation;
        lastNightModeUiBits = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        UiStyles.setUiModeOverride(getResources().getConfiguration());

        if (LauncherModeManager.isHomeIntent(getIntent())) {
            LauncherModeManager.ensurePersistedFromHomeLaunch(this);
            launchedAsHome = true;
        }
        if (LauncherModeManager.isEnabled(this)) {
            applyLauncherChrome(true);
            switchTab(TAB_LAUNCHER);
        } else {
            applyLauncherChrome(false);
            switchTab(TAB_HOME);
        }
        if (getIntent().getBooleanExtra(EXTRA_OPEN_LOG_TAB, false)) {
            getIntent().removeExtra(EXTRA_OPEN_LOG_TAB);
            switchTab(4);
        }

        if (getIntent().getBooleanExtra(EXTRA_OPEN_SETTINGS_AFTER_THEME, false)) {
            getIntent().removeExtra(EXTRA_OPEN_SETTINGS_AFTER_THEME);
            switchTab(7);
        }
        
        // Uygulamaları yükle
        if (appsTabBuilder != null) appsTabBuilder.loadAppsFromServer();

        if (!vdbusBridgeAcquired) {
            vdbusBridgeAcquired = true;
            MapControlVDBusKeyBridge.acquire(this);
        }

        // Otomatik seçim modu: Uygulama açıldığında önerilen uygulamayı otomatik seç
        autoSelectPreferredApp();
    }

    /** Splash → ana UI (splash initializeApp süresince zaten görünürdü). */
    private void presentMainUi(View mainRoot) {
        ThemeManager.applyActivityTheme(this);
        DisplayHelper.stopAppLaunchSplashAnimations();
        launchSplashRoot = null;
        setContentView(mainRoot);
        applyThemedWindowBackground();
        ThemeManager.applyCyberTypeface(mainRoot);
        if (ThemeManager.isCyber(this) && mainRoot instanceof android.view.ViewGroup) {
            CyberScanlineView scanlines = new CyberScanlineView(this);
            ((android.view.ViewGroup) mainRoot).addView(scanlines,
                    new android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        }
        attachSystemStatsOverlay();
        attachCameraRecordingBadge();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** Kamera kaydı başka sekmedeyken küçük bir hatırlatıcı gösterir. */
    private void attachCameraRecordingBadge() {
        if (mainRootContainer == null) {
            return;
        }
        if (cameraRecordingBadge != null) {
            updateCameraRecordingBadge();
            return;
        }
        cameraRecordingBadge = new TextView(this);
        cameraRecordingBadge.setText(R.string.camera360_recording_continues);
        cameraRecordingBadge.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
        cameraRecordingBadge.setTypeface(com.mapcontrol.ui.cockpit.CockpitTokens.inter(
                this, com.mapcontrol.ui.cockpit.CockpitTokens.WEIGHT_SEMIBOLD));
        cameraRecordingBadge.setTextColor(android.graphics.Color.WHITE);
        cameraRecordingBadge.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE6C62828);
        bg.setCornerRadius(dp(12));
        cameraRecordingBadge.setBackground(bg);
        cameraRecordingBadge.setVisibility(android.view.View.GONE);
        cameraRecordingBadge.setClickable(false);
        cameraRecordingBadge.setFocusable(false);
        int margin = dp(16);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM | android.view.Gravity.START);
        lp.setMargins(margin, margin, margin, margin);
        mainRootContainer.addView(cameraRecordingBadge, lp);
        cameraRecordingBadge.bringToFront();
        updateCameraRecordingBadge();
    }

    private void updateCameraRecordingBadge() {
        if (cameraRecordingBadge == null) {
            return;
        }
        boolean show = camera360TabBuilder != null
                && camera360TabBuilder.isRecordingActive()
                && currentTab != TAB_CAMERA_360;
        cameraRecordingBadge.setVisibility(show
                ? android.view.View.VISIBLE : android.view.View.GONE);
        if (show) {
            cameraRecordingBadge.bringToFront();
        }
    }

    /** Tüm sekmelerin üzerinde duran sistem HUD'ını kök FrameLayout'a ekler. */
    private void attachSystemStatsOverlay() {
        if (mainRootContainer == null) {
            return;
        }
        if (systemStatsOverlay != null
                && systemStatsOverlay.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) systemStatsOverlay.getParent()).removeView(systemStatsOverlay);
        }
        systemStatsOverlay = new SystemStatsOverlay(this);
        int margin = Math.round(16 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM | android.view.Gravity.END);
        lp.setMargins(margin, margin, margin, margin);
        mainRootContainer.addView(systemStatsOverlay, lp);
        systemStatsOverlay.setEnabled(getSharedPreferences("MapControlPrefs", MODE_PRIVATE)
                .getBoolean(SystemStatsOverlay.PREF_ENABLED, false));
        systemStatsOverlay.bringToFront();
    }

    /**
     * Pencere arka planı açılış splash temasında kalır. Menü yumuşatması veya alfa düşüşü
     * bu yüzeyi gösterince gece modunda açık gradient flaş gibi patlar; güncel paleti bas.
     */
    private void applyThemedWindowBackground() {
        if (getWindow() == null) {
            return;
        }
        Drawable background = UiStyles.drawable(this, R.drawable.bg_root_gradient);
        if (background != null) {
            getWindow().setBackgroundDrawable(background);
        }
    }

    /**
     * logcat'i dinleyerek SMS servisinden gelen key event'leri yakalar.
     * VDS-SMS-SourceFile tag'ini ve keyCode pattern'ini dinler.
     */
    private Thread keyEventLogcatThread;
    private volatile boolean keyEventLogcatRunning = false;
    
    private void playSoftAlert() {
        AlertSoundHelper.playSoftAlert(this, msg -> MainActivity.this.log(msg));
    }

    /**
     * Bir uygulamanın sistem uygulaması veya priv-app olup olmadığını kontrol eder
     */
    /**
     * ApplicationInfo ile sistem/priv uygulaması kontrolü (WebServerManager'daki gibi)
     */
    private boolean isSystemOrPrivApp(ApplicationInfo appInfo) {
        try {
            if (appInfo == null) {
                return true;
            }
            
            // com.mapcontrol hariç tut
            if (com.mapcontrol.BuildConfig.APPLICATION_ID.equals(appInfo.packageName)) {
                return false;
            }
            
            // Sistem uygulaması kontrolü
            boolean isSystemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean isUpdatedSystemApp = (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            
            if (isSystemApp || isUpdatedSystemApp) {
                return true;
            }
            
            // Priv-app kontrolü (sourceDir ve publicSourceDir kontrolü)
            String sourceDir = appInfo.sourceDir;
            String publicSourceDir = appInfo.publicSourceDir;
            
            if (sourceDir != null && (sourceDir.contains("/system/priv-app/") || sourceDir.contains("/system/app/"))) {
                return true;
            }
            
            if (publicSourceDir != null && (publicSourceDir.contains("/system/priv-app/") || publicSourceDir.contains("/system/app/"))) {
                return true;
            }
            
            return false;
        } catch (Exception e) {
            // Hata durumunda güvenli tarafta kal (sistem uygulaması say)
            return true;
        }
    }
    
    /**
     * Package name ile sistem/priv uygulaması kontrolü
     */
    private boolean isSystemOrPrivApp(String packageName) {
        try {
            if (packageName == null || packageName.isEmpty()) {
                return true; // Null veya boş ise sistem uygulaması say
            }
            
            // com.mapcontrol hariç tut
            if (packageName.equals(com.mapcontrol.BuildConfig.APPLICATION_ID)) {
                return false;
            }
            
            PackageManager pm = getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            
            // ApplicationInfo overload'unu kullan
            return isSystemOrPrivApp(appInfo);
        } catch (Exception e) {
            log("isSystemOrPrivApp kontrol hatası (" + packageName + "): " + e.getMessage());
            // Hata durumunda güvenli tarafta kal, sistem uygulaması say
            return true;
        }
    }
    
    private void showPreparingMessageOnDisplay(int displayId) {
        DisplayHelper.showPreparingMessageOnDisplay(this, displayId);
    }

    private void hidePreparingMessage() {
        DisplayHelper.hidePreparingMessage();
    }

    /**
     * Cluster display ID'sini al
     */
    private int getClusterDisplayId() {
        try {
            DisplayManager dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            Display[] displays = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
            if (displays.length == 0) {
                displays = dm.getDisplays();
            }
            for (Display d : displays) {
                if (d.getDisplayId() != Display.DEFAULT_DISPLAY) {
                    return d.getDisplayId();
                }
            }
        } catch (Exception e) {
            log("getClusterDisplayId hatası: " + e.getMessage());
        }
        return 2; // Varsayılan cluster display ID
    }

    /**
     * İlk kurulum / boş hedef: yüklü önerilen uygulamalardan birini otomatik seçer.
     * Daha önce kaydedilmiş {@code targetPackage} hâlâ başlatılabiliyorsa onu ezmek için çağrılmamalı;
     * aksi halde her açılışta liste sırasındaki ilk uygulama (ör. Yandex) ile kullanıcı seçiminin üstüne yazılırdı.
     */
    private void autoSelectPreferredApp() {
        try {
            PackageManager pm = getPackageManager();
            if (targetPackage != null && !targetPackage.trim().isEmpty()) {
                try {
                    if (pm.getLaunchIntentForPackage(targetPackage.trim()) != null) {
                        updateTargetLabel();
                        return;
                    }
                } catch (Exception ignored) {
                    // Kaldırıldı / geçersiz: aşağıda öneri listesinden yeniden dene
                }
            }
            String[] preferred = new String[] {
                    "ru.yandex.yandexnavi",
                    "ru.yandex.yandexmaps",
                    "com.google.android.apps.maps",
                    "com.waze",
                    "com.sygic.aura"
            };

            for (String pkg : preferred) {
                try {
                    Intent launchIntent = pm.getLaunchIntentForPackage(pkg);
                    if (launchIntent != null) {
                        saveTargetPackage(pkg);
                        updateTargetLabel();
                        log("Otomatik seçim: " + pkg);
                        return;
                    }
                } catch (Exception e) {
                    continue;
                }
            }
            log("Önerilen uygulama bulunamadı, manuel seçim gerekli");
        } catch (Exception e) {
            log("autoSelectPreferredApp hatası: " + e.getMessage());
        }
    }

    // createMenuItemView / createRailMenuItemView SideRailBuilder'a taşındı.
    
    // updateMenuSelection artık SideRailBuilder içinde yönetiliyor.

    /**
     * Tab değiştirme metodu
     * 0=Wi-Fi, 1=Web, 2=Profil, 3=Yansıtma, 4=LOG, 5=Uygulamalar, 6=Hafıza, 7=Ayarlar, 8=Açılış Sesi, 9=Araç Bilgi, 11=Ekolayzer, 12=Otomasyon
     */
    private void switchTab(int tabIndex) {
        Log.i("MainActivityTab", "switchTab=" + tabIndex + " current=" + currentTab);
        if (tabContentArea == null || projectionTabContent == null || wifiTabContent == null || logTabContent == null || appsTabContent == null || driveModeTabContent == null || fileUploadTabContent == null || welcomeSoundTabBuilder == null || equalizerTabBuilder == null || vehicleInfoScrollView == null || camera360TabContent == null) {
            return;
        }

        welcomeSoundTabBuilder.onHostPause();

        if (currentTab == 9 && tabIndex != 9 && vehicleInfoTabBuilder != null) {
            vehicleInfoTabBuilder.stopListening();
        }
        if (currentTab == TAB_HOME && tabIndex != TAB_HOME && homeTabBuilder != null) {
            homeTabBuilder.stopListening();
        }
        if (currentTab == TAB_LAUNCHER && tabIndex != TAB_LAUNCHER && launcherTabBuilder != null) {
            launcherTabBuilder.onTabHidden();
        }
        if (currentTab == 5 && tabIndex != 5 && appsTabBuilder != null) {
            appsTabBuilder.onTabHidden();
        }
        if (currentTab == TAB_CAMERA_360 && tabIndex != TAB_CAMERA_360 && camera360TabBuilder != null) {
            camera360TabBuilder.onTabHidden();
        }

        if (currentTab == TAB_DRIVING_HISTORY && tabIndex != TAB_DRIVING_HISTORY
                && drivingHistoryTabBuilder != null) {
            drivingHistoryTabBuilder.onTabHidden();
        }
        currentTab = tabIndex;
        if (sideRailBuilder != null) {
            sideRailBuilder.setSelectionForTabIndex(tabIndex);
        }
        if (subTabBar != null) {
            CockpitNavModel.Domain domain = CockpitNavModel.domainForTab(tabIndex);
            subTabBar.setDomain(domain, tabIndex);
            subTabBar.setVisibility(domain.items.size() <= 1 ? View.GONE : View.VISIBLE);
        }

        // TopBar buton container'ını temizle
        if (topBarButtonsContainer != null) {
            topBarButtonsContainer.removeAllViews();
        }

        // Launcher modu açıkken sol menü her zaman gizli; kapalıyken görünür.
        boolean launcherMode = LauncherModeManager.isEnabled(this);
        applyLauncherChrome(launcherMode);

        tabContentArea.removeAllViews();
        attachTabContent(tabIndex);
        CockpitLegacyStyler.apply(tabContentArea);

        applyTabChrome(tabIndex);
        ThemeManager.applyCyberTypeface(tabContentArea);

        if (tabIndex == 5 && appsTabBuilder != null) {
            appsTabBuilder.onTabVisible();
        }
        if (tabIndex == TAB_CAMERA_360 && camera360TabBuilder != null) {
            camera360TabBuilder.onTabVisible();
        }
        updateCameraRecordingBadge();
    }

    /** İçeriği verilen parent'a ekler; başlık / top-bar chrome {@link #applyTabChrome} ile. */
    private void attachTabContent(int tabIndex) {
        attachTabContent(tabContentArea, tabIndex);
    }

    private void attachTabContent(FrameLayout parent, int tabIndex) {
        Log.i("MainActivityTab", "attachTabContent=" + tabIndex);
        FrameLayout.LayoutParams matchParent = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        if (tabIndex == 0) {
            addTabChildWithHeader(parent, wifiTabContent, matchParent, tabIndex);
        } else if (tabIndex == 1) {
            addTabChildWithHeader(parent, fileUploadScrollView, matchParent, tabIndex);
        } else if (tabIndex == 3) {
            addTabChild(parent, projectionScrollView, matchParent);
        } else if (tabIndex == 4) {
            addTabChildWithHeader(parent, logTabContent, matchParent, tabIndex);
        } else if (tabIndex == 5) {
            addTabChildWithHeader(parent, appsTabContent, matchParent, tabIndex);
        } else if (tabIndex == 6) {
            addTabChild(parent, driveModeScrollView, matchParent);
        } else if (tabIndex == 7) {
            addTabChildWithHeader(parent, settingsScrollView, matchParent, tabIndex);
        } else if (tabIndex == 8) {
            addTabChild(parent, welcomeSoundTabBuilder.getScrollView(), matchParent);
        } else if (tabIndex == 9) {
            addTabChild(parent, vehicleInfoScrollView, matchParent);
        } else if (tabIndex == TAB_EQUALIZER) {
            addTabChildWithHeader(parent, equalizerTabBuilder.getScrollView(), matchParent, tabIndex);
        } else if (tabIndex == TAB_DRIVER_PROFILES) {
            addTabChildWithHeader(parent, driverProfilesTabBuilder.getScrollView(), matchParent, tabIndex);
        } else if (tabIndex == TAB_VALET) {
            addTabChildWithHeader(parent, valetTabBuilder.getScrollView(), matchParent, tabIndex);
        } else if (tabIndex == TAB_HOME) {
            addTabChild(parent, homeScrollView, matchParent);
        } else if (tabIndex == TAB_DRIVING_HISTORY) {
            drivingHistoryTabBuilder.onTabVisible();
            addTabChildWithHeader(parent, drivingHistoryScrollView, matchParent, tabIndex);
        } else if (tabIndex == TAB_CLIMATE_AUTOMATION) {
            rebuildClimateAutomationTab();
            addTabChild(parent, climateAutomationScrollView, matchParent);
        } else if (tabIndex == CockpitNavModel.TAB_ASSIST) {
            addTabChild(parent, assistTabBuilder.build(), matchParent);
        } else if (tabIndex == TAB_SUNROOF_AUTOMATION) {
            rebuildSunroofAutomationTab();
            addTabChild(parent, sunroofAutomationScrollView, matchParent);
        } else if (tabIndex == TAB_FACE_RECOGNITION) {
            addTabChildWithHeader(parent, faceRecognitionScrollView, matchParent, tabIndex);
            if (dmsFaceRecognitionTabBuilder != null) {
                dmsFaceRecognitionTabBuilder.refresh();
            }
        } else if (tabIndex == TAB_CAMERA_360) {
            addTabChild(parent, camera360TabContent, matchParent);
        } else if (tabIndex == TAB_SPLIT_SCREEN) {
            if (splitScreenTabBuilder != null) {
                addTabChildWithHeader(parent, splitScreenTabBuilder.getScrollView(), matchParent, tabIndex);
                splitScreenTabBuilder.onTabVisible();
            }
        } else if (tabIndex == CockpitNavModel.TAB_ROADMATE) {
            if (roadmateTabBuilder != null) {
                addTabChildWithHeader(parent, roadmateTabBuilder.getView(), matchParent, tabIndex);
            }
        } else if (tabIndex == TAB_LAUNCHER) {
            if (launcherScrollView != null) {
                addTabChild(parent, launcherScrollView, matchParent);
            }
            if (launcherTabBuilder != null) {
                launcherTabBuilder.onTabVisible();
            }
        }
    }

    private void addTabChildWithHeader(FrameLayout parent, View child,
            FrameLayout.LayoutParams lp, int tabIndex) {
        if (child == null) {
            return;
        }
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.addView(new CockpitPageHeader(this, pageTitle(tabIndex), pageSubtitle(tabIndex)),
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
        if (child.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) child.getParent()).removeView(child);
        }
        shell.addView(child, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        addTabChild(parent, shell, lp);
    }

    private CharSequence pageTitle(int tabIndex) {
        if (tabIndex == CockpitNavModel.TAB_SETTINGS) {
            return getString(R.string.ck_domain_settings);
        }
        return CockpitNavModel.title(this, tabIndex);
    }

    private CharSequence pageSubtitle(int tabIndex) {
        switch (tabIndex) {
            case CockpitNavModel.TAB_APPS: return getString(R.string.ck_page_apps_sub);
            case CockpitNavModel.TAB_WIFI: return getString(R.string.ck_page_wifi_sub);
            case CockpitNavModel.TAB_FILE: return getString(R.string.ck_page_web_sub);
            case CockpitNavModel.TAB_PROJECTION: return getString(R.string.ck_page_projection_sub);
            case CockpitNavModel.TAB_SPLIT_SCREEN: return getString(R.string.ck_page_split_sub);
            case CockpitNavModel.TAB_EQUALIZER: return getString(R.string.ck_page_equalizer_sub);
            case CockpitNavModel.TAB_DRIVER_PROFILES: return getString(R.string.ck_page_driver_profiles_sub);
            case CockpitNavModel.TAB_FACE_RECOGNITION: return getString(R.string.ck_page_face_sub);
            case CockpitNavModel.TAB_DRIVING_HISTORY: return getString(R.string.ck_page_history_sub);
            case CockpitNavModel.TAB_VALET: return getString(R.string.ck_page_valet_sub);
            case CockpitNavModel.TAB_ROADMATE: return getString(R.string.ck_page_roadmate_sub);
            case CockpitNavModel.TAB_LOG: return getString(R.string.ck_page_log_sub);
            case CockpitNavModel.TAB_SETTINGS: return getString(R.string.ck_page_settings_sub);
            default: return "";
        }
    }

    private void addTabChild(FrameLayout parent, View child, FrameLayout.LayoutParams lp) {
        CockpitLegacyStyler.apply(child);
        if (child.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) child.getParent()).removeView(child);
        }
        if (child == launcherScrollView || child == homeScrollView || child == sunroofAutomationScrollView) {
            parent.addView(child, lp);
            return;
        }
        MaxWidthFrameLayout widthHost = new MaxWidthFrameLayout(parent.getContext());
        widthHost.addView(child, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        lp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        parent.addView(widthHost, lp);
    }

    /** Alt sekme şeridi seçimi: uyarı akışı + geçiş. */
    private void onSubTabSelected(int tabId, String title) {
        SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
        CockpitNavModel.Domain domain = CockpitNavModel.domainForTab(tabId);
        // yalnız kullanıcı seçiminde son sekme yazılır (açılışta disk yazımı yok)
        prefs.edit().putInt(CockpitNavModel.PREF_LAST_TAB + "_" + domain.id, tabId)
                .putInt(CockpitNavModel.PREF_LAST_DOMAIN, domain.id).apply();
        Runnable open = () -> crossfadeContent(() -> {
            switchTab(tabId);
            if (topBarTitle != null) {
                topBarTitle.setText(title);
            }
        });
        if (tabId == CockpitNavModel.TAB_CLIMATE_AUTOMATION
                && !prefs.getBoolean("climateAutoBetaWarningSeen", false)) {
            showExperimentalWarning(prefs, "climateAutoBetaWarningSeen", open);
        } else if (tabId == CockpitNavModel.TAB_FACE_RECOGNITION
                && !prefs.getBoolean("dmsFaceBetaWarningSeen", false)) {
            showExperimentalWarning(prefs, "dmsFaceBetaWarningSeen", open);
        } else if (tabId == CockpitNavModel.TAB_APPS
                && !prefs.getBoolean("appManagementDisclaimerAccepted", false)) {
            showAppManagementDisclaimer();
        } else {
            open.run();
        }
    }

    private void showExperimentalWarning(SharedPreferences prefs, String key, Runnable open) {
        CockpitModal.builder(this)
                .title(getString(R.string.experimental_feature_warning_title))
                .message(getString(R.string.experimental_feature_warning))
                .positive(getString(R.string.experimental_feature_warning_ok), d -> {
                    prefs.edit().putBoolean(key, true).apply();
                    open.run();
                })
                .build()
                .show();
    }

    private void applyTabChrome(int tabIndex) {
        if (tabIndex == 0) {
            if (topBarTitle != null) topBarTitle.setText(R.string.topbar_wifi_management);
            if (topBarButtonsContainer != null && wifiTabBuilder != null) {
                topBarButtonsContainer.addView(wifiTabBuilder.buildTopBarIcon());
            }
            if (wifiTabBuilder != null) wifiTabBuilder.updateWifiStatus();
        } else if (tabIndex == 4) {
            if (topBarTitle != null) {
                topBarTitle.setText(R.string.topbar_system_logs);
            }
            if (!logWelcomeTtsDone) {
                logWelcomeTtsDone = true;
                handler.postDelayed(
                        () -> speakTtsText(getString(R.string.log_tts_welcome_phrase)), 450);
            }
        } else if (tabIndex == 5) {
            if (topBarTitle != null) topBarTitle.setText(R.string.topbar_apps);
            if (topBarButtonsContainer != null && appsTabBuilder != null) {
                topBarButtonsContainer.addView(appsTabBuilder.buildTopBarButtons(this));
            }
        } else if (tabIndex == 7) {
            if (settingsTabBuilder != null) {
                settingsTabBuilder.syncLauncherModeFromPrefs();
            }
        } else if (tabIndex == 8) {
            if (topBarTitle != null) {
                topBarTitle.setText(getString(R.string.welcome_sound_title));
            }
            if (welcomeSoundTabBuilder != null) {
                welcomeSoundTabBuilder.onTabVisible();
            }
        } else if (tabIndex == 9) {
            if (topBarTitle != null) {
                topBarTitle.setText(getString(R.string.side_rail_vehicle_info));
            }
            if (vehicleInfoTabBuilder != null) {
                vehicleInfoTabBuilder.startListening();
            }
        } else if (tabIndex == TAB_EQUALIZER) {
            if (topBarTitle != null) {
                topBarTitle.setText(getString(R.string.equalizer_title));
            }
        } else if (tabIndex == TAB_DRIVER_PROFILES) {
            if (topBarTitle != null) {
                topBarTitle.setText(getString(R.string.side_rail_driver_profiles));
            }
        } else if (tabIndex == TAB_VALET) {
            if (topBarTitle != null) {
                topBarTitle.setText(getString(R.string.side_rail_valet));
            }
            if (valetTabBuilder != null) {
                valetTabBuilder.onTabVisible();
            }
        } else if (tabIndex == TAB_HOME) {
            if (topBarTitle != null) {
                topBarTitle.setText(getString(R.string.side_rail_home));
            }
            if (homeTabBuilder != null) {
                homeTabBuilder.startListening();
            }
        } else if (tabIndex == TAB_DRIVING_HISTORY) {
            if (topBarTitle != null) {
                topBarTitle.setText(getString(R.string.side_rail_driving_report));
            }
        } else if (tabIndex == TAB_LAUNCHER) {
            if (topBarTitle != null) {
                topBarTitle.setText(R.string.topbar_vehicle_home);
            }
        } else if (tabIndex == TAB_CAMERA_360) {
            if (topBarTitle != null) {
                topBarTitle.setText(R.string.camera360_title);
            }
        } else if (tabIndex == TAB_SPLIT_SCREEN) {
            if (topBarTitle != null) {
                topBarTitle.setText(R.string.side_rail_split_screen);
            }
        } else if (tabIndex == CockpitNavModel.TAB_ROADMATE) {
            if (topBarTitle != null) {
                topBarTitle.setText(R.string.ck_roadmate_title);
            }
        } else if (tabIndex == TAB_SUNROOF_AUTOMATION) {
            if (topBarTitle != null) {
                topBarTitle.setText(R.string.sunroof_auto_title);
            }
        } else if (tabIndex == CockpitNavModel.TAB_ASSIST) {
            if (topBarTitle != null) {
                topBarTitle.setText(R.string.ck_sub_assist);
            }
        }
    }

    /**
     * Launcher modu chrome'unu uygular: sol menü görünürlüğü, içerik genişliği ve TopBar geri düğmesi.
     */
    private void applyLauncherChrome(boolean active) {
        if (sideRailBuilder != null) {
            sideRailBuilder.setLauncherModeActive(active);
            sideRailBuilder.setContentWide(contentWide);
        }
        boolean railHidden = active || contentWide;
        if (mainContent != null && mainContentParams != null) {
            if (portraitShell) {
                mainContentParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
                mainContentParams.height = 0;
                mainContentParams.weight = 1f;
            } else {
                mainContentParams.width = railHidden ? screenWidthPx : (screenWidthPx - sidebarWidthPx);
                mainContentParams.height = LinearLayout.LayoutParams.MATCH_PARENT;
                mainContentParams.weight = 0f;
            }
            mainContent.setLayoutParams(mainContentParams);
        }
        if (topBarBuilder != null) {
            boolean onLauncherHome = active && currentTab == TAB_LAUNCHER;
            topBarBuilder.setTopBarVisible(!onLauncherHome);
            topBarBuilder.setLauncherBackButtonVisible(active && !onLauncherHome);
            if (subTabBar != null) {
                boolean singleItemDomain = CockpitNavModel.domainForTab(currentTab).items.size() <= 1;
                subTabBar.setVisibility(onLauncherHome || singleItemDomain
                        ? android.view.View.GONE : android.view.View.VISIBLE);
            }
        }
        ImmersiveFullscreenHelper.setImmersiveFullscreen(this, active);
    }

    /** Sekme içeriği sol menüyü gizleyip tam genişlik ister (launcher modundan bağımsız). */
    private void setContentWide(boolean wide) {
        if (contentWide == wide) return;
        contentWide = wide;
        applyLauncherChrome(LauncherModeManager.isEnabled(this));
    }

    /** Launcher modunda alt sekmeden ana ekran ızgarasına döner; modu kapatmaz. */
    private void returnToLauncherHome() {
        if (!LauncherModeManager.isEnabled(this)) {
            return;
        }
        crossfadeLauncherTransition(() -> {
            switchTab(TAB_LAUNCHER);
            if (topBarTitle != null) {
                topBarTitle.setText(R.string.topbar_vehicle_home);
            }
        });
    }

    /** Fades only the content column (side rail stays put) around a tab switch. */
    private void crossfadeContent(Runnable change) {
        crossfadeWithVeil(mainContent, 110L, 200L, change);
    }

    /** Covers the window, applies the layout switch, then reveals it. */
    private void crossfadeLauncherTransition(Runnable change) {
        crossfadeWithVeil(findViewById(android.R.id.content), 140L, 260L, change);
    }

    /**
     * İçeriğin kendi alfasını 0’a çekmez. O yol, arkadaki açık pencereyi (veya katman temizleme
     * rengini) gece modunda flaş gibi gösterir. Örtü {@code bg_root_gradient} olduğu için
     * gündüz açık, gece koyu tona yumuşar; sekme ve launcher geçişlerinin ikisi de buradan geçer.
     */
    private void crossfadeWithVeil(View target, long coverMs, long revealMs, Runnable change) {
        if (target == null || target.getWidth() <= 0 || target.getHeight() <= 0) {
            if (change != null) {
                change.run();
            }
            return;
        }
        target.animate().cancel();
        target.setAlpha(1f);
        ValueAnimator running = transitionVeils.remove(target);
        if (running != null) {
            running.cancel();
        }
        Drawable veil = UiStyles.drawable(this, R.drawable.bg_root_gradient);
        if (veil == null) {
            if (change != null) {
                change.run();
            }
            return;
        }
        veil.setAlpha(0);
        veil.setBounds(0, 0, target.getWidth(), target.getHeight());
        target.getOverlay().add(veil);
        View.OnLayoutChangeListener layout = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                veil.setBounds(0, 0, v.getWidth(), v.getHeight());
        target.addOnLayoutChangeListener(layout);

        ValueAnimator cover = ValueAnimator.ofInt(0, 255);
        cover.setDuration(coverMs);
        cover.addUpdateListener(animation -> veil.setAlpha((int) animation.getAnimatedValue()));
        transitionVeils.put(target, cover);
        final boolean[] dropped = {false};
        cover.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                dropped[0] = true;
                finishTransitionVeil(target, veil, layout);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (dropped[0] || transitionVeils.get(target) != cover) {
                    return;
                }
                try {
                    if (change != null) {
                        change.run();
                    }
                } finally {
                    veil.setBounds(0, 0, Math.max(target.getWidth(), 0), Math.max(target.getHeight(), 0));
                    ValueAnimator reveal = ValueAnimator.ofInt(255, 0);
                    reveal.setDuration(revealMs);
                    reveal.addUpdateListener(revealAnim -> veil.setAlpha((int) revealAnim.getAnimatedValue()));
                    transitionVeils.put(target, reveal);
                    final boolean[] revealDropped = {false};
                    reveal.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationCancel(Animator animation) {
                            revealDropped[0] = true;
                            finishTransitionVeil(target, veil, layout);
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (revealDropped[0]) {
                                return;
                            }
                            if (transitionVeils.get(target) == reveal) {
                                transitionVeils.remove(target);
                            }
                            finishTransitionVeil(target, veil, layout);
                        }
                    });
                    reveal.start();
                }
            }
        });
        cover.start();
    }

    private void finishTransitionVeil(View target, Drawable veil, View.OnLayoutChangeListener layout) {
        target.removeOnLayoutChangeListener(layout);
        target.getOverlay().remove(veil);
        target.setAlpha(1f);
    }

    private void enterLauncherMode() {
        LauncherModeManager.setEnabled(this, true);
        launchedAsHome = true;
        applyLauncherChrome(true);
        switchTab(TAB_LAUNCHER);
        syncLauncherModeSettingsUi();
    }

    /**
     * Launcher modunu kapatır. Yalnızca araç paneli sekmesindeyken Ana Sayfa'ya döner;
     * Ayarlar gibi başka bir sekmeden kapatılırsa mevcut sekme korunur.
     */
    private void exitLauncherMode() {
        boolean wasOnLauncherTab = currentTab == TAB_LAUNCHER;
        LauncherModeManager.setEnabled(this, false);
        launchedAsHome = false;
        applyLauncherChrome(false);
        syncLauncherModeSettingsUi();
        detachParkedLauncher();
        if (wasOnLauncherTab) {
            switchTab(TAB_HOME);
        }
    }

    /** Launcher modu kapanırken ana ekran view'ını parent'tan ayırır. */
    private void detachParkedLauncher() {
        if (launcherScrollView == null || tabContentArea == null) {
            return;
        }
        if (launcherScrollView.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) launcherScrollView.getParent()).removeView(launcherScrollView);
        }
        launcherScrollView.setVisibility(View.VISIBLE);
    }

    private void syncLauncherModeSettingsUi() {
        if (settingsTabBuilder != null) {
            settingsTabBuilder.syncLauncherModeFromPrefs();
        }
    }


    /**
     * Seçilen uygulamayı TextView'de göster
     */
    private void updateTargetLabel() {
        if (targetAppLabel != null) {
            if (targetPackage == null || targetPackage.trim().isEmpty()) {
                targetAppLabel.setText(R.string.common_not_selected);
            } else {
                try {
                    PackageManager pm = getPackageManager();
                    ApplicationInfo appInfo = pm.getApplicationInfo(targetPackage, 0);
                    String appName = pm.getApplicationLabel(appInfo).toString();
                    targetAppLabel.setText(appName);
                } catch (Exception e) {
                    targetAppLabel.setText(targetPackage);
                }
            }
        }
    }

    private void speakTtsText(String text) {
        if (text == null) {
            return;
        }
        String t = text.trim();
        if (t.isEmpty()) {
            log("TTS: metin boş");
            return;
        }
        if (systemTts == null) {
            pendingTtsText = t;
            systemTts = new TextToSpeech(getApplicationContext(), status -> {
                if (status != TextToSpeech.SUCCESS) {
                    log("TTS: sistem ses sentezi başlatılamadı");
                    pendingTtsText = null;
                    return;
                }
                systemTts.setLanguage(new Locale("tr", "TR"));
                if (pendingTtsText != null) {
                    String queued = pendingTtsText;
                    pendingTtsText = null;
                    speakTtsTextNow(queued);
                }
            });
            return;
        }
        speakTtsTextNow(t);
    }

    private void speakTtsTextNow(String text) {
        int result = systemTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mapcontrol-tts");
        if (result == TextToSpeech.SUCCESS) {
            log("TTS: sistem ses sentezi ile okunuyor");
        } else {
            log("TTS: konuşma başlatılamadı (kod=" + result + ")");
        }
    }

    private String now() {
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
    }

    // QR üretimi `FileUploadTabBuilder.generateQRCode()` içine taşındı.

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (ApkSessionInstaller.handleSessionResultIntent(this, intent)) {
            return;
        }
        if (intent != null && intent.getBooleanExtra(EXTRA_OPEN_PROJECTION_TARGET_PICKER, false)) {
            intent.removeExtra(EXTRA_OPEN_PROJECTION_TARGET_PICKER);
            deferredOpenProjectionTargetPicker = true;
        }
        if (intent != null && intent.getBooleanExtra(EXTRA_OPEN_LOG_TAB, false)) {
            intent.removeExtra(EXTRA_OPEN_LOG_TAB);
            if (tabContentArea != null) {
                switchTab(4);
            }
        }
        // Cihaz Ana Ekran (Home) tuşuna basılıp uygulama zaten açıkken (singleTop) tekrar
        // çağrılırsa Araç Launcher karşılama ekranına dön.
        if (LauncherModeManager.isHomeIntent(intent)) {
            LauncherModeManager.ensurePersistedFromHomeLaunch(this);
            enterLauncherMode();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            ImmersiveFullscreenHelper.reapplyIfLauncherMode(this);
        if (DmsFaceRecognitionManager.isEnabled(this)
                && ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                DmsFaceRecognitionManager.start(this);
            } catch (RuntimeException e) {
                log(getString(R.string.dms_recognition_error, e.getMessage()));
            }
        }
        if (dmsFaceRecognitionTabBuilder != null) {
            dmsFaceRecognitionTabBuilder.refresh();
        }
        if (currentTab == TAB_DRIVER_PROFILES && driverProfilesTabBuilder != null) {
            driverProfilesTabBuilder.rebuild();
        }
        }
    }

    @Override
    public void onBackPressed() {
        if (LauncherModeManager.isEnabled(this)) {
            // Ana Ekran (Home) olarak çalışırken geri tuşu uygulamayı kapatmamalı:
            // Launcher ekranındaysak hiçbir şey yapma, başka sekmedeysek Launcher'a dön.
            if (currentTab == TAB_LAUNCHER) {
                return;
            }
            switchTab(TAB_LAUNCHER);
            if (topBarTitle != null) {
                topBarTitle.setText(R.string.topbar_vehicle_home);
            }
            return;
        }
        super.onBackPressed();
    }

    private void tryConsumeDeferredProjectionTargetPicker() {
        if (!deferredOpenProjectionTargetPicker || projectionTabBuilder == null) {
            return;
        }
        deferredOpenProjectionTargetPicker = false;
        handler.post(() -> projectionTabBuilder.openTargetAppPicker());
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation != lastOrientation) {
            lastOrientation = newConfig.orientation;
            mountShell(newConfig.orientation == Configuration.ORIENTATION_PORTRAIT);
            boolean launcherMode = LauncherModeManager.isEnabled(this);
            if (sideRailBuilder != null) {
                sideRailBuilder.setLauncherModeActive(launcherMode);
                if (currentTab != TAB_LAUNCHER) {
                    sideRailBuilder.setSelectionForTabIndex(currentTab);
                }
            }
            applyLauncherChrome(launcherMode);
            rebuildLauncherForOrientation();
        }
        int night = newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (night == lastNightModeUiBits) {
            return;
        }
        lastNightModeUiBits = night;
        // AppCompat Activity Resources yapışmasın diye çözümlemeyi newConfig'e kilitle.
        UiStyles.setUiModeOverride(newConfig);
        applyThemedWindowBackground();
        reapplyUiModeTheme();
    }

    /** Sistem light/dark değişince chrome + tüm sekmeleri yeniler. */
    private void reapplyUiModeTheme() {
        if (mainRootContainer != null) {
            mainRootContainer.setBackgroundColor(CockpitTokens.color(this, R.color.ck_bg_base));
        }
        if (tabContentArea != null) {
            tabContentArea.setBackgroundColor(0x00000000);
        }

        rebuildSideRailForUiMode();
        rebuildTopBarForUiMode();
        rebuildNonLauncherTabsForUiMode();

        if (launcherTabBuilder != null) {
            launcherTabBuilder.onUiModeChanged();
        }

        int tab = currentTab;
        boolean launcherMode = LauncherModeManager.isEnabled(this);
        applyLauncherChrome(launcherMode);
        if (sideRailBuilder != null && tab != TAB_LAUNCHER) {
            sideRailBuilder.setSelectionForTabIndex(tab);
        }
        if (tabContentArea == null) {
            return;
        }
        tabContentArea.removeAllViews();
        attachTabContent(tab);
        applyTabChrome(tab);
    }

    private void rebuildSideRailForUiMode() {
        if (sideRailBuilder == null || shellHost == null) {
            return;
        }
        boolean launcherActive = LauncherModeManager.isEnabled(this);
        int selectTab = currentTab == TAB_LAUNCHER ? 0 : currentTab;
        if (sideRail != null) {
            shellHost.removeView(sideRail);
        }
        sideRail = portraitShell ? sideRailBuilder.buildPortrait() : sideRailBuilder.build();
        LinearLayout.LayoutParams railParams = portraitShell
                ? new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                : new LinearLayout.LayoutParams(
                        sidebarWidthPx,
                        LinearLayout.LayoutParams.MATCH_PARENT);
        shellHost.addView(sideRail, 0, railParams);
        sideRailBuilder.setLauncherModeActive(launcherActive);
        sideRailBuilder.setSelectionForTabIndex(selectTab);
    }

    private boolean isPortraitConfiguration() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    /**
     * Yatay: sol şerit + sağ içerik. Dikey: üstte kaydırılan menü, içerik tam genişlik.
     * Genişlikler configChanges yüzünden activity yeniden kurulmadan burada ölçülür.
     */
    private void mountShell(boolean portrait) {
        portraitShell = portrait;
        android.util.DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        screenWidthPx = displayMetrics.widthPixels;
        sidebarWidthPx = portrait ? 0 : railWidthPx();

        if (shellHost == null) {
            shellHost = new LinearLayout(this);
            shellHost.setBackgroundColor(UiStyles.color(this, R.color.transparent));
            mainRootContainer.addView(shellHost, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }
        shellHost.setOrientation(portrait ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        shellHost.removeAllViews();

        sideRail = portrait ? sideRailBuilder.buildPortrait() : sideRailBuilder.build();
        LinearLayout.LayoutParams railParams = portrait
                ? new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                : new LinearLayout.LayoutParams(
                        sidebarWidthPx,
                        LinearLayout.LayoutParams.MATCH_PARENT);
        shellHost.addView(sideRail, railParams);

        if (mainContent.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) mainContent.getParent()).removeView(mainContent);
        }
        if (portrait) {
            mainContentParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f);
        } else {
            mainContentParams = new LinearLayout.LayoutParams(
                    screenWidthPx - sidebarWidthPx,
                    LinearLayout.LayoutParams.MATCH_PARENT);
        }
        shellHost.addView(mainContent, mainContentParams);
    }

    /** Launcher kartları yön değişince yeniden dizilir; normal sekmeler kaydırılmaya devam eder. */
    private void rebuildLauncherForOrientation() {
        if (launcherTabBuilder == null) {
            return;
        }
        boolean show = currentTab == TAB_LAUNCHER;
        launcherTabBuilder.onTabHidden();
        if (launcherScrollView != null
                && launcherScrollView.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) launcherScrollView.getParent()).removeView(launcherScrollView);
        }
        launcherScrollView = launcherTabBuilder.build();
        if (show && tabContentArea != null) {
            tabContentArea.removeAllViews();
            attachTabContent(TAB_LAUNCHER);
            applyTabChrome(TAB_LAUNCHER);
        }
    }

    private void rebuildTopBarForUiMode() {
        if (topBarBuilder == null || mainContent == null) {
            return;
        }
        CharSequence title = topBarTitle != null ? topBarTitle.getText() : null;
        LinearLayout newTopBar = topBarBuilder.build();
        if (mainContent.getChildCount() > 0) {
            mainContent.removeViewAt(0);
        }
        mainContent.addView(newTopBar, 0, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        topBarTitle = topBarBuilder.getTitleView();
        topBarButtonsContainer = subTabBar != null ? subTabBar.getActionHost()
                : topBarBuilder.getButtonsContainer();
        topBarBuilder.setLauncherBackButtonListener(v -> returnToLauncherHome());
        if (title != null && topBarTitle != null) {
            topBarTitle.setText(title);
        }
    }

    /** Aktif profil değişince hafıza ayarı gösteren sekmeleri yeniler ve açık sekmeyi yeniden bağlar. */
    private void onDriverProfileChanged() {
        if (topBarBuilder != null) {
            topBarBuilder.refreshProfile();
        }
        rebuildNonLauncherTabsForUiMode();
        if (currentTab != TAB_LAUNCHER) {
            switchTab(currentTab);
        }
    }

    /** Launcher dışındaki sekmeleri taze renklerle yeniden kurar. */
    private void rebuildNonLauncherTabsForUiMode() {
        if (wifiTabBuilder != null) {
            wifiTabContent = wifiTabBuilder.build();
        }
        if (fileUploadTabBuilder != null) {
            fileUploadScrollView = fileUploadTabBuilder.build();
            fileUploadTabContent = fileUploadTabBuilder.getFileUploadTabContent();
            btnWebServerToggle = fileUploadTabBuilder.getToggleButton();
            webServerStatusText = fileUploadTabBuilder.getStatusText();
            qrCodeImageView = fileUploadTabBuilder.getQrImageView();
            if (webServerManager != null && webServerManager.isRunning()) {
                if (btnWebServerToggle != null) {
                    btnWebServerToggle.setText(R.string.web_server_stop);
                }
                if (webServerStatusText != null) {
                    webServerStatusText.setTextColor(UiStyles.color(this, R.color.accentHighlight));
                }
            }
        }
        if (projectionTabBuilder != null) {
            projectionScrollView = projectionTabBuilder.build();
            projectionTabContent = projectionTabBuilder.getProjectionTabContent();
            targetAppLabel = projectionTabBuilder.getTargetAppLabel();
            updateTargetLabel();
            projectionTabBuilder.refreshProjectionStatusUi();
        }
        if (driveModeTabBuilder != null) {
            driveModeScrollView = driveModeTabBuilder.build();
            driveModeTabContent = driveModeTabBuilder.getTabContent();
        }
        if (logTabBuilder != null) {
            CharSequence logs = tvLogs != null ? tvLogs.getText()
                    : SystemLogBuffer.snapshot();
            logTabContent = logTabBuilder.build();
            tvLogs = logTabBuilder.getLogsTextView();
            scrollView = logTabBuilder.getScrollView();
            if (tvLogs != null && logs != null) {
                tvLogs.setText(logs);
            }
        }
        if (appsTabBuilder != null) {
            appsTabContent = appsTabBuilder.build();
            appsTabBuilder.loadAppsFromServer();
        }
        if (settingsTabBuilder != null) {
            settingsScrollView = settingsTabBuilder.build();
            settingsTabContent = settingsTabBuilder.getSettingsTabContent();
            settingsTabBuilder.syncLauncherModeFromPrefs();
        }
        if (welcomeSoundTabBuilder != null) {
            welcomeSoundTabBuilder.rebuild();
        }
        if (equalizerTabBuilder != null) {
            equalizerTabBuilder.rebuild();
        }
        if (driverProfilesTabBuilder != null) {
            driverProfilesTabBuilder.rebuild();
        }
        if (valetTabBuilder != null) {
            valetTabBuilder.rebuild();
        }
        if (climateAutomationTabBuilder != null) {
            rebuildClimateAutomationTab();
        }
        if (sunroofAutomationTabBuilder != null) {
            rebuildSunroofAutomationTab();
        }
        if (vehicleInfoTabBuilder != null) {
            boolean listening = currentTab == 9;
            if (listening) {
                vehicleInfoTabBuilder.stopListening();
            }
            vehicleInfoTabBuilder.rebuild();
            vehicleInfoScrollView = vehicleInfoTabBuilder.getScrollView();
            if (listening) {
                vehicleInfoTabBuilder.startListening();
            }
        }
        if (drivingHistoryTabBuilder != null) {
            drivingHistoryTabBuilder.refresh();
        }
        if (camera360TabBuilder != null) {
            camera360TabContent = camera360TabBuilder.rebuildForUiMode();
        }
        if (splitScreenTabBuilder != null) {
            splitScreenTabBuilder.rebuild();
        }
    }

        private void rebuildClimateAutomationTab() {
        if (climateAutomationTabBuilder == null) {
            return;
        }
        if (climateAutomationScrollView == null) {
            climateAutomationScrollView = new ScrollView(this);
            climateAutomationScrollView.setFillViewport(true);
        } else {
            climateAutomationScrollView.removeAllViews();
        }
        climateAutomationScrollView.addView(climateAutomationTabBuilder.build(),
                new ScrollView.LayoutParams(
                        ScrollView.LayoutParams.MATCH_PARENT,
                        ScrollView.LayoutParams.WRAP_CONTENT));
    }

    /**
     * Cam tavan otomasyonu yalnız kullanıcı sekmeyi açınca başlatılır; uygulama/servis
     * açılışında otomatik VDBus/sensör aboneliği kurulmaz.
     */
    private void rebuildSunroofAutomationTab() {
        if (sunroofAutomationTabBuilder == null) {
            sunroofAutomationTabBuilder = new SunroofAutomationTabBuilder(this,
                    msg -> MainActivity.this.log(msg));
        }
        if (sunroofAutomationScrollView == null) {
            sunroofAutomationScrollView = new ScrollView(this);
            sunroofAutomationScrollView.setFillViewport(true);
        } else {
            sunroofAutomationScrollView.removeAllViews();
        }
        sunroofAutomationScrollView.addView(sunroofAutomationTabBuilder.build(),
                new ScrollView.LayoutParams(
                        ScrollView.LayoutParams.MATCH_PARENT,
                        ScrollView.LayoutParams.WRAP_CONTENT));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == DmsFaceRecognitionTabBuilder.CAMERA_PERMISSION_REQUEST
                && dmsFaceRecognitionTabBuilder != null) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            dmsFaceRecognitionTabBuilder.onCameraPermissionResult(granted);
        }
    }

    private void registerFaceRecognitionStatusReceiver() {
        if (faceRecognitionStatusBroadcastRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(DmsFaceRecognitionManager.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(faceRecognitionStatusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(faceRecognitionStatusReceiver, filter);
        }
        faceRecognitionStatusBroadcastRegistered = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (topBarBuilder != null) {
            topBarBuilder.refreshProfile();
        }
        if (currentTab == TAB_CAMERA_360 && camera360TabBuilder != null) {
            camera360TabBuilder.onTabVisible();
        }
        MapControlVDBusKeyBridge.refreshSubscription(true, true);
        ImmersiveFullscreenHelper.reapplyIfLauncherMode(this);
        if (DmsFaceRecognitionManager.isEnabled(this)
                && ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                DmsFaceRecognitionManager.start(this);
            } catch (RuntimeException e) {
                log(getString(R.string.dms_recognition_error, e.getMessage()));
            }
        }
        if (dmsFaceRecognitionTabBuilder != null) {
            dmsFaceRecognitionTabBuilder.refresh();
        }
        if (currentTab == TAB_DRIVER_PROFILES && driverProfilesTabBuilder != null) {
            driverProfilesTabBuilder.rebuild();
        }
        sBenchHost = this;
        // Arka plandayken Floating Back / overlay hedef seçimi prefs'e yazılır; yayın onPause'da
        // alıcı kapatıldığı için kaçırılabilir — her öne gelişte disk ile bellek senkronu.
        loadTargetPackage();
        updateTargetLabel();
        tryConsumeDeferredProjectionTargetPicker();
        registerTargetPackageBroadcastReceiver();
        registerNavigationClusterBroadcastReceiver();
        registerGoogleMapsNavBroadcastReceiver();
        registerFaceRecognitionStatusReceiver();
        isNavigationOpen = ClusterNavigationState.getLastKnownOpen();
        if (projectionTabBuilder != null) {
            projectionTabBuilder.refreshProjectionStatusUi();
            projectionTabBuilder.updateGoogleMapsNavSummary(
                    GoogleMapsNavNotificationCoordinator.getLastSnapshot());
        }
        if (currentTab == TAB_DRIVING_HISTORY && drivingHistoryTabBuilder != null) {
            drivingHistoryTabBuilder.onTabVisible();
        }
        if (systemStatsOverlay != null) {
            systemStatsOverlay.setActive(true);
        }
        if (handler != null) {
            handler.removeCallbacks(cameraRecordingBadgeTicker);
            handler.post(cameraRecordingBadgeTicker);
        }
        updateCameraRecordingBadge();
    }

    @Override
    protected void onPause() {
        if (handler != null) {
            handler.removeCallbacks(cameraRecordingBadgeTicker);
        }
        if (systemStatsOverlay != null) {
            systemStatsOverlay.setActive(false);
        }
        if (drivingHistoryTabBuilder != null) {
            drivingHistoryTabBuilder.onTabHidden();
        }
        unregisterTargetPackageBroadcastReceiver();
        unregisterNavigationClusterBroadcastReceiver();
        unregisterGoogleMapsNavBroadcastReceiver();
        if (faceRecognitionStatusBroadcastRegistered) {
            unregisterReceiver(faceRecognitionStatusReceiver);
            faceRecognitionStatusBroadcastRegistered = false;
        }
        if (welcomeSoundTabBuilder != null) {
            welcomeSoundTabBuilder.onHostPause();
        }
        // Kamera 360 arka planda akmaya devam etmesin; ancak SurfaceView'ı
        // burada sökme (SurfaceFlinger producer/fence yarışı).
        if (currentTab == TAB_CAMERA_360 && camera360TabBuilder != null) {
            camera360TabBuilder.onHostPause();
        }
        super.onPause();
    }

    private void registerTargetPackageBroadcastReceiver() {
        if (targetPackageBroadcastRegistered) {
            return;
        }
        try {
            IntentFilter filter = new IntentFilter(TargetPackageStore.ACTION_TARGET_PACKAGE_UPDATED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(targetPackageUpdatedReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(targetPackageUpdatedReceiver, filter);
            }
            targetPackageBroadcastRegistered = true;
        } catch (Exception e) {
            log("Hedef paket yayını kaydı: " + e.getMessage());
        }
    }

    private void unregisterTargetPackageBroadcastReceiver() {
        if (!targetPackageBroadcastRegistered) {
            return;
        }
        try {
            unregisterReceiver(targetPackageUpdatedReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        targetPackageBroadcastRegistered = false;
    }

    private void registerNavigationClusterBroadcastReceiver() {
        if (navigationClusterBroadcastRegistered) {
            return;
        }
        try {
            IntentFilter filter = new IntentFilter(ClusterNavigationState.ACTION_NAVIGATION_CLUSTER_STATE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(navigationClusterStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(navigationClusterStateReceiver, filter);
            }
            navigationClusterBroadcastRegistered = true;
        } catch (Exception e) {
            log("Cluster durum yayını kaydı: " + e.getMessage());
        }
    }

    private void unregisterNavigationClusterBroadcastReceiver() {
        if (!navigationClusterBroadcastRegistered) {
            return;
        }
        try {
            unregisterReceiver(navigationClusterStateReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        navigationClusterBroadcastRegistered = false;
    }

    private void registerGoogleMapsNavBroadcastReceiver() {
        if (googleMapsNavBroadcastRegistered) {
            return;
        }
        try {
            IntentFilter filter = new IntentFilter(
                    GoogleMapsNavNotificationCoordinator.ACTION_GOOGLE_MAPS_NAV_SNAPSHOT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(googleMapsNavSnapshotReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(googleMapsNavSnapshotReceiver, filter);
            }
            googleMapsNavBroadcastRegistered = true;
        } catch (Exception e) {
            log("Google Maps bildirim yayını kaydı: " + e.getMessage());
        }
    }

    private void unregisterGoogleMapsNavBroadcastReceiver() {
        if (!googleMapsNavBroadcastRegistered) {
            return;
        }
        try {
            unregisterReceiver(googleMapsNavSnapshotReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        googleMapsNavBroadcastRegistered = false;
    }

    private void applyNavigationClusterOpenFromBus(boolean open) {
        isNavigationOpen = open;
        ClusterNavigationState.setLastKnownOpen(open);
        if (projectionTabBuilder != null) {
            projectionTabBuilder.refreshProjectionStatusUi();
        }
    }

    private void onApkSessionStatus(int status, String message) {
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            log("APK kurulumu kullanıcı onayı bekliyor");
            return;
        }
        if (status == PackageInstaller.STATUS_SUCCESS) {
            Toast.makeText(this, R.string.apps_installed_success, Toast.LENGTH_SHORT).show();
            log("APK kuruldu");
            if (appsTabBuilder != null) {
                appsTabBuilder.loadAppsFromServer();
            }
            return;
        }
        String detail = message != null && !message.isEmpty() ? message : ("status " + status);
        Toast.makeText(this, getString(R.string.apps_install_error, detail), Toast.LENGTH_LONG).show();
        log("[ERROR] APK kurulum sonucu: " + detail);
        if (appsTabBuilder != null) {
            appsTabBuilder.clearInstallingProgress();
        }
    }

    @Override
    protected void onDestroy() {
        if (topBarBuilder != null) {
            topBarBuilder.release();
        }
        if (roadmateTabBuilder != null) {
            roadmateTabBuilder.release();
        }
        ApkSessionInstaller.clearListener(apkSessionListener);
        if (sBenchHost == this) {
            sBenchHost = null;
        }
        if (welcomeSoundTabBuilder != null) {
            welcomeSoundTabBuilder.releaseAudioFully();
        }
        if (appsTabBuilder != null) {
            appsTabBuilder.onTabHidden();
        }
        if (driverProfilesTabBuilder != null) {
            driverProfilesTabBuilder.release();
        }
        if (settingsTabBuilder != null) {
            settingsTabBuilder.release();
        }
        if (homeTabBuilder != null) {
            homeTabBuilder.release();
        }
        if (vehicleInfoTabBuilder != null) {
            vehicleInfoTabBuilder.release();
        }
        if (systemStatsOverlay != null) {
            systemStatsOverlay.release();
            systemStatsOverlay = null;
        }
        if (vehicleMetricsRepository != null) {
            vehicleMetricsRepository.release();
        }
        if (systemTts != null) {
            systemTts.stop();
            systemTts.shutdown();
            systemTts = null;
        }
        pendingTtsText = null;
        VehicleQuickControls.getInstance(getApplicationContext()).release();

        // WebServerManager'ı durdur
        if (webServerManager != null) {
            webServerManager.stopServer();
        }
        // KeyEvent logcat thread'ini temizle
        keyEventLogcatRunning = false;
        if (keyEventLogcatThread != null) {
            keyEventLogcatThread.interrupt();
        }
        if (vdbusBridgeAcquired) {
            vdbusBridgeAcquired = false;
            MapControlVDBusKeyBridge.release(this);
        }
        SystemLogBuffer.removeListener(systemLogListener);
        if (serviceInitializer != null) serviceInitializer.onDestroy();
        super.onDestroy();
    }

    private void renderSystemLogs(String text) {
        if (tvLogs == null) {
            return;
        }
        tvLogs.setText(text == null ? "" : text);
        if (scrollView != null) {
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void log(String msg) {
        String timestamp = now();
        String line = "[" + timestamp + "] " + msg + "\n";
        
        // Renkli log seviyelerini tespit et ve ayarla
        String coloredLine = line;
        if (msg.contains("[INFO]") || msg.contains("ℹ️") || msg.contains("📡") || msg.contains("🔌")) {
            coloredLine = "[" + timestamp + "] INFO " + msg.replace("[INFO]", "").trim() + "\n";
        } else if (msg.contains("[WARN]") || msg.contains("⚠️")) {
            coloredLine = "[" + timestamp + "] WARN " + msg.replace("[WARN]", "").trim() + "\n";
        } else if (msg.contains("[ERROR]") || msg.contains("❌") || msg.contains("ERR")) {
            coloredLine = "[" + timestamp + "] ERR " + msg.replace("[ERROR]", "").trim() + "\n";
        } else if (msg.contains("[SUCCESS]") || msg.contains("✅") || msg.contains("✓")) {
            coloredLine = "[" + timestamp + "] OK " + msg.replace("[SUCCESS]", "").trim() + "\n";
        } else if (msg.contains("[DEBUG]") || msg.contains("🐛") || msg.contains("DBG")) {
            coloredLine = "[" + timestamp + "] DBG " + msg.replace("[DEBUG]", "").trim() + "\n";
        }
        
        SystemLogBuffer.append(coloredLine);
    }

    // App operations moved to `AppsTabBuilder`.

    /**
     * Foreground Service'i başlat (arka planda çalışması için)
     */
    private void savePrefInt(String key, int value) {
        getSharedPreferences("MapControlPrefs", MODE_PRIVATE).edit().putInt(key, value).apply();
    }

    private void savePrefBool(String key, boolean value) {
        getSharedPreferences("MapControlPrefs", MODE_PRIVATE).edit().putBoolean(key, value).apply();
    }

    // saveDriveModeSetting / savePowerModeSetting / saveMapControlKeySetting / saveAutoCloseOnPowerOffSetting removed.

    /**
     * Seçili ISS ayarını kaydet
     */
    private void saveAssistSetting(String settingKey, int value) {
        savePrefInt(settingKey, value);
    }

    private void onValetStateChanged() {
        if (sideRailBuilder != null) {
            sideRailBuilder.setValetModeActive(ValetModeManager.shouldHidePersonal(this));
        }
        rebuildTopBarForUiMode();
        if (currentTab == TAB_VALET && valetTabBuilder != null) {
            valetTabBuilder.onTabVisible();
        }
    }

    /**
     * Araç / tablet: şerit genişliği ekranın ~%30–36’sı + dp clamp ({@code rail_min_width}..{@code rail_max_width}).
     */
    private int railWidthPx() {
        return CockpitTokens.dim(this, railExpanded
                ? R.dimen.ck_rail_w_expanded : R.dimen.ck_rail_w_collapsed);
    }

    private void applyRailExpanded(boolean expanded) {
        if (railExpanded == expanded) {
            return;
        }
        railExpanded = expanded;
        if (portraitShell) {
            return;
        }
        sidebarWidthPx = railWidthPx();
        if (sideRail != null) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) sideRail.getLayoutParams();
            lp.width = sidebarWidthPx;
            sideRail.setLayoutParams(lp);
        }
        applyLauncherChrome(LauncherModeManager.isEnabled(this));
    }

    /**
     * SharedPreferences'a targetPackage'ı kaydet
     */
    private void saveTargetPackage(String packageName) {
        try {
            targetPackage = TargetPackageStore.normalize(packageName);
            TargetPackageStore.writeAndBroadcast(this, targetPackage);
        } catch (Exception e) {
            log("saveTargetPackage hatası: " + e.getMessage());
        }
    }

    /**
     * SharedPreferences'tan targetPackage'ı yükle (boş kayıtta bellek de temizlenir).
     */
    private void loadTargetPackage() {
        try {
            targetPackage = TargetPackageStore.read(this);
            if (targetPackage != null && !targetPackage.isEmpty()) {
                log("Kaydedilmiş uygulama yüklendi: " + targetPackage);
            }
        } catch (Exception e) {
            log("loadTargetPackage hatası: " + e.getMessage());
        }
    }

    /**
     * Aynı host’ta /q açılırken /maps gibi yollarda Yandex Haritalar (veya App Link) intent’i alıp
     * ekran göstermeyebiliyor; doğrudan tarayıcı hiç seçilmiyor. Bu URL’lerde önce seçici gösterilir.
     */
    private static boolean shouldShowChooserToPickBrowserOrMaps(Uri uri) {
        if (uri == null) {
            return false;
        }
        String h = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
        String p = uri.getPath() != null ? uri.getPath().toLowerCase() : "";
        if (h.contains("yandex") && p.contains("maps")) {
            return true;
        }
        if (h.equals("maps.app.goo.gl")) {
            return true;
        }
        return false;
    }

    // App operations moved to `AppsTabBuilder`.
    
    // Profil sekmesi UI/logic `ProfileTabBuilder` içine taşındı.
    
    // Profil sekmesi helper/metodları `ProfileTabBuilder` içine taşındı.
    // parseMarkdown / processBoldText `SettingsTabBuilder` içine taşındı.
    
    // Settings tab builder is created in initializeApp.

    /**
     * Yüzen geri tuşu MapControl öndeyken sistem BACK'i yutuyor; sekme geri mantığını burada uygula.
     */
    public static boolean dispatchOverlayBack() {
        MainActivity activity = sBenchHost;
        if (activity == null || activity.isFinishing()) {
            return false;
        }
        activity.handler.post(() -> {
            if (activity.isFinishing()) {
                return;
            }
            activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
            activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK));
        });
        return true;
    }

    /**
     * Bench ekranından ana loga satır düşer (MainActivity yaşıyorsa).
     */
    public static void appendBenchLogToMainIfActive(String msg) {
        MainActivity a = sBenchHost;
        if (a == null || msg == null) {
            return;
        }
        a.handler.post(() -> a.log(msg));
    }

    /** VDBus 26/4 ile aynı cluster toggle yolu. */
    public static void benchNavKeyToggle() {
        MainActivity a = sBenchHost;
        if (a == null) {
            Log.w("MainActivity", "[Bench] Ana ekran hazır değil (MainActivity yok)");
            return;
        }
        a.handler.post(() -> {
            if (a.clusterDisplayManager == null) {
                a.log("[Bench] clusterDisplayManager yok");
                return;
            }
            if (a.isNavigationOpen) {
                a.clusterDisplayManager.closeClusterDisplay(false);
            } else {
                a.clusterDisplayManager.openClusterDisplay();
            }
        });
    }

    public static void benchAlertTone() {
        MainActivity a = sBenchHost;
        if (a == null) {
            Log.w("MainActivity", "[Bench] Ana ekran hazır değil; uyarı sesi atlandı");
            return;
        }
        a.handler.post(a::playSoftAlert);
    }

    public static void benchClusterOpenDirect() {
        MainActivity a = sBenchHost;
        if (a == null) {
            Log.w("MainActivity", "[Bench] Ana ekran hazır değil; cluster aç atlandı");
            return;
        }
        a.handler.post(() -> {
            if (a.clusterDisplayManager == null) {
                a.log("[Bench] clusterDisplayManager yok");
                return;
            }
            a.clusterDisplayManager.openClusterDisplay();
        });
    }

    public static void benchClusterCloseDirect() {
        MainActivity a = sBenchHost;
        if (a == null) {
            Log.w("MainActivity", "[Bench] Ana ekran hazır değil; cluster kapat atlandı");
            return;
        }
        a.handler.post(() -> {
            if (a.clusterDisplayManager == null) {
                a.log("[Bench] clusterDisplayManager yok");
                return;
            }
            a.clusterDisplayManager.closeClusterDisplay(false);
        });
    }
}
