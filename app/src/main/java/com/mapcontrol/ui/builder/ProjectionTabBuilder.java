package com.mapcontrol.ui.builder;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.view.Display;
import android.widget.Toast;

import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.media.LauncherMediaController;
import com.mapcontrol.nav.ClusterNavOverlayOffsets;
import com.mapcontrol.nav.GoogleMapsClusterNavOverlay;
import com.mapcontrol.nav.GoogleMapsNavNotificationCoordinator;
import com.mapcontrol.nav.GoogleMapsNavSnapshot;
import com.mapcontrol.nav.YandexClusterNavCoordinator;
import com.mapcontrol.nav.YandexClusterNavOverlay;
import com.mapcontrol.nav.YandexNavScraper;
import com.mapcontrol.service.GlobalBackService;
import com.mapcontrol.ui.cockpit.CockpitGrid;
import com.mapcontrol.ui.cockpit.CockpitTokens;
import com.mapcontrol.ui.theme.UiStyles;

import com.mapcontrol.manager.CarSystemGestureController;
import com.mapcontrol.util.AppLaunchHelper;
import com.mapcontrol.util.ClusterNavigationState;
import com.mapcontrol.util.DialogHelper;
import com.mapcontrol.util.GestureConflicts;
import com.mapcontrol.util.ProjectionTargetApps;
import com.mapcontrol.util.UsageAccessHelper;

public class ProjectionTabBuilder {
    public interface ProjectionCallback {
        void onOpenCluster();
        void onCloseCluster();
        void onSavePowerMode(int mode);
        void onStartKeyEventListener();
        void onStopKeyEventListener();
        String getTargetPackage();
        void onTargetPackageSelected(String packageName);
        boolean isSystemOrPrivApp(ApplicationInfo appInfo);
        void log(String message);
        /** "Ana Ekrana Al" sonrası cluster boşsa boot splash (ClusterDisplayManager + getAppOnDisplay2). */
        void onBringToMainDisplayCheckClusterSplash();
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final ProjectionCallback callback;
    private final Handler handler;

    private ScrollView scrollView;
    private LinearLayout projectionTabContent;
    private TextView targetAppLabel;
    private TextView projectionStatusText;
    private TextView googleMapsNavSummaryText;
    private LinearLayout googleProviderCard;
    private LinearLayout yandexProviderCard;
    private LinearLayout googleSettingsCard;
    private LinearLayout googleCardTabsRow;
    private LinearLayout yandexCardTabsRow;
    private LinearLayout yandexSettingsCard;

    public ProjectionTabBuilder(Context context, SharedPreferences prefs, ProjectionCallback callback) {
        this.context = context;
        this.prefs = prefs;
        this.callback = callback;
        this.handler = new Handler(Looper.getMainLooper());
        build();
    }


    public ScrollView build() {
        scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(Color.TRANSPARENT);
        scrollView.setFillViewport(true);

        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);
        int margin = CockpitTokens.dim(context, R.dimen.ck_margin);
        outer.setPadding(margin, CockpitTokens.dim(context, R.dimen.ck_space_sm),
                margin, margin);

        LinearLayout columns = new LinearLayout(context);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setBaselineAligned(false);

