package com.mapcontrol.ui.builder;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mapcontrol.R;
import com.mapcontrol.driving.DrivingSessionRecorder;
import com.mapcontrol.driving.DrivingTrip;
import com.mapcontrol.driving.DrivingTripStore;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.valet.ValetModeManager;
import com.mapcontrol.vehicle.VehicleMetricsFormatter;
import com.mapcontrol.vehicle.VehicleMetricsRepository;
import com.mapcontrol.vehicle.VehicleMetricsSnapshot;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Varsayılan giriş ekranı: canlı araç özeti, hızlı işlemler ve son sürüş.
 * İlk açılışta kısa bir başlangıç kartı gösterir; kullanıcı kapattığında bir daha çıkmaz.
 */
public class HomeTabBuilder implements VehicleMetricsRepository.Listener {

    public interface HomeCallback {
        void onNavigate(int tabIndex, String title);
        void log(String message);
    }

    private static final int TAB_VEHICLE_INFO = 9;
    private static final int TAB_COMFORT = 15;
    private static final int TAB_VALET = 16;
    private static final int TAB_DRIVING_HISTORY = 14;
    private static final String PREFS = "MapControlPrefs";
    private static final String KEY_ONBOARDING_DONE = "homeOnboardingDismissed";

    private final Context context;
    private final VehicleMetricsRepository repository;
    private final VehicleMetricsFormatter formatter;
    private final HomeCallback callback;
    private final SharedPreferences prefs;

    private ScrollView scrollView;
    private LinearLayout content;
    private TextView statusText;
    private TextView fuelValue;
    private TextView rangeValue;
    private TextView odoValue;
    private TextView heroStatusText;
    private TextView heroSpeedValue;
    private TextView heroRangeValue;
    private TextView heroTireValue;
    private TextView heroDriveValue;
    private LinearLayout lastDriveHost;
    private volatile boolean tabVisible;

    public HomeTabBuilder(Context context, VehicleMetricsRepository repository, HomeCallback callback) {
        this.context = context;
        this.repository = repository;
        this.formatter = new VehicleMetricsFormatter(context);
        this.callback = callback;
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        build();
    }

    public ScrollView getScrollView() {
        return scrollView;
    }

    public void startListening() {
        tabVisible = true;
        renderLastDrive();
        repository.removeListener(this);
        repository.addListener(this);
        repository.refresh();
    }

    public void stopListening() {
        tabVisible = false;
        repository.removeListener(this);
    }

    public void release() {
        stopListening();
    }

    private void build() {
        scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.TRANSPARENT);

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        content.setPadding(pad, dp(16), pad, dp(28));
        scrollView.addView(content, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        content.addView(buildHeader());
        content.addView(buildCockpitHero(), cardParams());
        content.addView(buildQuickActions(), cardParams());
        content.addView(buildVehicleCard(), cardParams());
        lastDriveHost = new LinearLayout(context);
        lastDriveHost.setOrientation(LinearLayout.VERTICAL);
        content.addView(lastDriveHost, cardParams());
        renderLastDrive();
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);

        TextView greeting = text(greeting(), 24, R.color.textPrimary, true);
        header.addView(greeting);

        TextView subtitle = text("Tiggo 8 · araç özeti ve hızlı işlemler", 13,
                R.color.textSecondary, false);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleLp.topMargin = dp(2);
        subtitleLp.bottomMargin = dp(4);
        header.addView(subtitle, subtitleLp);

        header.addView(buildHeaderStatusRow());

        if (ValetModeManager.isActive(context)) {
            TextView valet = text("VALE MODU AKTİF · kişisel sekmeler gizli", 12,
                    R.color.textPrimary, true);
            valet.setGravity(Gravity.CENTER_VERTICAL);
            valet.setPadding(dp(12), dp(8), dp(12), dp(8));
            GradientDrawable bg = rounded(UiStyles.color(context, R.color.buttonDanger), dp(10));
            valet.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(10);
            header.addView(valet, lp);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        header.setLayoutParams(lp);
        return header;
    }

