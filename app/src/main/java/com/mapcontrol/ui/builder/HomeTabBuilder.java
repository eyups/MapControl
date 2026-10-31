package com.mapcontrol.ui.builder;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mapcontrol.R;
import com.mapcontrol.driving.DrivingScoreRingView;
import com.mapcontrol.driving.DrivingSessionRecorder;
import com.mapcontrol.driving.DrivingTrip;
import com.mapcontrol.driving.DrivingTripStore;
import com.mapcontrol.ui.cockpit.CockpitButton;
import com.mapcontrol.ui.cockpit.CockpitCard;
import com.mapcontrol.ui.cockpit.CockpitGrid;
import com.mapcontrol.ui.cockpit.CockpitNavModel;
import com.mapcontrol.ui.cockpit.CockpitSectionHeader;
import com.mapcontrol.ui.cockpit.CockpitSurfaces;
import com.mapcontrol.ui.cockpit.CockpitTokens;
import com.mapcontrol.ui.cockpit.MetricView;
import com.mapcontrol.ui.cockpit.Sparkline;
import com.mapcontrol.ui.cockpit.StatusBadge;
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
 * Kokpit (Home): 8 kolonlu telematik kart yerleşimi. Veri kaynakları ve callback'ler korunur.
 */
public class HomeTabBuilder implements VehicleMetricsRepository.Listener {

    public interface HomeCallback {
        void onNavigate(int tabIndex, String title);
        void log(String message);
    }

    private static final int TAB_VEHICLE_INFO = 9;
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
    private CockpitGrid grid;
    private MetricView fuelValue;
    private MetricView rangeValue;
    private MetricView odoValue;
    private StatusBadge statusBadge;
    private CockpitCard onboardingCard;
    private View lastDriveCard;
    private android.widget.ImageView vehicleView;
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
        // Last drive is built once with the tab; live trip updates are reflected on next open.
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
        int m = CockpitTokens.dim(context, R.dimen.ck_margin);
        content.setPadding(m, CockpitTokens.dim(context, R.dimen.ck_space_md), 0, m);
        scrollView.addView(content, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        content.addView(buildHero());

        grid = new CockpitGrid(context);
        content.addView(grid, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        grid.add(buildVehicleVisual(), 4);
        grid.add(buildVehicleCard(), 4);
        grid.add(buildQuickActions(), 4);
        grid.add(buildLastDriveCard(), 4);
        grid.addFullWidth(buildValetCard());
        grid.addFullWidth(buildOnboardingCard());
    }

    private View buildHero() {
        LinearLayout hero = new LinearLayout(context);
        hero.setOrientation(LinearLayout.VERTICAL);
        TextView greeting = new TextView(context);
        CockpitTokens.headlineLg(greeting);
        greeting.setText(greeting());
        hero.addView(greeting);

        TextView subtitle = new TextView(context);
        CockpitTokens.bodySm(subtitle);
        subtitle.setText(context.getString(R.string.ck_home_subtitle));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = CockpitTokens.dim(context, R.dimen.ck_space_lg);
        hero.addView(subtitle, lp);
        return hero;
    }

    private CockpitCard buildVehicleVisual() {
        CockpitCard card = new CockpitCard(context,
                context.getString(R.string.ck_home_vehicle_visual)).span(4);
        card.setMinimumHeight(dp(300));
        card.status(StatusBadge.nominal(context, context.getString(R.string.ck_home_vehicle_visual_hint)));
        vehicleView = new android.widget.ImageView(context);
        vehicleView.setImageResource(R.drawable.t18_vehicle);
        vehicleView.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        vehicleView.setContentDescription(context.getString(R.string.ck_home_vehicle_visual_hint));
        vehicleView.setAlpha(0f);
        vehicleView.setScaleX(0.96f);
        vehicleView.setScaleY(0.96f);
        vehicleView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        card.addContent(vehicleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, CockpitTokens.dp(context, 210)));
        return card;
    }