        LinearLayout leftCard = createSectionCard();
        LinearLayout rightCard = createSectionCard();
        buildCompactLeft(leftCard);
        buildCompactRight(rightCard);

        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.96f);
        leftLp.setMarginEnd(CockpitTokens.dim(context, R.dimen.ck_space_sm));
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.04f);
        rightLp.setMarginStart(CockpitTokens.dim(context, R.dimen.ck_space_sm));
        columns.addView(leftCard, leftLp);
        columns.addView(rightCard, rightLp);

        outer.addView(columns, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        projectionTabContent = outer;
        scrollView.addView(outer, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        handler.post(this::refreshProjectionStatusUi);
        refreshTargetLabel();
        refreshProviderSelection();
        return scrollView;
    }

    private void buildCompactLeft(LinearLayout card) {
        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(context);
        title.setText(R.string.projection_title);
        title.setTextSize(24);
        title.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_SEMIBOLD));
        title.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        titleRow.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView hud = new TextView(context);
        hud.setText(R.string.projection_hud_link_connected);
        hud.setTextSize(10);
        hud.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_SEMIBOLD));
        hud.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        hud.setPadding(8, 5, 8, 5);
        GradientDrawable hudBg = new GradientDrawable();
        hudBg.setColor(0xFF252E38);
        hudBg.setCornerRadius(4);
        hud.setBackground(hudBg);
        titleRow.addView(hud);
        card.addView(titleRow);

        LinearLayout status = new LinearLayout(context);
        status.setOrientation(LinearLayout.HORIZONTAL);
        status.setGravity(Gravity.CENTER_VERTICAL);
        TextView dot = new TextView(context);
        dot.setText("●");
        dot.setTextSize(10);
        dot.setTextColor(CockpitTokens.color(context, R.color.ck_nominal));
        status.addView(dot);
        TextView statusText = new TextView(context);
        statusText.setText(R.string.projection_status_off);
        statusText.setTextSize(13);
        statusText.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        statusLp.leftMargin = 6;
        status.addView(statusText, statusLp);
        LinearLayout.LayoutParams statusRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statusRowLp.topMargin = 4;
        statusRowLp.bottomMargin = 12;
        card.addView(status, statusRowLp);
        projectionStatusText = statusText;

        LinearLayout actionRow = new LinearLayout(context);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        Button open = compactButton(context.getString(R.string.projection_open), true);
        Button stop = compactButton(context.getString(R.string.projection_stop), false);
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(
                0, CockpitTokens.dim(context, R.dimen.button_height), 1f);
        b1.setMarginEnd(4);
        LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(
                0, CockpitTokens.dim(context, R.dimen.button_height), 1f);
        b2.setMarginStart(4);
        actionRow.addView(open, b1);
        actionRow.addView(stop, b2);
        card.addView(actionRow);

        open.setOnClickListener(v -> {
            callback.onOpenCluster();
            refreshProjectionStatusUi();
        });
        stop.setOnClickListener(v -> {
            callback.onCloseCluster();
            refreshProjectionStatusUi();
        });

        addCompactSectionHeader(card, R.string.projection_target_section,
                R.string.projection_target_single_tap);
        LinearLayout providers = new LinearLayout(context);
        providers.setOrientation(LinearLayout.HORIZONTAL);
        providers.setBaselineAligned(false);
        googleProviderCard = createProviderCard(
                AppLaunchHelper.GOOGLE_MAPS_PACKAGE,
                context.getString(R.string.projection_target_google),
                context.getString(R.string.projection_target_google_desc),
                R.drawable.ic_mdi_map);
        yandexProviderCard = createProviderCard(
                YandexNavScraper.PACKAGE_YANDEX_MAPS,
                context.getString(R.string.projection_target_yandex),
                context.getString(R.string.projection_target_yandex_desc),
                R.drawable.ic_mdi_map);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        gp.setMarginEnd(4);
        LinearLayout.LayoutParams yp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        yp.setMarginStart(4);
        providers.addView(googleProviderCard, gp);
        providers.addView(yandexProviderCard, yp);
        card.addView(providers);

        LinearLayout appRow = new LinearLayout(context);
        appRow.setOrientation(LinearLayout.HORIZONTAL);
        appRow.setGravity(Gravity.CENTER_VERTICAL);
        int appPad = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        appRow.setPadding(appPad, appPad, appPad, appPad);
        com.mapcontrol.ui.cockpit.CockpitSurfaces.level2(appRow);
        TextView appText = new TextView(context);
        appText.setText(R.string.projection_app_desc);
        appText.setTextSize(12);
        appText.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        appText.setMaxLines(1);
        appText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        appRow.addView(appText, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button change = compactButton(context.getString(R.string.projection_change_app), false);
        change.setTextSize(11);
        appRow.addView(change, compactButtonLp());
        Button launch = compactButton(context.getString(R.string.projection_launch_cluster), true);
        launch.setTextSize(11);
        appRow.addView(launch, compactButtonLp());
        LinearLayout.LayoutParams appRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        appRowLp.topMargin = 8;
        card.addView(appRow, appRowLp);
        change.setOnClickListener(v -> selectTargetApp());

        appText.setOnClickListener(v -> selectTargetApp());
        launch.setOnClickListener(v -> launchTargetOnMainDisplay());

        addCompactSectionHeader(card, R.string.projection_start_section, 0);
        final UiStyles.TernarySegmentHandle[] power = new UiStyles.TernarySegmentHandle[1];
        LinearLayout powerBlock = new LinearLayout(context);
        powerBlock.setOrientation(LinearLayout.VERTICAL);
        card.addView(powerBlock);
        power[0] = UiStyles.addTernarySegmentedControl(context, powerBlock, null,
                new String[]{context.getString(R.string.projection_power_engine),
                        context.getString(R.string.projection_power_ready),
                        context.getString(R.string.projection_power_manual)},
                new String[]{"", "", ""},
                new int[]{2, 1, 0}, prefs.getInt("powerModeSetting", 2),
                modeId -> {
                    callback.onSavePowerMode(modeId);
                    callback.log("Navigasyon açma modu: " + modeId);
                });
        handler.post(() -> power[0].syncVisualFromModeId(prefs.getInt("powerModeSetting", 2)));

        addCompactToggleRow(card, "Kapanış Otomasyonu",
                "Araç kapanınca yansıtma otomatik kapansın mı?",
                prefs.getBoolean("autoCloseOnPowerOff", true),
                isEnabled -> {
                    prefs.edit().putBoolean("autoCloseOnPowerOff", isEnabled).apply();
                    callback.log("Araç kapanınca otomatik kapatma: " + isEnabled);
                });
        addCompactToggleRow(card, "Harita Kontrol Tuşu",
                "Direksiyon/konsol donanım tuşuyla haritayı kontrol et.",
                prefs.getBoolean("mapControlKeyEnabled", true),
                isEnabled -> {
                    prefs.edit().putBoolean("mapControlKeyEnabled", isEnabled).apply();
                    if (isEnabled) callback.onStartKeyEventListener();
                    else callback.onStopKeyEventListener();
                });
        addCompactToggleRow(card, "İki Parmak Harita Kaydırma",
                "Sola: Göstergeye al | Sağa: Kapat",
                CarSystemGestureController.isEnabled(context),
                isEnabled -> {
                    if (isEnabled) {
                        int conflictOwner = GestureConflicts.findMapSwipeConflict(context);
                        if (conflictOwner != 0) {
                            DialogHelper.showGestureConflict(context, conflictOwner);
                            return;
                        }
                    }
                    if (isEnabled && !UsageAccessHelper.hasPackageUsageAccess(context)) {
                        UsageAccessHelper.openUsageAccessSettings(context);
                        Toast.makeText(context, R.string.projection_map_swipe_need_access,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    prefs.edit().putBoolean(CarSystemGestureController.PREF_ENABLED, isEnabled).apply();
                    callback.log("Harita kaydırma: " + isEnabled);
                });
    }

    private void buildCompactRight(LinearLayout host) {
        LinearLayout googlePanel = createSectionCard();
        LinearLayout yandexPanel = createSectionCard();
        buildCompactGooglePanel(googlePanel);
        buildCompactYandexPanel(yandexPanel);
        googleSettingsCard = googlePanel;
        yandexSettingsCard = yandexPanel;
        host.addView(googlePanel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        host.addView(yandexPanel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void buildCompactGooglePanel(LinearLayout card) {
        addProviderHeader(card, R.string.projection_google_settings_title,
                R.string.projection_google_settings_desc);
        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setBaselineAligned(false);
        LinearLayout nav = compactSettingCard("Navigasyon Bildirimi",
                "Bildirim gelince kümede otomatik başlat",
                GoogleMapsNavNotificationCoordinator.isAutoProjectionEnabled(context),
                enabled -> {
                    if (enabled && !LauncherMediaController.isNotificationAccessEnabled(context)) {
                        new LauncherMediaController(context).openNotificationAccessSettings();
                        Toast.makeText(context, R.string.projection_google_maps_nav_need_access,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    GoogleMapsNavNotificationCoordinator.setAutoProjectionEnabled(context, enabled);
                });
        LinearLayout cluster = compactSettingCard("Cluster Kartları (ETA & Dönüş)",
                "Manevra ve varış süresini kadrana yerleştir",
                GoogleMapsNavNotificationCoordinator.isClusterOverlayEnabled(context),
                enabled -> {
                    if (enabled && !LauncherMediaController.isNotificationAccessEnabled(context)) {
                        new LauncherMediaController(context).openNotificationAccessSettings();
                        Toast.makeText(context, R.string.projection_google_maps_nav_need_access,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    GoogleMapsNavNotificationCoordinator.setClusterOverlayEnabled(context, enabled);
                });
        LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        navLp.setMarginEnd(4);
        LinearLayout.LayoutParams clusterLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        clusterLp.setMarginStart(4);
        top.addView(nav, navLp);
        top.addView(cluster, clusterLp);
        card.addView(top);

        addCompactStyleHeader(card);
        addCompactStyleCards(card);
        addCompactLivePreview(card);
        addClusterOverlayLayoutSection(card,
                R.string.projection_google_cluster_position_title,
                ClusterNavOverlayOffsets.Provider.GOOGLE,
                () -> GoogleMapsClusterNavOverlay.getInstance(context).applyStoredOffsets());
    }

    private void buildCompactYandexPanel(LinearLayout card) {
        addProviderHeader(card, R.string.projection_yandex_settings_title,
                R.string.projection_yandex_settings_desc);
        addCompactToggleRow(card, context.getString(R.string.yandex_cluster_nav_section_title),
                context.getString(R.string.yandex_cluster_nav_section_desc),
                YandexClusterNavOverlay.isEnabled(context),
                enabled -> {
                    if (!enabled) {
                        YandexClusterNavCoordinator.deactivate(context);
                        return;
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                            && !android.provider.Settings.canDrawOverlays(context)) {
                        try {
                            Intent intent = new Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                            intent.setData(android.net.Uri.parse(
                                    "package:" + context.getPackageName()));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(intent);
                        } catch (Exception ignored) {
                        }
                        return;
                    }
                    if (!GlobalBackService.isRegisteredInSystemAccessibilitySettings(context)) {
                        YandexClusterNavCoordinator.openAccessibilitySettings(context);
                        return;
                    }
                    YandexClusterNavCoordinator.activate(context);
                });
        addClusterOverlayLayoutSection(card,
                R.string.projection_yandex_cluster_position_title,
                ClusterNavOverlayOffsets.Provider.YANDEX,
                () -> YandexClusterNavOverlay.getInstance(context).applyStoredOffsets());
    }

    private void addCompactSectionHeader(LinearLayout card, int titleRes, int hintRes) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(context);
        title.setText(titleRes);
        title.setTextSize(15);
        title.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_SEMIBOLD));
        title.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        row.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (hintRes != 0) {
            TextView hint = new TextView(context);
            hint.setText(hintRes);
            hint.setTextSize(11);
            hint.setTextColor(CockpitTokens.color(context, R.color.ck_accent));
            row.addView(hint);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 16;
        lp.bottomMargin = 7;
        card.addView(row, lp);
    }

    private void addCompactToggleRow(LinearLayout card, String title, String desc,
            boolean initial, java.util.function.Consumer<Boolean> onCommit) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        row.setPadding(pad, pad, pad, pad);
        com.mapcontrol.ui.cockpit.CockpitSurfaces.level2(row);
        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(14);
        titleView.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_SEMIBOLD));
        titleView.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        textCol.addView(titleView);
        TextView descView = new TextView(context);
        descView.setText(desc);
        descView.setTextSize(11);
        descView.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        descView.setMaxLines(1);
        descView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textCol.addView(descView);
        row.addView(textCol, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(createCompactToggle(initial, onCommit));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 7;
        card.addView(row, lp);
    }

    private LinearLayout compactSettingCard(String title, String desc, boolean initial,
            java.util.function.Consumer<Boolean> onCommit) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        card.setPadding(pad, pad, pad, pad);
        com.mapcontrol.ui.cockpit.CockpitSurfaces.level2(card);
        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(14);
        titleView.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_SEMIBOLD));
        titleView.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        card.addView(titleView);
        TextView descView = new TextView(context);
        descView.setText(desc);
        descView.setTextSize(11);
        descView.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        descView.setMaxLines(2);
        card.addView(descView);
        card.addView(createCompactToggle(initial, onCommit));
        return card;
    }

    private LinearLayout createCompactToggle(boolean initial,
            java.util.function.Consumer<Boolean> onCommit) {
        LinearLayout track = new LinearLayout(context);
        track.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CockpitTokens.color(context, R.color.ck_surface_2));
        bg.setCornerRadius(6);
        track.setBackground(bg);
        TextView on = new TextView(context);
        TextView off = new TextView(context);
        on.setText(R.string.common_on);
        off.setText(R.string.common_off);
        on.setTextSize(11);
        off.setTextSize(11);
        on.setGravity(Gravity.CENTER);
        off.setGravity(Gravity.CENTER);
        on.setPadding(10, 5, 10, 5);
        off.setPadding(10, 5, 10, 5);
        final boolean[] selected = {initial};
        Runnable render = () -> {
            on.setBackground(compactSegmentBg(selected[0]));
            off.setBackground(compactSegmentBg(!selected[0]));
            on.setTextColor(CockpitTokens.color(context,
                    selected[0] ? R.color.ck_on_accent : R.color.ck_text_medium));
            off.setTextColor(CockpitTokens.color(context,
                    !selected[0] ? R.color.ck_on_accent : R.color.ck_text_medium));
        };
        on.setOnClickListener(v -> { selected[0] = true; render.run(); onCommit.accept(true); });
        off.setOnClickListener(v -> { selected[0] = false; render.run(); onCommit.accept(false); });
        track.addView(on);
        track.addView(off);
        render.run();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 6;
        track.setLayoutParams(lp);
        return track;
    }

    private GradientDrawable compactSegmentBg(boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CockpitTokens.color(context,
                selected ? R.color.ck_accent : R.color.ck_surface_1));
        bg.setCornerRadius(5);
        return bg;
    }

    private Button compactButton(String text, boolean accent) {
        Button b = new Button(context);
        b.setText(text);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(12, 6, 12, 6);
        UiStyles.styleOemButton(b, CockpitTokens.color(context,
                accent ? R.color.ck_accent : R.color.ck_surface_2));
        b.setTextColor(CockpitTokens.color(context,
                accent ? R.color.ck_on_accent : R.color.ck_text_high));
        return b;
    }

    private LinearLayout.LayoutParams compactButtonLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(6);
        return lp;
    }

    private void launchTargetOnMainDisplay() {
        String pkg = callback.getTargetPackage();
        if (pkg == null || pkg.trim().isEmpty()) {
            Toast.makeText(context, R.string.projection_select_app_first, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            AppLaunchHelper.launchAppOnDisplay(context, pkg.trim(),
                    Display.DEFAULT_DISPLAY, Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            callback.onBringToMainDisplayCheckClusterSplash();
        } catch (Exception e) {
            callback.log("Uygulama başlatılamadı: " + e.getMessage());
        }
    }

    private void addCompactStyleHeader(LinearLayout card) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(context);
        title.setText(R.string.projection_google_cluster_design_title);
        title.setTextSize(13);
        title.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_SEMIBOLD));
        title.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        row.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView hint = new TextView(context);
        hint.setText("4 Hazır Stil");
        hint.setTextSize(11);
        hint.setTextColor(CockpitTokens.color(context, R.color.ck_accent));
        row.addView(hint);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 16;
        lp.bottomMargin = 7;
        card.addView(row, lp);
    }

    private void addCompactStyleCards(LinearLayout card) {
        String[] styles = {
                GoogleMapsClusterNavOverlay.STYLE_HUD,
                GoogleMapsClusterNavOverlay.STYLE_DUAL,
                GoogleMapsClusterNavOverlay.STYLE_RADIAL,
                GoogleMapsClusterNavOverlay.STYLE_TRAFFIC
        };
        int[] labels = {
                R.string.projection_cluster_variant1_title,
                R.string.projection_cluster_variant2_title,
                R.string.projection_cluster_variant3_title,
                R.string.projection_cluster_variant4_title
        };
        String[] descs = {
                "Mesafe, cadde, süre ve varış tek parçada",
                "Manevra ve ETA ayrı; bağımsız konum/opaklık",
                "İkon, mesafe, cadde, süre ve varış tek satırda",
                "Üst pill manevra, alt pill süre/mesafe/varış"
        };
        LinearLayout selectedLine = null;
        java.util.List<LinearLayout> cards = new java.util.ArrayList<>();
        String selected = GoogleMapsClusterNavOverlay.getStyle(context);
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(context);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setBaselineAligned(false);
            for (int col = 0; col < 2; col++) {
                int i = row * 2 + col;
                LinearLayout option = new LinearLayout(context);
                option.setOrientation(LinearLayout.VERTICAL);
                int pad = CockpitTokens.dim(context, R.dimen.ck_space_sm);
                option.setPadding(pad, pad, pad, pad);
                com.mapcontrol.ui.cockpit.CockpitSurfaces.level2(option);
                option.setClickable(true);
                TextView badge = new TextView(context);
                badge.setText("VARYANT " + (i + 1));
                badge.setTextSize(9);
                badge.setTextColor(CockpitTokens.color(context, R.color.ck_accent));
                option.addView(badge);
                TextView name = new TextView(context);
                name.setText(labels[i]);
                name.setTextSize(13);
                name.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_SEMIBOLD));
                name.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
                option.addView(name);
                TextView desc = new TextView(context);
                desc.setText(descs[i]);
                desc.setTextSize(10);
                desc.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
                option.addView(desc);
                String st = styles[i];
                option.setOnClickListener(v -> {
                    GoogleMapsClusterNavOverlay.setStyle(context, st);
                    GoogleMapsClusterNavOverlay.getInstance(context).apply(
                            GoogleMapsNavNotificationCoordinator.getLastSnapshot());
                    applyClusterStyleSelection(cards, styles, st);
                    updateCardTabsVisibility();
                });
                cards.add(option);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (col == 0) lp.setMarginEnd(4); else lp.setMarginStart(4);
                line.addView(option, lp);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = 6;
            card.addView(line, lp);
        }
        applyClusterStyleSelection(cards, styles, selected);
    }

    private void addCompactLivePreview(LinearLayout card) {
        LinearLayout previewRow = new LinearLayout(context);
        previewRow.setOrientation(LinearLayout.HORIZONTAL);
        previewRow.setGravity(Gravity.CENTER_VERTICAL);
        int pad = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        previewRow.setPadding(pad, pad, pad, pad);
        com.mapcontrol.ui.cockpit.CockpitSurfaces.level2(previewRow);
        TextView badge = new TextView(context);
        badge.setText("CANLI ÖNİZLEME");
        badge.setTextSize(9);
        badge.setTextColor(CockpitTokens.color(context, R.color.ck_accent));
        previewRow.addView(badge);
        View preview = buildClusterStylePreview(
                GoogleMapsClusterNavOverlay.getStyle(context));
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(
                0, CockpitTokens.dim(context, R.dimen.ck_touch_primary), 1f);
        previewLp.setMarginStart(8);
        previewRow.addView(preview, previewLp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 10;
        card.addView(previewRow, lp);
    }

    private ScrollView buildLegacy() {
        scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(Color.TRANSPARENT);
        scrollView.setPadding(0, 0, 0, 0);
        scrollView.setFillViewport(true);

        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);
        int margin = CockpitTokens.dim(context, R.dimen.ck_margin);
        outer.setPadding(margin, CockpitTokens.dim(context, R.dimen.ck_space_sm),
                margin, margin);

        LinearLayout pageHeader = new LinearLayout(context);
        pageHeader.setOrientation(LinearLayout.VERTICAL);
        TextView pageTitle = new TextView(context);
        pageTitle.setText(R.string.projection_title);
        CockpitTokens.headlineMd(pageTitle);
        pageHeader.addView(pageTitle);
        TextView pageSubtitle = new TextView(context);
        pageSubtitle.setText(R.string.ck_page_projection_sub);
        CockpitTokens.bodySm(pageSubtitle);
        pageSubtitle.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        LinearLayout.LayoutParams pageSubLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pageSubLp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_xs);
        pageSubLp.bottomMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
        pageHeader.addView(pageSubtitle, pageSubLp);
        outer.addView(pageHeader, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        projectionTabContent = new LinearLayout(context);
        projectionTabContent.setOrientation(LinearLayout.VERTICAL);
        projectionTabContent.setPadding(0, 0, 0, 0);
        projectionTabContent.setBackgroundColor(Color.TRANSPARENT);

        CockpitGrid projectionGrid = new CockpitGrid(context);
        LinearLayout leftCard = createSectionCard();
        outer.addView(projectionTabContent, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        scrollView.addView(outer, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView projectionTitle = new TextView(context);
        projectionTitle.setText(R.string.projection_title);
        CockpitTokens.title(projectionTitle);
        projectionTitle.setPadding(0, 0, 0, CockpitTokens.dim(context, R.dimen.ck_space_sm));
        leftCard.addView(projectionTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout statusRow = new LinearLayout(context);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView statusDot = new TextView(context);
        statusDot.setText("●");
        statusDot.setTextSize(11);
        statusDot.setTextColor(CockpitTokens.color(context, R.color.ck_nominal));
        statusRow.addView(statusDot);
        TextView projectionStatus = new TextView(context);
        projectionStatus.setText(R.string.projection_status_off);
        CockpitTokens.bodySm(projectionStatus);
        projectionStatus.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        statusLp.leftMargin = 6;
        statusRow.addView(projectionStatus, statusLp);
        TextView hudLink = new TextView(context);
        hudLink.setText(R.string.projection_hud_link_connected);
        hudLink.setTextSize(10);
        hudLink.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_SEMIBOLD));
        hudLink.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        hudLink.setPadding(8, 4, 8, 4);
        GradientDrawable hudBg = new GradientDrawable();
        hudBg.setColor(0xFF252E38);
        hudBg.setCornerRadius(4);
        hudLink.setBackground(hudBg);
        statusRow.addView(hudLink);
        LinearLayout.LayoutParams statusRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statusRowLp.bottomMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
        leftCard.addView(statusRow, statusRowLp);
        projectionStatusText = projectionStatus;

        LinearLayout controlButtonContainer = new LinearLayout(context);
        controlButtonContainer.setOrientation(LinearLayout.HORIZONTAL);
        controlButtonContainer.setPadding(16, 0, 16, 16);

        Button btnOpen = new Button(context);
        btnOpen.setText(R.string.projection_open);
        btnOpen.setTextSize(18);
        btnOpen.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_SEMIBOLD));
        UiStyles.styleOemButton(btnOpen, CockpitTokens.color(context, R.color.ck_accent));
        btnOpen.setTextColor(CockpitTokens.color(context, R.color.ck_on_accent));
        btnOpen.setMinHeight(CockpitTokens.dim(context, R.dimen.ck_touch_primary));
        btnOpen.setPadding(16, 12, 16, 12);
        btnOpen.setMinWidth(CockpitTokens.dim(context, R.dimen.ck_action_min_w));
        btnOpen.setMaxWidth(CockpitTokens.dim(context, R.dimen.ck_action_max_w));
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        openParams.setMargins(0, 0, 8, 0);
        btnOpen.setId(View.generateViewId());
        controlButtonContainer.addView(btnOpen, openParams);

        Button btnClose = new Button(context);
        btnClose.setText(R.string.projection_stop);
        btnClose.setTextSize(18);
        btnClose.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_SEMIBOLD));
        UiStyles.styleOemButton(btnClose, CockpitTokens.color(context, R.color.ck_surface_2));
        btnClose.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        btnClose.setMinHeight(CockpitTokens.dim(context, R.dimen.ck_touch_primary));
        btnClose.setPadding(16, 12, 16, 12);
        btnClose.setMinWidth(CockpitTokens.dim(context, R.dimen.ck_action_min_w));
        btnClose.setMaxWidth(CockpitTokens.dim(context, R.dimen.ck_action_max_w));
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnClose.setId(View.generateViewId());
        controlButtonContainer.addView(btnClose, closeParams);

        leftCard.addView(controlButtonContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        btnOpen.setOnClickListener(v -> {
            callback.onOpenCluster();
            refreshProjectionStatusUi();
            handleButtonClickWithDelay(btnOpen,
                    context.getString(R.string.projection_open),
                    context.getString(R.string.projection_projecting));
        });

        btnClose.setOnClickListener(v -> {
            callback.onCloseCluster();
            refreshProjectionStatusUi();
            handleButtonClickWithDelay(btnClose,
                    context.getString(R.string.projection_stop),
                    context.getString(R.string.projection_stopping));
        });

        handler.post(this::refreshProjectionStatusUi);

        TextView appTitle = new TextView(context);
        appTitle.setText(R.string.projection_target_section);
        CockpitTokens.title(appTitle);
        appTitle.setPadding(0, CockpitTokens.dim(context, R.dimen.ck_space_md), 0,
                CockpitTokens.dim(context, R.dimen.ck_space_sm));
        LinearLayout targetHeader = new LinearLayout(context);
        targetHeader.setOrientation(LinearLayout.HORIZONTAL);
        targetHeader.setGravity(Gravity.CENTER_VERTICAL);
        targetHeader.addView(appTitle, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView targetHint = new TextView(context);
        targetHint.setText(R.string.projection_target_single_tap);
        targetHint.setTextSize(11);
        targetHint.setTextColor(CockpitTokens.color(context, R.color.ck_accent));
        targetHeader.addView(targetHint);
        leftCard.addView(targetHeader, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout providerRow = new LinearLayout(context);
        providerRow.setOrientation(LinearLayout.HORIZONTAL);
        providerRow.setBaselineAligned(false);
        googleProviderCard = createProviderCard(
                AppLaunchHelper.GOOGLE_MAPS_PACKAGE,
                context.getString(R.string.projection_target_google),
                context.getString(R.string.projection_target_google_desc),
                R.drawable.ic_mdi_map);
        yandexProviderCard = createProviderCard(
                YandexNavScraper.PACKAGE_YANDEX_MAPS,
                context.getString(R.string.projection_target_yandex),
                context.getString(R.string.projection_target_yandex_desc),
                R.drawable.ic_mdi_map);
        LinearLayout.LayoutParams gpLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        gpLp.setMarginEnd(6);
        providerRow.addView(googleProviderCard, gpLp);
        LinearLayout.LayoutParams ypLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        ypLp.setMarginStart(6);
        providerRow.addView(yandexProviderCard, ypLp);
        LinearLayout.LayoutParams providerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        providerLp.setMargins(0, 0, 0, CockpitTokens.dim(context, R.dimen.ck_space_md));
        leftCard.addView(providerRow, providerLp);

        LinearLayout appCard = new LinearLayout(context);
        appCard.setOrientation(LinearLayout.VERTICAL);
        com.mapcontrol.ui.cockpit.CockpitSurfaces.level2(appCard);
        appCard.setPadding(CockpitTokens.dim(context, R.dimen.ck_space_md),
                CockpitTokens.dim(context, R.dimen.ck_space_md),
                CockpitTokens.dim(context, R.dimen.ck_space_md),
                CockpitTokens.dim(context, R.dimen.ck_space_md));
        LinearLayout.LayoutParams appCardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        appCardParams.setMargins(0, 0, 0, CockpitTokens.dim(context, R.dimen.ck_space_md));

        LinearLayout appInfo = new LinearLayout(context);
        appInfo.setOrientation(LinearLayout.HORIZONTAL);
        appInfo.setPadding(0, 0, 0, 20);

        LinearLayout iconBox = new LinearLayout(context);
        iconBox.setOrientation(LinearLayout.VERTICAL);
        com.mapcontrol.ui.cockpit.CockpitSurfaces.level1(iconBox);
        iconBox.setGravity(Gravity.CENTER);
        iconBox.setPadding(16, 16, 16, 16);

        AppCompatImageView mapIcon = new AppCompatImageView(context);
        mapIcon.setImageResource(R.drawable.ic_mdi_map);
        mapIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int mapIconPx = Math.round(36 * context.getResources().getDisplayMetrics().density);
        mapIcon.setLayoutParams(new LinearLayout.LayoutParams(mapIconPx, mapIconPx));
        mapIcon.setImageTintList(ColorStateList.valueOf(
                CockpitTokens.color(context, R.color.ck_accent)));
        iconBox.addView(mapIcon);

        LinearLayout.LayoutParams iconBoxParams = new LinearLayout.LayoutParams(80, 80);
        iconBoxParams.setMargins(0, 0, 16, 0);
        appInfo.addView(iconBox, iconBoxParams);

        LinearLayout textInfo = new LinearLayout(context);
        textInfo.setOrientation(LinearLayout.VERTICAL);
        textInfo.setPadding(0, 0, 0, 0);

        targetAppLabel = new TextView(context);
        targetAppLabel.setText(R.string.common_not_selected);
        CockpitTokens.title(targetAppLabel);
        LinearLayout.LayoutParams targetLabelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        targetLabelParams.setMargins(0, 0, 0, 4);
        textInfo.addView(targetAppLabel, targetLabelParams);

        TextView appDesc = new TextView(context);
        appDesc.setText(R.string.projection_app_desc);
        CockpitTokens.bodySm(appDesc);
        appDesc.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        textInfo.addView(appDesc);

        appInfo.addView(textInfo, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        appCard.addView(appInfo);

        LinearLayout appButtons = new LinearLayout(context);
        appButtons.setOrientation(LinearLayout.HORIZONTAL);

        Button btnSelectApp = new Button(context);
        btnSelectApp.setText(R.string.projection_change_app);
        btnSelectApp.setTextSize(15);
        btnSelectApp.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_SEMIBOLD));
        UiStyles.styleOemButton(btnSelectApp, CockpitTokens.color(context, R.color.ck_surface_2));
        btnSelectApp.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        btnSelectApp.setMinHeight(CockpitTokens.dim(context, R.dimen.ck_touch_min));
        btnSelectApp.setPadding(16, 10, 16, 10);
        btnSelectApp.setMinWidth(CockpitTokens.dim(context, R.dimen.ck_action_min_w));
        btnSelectApp.setMaxWidth(CockpitTokens.dim(context, R.dimen.ck_action_max_w));
        LinearLayout.LayoutParams selectParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        selectParams.setMargins(0, 0, 8, 0);
        appButtons.addView(btnSelectApp, selectParams);
        btnSelectApp.setOnClickListener(v -> selectTargetApp());

        Button btnLaunchOnCluster = new Button(context);
        btnLaunchOnCluster.setText(R.string.projection_launch_cluster);
        btnLaunchOnCluster.setTextSize(15);
        btnLaunchOnCluster.setTypeface(CockpitTokens.inter(context,
                CockpitTokens.WEIGHT_SEMIBOLD));
        UiStyles.styleOemButton(btnLaunchOnCluster,
                CockpitTokens.color(context, R.color.ck_accent));
        btnLaunchOnCluster.setTextColor(CockpitTokens.color(context, R.color.ck_on_accent));
        btnLaunchOnCluster.setMinHeight(CockpitTokens.dim(context, R.dimen.ck_touch_min));
        btnLaunchOnCluster.setPadding(16, 10, 16, 10);
        btnLaunchOnCluster.setMinWidth(CockpitTokens.dim(context, R.dimen.ck_action_min_w));
        btnLaunchOnCluster.setMaxWidth(CockpitTokens.dim(context, R.dimen.ck_action_max_w));
        LinearLayout.LayoutParams launchParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        launchParams.setMargins(0, 0, 0, 0);
        appButtons.addView(btnLaunchOnCluster, launchParams);
        btnLaunchOnCluster.setOnClickListener(v -> {
            String pkg = callback.getTargetPackage();
            if (pkg == null || pkg.trim().isEmpty()) {
                Toast.makeText(context, R.string.projection_select_app_first, Toast.LENGTH_SHORT).show();
                callback.log("Uygulama seçilmedi");
                return;
            }
            String trimmed = pkg.trim();
            if (context.getPackageManager().getLaunchIntentForPackage(trimmed) == null) {
                Toast.makeText(context, context.getString(R.string.projection_app_not_found, pkg), Toast.LENGTH_SHORT).show();
                callback.log("Launch intent bulunamadı: " + pkg);
                return;
            }
            try {
                AppLaunchHelper.launchAppOnDisplay(context, trimmed, Display.DEFAULT_DISPLAY, Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                callback.log("Uygulama başlatıldı (displayId=" + Display.DEFAULT_DISPLAY + "): " + pkg);
                callback.onBringToMainDisplayCheckClusterSplash();
            } catch (Exception e) {
                callback.log("launchSelectedAppOnDisplay hatası: " + e.getMessage());
                Toast.makeText(context, context.getString(R.string.common_error_prefix, e.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });

        appCard.addView(appButtons);
        leftCard.addView(appCard, appCardParams);

        TextView mainGroupTitle = new TextView(context);
        mainGroupTitle.setText(R.string.projection_nav_behavior);
        mainGroupTitle.setTextSize(18);
        mainGroupTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        mainGroupTitle.setTypeface(null, Typeface.BOLD);
        mainGroupTitle.setPadding(16, 24, 16, 8);
        // mainGroupTitle kullanilmiyor: konu kartlari kendi basliklarini tasiyor.

        LinearLayout mainCardContainer = new LinearLayout(context);
        mainCardContainer.setOrientation(LinearLayout.VERTICAL);
        com.mapcontrol.ui.cockpit.CockpitSurfaces.level1(mainCardContainer);
        LinearLayout.LayoutParams mainCardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        mainCardParams.setMargins(16, 0, 16, 32);

        TextView section1Title = new TextView(context);
        section1Title.setText(R.string.projection_start_section);
        section1Title.setTextSize(17);
        section1Title.setTextColor(UiStyles.color(context, R.color.textPrimary));
        section1Title.setTypeface(null, Typeface.BOLD);
        section1Title.setPadding(20, 20, 20, 8);
        mainCardContainer.addView(section1Title);

        TextView section1Desc = new TextView(context);
        section1Desc.setText(R.string.projection_start_desc);
        section1Desc.setTextSize(13);
        section1Desc.setTextColor(UiStyles.color(context, R.color.textHint));
        section1Desc.setPadding(20, 0, 20, 12);
        mainCardContainer.addView(section1Desc);

        final UiStyles.TernarySegmentHandle[] powerHandle = new UiStyles.TernarySegmentHandle[1];
        powerHandle[0] = UiStyles.addTernarySegmentedControl(context, mainCardContainer,
                null,
                new String[]{
                        context.getString(R.string.projection_power_engine),
                        context.getString(R.string.projection_power_ready),
                        context.getString(R.string.projection_power_manual)
                },
                new String[]{
                        context.getString(R.string.projection_power_engine_help),
                        context.getString(R.string.projection_power_ready_help),
                        context.getString(R.string.projection_power_manual_help)
                },
                new int[]{2, 1, 0},
                prefs.getInt("powerModeSetting", 2),
                modeId -> {
                    callback.onSavePowerMode(modeId);
                    String modeName = modeId == 2 ? "Motor Çalışınca"
                            : (modeId == 1 ? "Araç Hazır Durumdayken" : "Elle Çalıştır");
                    callback.log("Navigasyon açma modu: " + modeName);
                });
        handler.post(() -> powerHandle[0].syncVisualFromModeId(prefs.getInt("powerModeSetting", 2)));

        View sectionDivider1 = new View(context);
        sectionDivider1.setBackgroundColor(UiStyles.color(context, R.color.dividerWhite12));
        LinearLayout.LayoutParams dividerParams1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        dividerParams1.setMargins(20, 24, 20, 24);
        mainCardContainer.addView(sectionDivider1, dividerParams1);

        TextView section2Title = new TextView(context);
        section2Title.setText(R.string.projection_stop_section);
        section2Title.setTextSize(15);
        section2Title.setTextColor(UiStyles.color(context, R.color.textPrimary87));
        section2Title.setTypeface(null, Typeface.NORMAL);
        section2Title.setPadding(20, 0, 20, 8);
        mainCardContainer.addView(section2Title);

        TextView section2Desc = new TextView(context);
        section2Desc.setText(R.string.projection_stop_desc);
        section2Desc.setTextSize(13);
        section2Desc.setTextColor(UiStyles.color(context, R.color.textHint));
        section2Desc.setPadding(20, 0, 20, 12);
        mainCardContainer.addView(section2Desc);

        LinearLayout autoCloseBlock = new LinearLayout(context);
        autoCloseBlock.setOrientation(LinearLayout.VERTICAL);
        autoCloseBlock.setPadding(20, 0, 20, 0);
        mainCardContainer.addView(autoCloseBlock);

        UiStyles.addBinarySegmentedControl(context, autoCloseBlock,
                null,
                context.getString(R.string.common_yes),
                context.getString(R.string.common_no),
                context.getString(R.string.projection_auto_close_on_help),
                context.getString(R.string.projection_auto_close_off_help),
                prefs.getBoolean("autoCloseOnPowerOff", true),
                isEnabled -> {
                    prefs.edit().putBoolean("autoCloseOnPowerOff", isEnabled).apply();
                    callback.log(isEnabled
                            ? "Araç kapanınca otomatik kapatma açıldı"
                            : "Araç kapanınca otomatik kapatma kapatıldı");
                });

        View sectionDivider2 = new View(context);
        sectionDivider2.setBackgroundColor(UiStyles.color(context, R.color.dividerWhite12));
        LinearLayout.LayoutParams dividerParams2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        dividerParams2.setMargins(20, 24, 20, 24);
        mainCardContainer.addView(sectionDivider2, dividerParams2);

        TextView mapKeyTitle = new TextView(context);
        mapKeyTitle.setText(R.string.projection_map_key_title);
        mapKeyTitle.setTextSize(15);
        mapKeyTitle.setTextColor(UiStyles.color(context, R.color.textPrimary87));
        mapKeyTitle.setTypeface(null, Typeface.NORMAL);
        mapKeyTitle.setPadding(20, 0, 20, 8);
        mainCardContainer.addView(mapKeyTitle);

        TextView mapKeyDesc = new TextView(context);
        mapKeyDesc.setText(R.string.projection_map_key_desc);
        mapKeyDesc.setTextSize(13);
        mapKeyDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        mapKeyDesc.setPadding(20, 0, 20, 12);
        mainCardContainer.addView(mapKeyDesc);

        LinearLayout mapControlBlock = new LinearLayout(context);
        mapControlBlock.setOrientation(LinearLayout.VERTICAL);
        mapControlBlock.setPadding(20, 0, 20, 0);
        mainCardContainer.addView(mapControlBlock);

        UiStyles.addBinarySegmentedControl(context, mapControlBlock,
                null,
                context.getString(R.string.common_on),
                context.getString(R.string.common_off),
                context.getString(R.string.projection_map_key_on_help),
                context.getString(R.string.projection_map_key_off_help),
                prefs.getBoolean("mapControlKeyEnabled", true),
                isEnabled -> {
                    prefs.edit().putBoolean("mapControlKeyEnabled", isEnabled).apply();
                    if (isEnabled) {
                        callback.log("Harita kontrol tuşu açıldı");
                        callback.onStartKeyEventListener();
                    } else {
                        callback.log("Harita kontrol tuşu kapatıldı");
                        callback.onStopKeyEventListener();
                    }
                });

        View sectionDividerMaps = new View(context);
        sectionDividerMaps.setBackgroundColor(UiStyles.color(context, R.color.dividerWhite12));
        LinearLayout.LayoutParams dividerParamsMaps = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        dividerParamsMaps.setMargins(20, 24, 20, 24);
        mainCardContainer.addView(sectionDividerMaps, dividerParamsMaps);

        TextView mapsNavTitle = new TextView(context);
        mapsNavTitle.setText(R.string.projection_google_maps_nav_title);
        mapsNavTitle.setTextSize(15);
        mapsNavTitle.setTextColor(UiStyles.color(context, R.color.textPrimary87));
        mapsNavTitle.setTypeface(null, Typeface.NORMAL);
        mapsNavTitle.setPadding(20, 0, 20, 8);
        mainCardContainer.addView(mapsNavTitle);

        TextView mapsNavDesc = new TextView(context);
        mapsNavDesc.setText(R.string.projection_google_maps_nav_desc);
        mapsNavDesc.setTextSize(13);
        mapsNavDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        mapsNavDesc.setPadding(20, 0, 20, 12);
        mapsNavDesc.setLineSpacing(3, 1.05f);
        mainCardContainer.addView(mapsNavDesc);

        LinearLayout mapsNavBlock = new LinearLayout(context);
        mapsNavBlock.setOrientation(LinearLayout.VERTICAL);
        mapsNavBlock.setPadding(20, 0, 20, 0);
        mainCardContainer.addView(mapsNavBlock);

        final UiStyles.BinarySegmentHandle[] mapsNavHandle = new UiStyles.BinarySegmentHandle[1];
        mapsNavHandle[0] = UiStyles.addBinarySegmentedControl(context, mapsNavBlock,
                null,
                context.getString(R.string.common_on),
                context.getString(R.string.common_off),
                context.getString(R.string.projection_google_maps_nav_on_help),
                context.getString(R.string.projection_google_maps_nav_off_help),
                GoogleMapsNavNotificationCoordinator.isAutoProjectionEnabled(context),
                isEnabled -> {
                    if (isEnabled && !LauncherMediaController.isNotificationAccessEnabled(context)) {
                        LauncherMediaController controller = new LauncherMediaController(context);
                        controller.openNotificationAccessSettings();
                        Toast.makeText(context,
                                R.string.projection_google_maps_nav_need_access,
                                Toast.LENGTH_LONG).show();
                        mapsNavHandle[0].setLeftSelected(false);
                        callback.log("Google Maps bildirimi: bildirim erişimi kapalı");
                        return;
                    }
                    GoogleMapsNavNotificationCoordinator.setAutoProjectionEnabled(context, isEnabled);
                    callback.log(isEnabled
                            ? "Google Maps bildirimiyle otomatik yansıtma açıldı"
                            : "Google Maps bildirimiyle otomatik yansıtma kapatıldı");
                });

        googleMapsNavSummaryText = new TextView(context);
        googleMapsNavSummaryText.setTextSize(13);
        googleMapsNavSummaryText.setTextColor(UiStyles.color(context, R.color.textHint));
        googleMapsNavSummaryText.setPadding(20, 12, 20, 8);
        mainCardContainer.addView(googleMapsNavSummaryText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        updateGoogleMapsNavSummary(GoogleMapsNavNotificationCoordinator.getLastSnapshot());

        TextView mapSwipeTitle = new TextView(context);
        mapSwipeTitle.setText(R.string.projection_map_swipe_title);
        mapSwipeTitle.setTextSize(15);
        mapSwipeTitle.setTextColor(UiStyles.color(context, R.color.textPrimary87));
        mapSwipeTitle.setTypeface(null, Typeface.NORMAL);
        mapSwipeTitle.setPadding(20, 20, 20, 8);
        mainCardContainer.addView(mapSwipeTitle);

        TextView mapSwipeDesc = new TextView(context);
        mapSwipeDesc.setText(R.string.projection_map_swipe_desc);
        mapSwipeDesc.setTextSize(13);
        mapSwipeDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        mapSwipeDesc.setPadding(20, 0, 20, 12);
        mapSwipeDesc.setLineSpacing(3, 1.05f);
        mainCardContainer.addView(mapSwipeDesc);

        LinearLayout mapSwipeBlock = new LinearLayout(context);
        mapSwipeBlock.setOrientation(LinearLayout.VERTICAL);
        mapSwipeBlock.setPadding(20, 0, 20, 0);
        mainCardContainer.addView(mapSwipeBlock);

        final UiStyles.BinarySegmentHandle[] mapSwipeHandle = new UiStyles.BinarySegmentHandle[1];
        mapSwipeHandle[0] = UiStyles.addBinarySegmentedControl(context, mapSwipeBlock,
                null,
                context.getString(R.string.common_on),
                context.getString(R.string.common_off),
                context.getString(R.string.projection_map_swipe_on_help),
                context.getString(R.string.projection_map_swipe_off_help),
                CarSystemGestureController.isEnabled(context),
                isEnabled -> {
                    if (isEnabled) {
                        int conflictOwner = GestureConflicts.findMapSwipeConflict(context);
                        if (conflictOwner != 0) {
                            mapSwipeHandle[0].syncVisualWithoutCommit(false);
                            prefs.edit().putBoolean(CarSystemGestureController.PREF_ENABLED, false).apply();
                            DialogHelper.showGestureConflict(context, conflictOwner);
                            callback.log("Harita kaydırma: hareket başka özellikte kullanılıyor");
                            return;
                        }
                    }
                    if (isEnabled && !UsageAccessHelper.hasPackageUsageAccess(context)) {
                        UsageAccessHelper.openUsageAccessSettings(context);
                        Toast.makeText(context,
                                R.string.projection_map_swipe_need_access,
                                Toast.LENGTH_LONG).show();
                        mapSwipeHandle[0].syncVisualWithoutCommit(false);
                        prefs.edit().putBoolean(CarSystemGestureController.PREF_ENABLED, false).apply();
                        callback.log("Harita kaydırma: kullanım erişimi kapalı");
                        return;
                    }
                    prefs.edit().putBoolean(CarSystemGestureController.PREF_ENABLED, isEnabled).apply();
                    callback.log(isEnabled
                            ? "Harita kaydırma açıldı"
                            : "Harita kaydırma kapatıldı");
                });

        TextView mapsClusterTitle = new TextView(context);
        mapsClusterTitle.setText(R.string.projection_google_maps_cluster_cards_title);
        mapsClusterTitle.setTextSize(15);
        mapsClusterTitle.setTextColor(UiStyles.color(context, R.color.textPrimary87));
        mapsClusterTitle.setTypeface(null, Typeface.NORMAL);
        mapsClusterTitle.setPadding(20, 20, 20, 8);
        mainCardContainer.addView(mapsClusterTitle);

        TextView mapsClusterDesc = new TextView(context);
        mapsClusterDesc.setText(R.string.projection_google_maps_cluster_cards_desc);
        mapsClusterDesc.setTextSize(13);
        mapsClusterDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        mapsClusterDesc.setPadding(20, 0, 20, 12);
        mapsClusterDesc.setLineSpacing(3, 1.05f);
        mainCardContainer.addView(mapsClusterDesc);

        LinearLayout mapsClusterBlock = new LinearLayout(context);
        mapsClusterBlock.setOrientation(LinearLayout.VERTICAL);
        mapsClusterBlock.setPadding(20, 0, 20, 0);
        mainCardContainer.addView(mapsClusterBlock);

        UiStyles.addBinarySegmentedControl(context, mapsClusterBlock,
                context.getString(R.string.projection_google_maps_cluster_overlay_label),
                context.getString(R.string.common_on),
                context.getString(R.string.common_off),
                context.getString(R.string.projection_google_maps_cluster_overlay_on_help),
                context.getString(R.string.projection_google_maps_cluster_overlay_off_help),
                GoogleMapsNavNotificationCoordinator.isClusterOverlayEnabled(context),
                isEnabled -> {
                    if (isEnabled && !LauncherMediaController.isNotificationAccessEnabled(context)) {
                        new LauncherMediaController(context).openNotificationAccessSettings();
                        Toast.makeText(context,
                                R.string.projection_google_maps_nav_need_access,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    GoogleMapsNavNotificationCoordinator.setClusterOverlayEnabled(context, isEnabled);
                    callback.log(isEnabled
                            ? "Google Maps cluster kartları açıldı"
                            : "Google Maps cluster kartları kapatıldı");
                });

        addGoogleClusterStyleSection(mapsClusterBlock);

        addClusterOverlayLayoutSection(
                mainCardContainer,
                R.string.projection_google_cluster_position_title,
                ClusterNavOverlayOffsets.Provider.GOOGLE,
                () -> GoogleMapsClusterNavOverlay.getInstance(context).applyStoredOffsets());

        TextView yandexTitle = new TextView(context);
        yandexTitle.setText(R.string.yandex_cluster_nav_section_title);
        yandexTitle.setTextSize(15);
        yandexTitle.setTextColor(UiStyles.color(context, R.color.textPrimary87));
        yandexTitle.setTypeface(null, Typeface.NORMAL);
        yandexTitle.setPadding(20, 20, 20, 8);
        mainCardContainer.addView(yandexTitle);

        TextView yandexDesc = new TextView(context);
        yandexDesc.setText(R.string.yandex_cluster_nav_section_desc);
        yandexDesc.setTextSize(13);
        yandexDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        yandexDesc.setPadding(20, 0, 20, 12);
        yandexDesc.setLineSpacing(3, 1.05f);
        mainCardContainer.addView(yandexDesc);

        LinearLayout yandexBlock = new LinearLayout(context);
        yandexBlock.setOrientation(LinearLayout.VERTICAL);
        yandexBlock.setPadding(20, 0, 20, 0);
        mainCardContainer.addView(yandexBlock);

        final UiStyles.BinarySegmentHandle[] yandexHandle = new UiStyles.BinarySegmentHandle[1];
        yandexHandle[0] = UiStyles.addBinarySegmentedControl(context, yandexBlock,
                null,
                context.getString(R.string.common_on),
                context.getString(R.string.common_off),
                context.getString(R.string.yandex_cluster_nav_help_on),
                context.getString(R.string.yandex_cluster_nav_help_off),
                YandexClusterNavOverlay.isEnabled(context),
                isEnabled -> {
                    if (!isEnabled) {
                        YandexClusterNavCoordinator.deactivate(context);
                        callback.log("Yandex cluster nav: Kapalı");
                        return;
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                            && !android.provider.Settings.canDrawOverlays(context)) {
                        try {
                            Intent intent = new Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                            intent.setData(android.net.Uri.parse(
                                    "package:" + context.getPackageName()));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(intent);
                            Toast.makeText(context,
                                    R.string.overlay_permission_toast,
                                    Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            callback.log("İzin ayarlarına gidilemedi: " + e.getMessage());
                        }
                        yandexHandle[0].setLeftSelected(false);
                        return;
                    }
                    if (!GlobalBackService.isRegisteredInSystemAccessibilitySettings(context)) {
                        YandexClusterNavCoordinator.openAccessibilitySettings(context);
                        Toast.makeText(context,
                                R.string.yandex_cluster_nav_toast_accessibility,
                                Toast.LENGTH_LONG).show();
                        yandexHandle[0].setLeftSelected(false);
                        return;
                    }
                    YandexClusterNavCoordinator.ActivateResult result =
                            YandexClusterNavCoordinator.activate(context);
                    callback.log("Yandex cluster nav: Açık — " + result.name());
                    YandexClusterNavCoordinator.showActivateToast(context, result);
                    if (result == YandexClusterNavCoordinator.ActivateResult.SERVICE_CONNECTING) {
                        handler.postDelayed(() -> {
                            YandexClusterNavCoordinator.ActivateResult retry =
                                    YandexClusterNavCoordinator.syncNow(context);
                            YandexClusterNavCoordinator.showActivateToast(context, retry);
                            callback.log("Yandex cluster nav yeniden deneme: " + retry.name());
                        }, 500);
                    }
                });

        addClusterOverlayLayoutSection(
                mainCardContainer,
                R.string.projection_yandex_cluster_position_title,
                ClusterNavOverlayOffsets.Provider.YANDEX,
                () -> YandexClusterNavOverlay.getInstance(context).applyStoredOffsets());

        // Navigasyon ayarlarını anlamlı 2x2 karta topla.
        LinearLayout googleCard = createSectionCard();
        LinearLayout yandexCard = createSectionCard();
        LinearLayout advancedCard = createSectionCard();
        LinearLayout target = leftCard;
        for (int i = 0; i < mainCardContainer.getChildCount(); ) {
            View child = mainCardContainer.getChildAt(i);
            boolean divider = !(child instanceof android.view.ViewGroup)
                    && child.getLayoutParams() != null
                    && child.getLayoutParams().height == 1;
            if (divider) {
                mainCardContainer.removeViewAt(i);
                continue;
            }
            if (child instanceof TextView) {
                String text = ((TextView) child).getText().toString();
                if (text.startsWith("Yandex")) {
                    target = yandexCard;
                } else if (text.contains("Harita kaydırma")) {
                    target = advancedCard;
                } else if (text.startsWith("Başlatma") || text.startsWith("Kapanış")
                        || text.startsWith("Harita kontrol")) {
                    target = leftCard;
                } else if (text.startsWith("Google Maps")) {
                    target = googleCard;
                }
            }
            mainCardContainer.removeViewAt(i);
            target.addView(child);
        }

        LinearLayout columns = new LinearLayout(context);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setBaselineAligned(false);
        LinearLayout leftStack = new LinearLayout(context);
        leftStack.setOrientation(LinearLayout.VERTICAL);
        LinearLayout rightStack = new LinearLayout(context);
        rightStack.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
        leftStack.addView(leftCard, cardLp);
        LinearLayout.LayoutParams advLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        advLp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
        leftStack.addView(advancedCard, advLp);

        googleSettingsCard = googleCard;
        yandexSettingsCard = yandexCard;
        addProviderHeader(googleCard,
                R.string.projection_google_settings_title,
                R.string.projection_google_settings_desc);
        addProviderHeader(yandexCard,
                R.string.projection_yandex_settings_title,
                R.string.projection_yandex_settings_desc);
        LinearLayout rightHost = new LinearLayout(context);
        rightHost.setOrientation(LinearLayout.VERTICAL);
        rightHost.addView(googleCard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        rightHost.addView(yandexCard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        rightStack.addView(rightHost, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams leftColLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        leftColLp.setMarginEnd(8);
        LinearLayout.LayoutParams rightColLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        rightColLp.setMarginStart(8);
        columns.addView(leftStack, leftColLp);
        columns.addView(rightStack, rightColLp);
        projectionTabContent.addView(columns, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        refreshProviderSelection();
        return scrollView;
    }

    /** {@link ClusterNavigationState} ile uyumlu durum metni (servis/tuş/sekme ortak). */
    public void refreshProjectionStatusUi() {
        if (projectionStatusText == null) {
            return;
        }
        projectionStatusText.setText(ClusterNavigationState.getLastKnownOpen()
                ? R.string.projection_status_on
                : R.string.projection_status_off);
    }

    public void updateGoogleMapsNavSummary(GoogleMapsNavSnapshot snapshot) {
        if (googleMapsNavSummaryText == null) {
            return;
        }
        CharSequence next;
        if (snapshot == null || !snapshot.active) {
            next = contextText(R.string.projection_google_maps_nav_summary_idle);
        } else {
            String line = snapshot.formatSummaryLine();
            if (line == null || line.isEmpty()) {
                next = contextText(R.string.projection_google_maps_nav_summary_idle);
            } else {
                next = line;
            }
        }
        CharSequence current = googleMapsNavSummaryText.getText();
        if (current == null || !current.toString().contentEquals(next)) {
            googleMapsNavSummaryText.setText(next);
        }
    }

    private CharSequence contextText(int resId) {
        return googleMapsNavSummaryText.getContext().getText(resId);
    }

    private void addGoogleClusterStyleSection(LinearLayout parent) {
        TextView title = new TextView(context);
        title.setText(R.string.projection_google_cluster_design_title);
        title.setTextSize(15);
        title.setTextColor(UiStyles.color(context, R.color.textPrimary87));
        title.setPadding(0, 18, 0, 6);
        parent.addView(title);

        TextView desc = new TextView(context);
        desc.setText(R.string.projection_google_cluster_design_desc);
        desc.setTextSize(13);
        desc.setTextColor(UiStyles.color(context, R.color.textHint));
        desc.setPadding(0, 0, 0, 10);
        parent.addView(desc);

        String[] styles = {
                GoogleMapsClusterNavOverlay.STYLE_HUD,
                GoogleMapsClusterNavOverlay.STYLE_DUAL,
                GoogleMapsClusterNavOverlay.STYLE_RADIAL,
                GoogleMapsClusterNavOverlay.STYLE_TRAFFIC
        };
        int[] labels = {
                R.string.projection_cluster_variant1_title,
                R.string.projection_cluster_variant2_title,
                R.string.projection_cluster_variant3_title,
                R.string.projection_cluster_variant4_title
        };
        int[] descriptions = {
                R.string.projection_cluster_variant1_desc,
                R.string.projection_cluster_variant2_desc,
                R.string.projection_cluster_variant3_desc,
                R.string.projection_cluster_variant4_desc
        };
        String selected = GoogleMapsClusterNavOverlay.getStyle(context);
        java.util.List<LinearLayout> cards = new java.util.ArrayList<>();
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(context);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setBaselineAligned(false);
            for (int col = 0; col < 2; col++) {
                int index = row * 2 + col;
                final String style = styles[index];
                LinearLayout card = new LinearLayout(context);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(14, 12, 14, 12);
                card.setMinimumHeight(Math.round(240 * context.getResources().getDisplayMetrics().density));
                card.setClickable(true);
                card.setFocusable(true);
                TextView variant = new TextView(context);
                variant.setText(context.getString(R.string.projection_cluster_variant_label, index + 1));
                variant.setTextSize(11);
                variant.setTypeface(null, Typeface.BOLD);
                variant.setTextColor(UiStyles.color(context, R.color.ck_accent));
                variant.setPadding(8, 3, 8, 3);
                GradientDrawable variantBg = new GradientDrawable();
                variantBg.setColor(0x3325D9E8);
                variantBg.setStroke(1, UiStyles.color(context, R.color.ck_accent));
                variantBg.setCornerRadius(8);
                variant.setBackground(variantBg);
                card.addView(variant, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                TextView label = new TextView(context);
                label.setText(labels[index]);
                label.setTextSize(15);
                label.setTextColor(UiStyles.color(context, R.color.textPrimary));
                label.setTypeface(null, Typeface.BOLD);
                label.setPadding(0, 6, 0, 0);
                card.addView(label, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                TextView hint = new TextView(context);
                hint.setText(descriptions[index]);
                hint.setTextSize(13);
                hint.setTextColor(UiStyles.color(context, R.color.textHint));
                hint.setLineSpacing(2, 1.05f);
                hint.setPadding(0, 8, 0, 8);
                card.addView(hint);
                View preview = buildClusterStylePreview(style);
                card.addView(preview, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, Math.round(78 * context.getResources().getDisplayMetrics().density)));
                TextView footer = new TextView(context);
                footer.setText(R.string.projection_cluster_style_hint);
                footer.setTextSize(12);
                footer.setTextColor(UiStyles.color(context, R.color.textHint));
                footer.setPadding(0, 8, 0, 0);
                card.addView(footer);
                card.setOnClickListener(v -> {
                    GoogleMapsClusterNavOverlay.setStyle(context, style);
                    GoogleMapsClusterNavOverlay.getInstance(context).apply(
                            GoogleMapsNavNotificationCoordinator.getLastSnapshot());
                    applyClusterStyleSelection(cards, styles, GoogleMapsClusterNavOverlay.getStyle(context));
                    callback.log("Google Maps cluster kart tasarımı: " + style);
                });
                cards.add(card);
                LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (col == 0) cardLp.setMarginEnd(4); else cardLp.setMarginStart(4);
                line.addView(card, cardLp);
            }
            LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lineLp.topMargin = 8;
            parent.addView(line, lineLp);
        }
        applyClusterStyleSelection(cards, styles, selected);
    }

    private View buildClusterStylePreview(String style) {
        LinearLayout preview = new LinearLayout(context);
        preview.setOrientation(LinearLayout.HORIZONTAL);
        preview.setGravity(Gravity.CENTER_VERTICAL);
        preview.setPadding(12, 8, 12, 8);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(UiStyles.color(context, R.color.ck_bg_recessed));
        bg.setCornerRadius(12);
        bg.setStroke(1, UiStyles.color(context, R.color.ck_stroke));
        preview.setBackground(bg);
        if (GoogleMapsClusterNavOverlay.STYLE_DUAL.equals(style)) {
            preview.addView(miniCard("350 m", "Amphitheatre Pkwy"));
            preview.addView(miniCard("11:15", "13 dk · 8.5 km"));
        } else if (GoogleMapsClusterNavOverlay.STYLE_HUD.equals(style)) {
            TextView arrow = new TextView(context);
            arrow.setText("←");
            arrow.setTextSize(24);
            arrow.setGravity(Gravity.CENTER);
            arrow.setTextColor(UiStyles.color(context, R.color.ck_accent));
            GradientDrawable arrowBg = new GradientDrawable();
            arrowBg.setColor(UiStyles.color(context, R.color.ck_surface_1));
            arrowBg.setCornerRadius(10);
            arrowBg.setStroke(1, UiStyles.color(context, R.color.ck_accent_stroke_30));
            arrow.setBackground(arrowBg);
            preview.addView(arrow, new LinearLayout.LayoutParams(36, 36));
            LinearLayout left = new LinearLayout(context);
            left.setOrientation(LinearLayout.VERTICAL);
            left.setPadding(10, 0, 8, 0);
            TextView distance = new TextView(context);
            android.text.SpannableStringBuilder distanceText =
                    new android.text.SpannableStringBuilder("150");
            distanceText.append("  M");
            distanceText.setSpan(new android.text.style.ForegroundColorSpan(
                            UiStyles.color(context, R.color.ck_accent)),
                    5, distanceText.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            distanceText.setSpan(new android.text.style.RelativeSizeSpan(0.62f),
                    5, distanceText.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            distance.setText(distanceText);
            distance.setTextSize(21);
            distance.setTypeface(null, Typeface.BOLD);
            distance.setTextColor(UiStyles.color(context, R.color.ck_text_high));
            left.addView(distance);
            TextView street = new TextView(context);
            street.setText("Amphitheatre Pkwy");
            street.setTextSize(9);
            street.setSingleLine(true);
            street.setEllipsize(android.text.TextUtils.TruncateAt.END);
            street.setTextColor(UiStyles.color(context, R.color.ck_text_medium));
            left.addView(street);
            preview.addView(left, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1.35f));
            View hudDivider = new View(context);
            hudDivider.setBackgroundColor(UiStyles.color(context, R.color.ck_stroke));
            preview.addView(hudDivider, new LinearLayout.LayoutParams(1, 48));
            LinearLayout right = new LinearLayout(context);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.END);
            right.setPadding(10, 0, 0, 0);
            TextView eta = new TextView(context);
            eta.setText("13 dk  ●");
            eta.setTextSize(16);
            eta.setSingleLine(true);
            eta.setTypeface(null, Typeface.BOLD);
            eta.setTextColor(UiStyles.color(context, R.color.ck_text_high));
            right.addView(eta);
            TextView detail = new TextView(context);
            detail.setText("8.5 km · 11:15");
            detail.setTextSize(9);
            detail.setSingleLine(true);
            detail.setTextColor(UiStyles.color(context, R.color.ck_text_medium));
            right.addView(detail);
            preview.addView(right, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        } else if (GoogleMapsClusterNavOverlay.STYLE_RADIAL.equals(style)) {
            TextView ring = new TextView(context);
            ring.setText("13\ndk");
            ring.setTextSize(15);
            ring.setGravity(Gravity.CENTER);
            ring.setTextColor(UiStyles.color(context, R.color.ck_accent));
            GradientDrawable ringBg = new GradientDrawable();
            ringBg.setShape(GradientDrawable.OVAL);
            ringBg.setColor(UiStyles.color(context, R.color.ck_surface_1));
            ringBg.setStroke(3, UiStyles.color(context, R.color.ck_accent));
            ring.setBackground(ringBg);
            preview.addView(ring, new LinearLayout.LayoutParams(56, 56));
            LinearLayout col = new LinearLayout(context);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView route = new TextView(context);
            route.setText("Rota ilerlemesi                    %68");
            route.setTextSize(12);
            route.setTextColor(UiStyles.color(context, R.color.ck_text_medium));
            col.addView(route);
            android.widget.ProgressBar bar = new android.widget.ProgressBar(context,
                    null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setProgress(68);
            bar.setPadding(8, 4, 8, 0);
            col.addView(bar, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 12));
            TextView info = new TextView(context);
            info.setText("8.5 km  ·  11:15");
            info.setTextSize(12);
            info.setTextColor(UiStyles.color(context, R.color.ck_text_medium));
            info.setPadding(8, 4, 0, 0);
            col.addView(info);
            preview.addView(col, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        } else {
            LinearLayout col = new LinearLayout(context);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView route = new TextView(context);
            route.setText("200 m  ·  Amphitheatre Pkwy");
            route.setTextSize(12);
            route.setTextColor(UiStyles.color(context, R.color.ck_text_high));
            col.addView(route);
            android.widget.ProgressBar progress = new android.widget.ProgressBar(context,
                    null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(100);
            progress.setProgress(68);
            progress.setPadding(0, 5, 0, 3);
            col.addView(progress, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 12));
            TextView eta = new TextView(context);
            eta.setText("11:15  ·  13 dk  ·  8.5 km");
            eta.setTextSize(12);
            eta.setTextColor(UiStyles.color(context, R.color.ck_accent));
            col.addView(eta);
            preview.addView(col, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        return preview;
    }

    private View miniPill(String text) {
        TextView v = new TextView(context);
        v.setText(text);
        v.setTextSize(12);
        v.setTextColor(UiStyles.color(context, R.color.ck_text_high));
        v.setPadding(10, 7, 10, 7);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xAA1D242C);
        bg.setCornerRadius(14);
        bg.setStroke(1, UiStyles.color(context, R.color.ck_accent_stroke_30));
        v.setBackground(bg);
        return v;
    }

    private View miniCard(String value, String text) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(10, 7, 10, 7);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(UiStyles.color(context, R.color.ck_surface_1));
        bg.setCornerRadius(10);
        bg.setStroke(1, UiStyles.color(context, R.color.ck_stroke));
        card.setBackground(bg);
        TextView v = new TextView(context);
        v.setText(value);
        v.setTextSize(18);
        v.setTypeface(null, Typeface.BOLD);
        v.setTextColor(UiStyles.color(context, R.color.ck_accent));
        card.addView(v);
        TextView t = new TextView(context);
        t.setText(text);
        t.setTextSize(11);
        t.setTextColor(UiStyles.color(context, R.color.ck_text_medium));
        card.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginEnd(6);
        card.setLayoutParams(lp);
        return card;
    }

    private void applyClusterStyleSelection(java.util.List<LinearLayout> cards,
            String[] styles, String selected) {
        for (int i = 0; i < cards.size(); i++) {
            boolean active = styles[i].equals(selected);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(UiStyles.color(context,
                    active ? R.color.ck_surface_2 : R.color.ck_surface_1));
            bg.setCornerRadius(12);
            bg.setStroke(Math.max(1, Math.round(context.getResources().getDisplayMetrics().density
                            * (active ? 2 : 1))),
                    UiStyles.color(context, active ? R.color.ck_accent : R.color.ck_stroke));
            cards.get(i).setBackground(bg);
        }
    }

    private void addClusterOverlayLayoutSection(LinearLayout parent, int titleResId,
            ClusterNavOverlayOffsets.Provider provider, Runnable applyOverlay) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 18, 0, 8);
        TextView title = new TextView(context);
        title.setText(R.string.projection_cluster_advanced_title);
        title.setTextSize(17);
        title.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_SEMIBOLD));
        title.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        header.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView reset = new TextView(context);
        reset.setText(R.string.projection_cluster_reset_short);
        reset.setTextSize(12);
        reset.setTextColor(CockpitTokens.color(context, R.color.ck_accent));
        reset.setClickable(true);
        header.addView(reset);
        parent.addView(header);

        final ClusterNavOverlayOffsets.Card[] selected = {
                ClusterNavOverlayOffsets.Card.MANEUVER
        };
        LinearLayout tabs = new LinearLayout(context);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        TextView maneuverTab = compactCardTab("Manevra");
        TextView etaTab = compactCardTab("ETA");
        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tabs.addView(maneuverTab, tabLp);
        LinearLayout.LayoutParams etaLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tabs.addView(etaTab, etaLp);
        if (provider == ClusterNavOverlayOffsets.Provider.GOOGLE) {
            googleCardTabsRow = tabs;
        } else {
            yandexCardTabsRow = tabs;
        }
        LinearLayout.LayoutParams tabsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tabsLp.bottomMargin = 8;
        parent.addView(tabs, tabsLp);
        updateCardTabsVisibility();

        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        int pad = CockpitTokens.dim(context, R.dimen.ck_space_md);
        grid.setPadding(pad, pad, pad, pad);
        com.mapcontrol.ui.cockpit.CockpitSurfaces.level2(grid);
        parent.addView(grid);

        final int rangeX = ClusterNavOverlayOffsets.getOffsetRangeXDp(context);
        final int rangeY = ClusterNavOverlayOffsets.getOffsetRangeYDp(context);
        final SeekBar[] bars = new SeekBar[4];
        final TextView[] values = new TextView[4];
        final Runnable[] sync = new Runnable[1];

        LinearLayout row1 = new LinearLayout(context);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setBaselineAligned(false);
        addCompactOffsetCell(row1,
                R.string.projection_cluster_x_offset, 0, rangeX * 2,
                progress -> {
                    int x = ClusterNavOverlayOffsets.fromOffsetSeekProgress(progress, rangeX);
                    updateCardLayout(provider, selected[0], x, null, null);
                    applyOverlay.run();
                }, bars, values, 0, " dp");
        addCompactOffsetCell(row1,
                R.string.projection_cluster_y_offset, 0, rangeY * 2,
                progress -> {
                    int y = ClusterNavOverlayOffsets.fromOffsetSeekProgress(progress, rangeY);
                    updateCardLayout(provider, selected[0], null, y, null);
                    applyOverlay.run();
                }, bars, values, 1, " dp");
        grid.addView(row1);

        LinearLayout row2 = new LinearLayout(context);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setBaselineAligned(false);
        addCompactOffsetCell(row2,
                R.string.projection_cluster_scale, 50, 100,
                progress -> {
                    int scale = ClusterNavOverlayOffsets.fromScaleSeekProgress(progress);
                    updateCardLayout(provider, selected[0], null, null, scale);
                    applyOverlay.run();
                }, bars, values, 2, "%");
        addCompactOffsetCell(row2,
                R.string.projection_cluster_bg_opacity, 80, 80,
                progress -> GoogleMapsClusterNavOverlay.setCardOpacity(
                        context, selected[0] == ClusterNavOverlayOffsets.Card.ETA,
                        progress + 20),
                bars, values, 3, "%");
        grid.addView(row2);

        sync[0] = () -> {
            ClusterNavOverlayOffsets.CardLayout layout = ClusterNavOverlayOffsets.getCardLayout(
                    context, provider, selected[0]);
            bars[0].setProgress(ClusterNavOverlayOffsets.toOffsetSeekProgress(
                    layout.offsetXDp, rangeX));
            bars[1].setProgress(ClusterNavOverlayOffsets.toOffsetSeekProgress(
                    layout.offsetYDp, rangeY));
            bars[2].setProgress(ClusterNavOverlayOffsets.toScaleSeekProgress(layout.scalePercent));
            int opacity = GoogleMapsClusterNavOverlay.getCardOpacity(
                    context, selected[0] == ClusterNavOverlayOffsets.Card.ETA);
            bars[3].setProgress(Math.max(0, opacity - 20));
            values[0].setText(layout.offsetXDp + " dp");
            values[1].setText(layout.offsetYDp + " dp");
            values[2].setText("%" + layout.scalePercent);
            values[3].setText("%" + opacity);
        };

        Runnable renderTabs = () -> {
            styleCardTab(maneuverTab, selected[0] == ClusterNavOverlayOffsets.Card.MANEUVER);
            styleCardTab(etaTab, selected[0] == ClusterNavOverlayOffsets.Card.ETA);
        };
        maneuverTab.setOnClickListener(v -> {
            selected[0] = ClusterNavOverlayOffsets.Card.MANEUVER;
            renderTabs.run();
            sync[0].run();
        });
        etaTab.setOnClickListener(v -> {
            selected[0] = ClusterNavOverlayOffsets.Card.ETA;
            renderTabs.run();
            sync[0].run();
        });
        styleCardTab(maneuverTab, true);
        styleCardTab(etaTab, false);
        sync[0].run();

        reset.setOnClickListener(v -> {
            ClusterNavOverlayOffsets.resetProvider(context, provider);
            GoogleMapsClusterNavOverlay.setCardOpacity(context, false, 100);
            GoogleMapsClusterNavOverlay.setCardOpacity(context, true, 100);
            sync[0].run();
            applyOverlay.run();
            callback.log(provider == ClusterNavOverlayOffsets.Provider.GOOGLE
                    ? "Google Maps kart düzeni sıfırlandı"
                    : "Yandex cluster kart düzeni sıfırlandı");
            Toast.makeText(context, R.string.projection_cluster_layout_reset_done,
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void updateCardTabsVisibility() {
        boolean googleVisible = GoogleMapsClusterNavOverlay.STYLE_DUAL.equals(
                GoogleMapsClusterNavOverlay.getStyle(context));
        if (googleCardTabsRow != null) {
            googleCardTabsRow.setVisibility(googleVisible ? View.VISIBLE : View.GONE);
        }
        if (yandexCardTabsRow != null) {
            yandexCardTabsRow.setVisibility(View.VISIBLE);
        }
    }

    private void updateCardLayout(ClusterNavOverlayOffsets.Provider provider,
            ClusterNavOverlayOffsets.Card card, Integer x, Integer y, Integer scale) {
        ClusterNavOverlayOffsets.CardLayout current = ClusterNavOverlayOffsets.getCardLayout(
                context, provider, card);
        ClusterNavOverlayOffsets.setCardLayout(context, provider, card,
                new ClusterNavOverlayOffsets.CardLayout(
                        x != null ? x : current.offsetXDp,
                        y != null ? y : current.offsetYDp,
                        scale != null ? scale : current.scalePercent));
    }

    private TextView compactCardTab(String text) {
        TextView tab = new TextView(context);
        tab.setText(text);
        tab.setTextSize(12);
        tab.setGravity(Gravity.CENTER);
        tab.setPadding(10, 6, 10, 6);
        return tab;
    }

    private void styleCardTab(TextView tab, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CockpitTokens.color(context,
                selected ? R.color.ck_accent : R.color.ck_surface_1));
        bg.setCornerRadius(6);
        tab.setBackground(bg);
        tab.setTextColor(CockpitTokens.color(context,
                selected ? R.color.ck_on_accent : R.color.ck_text_medium));
    }

    private interface CompactSliderChange {
        void onChanged(int progress);
    }

    private void addCompactOffsetCell(LinearLayout row, int labelRes, int progress,
            int max, CompactSliderChange change, SeekBar[] bars, TextView[] values,
            int index, String suffix) {
        LinearLayout cell = new LinearLayout(context);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(0, 0, 12, 10);
        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = new TextView(context);
        label.setText(labelRes);
        label.setTextSize(13);
        label.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        top.addView(label, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView value = new TextView(context);
        value.setTextSize(12);
        value.setTextColor(CockpitTokens.color(context, R.color.ck_accent));
        top.addView(value);
        cell.addView(top);
        SeekBar bar = new SeekBar(context);
        bar.setMax(max);
        bar.setProgress(Math.max(0, Math.min(max, progress)));
        bar.setProgressTintList(ColorStateList.valueOf(
                CockpitTokens.color(context, R.color.ck_accent)));
        bar.setThumbTintList(ColorStateList.valueOf(
                CockpitTokens.color(context, R.color.ck_accent)));
        bar.setMinimumHeight(CockpitTokens.dim(context, R.dimen.ck_touch_min));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int p, boolean fromUser) {
                String text;
                if (suffix.equals(" dp")) {
                    text = p - max / 2 + " dp";
                } else if (suffix.equals("%") && index == 2) {
                    text = "%" + (p + 50);
                } else {
                    text = "%" + (p + 20);
                }
                value.setText(text);
                if (fromUser) change.onChanged(p);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                change.onChanged(seekBar.getProgress());
            }
        });
        cell.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                CockpitTokens.dim(context, R.dimen.ck_touch_min)));
        bars[index] = bar;
        values[index] = value;
        LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(cell, cellLp);
    }

    private static final class ClusterCardSliderUi {
        final ClusterNavOverlayOffsets.Provider provider;
        final ClusterNavOverlayOffsets.Card card;
        final int offsetRangeXDp;
        final int offsetRangeYDp;
        final ClusterNavOverlayOffsets.CardLayout layout;
        final SeekBar xBar;
        final SeekBar yBar;
        final SeekBar scaleBar;
        final TextView xValue;
        final TextView yValue;
        final TextView scaleValue;
        final ProjectionTabBuilder owner;

        ClusterCardSliderUi(ProjectionTabBuilder owner,
                ClusterNavOverlayOffsets.Provider provider,
                ClusterNavOverlayOffsets.Card card,
                int offsetRangeXDp,
                int offsetRangeYDp,
                ClusterNavOverlayOffsets.CardLayout layout,
                SeekBar xBar, SeekBar yBar, SeekBar scaleBar,
                TextView xValue, TextView yValue, TextView scaleValue) {
            this.owner = owner;
            this.provider = provider;
            this.card = card;
            this.offsetRangeXDp = offsetRangeXDp;
            this.offsetRangeYDp = offsetRangeYDp;
            this.layout = layout;
            this.xBar = xBar;
            this.yBar = yBar;
            this.scaleBar = scaleBar;
            this.xValue = xValue;
            this.yValue = yValue;
            this.scaleValue = scaleValue;
        }

        void syncFromPrefs() {
            ClusterNavOverlayOffsets.CardLayout fresh =
                    ClusterNavOverlayOffsets.getCardLayout(owner.context, provider, card);
            layout.offsetXDp = fresh.offsetXDp;
            layout.offsetYDp = fresh.offsetYDp;
            layout.scalePercent = fresh.scalePercent;
            xBar.setProgress(ClusterNavOverlayOffsets.toOffsetSeekProgress(
                    layout.offsetXDp, offsetRangeXDp));
            yBar.setProgress(ClusterNavOverlayOffsets.toOffsetSeekProgress(
                    layout.offsetYDp, offsetRangeYDp));
            scaleBar.setProgress(ClusterNavOverlayOffsets.toScaleSeekProgress(layout.scalePercent));
            xValue.setText(owner.context.getString(
                    R.string.projection_cluster_offset_value, layout.offsetXDp));
            yValue.setText(owner.context.getString(
                    R.string.projection_cluster_offset_value, layout.offsetYDp));
            scaleValue.setText(owner.context.getString(
                    R.string.projection_cluster_offset_scale_value, layout.scalePercent));
        }
    }

    private ClusterCardSliderUi addSingleClusterCardLayoutSliders(LinearLayout parent,
            ClusterNavOverlayOffsets.Provider provider,
            ClusterNavOverlayOffsets.Card card,
            int cardTitleResId,
            int offsetRangeXDp,
            int offsetRangeYDp,
            Runnable applyOverlay) {
        TextView cardTitle = new TextView(context);
        cardTitle.setText(cardTitleResId);
        cardTitle.setTextSize(13);
        cardTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        cardTitle.setTypeface(null, Typeface.BOLD);
        cardTitle.setPadding(0, 12, 0, 4);
        parent.addView(cardTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final ClusterNavOverlayOffsets.CardLayout layout =
                ClusterNavOverlayOffsets.getCardLayout(context, provider, card);

        Runnable persistAndApply = () -> {
            ClusterNavOverlayOffsets.setCardLayout(context, provider, card, layout);
            applyOverlay.run();
        };

        ClusterOffsetSliderRow xRow = addClusterOffsetSliderRow(
                parent,
                R.string.projection_cluster_offset_x,
                layout.offsetXDp,
                offsetRangeXDp,
                value -> {
                    layout.offsetXDp = value;
                    persistAndApply.run();
                });
        ClusterOffsetSliderRow yRow = addClusterOffsetSliderRow(
                parent,
                R.string.projection_cluster_offset_y,
                layout.offsetYDp,
                offsetRangeYDp,
                value -> {
                    layout.offsetYDp = value;
                    persistAndApply.run();
                });
        ClusterOffsetSliderRow scaleRow = addClusterScaleSliderRow(
                parent,
                layout.scalePercent,
                value -> {
                    layout.scalePercent = value;
                    persistAndApply.run();
                });
        return new ClusterCardSliderUi(this, provider, card, offsetRangeXDp, offsetRangeYDp, layout,
                xRow.bar, yRow.bar, scaleRow.bar,
                xRow.value, yRow.value, scaleRow.value);
    }

    private static final class ClusterOffsetSliderRow {
        final SeekBar bar;
        final TextView value;

        ClusterOffsetSliderRow(SeekBar bar, TextView value) {
            this.bar = bar;
            this.value = value;
        }
    }

    private ClusterOffsetSliderRow addClusterOffsetSliderRow(LinearLayout parent, int labelResId,
            int initialOffsetDp, int offsetRangeDp, java.util.function.IntConsumer onChanged) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 8, 0, 0);

        TextView label = new TextView(context);
        label.setText(labelResId);
        label.setTextSize(13);
        label.setTextColor(UiStyles.color(context, R.color.textSecondary));
        row.addView(label, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = new TextView(context);
        value.setTextSize(13);
        value.setTextColor(UiStyles.color(context, R.color.textHint));
        value.setGravity(Gravity.END);
        row.addView(value, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        parent.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        value.setText(context.getString(R.string.projection_cluster_offset_value, initialOffsetDp));

        SeekBar bar = createClusterSeekBar(ClusterNavOverlayOffsets.offsetSeekMax(offsetRangeDp));
        bar.setProgress(ClusterNavOverlayOffsets.toOffsetSeekProgress(initialOffsetDp, offsetRangeDp));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int offset = ClusterNavOverlayOffsets.fromOffsetSeekProgress(progress, offsetRangeDp);
                value.setText(context.getString(R.string.projection_cluster_offset_value, offset));
                if (fromUser) {
                    onChanged.accept(offset);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        parent.addView(bar, clusterOffsetSeekBarLayoutParams());
        return new ClusterOffsetSliderRow(bar, value);
    }

    private ClusterOffsetSliderRow addClusterScaleSliderRow(LinearLayout parent, int initialScalePercent,
            java.util.function.IntConsumer onChanged) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 8, 0, 0);

        TextView label = new TextView(context);
        label.setText(R.string.projection_cluster_offset_scale);
        label.setTextSize(13);
        label.setTextColor(UiStyles.color(context, R.color.textSecondary));
        row.addView(label, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = new TextView(context);
        value.setTextSize(13);
        value.setTextColor(UiStyles.color(context, R.color.textHint));
        value.setGravity(Gravity.END);
        value.setText(context.getString(
                R.string.projection_cluster_offset_scale_value, initialScalePercent));
        row.addView(value, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        parent.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        SeekBar bar = createClusterSeekBar(ClusterNavOverlayOffsets.scaleSeekMax());
        bar.setProgress(ClusterNavOverlayOffsets.toScaleSeekProgress(initialScalePercent));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int scale = ClusterNavOverlayOffsets.fromScaleSeekProgress(progress);
                value.setText(context.getString(R.string.projection_cluster_offset_scale_value, scale));
                if (fromUser) {
                    onChanged.accept(scale);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        parent.addView(bar, clusterOffsetSeekBarLayoutParams());
        return new ClusterOffsetSliderRow(bar, value);
    }

    private SeekBar createClusterSeekBar(int max) {
        SeekBar bar = new SeekBar(context);
        bar.setMax(max);
        int accent = UiStyles.color(context, R.color.oemAccent);
        int thumbPx = UiStyles.dimenPx(context, R.dimen.equalizer_thumb_size);
        int trackPx = UiStyles.dimenPx(context, R.dimen.equalizer_track_height);
        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(accent);
        thumb.setSize(thumbPx, thumbPx);
        bar.setThumb(thumb);
        bar.setThumbOffset(thumbPx / 2);
        bar.setSplitTrack(false);
        bar.setMinHeight(trackPx);
        bar.setMaxHeight(trackPx);
        bar.setProgressTintList(ColorStateList.valueOf(accent));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(
                UiStyles.color(context, R.color.buttonInactive)));
        bar.setThumbTintList(ColorStateList.valueOf(accent));
        int side = thumbPx / 2;
        bar.setPadding(side, 0, side, 0);
        return bar;
    }

    private LinearLayout.LayoutParams clusterOffsetSeekBarLayoutParams() {
        int h = UiStyles.dimenPx(context, R.dimen.spacing_small);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                UiStyles.dimenPx(context, R.dimen.equalizer_slider_height));
        lp.setMargins(0, 0, 0, h);
        return lp;
    }

    private void handleButtonClickWithDelay(Button button, String originalText, String loadingText) {
        if (button == null) return;
        button.setEnabled(false);
        button.setText(loadingText);
        button.setAlpha(0.6f);
        handler.postDelayed(() -> {
            button.setText(originalText);
            button.setAlpha(1.0f);
            button.setEnabled(true);
        }, 2500);
    }

    private LinearLayout createProviderCard(String packageName, String title, String desc,
            int iconRes) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = CockpitTokens.dim(context, R.dimen.ck_space_md);
        card.setPadding(pad, pad, pad, pad);
        com.mapcontrol.ui.cockpit.CockpitSurfaces.level2(card);
        card.setClickable(true);
        card.setFocusable(true);
        AppCompatImageView icon = new AppCompatImageView(context);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(
                CockpitTokens.color(context, R.color.ck_accent)));
        int providerIcon = Math.round(30 * context.getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                providerIcon, providerIcon);
        iconLp.bottomMargin = CockpitTokens.dim(context, R.dimen.ck_space_xs);
        card.addView(icon, iconLp);
        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_SEMIBOLD));
        titleView.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        card.addView(titleView);
        TextView descView = new TextView(context);
        descView.setText(desc);
        descView.setTextSize(12);
        descView.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        card.addView(descView);
        card.setOnClickListener(v -> {
            callback.onTargetPackageSelected(packageName);
            refreshTargetLabel();
            refreshProviderSelection();
            callback.log("Yansıtma hedefi: " + packageName);
        });
        return card;
    }

    private void refreshProviderSelection() {
        if (googleProviderCard == null || yandexProviderCard == null) {
            return;
        }
        String target = callback.getTargetPackage();
        boolean yandex = YandexNavScraper.PACKAGE_YANDEX_MAPS.equals(target);
        styleProviderCard(googleProviderCard, !yandex);
        styleProviderCard(yandexProviderCard, yandex);
        if (googleSettingsCard != null) {
            googleSettingsCard.setVisibility(yandex ? View.GONE : View.VISIBLE);
        }
        if (yandexSettingsCard != null) {
            yandexSettingsCard.setVisibility(yandex ? View.VISIBLE : View.GONE);
        }
    }

    private void styleProviderCard(LinearLayout card, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CockpitTokens.color(context,
                selected ? R.color.ck_surface_2 : R.color.ck_surface_1));
        bg.setCornerRadius(CockpitTokens.dim(context, R.dimen.ck_radius_lg));
        bg.setStroke(selected ? 2 : 1, CockpitTokens.color(context,
                selected ? R.color.ck_accent : R.color.ck_stroke));
        card.setBackground(bg);
    }

    private void addProviderHeader(LinearLayout card, int titleRes, int descRes) {
        if (card == null) return;
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(context);
        title.setText(titleRes);
        title.setTextSize(21);
        title.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_SEMIBOLD));
        title.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        titleRow.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView badge = new TextView(context);
        badge.setText(R.string.projection_settings_badge);
        badge.setTextSize(11);
        badge.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_SEMIBOLD));
        badge.setTextColor(CockpitTokens.color(context, R.color.ck_accent));
        badge.setPadding(8, 4, 8, 4);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(0x3310B981);
        badgeBg.setCornerRadius(6);
        badge.setBackground(badgeBg);
        titleRow.addView(badge);
        header.addView(titleRow);
        TextView desc = new TextView(context);
        desc.setText(descRes);
        desc.setTextSize(13);
        desc.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        desc.setPadding(0, 6, 0, 12);
        header.addView(desc);
        card.addView(header, 0);
    }

    private void refreshTargetLabel() {
        refreshProviderSelection();
        if (targetAppLabel == null) return;
        String pkg = callback.getTargetPackage();
        if (pkg == null || pkg.trim().isEmpty()) {
            targetAppLabel.setText(R.string.common_not_selected);
        } else {
            targetAppLabel.setText(pkg.trim());
        }
    }

    /** Yüzen kontrol veya MainActivity extra ile hedef uygulama listesi (sekmedeki Değiştir ile aynı). */
    public void openTargetAppPicker() {
        selectTargetApp();
    }

    private void selectTargetApp() {
        try {
            callback.log("Yüklü uygulamalar listeleniyor...");
            java.util.List<ProjectionTargetApps.Row> rows = ProjectionTargetApps.loadSortedRows(context);
            if (rows.isEmpty()) {
                Toast.makeText(context, R.string.projection_list_failed, Toast.LENGTH_SHORT).show();
                return;
            }

            java.util.List<String> appNames = new java.util.ArrayList<>();
            java.util.List<String> sortedPackages = new java.util.ArrayList<>();
            for (ProjectionTargetApps.Row row : rows) {
                appNames.add(row.label + " (" + row.packageName + ")");
                sortedPackages.add(row.packageName);
            }

            String[] items = appNames.toArray(new String[0]);
            String titleText = context.getString(R.string.projection_installed_apps, items.length);

            DialogHelper.showAppSelectionDialog(
                    context,
                    titleText,
                    items,
                    sortedPackages,
                    null,
                    selectedPkg -> {
                        callback.onTargetPackageSelected(selectedPkg);
                        refreshTargetLabel();
                        Toast.makeText(context, context.getString(R.string.projection_selected, selectedPkg), Toast.LENGTH_SHORT).show();
                    },
                    null,
                    () -> {
                        callback.onTargetPackageSelected("");
                        refreshTargetLabel();
                        Toast.makeText(context, R.string.projection_target_cleared, Toast.LENGTH_SHORT).show();
                    }
            );
        } catch (Exception e) {
            callback.log("selectTargetApp hatası: " + e.getMessage());
            Toast.makeText(context, context.getString(R.string.common_error_prefix, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private LinearLayout createSectionCard() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int inner = CockpitTokens.dim(context, R.dimen.ck_card_padding);
        card.setPadding(inner, inner, inner, inner);
        com.mapcontrol.ui.cockpit.CockpitSurfaces.level1(card);
        return card;
    }

    public ScrollView getScrollView() {
        return scrollView != null ? scrollView : build();
    }

    public LinearLayout getProjectionTabContent() {
        return projectionTabContent;
    }

    public TextView getTargetAppLabel() {
        refreshTargetLabel();
        return targetAppLabel;
    }
}
