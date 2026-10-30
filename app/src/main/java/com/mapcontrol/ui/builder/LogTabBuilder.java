package com.mapcontrol.ui.builder;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.desaysv.ivi.extra.project.carinfo.AvmID;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;

import com.mapcontrol.R;
import com.mapcontrol.ui.cockpit.CockpitSurfaces;
import com.mapcontrol.ui.cockpit.CockpitTokens;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.ui.activity.CameraActivity;
import com.mapcontrol.ui.activity.DmsFaceProfileActivity;
import com.mapcontrol.ui.activity.ClusterVDBusTestActivity;
import com.mapcontrol.ui.activity.AdasProbeActivity;
import com.mapcontrol.ui.activity.TrafficSignMonitorActivity;
import com.mapcontrol.ui.activity.FrontObjectProbeActivity;
import com.mapcontrol.tts.IflytekTts;
import com.mapcontrol.ui.activity.IdDiscoveryActivity;
import com.mapcontrol.vehicle.DriveFollowLogger;
import com.mapcontrol.vehicle.VehicleValueWatcher;

public class LogTabBuilder {
    public interface LogCallback {
        void onClearLogs();
        void log(String message);
        /** Kullanıcı metnini sistem TTS ile okut */
        void onReadAloud(String text);
        /** Hoşgeldin cümlesini tekrar okut */
        void onWelcomeTts();
    }

    private final Context context;
    private final LogCallback callback;
    private final VehicleValueWatcher valueWatcher = new VehicleValueWatcher();
    private final DriveFollowLogger driveFollowLogger;

    private LinearLayout tabContent;
    private TextView logsTextView;
    private ScrollView logScrollView;
    private EditText ttsInput;

    public LogTabBuilder(Context context, LogCallback callback) {
        this.context = context;
        this.callback = callback;
        this.driveFollowLogger = new DriveFollowLogger(context);
        build();
    }

    public LinearLayout build() {
        tabContent = new LinearLayout(context);
        tabContent.setOrientation(LinearLayout.VERTICAL);
        tabContent.setBaselineAligned(false);
        int margin = CockpitTokens.dim(context, R.dimen.ck_margin);
        tabContent.setPadding(margin, CockpitTokens.dim(context, R.dimen.ck_space_sm),
                margin, margin);
        tabContent.setBackgroundColor(Color.TRANSPARENT);
        tabContent.setVisibility(android.view.View.VISIBLE);

        LinearLayout glassShell = new LinearLayout(context);
        glassShell.setOrientation(LinearLayout.VERTICAL);
        glassShell.setBaselineAligned(false);
        int inner = CockpitTokens.dim(context, R.dimen.ck_card_padding);
        glassShell.setPadding(inner, inner, inner, inner);
        CockpitSurfaces.level1(glassShell);
        LinearLayout.LayoutParams shellParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f);
        tabContent.addView(glassShell, shellParams);

        LinearLayout logControlPanel = new LinearLayout(context);
        logControlPanel.setOrientation(LinearLayout.HORIZONTAL);
        logControlPanel.setPadding(0, 0, 0, 8);
        logControlPanel.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView logTitle = new TextView(context);
        logTitle.setText(R.string.log_system_logs);
        CockpitTokens.headlineSm(logTitle);
        LinearLayout.LayoutParams logTitleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        logControlPanel.addView(logTitle, logTitleParams);

        Button btnCameraTest = new Button(context);
        btnCameraTest.setText(R.string.log_camera_test);
        btnCameraTest.setTextColor(UiStyles.color(context, R.color.textPrimary));
        btnCameraTest.setTextSize(15);
        UiStyles.styleOemButton(btnCameraTest, CockpitTokens.color(context, R.color.ck_accent));
        btnCameraTest.setTextColor(CockpitTokens.color(context, R.color.ck_on_accent));
        btnCameraTest.setPadding(16, 8, 16, 8);
        UiStyles.setButtonStartIconTinted(btnCameraTest, R.drawable.ic_mdi_camera,
                UiStyles.color(context, R.color.textPrimary),
                UiStyles.dimenPx(context, R.dimen.spacing_small));
        btnCameraTest.setOnClickListener(v -> {
            try {
                CarInfoProxy carInfo = CarInfoProxy.getInstance();
                if (carInfo.isServiceConnnected()) {
                    carInfo.sendItemValue(VDEventCarInfo.MODULE_AVM, AvmID.ID_AVM_DISPLAY, 1);
                    callback.log("Kamera Test: AVM görüntü isteği gönderildi (CarInfo MODULE_AVM, ID_AVM_DISPLAY=1), Camera2 ekranı açılıyor");
                } else {
                    callback.log("Kamera Test: CarInfo bağlı değil — sadece Camera2 önizleme açılıyor (AVM isteği atlandı)");
                }
            } catch (Throwable t) {
                callback.log("Kamera Test: AVM isteği hata: " + t.getMessage() + " — yine de Camera2 açılıyor");
            }
            context.startActivity(new Intent(context, CameraActivity.class));
        });
        LinearLayout.LayoutParams cameraTestParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cameraTestParams.setMargins(0, 0, 8, 0);
        logControlPanel.addView(btnCameraTest, cameraTestParams);