    private CockpitCard buildVehicleCard() {
        CockpitCard card = new CockpitCard(context, context.getString(R.string.ck_home_vehicle_card)).span(4);
        card.setMinimumHeight(dp(300));
        statusBadge = StatusBadge.neutral(context, context.getString(R.string.ck_home_connecting));
        card.status(statusBadge);

        LinearLayout metrics = new LinearLayout(context);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.setGravity(Gravity.BOTTOM);
        fuelValue = new MetricView(context, context.getString(R.string.ck_home_fuel));
        rangeValue = new MetricView(context, context.getString(R.string.ck_home_range));
        odoValue = new MetricView(context, context.getString(R.string.ck_home_odo));
        metrics.addView(fuelValue, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        metrics.addView(rangeValue, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        metrics.addView(odoValue, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addContent(metrics);

        CockpitButton detail = CockpitButton.secondary(context, context.getString(R.string.ck_home_open_vehicle));
        detail.setOnClickListener(v -> callback.onNavigate(TAB_VEHICLE_INFO, context.getString(R.string.ck_home_vehicle_info)));
        card.addAction(detail);
        return card;
    }

    private CockpitCard buildQuickActions() {
        CockpitCard card = new CockpitCard(context, context.getString(R.string.ck_home_quick)).span(4);
        card.setMinimumHeight(dp(300));
        LinearLayout row1 = new LinearLayout(context);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(quick(context.getString(R.string.ck_home_vehicle_info), TAB_VEHICLE_INFO, context.getString(R.string.ck_home_vehicle_info)),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams gap1 = new LinearLayout.LayoutParams(
                CockpitTokens.dim(context, R.dimen.ck_space_sm), 1);
        row1.addView(new View(context), gap1);
        row1.addView(quick(context.getString(R.string.ck_home_drive_report), TAB_DRIVING_HISTORY, context.getString(R.string.ck_home_drive_report)),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addContent(row1);

        LinearLayout row2 = new LinearLayout(context);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        row2Lp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        card.addContent(row2, row2Lp);
        row2.addView(quick(context.getString(R.string.ck_sub_climate),
                        CockpitNavModel.TAB_CLIMATE_AUTOMATION,
                        context.getString(R.string.ck_sub_climate)),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row2.addView(new View(context), new LinearLayout.LayoutParams(
                CockpitTokens.dim(context, R.dimen.ck_space_sm), 1));
        row2.addView(quick(context.getString(R.string.ck_home_valet), TAB_VALET, context.getString(R.string.ck_home_valet)),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return card;
    }

    private CockpitButton quick(String label, int tab, String title) {
        CockpitButton b = CockpitButton.secondary(context, label);
        b.setMinHeight(CockpitTokens.dim(context, R.dimen.ck_touch_primary));
        b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
        b.setOnClickListener(v -> callback.onNavigate(tab, title));
        return b;
    }

    private View buildValetCard() {
        if (!ValetModeManager.isActive(context)) {
            return new View(context);
        }
        CockpitCard card = new CockpitCard(context, context.getString(R.string.ck_home_valet)).span(8);
        card.status(StatusBadge.advisory(context, context.getString(R.string.ck_home_active)));
        TextView body = new TextView(context);
        CockpitTokens.bodyMd(body);
        body.setText(context.getString(R.string.ck_home_valet_body));
        body.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        card.addContent(body);
        return card;
    }

    private CockpitCard buildOnboardingCard() {
        onboardingCard = new CockpitCard(context, context.getString(R.string.ck_home_onboarding_title)).span(8);
        onboardingCard.status(StatusBadge.nominal(context, context.getString(R.string.ck_home_setup)));
        if (prefs.getBoolean(KEY_ONBOARDING_DONE, false)) {
            onboardingCard.setVisibility(View.GONE);
            return onboardingCard;
        }

        TextView body = new TextView(context);
        CockpitTokens.bodyMd(body);
        body.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        body.setText(context.getString(R.string.ck_home_onboarding_body));
        onboardingCard.addContent(body);

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
        onboardingCard.addContent(actions, actionsLp);

        CockpitButton vehicle = CockpitButton.primary(context, context.getString(R.string.ck_home_vehicle_info));
        vehicle.setOnClickListener(v -> {
            dismissOnboarding();
            callback.onNavigate(TAB_VEHICLE_INFO, context.getString(R.string.ck_home_vehicle_info));
        });
        vehicle.setMinWidth(CockpitTokens.dim(context, R.dimen.ck_action_min_w));
        vehicle.setMaxWidth(CockpitTokens.dim(context, R.dimen.ck_action_max_w));
        actions.addView(vehicle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        CockpitButton start = CockpitButton.secondary(context, context.getString(R.string.ck_home_start));
        start.setOnClickListener(v -> dismissOnboarding());
        start.setMinWidth(CockpitTokens.dim(context, R.dimen.ck_action_min_w));
        start.setMaxWidth(CockpitTokens.dim(context, R.dimen.ck_action_max_w));
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        startLp.leftMargin = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        actions.addView(start, startLp);
        return onboardingCard;
    }

    private CockpitCard buildLastDriveCard() {
        CockpitCard card = new CockpitCard(context,
                context.getString(R.string.ck_home_last_drive)).span(4);
        card.setMinimumHeight(dp(300));
        DrivingSessionRecorder recorder = DrivingSessionRecorder.get(context);
        DrivingTrip trip = recorder.isActive() ? recorder.getDisplayTrip() : recorder.getLastTrip();
        if (trip == null) {
            List<DrivingTrip> history = DrivingTripStore.loadHistory(context);
            if (!history.isEmpty()) {
                trip = history.get(0);
            }
        }
        if (trip == null) {
            card.status(StatusBadge.neutral(context,
                    context.getString(R.string.ck_home_no_record)));
            TextView empty = new TextView(context);
            CockpitTokens.bodyMd(empty);
            empty.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
            empty.setText(context.getString(R.string.ck_home_no_drive));
            card.addContent(empty);
            return card;
        }

        StatusBadge badge = StatusBadge.nominal(context, recorder.isActive()
                ? context.getString(R.string.ck_home_in_progress)
                : context.getString(R.string.ck_home_completed));

        CockpitButton open = CockpitButton.secondary(context,
                context.getString(R.string.ck_home_view_all) + " →");
        open.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
        open.setMinHeight(dp(48));
        open.setOnClickListener(v -> callback.onNavigate(TAB_DRIVING_HISTORY,
                context.getString(R.string.ck_home_drive_report)));

        LinearLayout statusGroup = new LinearLayout(context);
        statusGroup.setOrientation(LinearLayout.HORIZONTAL);
        statusGroup.setGravity(Gravity.CENTER_VERTICAL);
        statusGroup.addView(badge);
        LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        openLp.leftMargin = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        statusGroup.addView(open, openLp);
        card.status(statusGroup);

        // Telemetri özeti: solda skor halkası + sürüş profili, sağda metrik kartları.
        LinearLayout hero = new LinearLayout(context);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable heroBg = new GradientDrawable();
        heroBg.setColor(CockpitTokens.color(context, R.color.ck_surface_2));
        heroBg.setCornerRadius(dp(14));
        hero.setBackground(heroBg);

        DrivingScoreRingView scoreRing = new DrivingScoreRingView(context);
        scoreRing.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_SEMIBOLD));
        scoreRing.setAccentColor(CockpitTokens.color(context, R.color.ck_accent));
        scoreRing.setTrackColor(CockpitTokens.color(context, R.color.ck_surface_1));
        scoreRing.setScore(trip.score, false);
        hero.addView(scoreRing, new LinearLayout.LayoutParams(dp(86), dp(86)));

        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        copyLp.leftMargin = dp(10);
        hero.addView(copy, copyLp);

        TextView profileLabel = new TextView(context);
        CockpitTokens.labelCaps(profileLabel);
        profileLabel.setText("SÜRÜŞ PROFİLİ");
        profileLabel.setTextColor(CockpitTokens.color(context, R.color.ck_accent));
        copy.addView(profileLabel);

        TextView profile = new TextView(context);
        CockpitTokens.title(profile);
        profile.setText(trip.title == null || trip.title.isEmpty()
                ? context.getString(R.string.ck_home_drive) : trip.title);
        copy.addView(profile);

        TextView profileMeta = new TextView(context);
        CockpitTokens.bodySm(profileMeta);
        SimpleDateFormat fmt = new SimpleDateFormat("d MMM · HH:mm", new Locale("tr", "TR"));
        profileMeta.setText(fmt.format(new Date(trip.startTimeMs))
                + " · " + String.format(Locale.getDefault(), "%.1f km", trip.distanceKm));
        profileMeta.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        copy.addView(profileMeta);

        card.addContent(hero);

        if (trip.speedSamples != null && trip.speedSamples.size() >= 2) {
            LinearLayout chartHeader = new LinearLayout(context);
            chartHeader.setOrientation(LinearLayout.HORIZONTAL);
            chartHeader.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams chartHeaderLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            chartHeaderLp.topMargin = dp(10);
            card.addContent(chartHeader, chartHeaderLp);

            TextView chartLabel = new TextView(context);
            CockpitTokens.labelCaps(chartLabel);
            chartLabel.setText("HIZ EĞRİSİ");
            chartLabel.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
            chartHeader.addView(chartLabel, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView chartMax = new TextView(context);
            CockpitTokens.bodySm(chartMax);
            chartMax.setText("Maks " + trip.maxSpeedKmh + " km/h");
            chartMax.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
            chartHeader.addView(chartMax);

            Sparkline sparkline = new Sparkline(context);
            int sampleCount = Math.min(60, trip.speedSamples.size());
            float[] data = new float[sampleCount];
            int stride = Math.max(1, trip.speedSamples.size() / sampleCount);
            for (int i = 0; i < sampleCount; i++) {
                data[i] = trip.speedSamples.get(Math.min(trip.speedSamples.size() - 1, i * stride));
            }
            sparkline.setData(data);
            LinearLayout chartBox = new LinearLayout(context);
            chartBox.setOrientation(LinearLayout.VERTICAL);
            chartBox.setPadding(dp(8), dp(6), dp(8), dp(6));
            CockpitSurfaces.level2(chartBox);
            chartBox.addView(sparkline, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));
            LinearLayout.LayoutParams chartLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            chartLp.topMargin = dp(4);
            card.addContent(chartBox, chartLp);
        }

        LinearLayout metrics = new LinearLayout(context);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.setBaselineAligned(false);
        LinearLayout.LayoutParams metricsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        metricsLp.topMargin = dp(8);
        card.addContent(metrics, metricsLp);

        metrics.addView(buildTelemetryMetric(
                        context.getString(R.string.driving_history_metric_distance),
                        String.format(Locale.getDefault(), "%.1f", trip.distanceKm),
                        "km", "Süre " + formatDuration(trip.durationSec)),
                weightedMetric(0));
        metrics.addView(buildTelemetryMetric(
                        context.getString(R.string.driving_history_metric_consumption),
                        trip.consumptionL100 > 0
                                ? String.format(Locale.getDefault(), "%.1f", trip.consumptionL100) : "—",
                        "L/100", "Ortalama"),
                weightedMetric(1));
        metrics.addView(buildTelemetryMetric(
                        context.getString(R.string.driving_history_metric_avg),
                        String.valueOf(trip.avgSpeedKmh),
                        "km/h", "Maks " + trip.maxSpeedKmh),
                weightedMetric(2));

        TextView events = new TextView(context);
        CockpitTokens.bodySm(events);
        events.setText("SÜRÜŞ OLAYLARI   Sert fren " + trip.harshBrakes
                + "  ·  Ani hızlanma " + trip.harshAccels);
        events.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        events.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_MEDIUM));
        LinearLayout.LayoutParams eventsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        eventsLp.topMargin = dp(8);
        card.addContent(events, eventsLp);
        return card;
    }

    private LinearLayout buildTelemetryMetric(String label, String value,
            String unit, String caption) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(9), dp(8), dp(9), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CockpitTokens.color(context, R.color.ck_surface_2));
        bg.setCornerRadius(dp(12));
        box.setBackground(bg);

        TextView labelView = new TextView(context);
        CockpitTokens.labelCaps(labelView);
        labelView.setText(label);
        labelView.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        box.addView(labelView);

        LinearLayout valueRow = new LinearLayout(context);
        valueRow.setOrientation(LinearLayout.HORIZONTAL);
        valueRow.setGravity(Gravity.BOTTOM);
        TextView valueView = new TextView(context);
        CockpitTokens.headlineMd(valueView);
        valueView.setText(value);
        valueView.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        valueRow.addView(valueView);
        TextView unitView = new TextView(context);
        CockpitTokens.bodySm(unitView);
        unitView.setText(unit);
        unitView.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        LinearLayout.LayoutParams unitLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        unitLp.leftMargin = dp(4);
        unitLp.bottomMargin = dp(3);
        valueRow.addView(unitView, unitLp);
        box.addView(valueRow);

        TextView captionView = new TextView(context);
        CockpitTokens.bodySm(captionView);
        captionView.setText(caption);
        captionView.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        box.addView(captionView);
        return box;
    }

    private LinearLayout.LayoutParams weightedMetric(int index) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (index > 0) {
            lp.leftMargin = dp(8);
        }
        return lp;
    }

    private String formatDuration(int seconds) {
        int minutes = Math.max(0, seconds) / 60;
        if (minutes < 60) {
            return minutes + " dk";
        }
        return (minutes / 60) + " sa " + (minutes % 60) + " dk";
    }

    @Override
    public void onMetricsUpdated(VehicleMetricsSnapshot snapshot) {
        if (!tabVisible || fuelValue == null) {
            return;
        }
        if (!snapshot.connected) {
            if (statusBadge != null) {
                statusBadge.setLevel(StatusBadge.ADVISORY);
                statusBadge.setText(context.getString(R.string.ck_home_connecting));
            }
            fuelValue.value("—");
            rangeValue.value("—");
            odoValue.value("—");
            return;
        }
        if (statusBadge != null) {
            statusBadge.setLevel(StatusBadge.NOMINAL);
            statusBadge.setText("Bağlı");
        }
        fuelValue.value(formatter.formatDashboardFuel(snapshot));
        rangeValue.value(formatter.formatDashboardRange(snapshot));
        odoValue.value(formatter.formatDashboardOdo(snapshot));
    }

    private void dismissOnboarding() {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply();
        if (onboardingCard != null) {
            onboardingCard.setVisibility(View.GONE);
        }
    }

    private int dp(int value) {
        return CockpitTokens.dp(context, value);
    }

    private String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 6) return context.getString(R.string.ck_home_greeting_night);
        if (hour < 12) return context.getString(R.string.ck_home_greeting_morning);
        if (hour < 18) return context.getString(R.string.ck_home_greeting_day);
        return context.getString(R.string.ck_home_greeting_evening);
    }
}