    private View buildOnboardingCard() {
        LinearLayout card = card();
        card.addView(text("MAPCONTROL'A HOŞ GELDİN", 12, R.color.oemAccent, true));

        TextView title = text("Aracını tek ekrandan tanı", 20, R.color.textPrimary, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(4);
        card.addView(title, titleLp);

        TextView body = text("Önce araç verisini gör, sonra sürüşünü analiz et. "
                + "Wi-Fi, konfor ve valet ayarları hazır olduğunda tek dokunuşla açılır.", 13,
                R.color.textSecondary, false);
        body.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bodyLp.topMargin = dp(6);
        bodyLp.bottomMargin = dp(10);
        card.addView(body, bodyLp);

        LinearLayout steps = new LinearLayout(context);
        steps.setOrientation(LinearLayout.VERTICAL);
        steps.addView(step("1", "Araç Bilgisi'nde canlı veriyi doğrula"));
        steps.addView(step("2", "Sürüş Karnesi'nde son yolculuğunu gör"));
        LinearLayout.LayoutParams stepsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        stepsLp.bottomMargin = dp(12);
        card.addView(steps, stepsLp);

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button primary = actionButton("Araç Bilgisi", R.color.buttonPrimary);
        primary.setOnClickListener(v -> {
            dismissOnboarding();
            callback.onNavigate(TAB_VEHICLE_INFO, "Araç Bilgisi");
        });
        Button secondary = actionButton("Başla", R.color.buttonNeutral);
        secondary.setOnClickListener(v -> dismissOnboarding());
        actions.addView(primary, weighted(0, dp(4)));
        actions.addView(secondary, weighted(dp(4), 0));
        card.addView(actions);
        return card;
    }

    private View buildHeaderStatusRow() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        lp.bottomMargin = dp(2);
        row.setLayoutParams(lp);

        LinearLayout score = new LinearLayout(context);
        score.setOrientation(LinearLayout.HORIZONTAL);
        score.setGravity(Gravity.CENTER_VERTICAL);
        score.setPadding(dp(12), dp(7), dp(12), dp(7));
        GradientDrawable scoreBg = rounded(0x1F22C55E, dp(12));
        scoreBg.setStroke(Math.max(1, dp(1)), 0x5522C55E);
        score.setBackground(scoreBg);

        TextView scoreValue = text(String.valueOf(driverScore()), 18, R.color.buttonSuccessBright, true);
        score.addView(scoreValue);
        TextView scoreLabel = text("  SÜRÜCÜ PUANI", 10, R.color.textSecondary, true);
        scoreLabel.setLetterSpacing(0.08f);
        score.addView(scoreLabel);
        row.addView(score);

        View spacer = new View(context);
        row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        TextView time = text(new SimpleDateFormat("HH:mm:ss", new Locale("tr", "TR")).format(new Date()),
                12, R.color.textSecondary, false);
        row.addView(time);
        return row;
    }

    private View buildCockpitHero() {
        LinearLayout card = card();
        card.setPadding(dp(18), dp(16), dp(18), dp(16));

        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = text("360° AKTİF İZLEME", 10, R.color.oemAccent, true);
        badge.setLetterSpacing(0.08f);
        badge.setPadding(dp(9), dp(5), dp(9), dp(5));
        GradientDrawable badgeBg = rounded(0x1F5EEAD4, dp(7));
        badgeBg.setStroke(Math.max(1, dp(1)), 0x555EEAD4);
        badge.setBackground(badgeBg);
        top.addView(badge);

        View spacer = new View(context);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        heroStatusText = text("Bağlantı bekleniyor", 10, R.color.textLoading, true);
        top.addView(heroStatusText);
        card.addView(top);

        LinearLayout carRow = new LinearLayout(context);
        carRow.setOrientation(LinearLayout.HORIZONTAL);
        carRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams carLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(112));
        carLp.topMargin = dp(8);
        carRow.setLayoutParams(carLp);

        ImageView carIcon = new ImageView(context);
        carIcon.setImageResource(R.drawable.ic_mdi_car);
        carIcon.setColorFilter(UiStyles.color(context, R.color.oemAccent));
        carIcon.setAlpha(0.88f);
        carIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        carRow.addView(carIcon, new LinearLayout.LayoutParams(dp(118), dp(96)));