        Button btnDmsFaces = new Button(context);
        btnDmsFaces.setText(R.string.log_dms_face_profiles);
        btnDmsFaces.setTextColor(UiStyles.color(context, R.color.textPrimary));
        btnDmsFaces.setTextSize(15);
        UiStyles.styleOemButton(btnDmsFaces, CockpitTokens.color(context, R.color.ck_surface_2));
        btnDmsFaces.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        btnDmsFaces.setPadding(16, 8, 16, 8);
        btnDmsFaces.setOnClickListener(v ->
                context.startActivity(new Intent(context, DmsFaceProfileActivity.class)));
        LinearLayout.LayoutParams dmsFacesParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        dmsFacesParams.setMargins(0, 0, 8, 0);
        logControlPanel.addView(btnDmsFaces, dmsFacesParams);

        Button btnWelcome = new Button(context);
        btnWelcome.setText(R.string.log_welcome);
        btnWelcome.setTextColor(UiStyles.color(context, R.color.textPrimary));
        btnWelcome.setTextSize(15);
        btnWelcome.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_SEMIBOLD));
        UiStyles.styleOemButton(btnWelcome, CockpitTokens.color(context, R.color.ck_surface_2));
        btnWelcome.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        btnWelcome.setPadding(16, 8, 16, 8);
        UiStyles.setButtonStartIconTinted(btnWelcome, R.drawable.ic_mdi_bell,
                UiStyles.color(context, R.color.textPrimary),
                UiStyles.dimenPx(context, R.dimen.spacing_small));
        btnWelcome.setOnClickListener(v -> callback.onWelcomeTts());
        LinearLayout.LayoutParams welcomeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        welcomeParams.setMargins(0, 0, 8, 0);
        logControlPanel.addView(btnWelcome, welcomeParams);

        Button btnClearLogs = new Button(context);
        btnClearLogs.setTextColor(UiStyles.color(context, R.color.textPrimary));
        btnClearLogs.setTextSize(20);
        btnClearLogs.setBackgroundColor(CockpitTokens.color(context, R.color.ck_critical_bg));
        btnClearLogs.setPadding(0, 0, 0, 0);
        UiStyles.setButtonIconOnlyTinted(btnClearLogs, R.drawable.ic_mdi_delete,
                CockpitTokens.color(context, R.color.ck_critical), "Kayıtları temizle");
        LinearLayout.LayoutParams clearLogsParams = new LinearLayout.LayoutParams(56, 56);
        logControlPanel.addView(btnClearLogs, clearLogsParams);

        LinearLayout ttsCard = new LinearLayout(context);
        ttsCard.setOrientation(LinearLayout.VERTICAL);
        CockpitSurfaces.level2(ttsCard);
        ttsCard.setPadding(CockpitTokens.dim(context, R.dimen.ck_space_md),
                CockpitTokens.dim(context, R.dimen.ck_space_sm),
                CockpitTokens.dim(context, R.dimen.ck_space_md),
                CockpitTokens.dim(context, R.dimen.ck_space_sm));
        LinearLayout.LayoutParams ttsCardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        ttsCardParams.setMargins(0, 0, 0, 10);

        TextView ttsSectionTitle = new TextView(context);
        ttsSectionTitle.setText(R.string.log_tts_section);
        CockpitTokens.labelCaps(ttsSectionTitle);
        ttsCard.addView(ttsSectionTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        ttsInput = new EditText(context);
        ttsInput.setHint(R.string.log_tts_hint);
        ttsInput.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        ttsInput.setHintTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        ttsInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        ttsInput.setTypeface(Typeface.MONOSPACE);
        ttsInput.setMinLines(2);
        ttsInput.setMaxLines(5);
        ttsInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        ttsInput.setPadding(14, 12, 14, 12);
        android.graphics.drawable.GradientDrawable ttsFieldBg = new android.graphics.drawable.GradientDrawable();
        ttsFieldBg.setColor(UiStyles.color(context, R.color.logFieldBg));
        ttsFieldBg.setCornerRadius(context.getResources().getDimension(R.dimen.oem_button_radius));
        ttsFieldBg.setStroke(1, UiStyles.color(context, R.color.outlineStrong));
        ttsInput.setBackground(ttsFieldBg);
        LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        etParams.setMargins(0, 8, 0, 0);
        ttsCard.addView(ttsInput, etParams);

        LinearLayout ttsButtons = new LinearLayout(context);
        ttsButtons.setOrientation(LinearLayout.HORIZONTAL);
        ttsButtons.setGravity(Gravity.END);
        ttsButtons.setPadding(0, 10, 0, 0);

        Button btnReadAloud = new Button(context);
        btnReadAloud.setText(R.string.log_read_aloud);
        btnReadAloud.setTextColor(UiStyles.color(context, R.color.textPrimary));
        btnReadAloud.setTextSize(15);
        btnReadAloud.setTypeface(null, Typeface.BOLD);
        UiStyles.styleOemButton(btnReadAloud, UiStyles.color(context, R.color.buttonPrimary));
        btnReadAloud.setPadding(20, 12, 20, 12);
        btnReadAloud.setOnClickListener(v -> {
            CharSequence cs = ttsInput.getText();
            String t = cs != null ? cs.toString() : "";
            callback.onReadAloud(t);
        });
        LinearLayout.LayoutParams readParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        ttsButtons.addView(btnReadAloud, readParams);

        Button btnIflytek = new Button(context);
        btnIflytek.setText(R.string.log_iflytek_read);
        btnIflytek.setTextColor(UiStyles.color(context, R.color.textPrimary));
        btnIflytek.setTextSize(15);
        btnIflytek.setTypeface(null, Typeface.BOLD);
        UiStyles.styleOemButton(btnIflytek, UiStyles.color(context, R.color.buttonSecondaryMuted));
        btnIflytek.setPadding(20, 12, 20, 12);
        btnIflytek.setOnClickListener(v -> {
            CharSequence cs = ttsInput.getText();
            String t = cs != null ? cs.toString().trim() : "";
            final String textToSpeak = t.isEmpty()
                    ? "Merhaba, iFlytek TTS testi." : t;
            IflytekTts.speak(context, textToSpeak, (ok, msg) -> {
                callback.log(msg);
                if (ok) callback.log("Metin: " + textToSpeak);
            });
        });
        LinearLayout.LayoutParams iflytekParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        iflytekParams.setMarginStart(8);
        ttsButtons.addView(btnIflytek, iflytekParams);

        Button btnIflytekStop = new Button(context);
        btnIflytekStop.setText(R.string.log_iflytek_stop);
        btnIflytekStop.setTextColor(UiStyles.color(context, R.color.textPrimary));
        btnIflytekStop.setTextSize(15);
        UiStyles.styleOemButton(btnIflytekStop, UiStyles.color(context, R.color.buttonSecondaryMuted));
        btnIflytekStop.setPadding(20, 12, 20, 12);
        btnIflytekStop.setOnClickListener(v ->
                IflytekTts.stop(context, (ok, msg) -> callback.log(msg)));
        LinearLayout.LayoutParams iflytekStopParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        iflytekStopParams.setMarginStart(8);
        ttsButtons.addView(btnIflytekStop, iflytekStopParams);

        ttsCard.addView(ttsButtons, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        Button btnClusterVDBusTest = new Button(context);
        btnClusterVDBusTest.setText(R.string.log_cluster_test);
        btnClusterVDBusTest.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btnClusterVDBusTest.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        UiStyles.styleOemButton(btnClusterVDBusTest, CockpitTokens.color(context, R.color.ck_surface_2));
        btnClusterVDBusTest.setPadding(16, 12, 16, 12);
        btnClusterVDBusTest.setOnClickListener(v ->
                context.startActivity(new Intent(context, ClusterVDBusTestActivity.class)));
        LinearLayout.LayoutParams benchBtnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        benchBtnLp.setMargins(0, 10, 0, 0);

        Button btnValueWatcher = new Button(context);
        btnValueWatcher.setText(R.string.log_value_watcher_start);
        btnValueWatcher.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btnValueWatcher.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        UiStyles.styleOemButton(btnValueWatcher, CockpitTokens.color(context, R.color.ck_surface_2));
        btnValueWatcher.setPadding(16, 12, 16, 12);
        btnValueWatcher.setOnClickListener(v -> {
            if (valueWatcher.isRunning()) {
                valueWatcher.stop(callback::log);
                btnValueWatcher.setText(R.string.log_value_watcher_start);
            } else {
                valueWatcher.start(callback::log);
                btnValueWatcher.setText(R.string.log_value_watcher_stop);
            }
        });

        Button btnDriveFollow = new Button(context);
        btnDriveFollow.setText(R.string.log_drive_follow_start);
        btnDriveFollow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btnDriveFollow.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        UiStyles.styleOemButton(btnDriveFollow, CockpitTokens.color(context, R.color.ck_surface_2));
        btnDriveFollow.setPadding(16, 12, 16, 12);
        btnDriveFollow.setOnClickListener(v -> {
            if (driveFollowLogger.isRunning()) {
                driveFollowLogger.stop(callback::log);
                btnDriveFollow.setText(R.string.log_drive_follow_start);
            } else {
                driveFollowLogger.start(callback::log);
                btnDriveFollow.setText(R.string.log_drive_follow_stop);
            }
        });

        Button btnAdasProbe = new Button(context);
        btnAdasProbe.setText(R.string.log_adas_probe);
        // ADAS/FCM kaynakları araçta güvenilir değil; kazara araç hattı okumayı önle.
        btnAdasProbe.setVisibility(android.view.View.VISIBLE);
        btnAdasProbe.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btnAdasProbe.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        UiStyles.styleOemButton(btnAdasProbe, CockpitTokens.color(context, R.color.ck_surface_2));
        btnAdasProbe.setPadding(16, 12, 16, 12);
        btnAdasProbe.setOnClickListener(v ->
                context.startActivity(new Intent(context, AdasProbeActivity.class)));

        Button btnTsrMonitor = new Button(context);
        btnTsrMonitor.setText(R.string.log_tsr_monitor);
        btnTsrMonitor.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btnTsrMonitor.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        UiStyles.styleOemButton(btnTsrMonitor, CockpitTokens.color(context, R.color.ck_surface_2));
        btnTsrMonitor.setPadding(16, 12, 16, 12);
        btnTsrMonitor.setOnClickListener(v ->
                context.startActivity(new Intent(context, TrafficSignMonitorActivity.class)));

        Button btnFrontProbe = new Button(context);
        btnFrontProbe.setText(R.string.log_front_probe);
        btnFrontProbe.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btnFrontProbe.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        UiStyles.styleOemButton(btnFrontProbe, CockpitTokens.color(context, R.color.ck_surface_2));
        btnFrontProbe.setPadding(16, 12, 16, 12);
        btnFrontProbe.setOnClickListener(v ->
                context.startActivity(new Intent(context, FrontObjectProbeActivity.class)));

        Button btnIdDiscovery = new Button(context);
        btnIdDiscovery.setText(R.string.log_id_discovery);
        btnIdDiscovery.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btnIdDiscovery.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        UiStyles.styleOemButton(btnIdDiscovery, CockpitTokens.color(context, R.color.ck_surface_2));
        btnIdDiscovery.setPadding(16, 12, 16, 12);
        btnIdDiscovery.setOnClickListener(v ->
                context.startActivity(new Intent(context, IdDiscoveryActivity.class)));

        ScrollView upperScroll = new ScrollView(context);
        upperScroll.setFillViewport(true);
        LinearLayout upperColumn = new LinearLayout(context);
        upperColumn.setOrientation(LinearLayout.VERTICAL);
        upperColumn.setBaselineAligned(false);
        upperColumn.addView(logControlPanel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        upperColumn.addView(btnClusterVDBusTest, benchBtnLp);
        upperColumn.addView(btnValueWatcher, new LinearLayout.LayoutParams(benchBtnLp));
        upperColumn.addView(btnDriveFollow, new LinearLayout.LayoutParams(benchBtnLp));
        upperColumn.addView(btnAdasProbe, new LinearLayout.LayoutParams(benchBtnLp));
        upperColumn.addView(btnTsrMonitor, new LinearLayout.LayoutParams(benchBtnLp));
        upperColumn.addView(btnFrontProbe, new LinearLayout.LayoutParams(benchBtnLp));
        upperColumn.addView(btnIdDiscovery, new LinearLayout.LayoutParams(benchBtnLp));
        upperColumn.addView(ttsCard, ttsCardParams);
        upperScroll.addView(upperColumn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams upperParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 0.42f);
        glassShell.addView(upperScroll, upperParams);

        LinearLayout terminalContainer = new LinearLayout(context);
        terminalContainer.setOrientation(LinearLayout.VERTICAL);
        terminalContainer.setBackgroundColor(CockpitTokens.color(context, R.color.ck_bg_recessed));
        terminalContainer.setPadding(0, 0, 0, 0);

        LinearLayout terminalHeader = new LinearLayout(context);
        terminalHeader.setOrientation(LinearLayout.HORIZONTAL);
        terminalHeader.setBackgroundColor(CockpitTokens.color(context, R.color.ck_surface_1));
        terminalHeader.setPadding(16, 12, 16, 12);
        terminalHeader.setGravity(android.view.Gravity.CENTER_VERTICAL);

        LinearLayout dotsLayout = new LinearLayout(context);
        dotsLayout.setOrientation(LinearLayout.HORIZONTAL);

        TextView redDot = new TextView(context);
        redDot.setText("●");
        redDot.setTextColor(UiStyles.color(context, R.color.terminalDotRed));
        redDot.setTextSize(16);
        redDot.setPadding(0, 0, 8, 0);
        dotsLayout.addView(redDot);

        TextView yellowDot = new TextView(context);
        yellowDot.setText("●");
        yellowDot.setTextColor(UiStyles.color(context, R.color.terminalDotYellow));
        yellowDot.setTextSize(16);
        yellowDot.setPadding(0, 0, 8, 0);
        dotsLayout.addView(yellowDot);

        TextView greenDot = new TextView(context);
        greenDot.setText("●");
        greenDot.setTextColor(UiStyles.color(context, R.color.terminalDotGreen));
        greenDot.setTextSize(16);
        dotsLayout.addView(greenDot);

        terminalHeader.addView(dotsLayout, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView terminalPath = new TextView(context);
        terminalPath.setText("/var/log/syslog/vehicle_core.log");
        terminalPath.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        terminalPath.setTextSize(15);
        terminalPath.setTypeface(CockpitTokens.mono(context));
        LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        pathParams.gravity = android.view.Gravity.END;
        terminalHeader.addView(terminalPath, pathParams);

        terminalContainer.addView(terminalHeader, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        logScrollView = new ScrollView(context);
        logScrollView.setFillViewport(true);
        logScrollView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_INSET);
        logScrollView.setBackgroundColor(CockpitTokens.color(context, R.color.ck_bg_recessed));
        logScrollView.setPadding(12, 12, 12, 12);

        logsTextView = new TextView(context);
        logsTextView.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        logsTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        logsTextView.setTypeface(CockpitTokens.mono(context));
        logsTextView.setPadding(10, 12, 10, 12);
        logsTextView.setLineSpacing(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 5f, context.getResources().getDisplayMetrics()), 1.0f);
        logsTextView.setMinHeight((int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 120f, context.getResources().getDisplayMetrics()));
        logScrollView.addView(logsTextView);

        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f);
        terminalContainer.addView(logScrollView, scrollParams);

        LinearLayout.LayoutParams terminalParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 0.66f);
        terminalParams.setMargins(0, 8, 0, 0);
        glassShell.addView(terminalContainer, terminalParams);

        btnClearLogs.setOnClickListener(v -> callback.onClearLogs());

        return tabContent;
    }

    public LinearLayout getTabContent() {
        return tabContent;
    }

    public TextView getLogsTextView() {
        return logsTextView;
    }

    public ScrollView getScrollView() {
        return logScrollView;
    }

    public EditText getTtsInput() {
        return ttsInput;
    }
}