        LinearLayout carText = new LinearLayout(context);
        carText.setOrientation(LinearLayout.VERTICAL);
        carText.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams carTextLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        carTextLp.setMarginStart(dp(16));
        TextView model = text("CHERY TIGGO 8 PRO MAX", 15, R.color.textPrimary, true);
        model.setLetterSpacing(0.04f);
        carText.addView(model);
        TextView telematics = text("CAN-FD Telematik • Tüm sistemler izleniyor", 12,
                R.color.textSecondary, false);
        LinearLayout.LayoutParams teleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        teleLp.topMargin = dp(3);
        carText.addView(telematics, teleLp);
        heroDriveValue = text("Sürüş Modu: Eco / Konfor", 12, R.color.textSecondary, false);
        LinearLayout.LayoutParams driveLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        driveLp.topMargin = dp(3);
        carText.addView(heroDriveValue, driveLp);
        carRow.addView(carText, carTextLp);
        card.addView(carRow);

        LinearLayout metrics = new LinearLayout(context);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams metricsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        metricsLp.topMargin = dp(4);
        card.addView(metrics, metricsLp);
        heroSpeedValue = metric(metrics, "ANLIK HIZ", "0 km/s");
        heroRangeValue = metric(metrics, "MENZİL", "—");
        heroTireValue = metric(metrics, "LASTİK", "TPMS OK");
        return card;
    }

    private int driverScore() {
        DrivingSessionRecorder recorder = DrivingSessionRecorder.get(context);
        DrivingTrip trip = recorder.isActive() ? recorder.getDisplayTrip() : recorder.getLastTrip();
        if (trip != null && trip.score > 0) {
            return trip.score;
        }
        List<DrivingTrip> history = DrivingTripStore.loadHistory(context);
        return history.isEmpty() ? 100 : history.get(0).score;
    }

    private View buildQuickActions() {
        LinearLayout section = new LinearLayout(context);
        section.setOrientation(LinearLayout.VERTICAL);

        TextView title = text("HIZLI İŞLEMLER", 12, R.color.textHint, true);
        title.setLetterSpacing(0.12f);
        section.addView(title);

        LinearLayout firstRow = new LinearLayout(context);
        firstRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams firstLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        firstLp.topMargin = dp(8);
        section.addView(firstRow, firstLp);

        firstRow.addView(quickAction("Araç Bilgisi", "Canlı hız, yakıt, menzil",
                R.color.oemAccent, () -> callback.onNavigate(TAB_VEHICLE_INFO, "Araç Bilgisi")),
                weighted(0, dp(5)));
        firstRow.addView(quickAction("Sürüş Karnesi", "Puan ve sürüş geçmişi",
                R.color.buttonSuccessBright, () -> callback.onNavigate(TAB_DRIVING_HISTORY, "Sürüş Karnesi")),
                weighted(dp(5), 0));

        LinearLayout secondRow = new LinearLayout(context);
        secondRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams secondLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        secondLp.topMargin = dp(10);
        section.addView(secondRow, secondLp);

        secondRow.addView(quickAction("Konfor", "Klima ve koltuk ayarları",
                R.color.oemAccent, () -> callback.onNavigate(TAB_COMFORT, "Konfor")),
                weighted(0, dp(5)));
        secondRow.addView(quickAction("Vale Modu", "Hız, alan ve saat sınırı",
                R.color.buttonDanger, () -> callback.onNavigate(TAB_VALET, "Vale Modu")),
                weighted(dp(5), 0));
        return section;
    }

    private View buildVehicleCard() {
        LinearLayout card = card();
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("ARAÇ DURUMU", 12, R.color.textHint, true);
        title.setLetterSpacing(0.12f);
        header.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        statusText = text("Bağlantı bekleniyor", 11, R.color.textLoading, true);
        header.addView(statusText);
        card.addView(header);

        LinearLayout metrics = new LinearLayout(context);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams metricsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        metricsLp.topMargin = dp(12);
        card.addView(metrics, metricsLp);

        fuelValue = metric(metrics, "YAKIT", "—");
        rangeValue = metric(metrics, "MENZİL", "—");
        odoValue = metric(metrics, "ODOMETRE", "—");

        Button detail = actionButton("Araç Bilgisi'ni aç", R.color.buttonNeutral);
        detail.setOnClickListener(v -> callback.onNavigate(TAB_VEHICLE_INFO, "Araç Bilgisi"));
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        detailLp.topMargin = dp(12);
        card.addView(detail, detailLp);
        return card;
    }

    private void renderLastDrive() {
        if (lastDriveHost == null) {
            return;
        }
        lastDriveHost.removeAllViews();
        lastDriveHost.addView(buildLastDriveCard(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private View buildLastDriveCard() {
        LinearLayout card = card();
        TextView title = text("SON SÜRÜŞ", 12, R.color.textHint, true);
        title.setLetterSpacing(0.12f);
        card.addView(title);

        DrivingSessionRecorder recorder = DrivingSessionRecorder.get(context);
        DrivingTrip trip = recorder.isActive() ? recorder.getDisplayTrip() : recorder.getLastTrip();
        if (trip == null) {
            List<DrivingTrip> history = DrivingTripStore.loadHistory(context);
            if (!history.isEmpty()) {
                trip = history.get(0);
            }
        }

        if (trip == null) {
            TextView empty = text("Henüz kayıtlı bir sürüş yok. İlk yolculuğundan sonra "
                    + "puanın ve yakıt analizin burada görünecek.", 13, R.color.textSecondary, false);
            empty.setPadding(0, dp(8), 0, dp(8));
            card.addView(empty);
            return card;
        }

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(8);
        card.addView(row, rowLp);

        TextView score = text(String.valueOf(trip.score), 34, R.color.oemAccent, true);
        row.addView(score, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout info = new LinearLayout(context);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        infoLp.setMarginStart(dp(14));
        row.addView(info, infoLp);

        info.addView(text(trip.title.isEmpty() ? "Sürüş" : trip.title, 16,
                R.color.textPrimary, true));
        SimpleDateFormat fmt = new SimpleDateFormat("d MMM · HH:mm", new Locale("tr", "TR"));
        String meta = fmt.format(new Date(trip.startTimeMs))
                + " · " + String.format(Locale.getDefault(), "%.1f km", trip.distanceKm);
        info.addView(text(meta, 12, R.color.textSecondary, false));
        if (trip.consumptionL100 > 0) {
            info.addView(text(String.format(Locale.getDefault(), "%.1f L/100 km", trip.consumptionL100),
                    12, R.color.textHint, false));
        }

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = dp(12);
        card.addView(actions, actionsLp);

        Button open = actionButton("Tüm sürüşleri gör", R.color.buttonPrimary);
        open.setOnClickListener(v -> callback.onNavigate(TAB_DRIVING_HISTORY, "Sürüş Karnesi"));
        actions.addView(open, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return card;
    }

    @Override
    public void onMetricsUpdated(VehicleMetricsSnapshot snapshot) {
        if (!tabVisible || statusText == null) {
            return;
        }
        if (!snapshot.connected) {
            statusText.setText("Bağlantı bekleniyor");
            statusText.setTextColor(UiStyles.color(context, R.color.textLoading));
            fuelValue.setText("—");
            rangeValue.setText("—");
            odoValue.setText("—");
            if (heroStatusText != null) heroStatusText.setText("Bağlantı bekleniyor");
            if (heroSpeedValue != null) heroSpeedValue.setText("0 km/s");
            if (heroRangeValue != null) heroRangeValue.setText("—");
            if (heroTireValue != null) heroTireValue.setText("TPMS —");
            return;
        }
        statusText.setText("Bağlı");
        statusText.setTextColor(UiStyles.color(context, R.color.buttonSuccessBright));
        fuelValue.setText(formatter.formatDashboardFuel(snapshot));
        rangeValue.setText(formatter.formatDashboardRange(snapshot));
        odoValue.setText(formatter.formatDashboardOdo(snapshot));
        if (heroStatusText != null) {
            heroStatusText.setText("SİSTEMLER HAZIR");
            heroStatusText.setTextColor(UiStyles.color(context, R.color.buttonSuccessBright));
        }
        if (heroSpeedValue != null) heroSpeedValue.setText(formatter.formatDashboardSpeed(snapshot));
        if (heroRangeValue != null) heroRangeValue.setText(formatter.formatDashboardRange(snapshot));
        if (heroTireValue != null) {
            boolean warn = VehicleMetricsSnapshot.isValid(snapshot.tirePressureWarning)
                    && snapshot.tirePressureWarning != 0;
            heroTireValue.setText(warn ? "TPMS UYARI" : "TPMS OK");
            heroTireValue.setTextColor(UiStyles.color(context,
                    warn ? R.color.buttonDanger : R.color.textPrimary));
        }
        if (heroDriveValue != null) {
            heroDriveValue.setText("Sürüş Modu: " + driveModeLabel(snapshot.driveMode));
        }
    }

    private String driveModeLabel(int mode) {
        switch (mode) {
            case 0: return "Eco";
            case 1: return "Normal";
            case 2: return "Sport";
            case 3: return "Kar";
            case 4: return "Çamur";
            case 5: return "Offroad";
            case 7: return "Kum";
            default: return "Konfor";
        }
    }

    private void dismissOnboarding() {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply();
        View onboarding = content.getChildAt(1);
        if (onboarding != null) {
            content.removeView(onboarding);
        }
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(UiStyles.color(context, R.color.surfaceCard));
        bg.setCornerRadius(dp(16));
        bg.setStroke(Math.max(1, Math.round(dp(1))), UiStyles.color(context, R.color.outlineSubtle));
        card.setBackground(bg);
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        return lp;
    }

    private View step(String number, String label) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(3), 0, dp(3));
        TextView badge = text(number, 11, R.color.oemAccent, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rounded(UiStyles.color(context, R.color.surfaceCardElevated), dp(8)));
        badge.setWidth(dp(22));
        badge.setHeight(dp(22));
        row.addView(badge);
        TextView labelView = text(label, 13, R.color.textSecondary, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(dp(10));
        row.addView(labelView, lp);
        return row;
    }

    private View quickAction(String title, String subtitle, int accentColor, Runnable action) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.setClickable(true);
        card.setFocusable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(UiStyles.color(context, R.color.surfaceCardElevated));
        bg.setCornerRadius(dp(16));
        bg.setStroke(Math.max(1, Math.round(dp(1))), UiStyles.color(context, R.color.outlineSubtle));
        card.setBackground(bg);
        card.setOnClickListener(v -> action.run());

        TextView titleView = text(title, 15, R.color.textPrimary, true);
        card.addView(titleView);
        TextView subtitleView = text(subtitle, 11, R.color.textSecondary, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(3);
        card.addView(subtitleView, lp);

        View accent = new View(context);
        accent.setBackgroundColor(UiStyles.color(context, accentColor));
        LinearLayout.LayoutParams accentLp = new LinearLayout.LayoutParams(dp(28), dp(3));
        accentLp.topMargin = dp(10);
        card.addView(accent, accentLp);
        return card;
    }

    private TextView metric(LinearLayout parent, String label, String value) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        parent.addView(column, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView labelView = text(label, 10, R.color.textHint, true);
        labelView.setLetterSpacing(0.1f);
        column.addView(labelView);
        TextView valueView = text(value, 19, R.color.textPrimary, true);
        column.addView(valueView);
        return valueView;
    }

    private Button actionButton(String label, int bgColor) {
        Button button = new Button(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTextColor(UiStyles.color(context, R.color.textPrimary));
        button.setTypeface(null, Typeface.BOLD);
        UiStyles.styleOemButton(button, UiStyles.color(context, bgColor));
        button.setPadding(dp(14), dp(10), dp(14), dp(10));
        return button;
    }

    private TextView text(String value, int sp, int colorRes, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(UiStyles.color(context, colorRes));
        if (bold) {
            view.setTypeface(null, Typeface.BOLD);
        }
        return view;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private LinearLayout.LayoutParams weighted(int start, int end) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(start);
        lp.setMarginEnd(end);
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 6) return "İyi geceler";
        if (hour < 12) return "Günaydın";
        if (hour < 18) return "İyi günler";
        return "İyi akşamlar";
    }
}
