package com.mapcontrol.ui.builder;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mapcontrol.R;
import com.mapcontrol.driving.DrivingCoach;
import com.mapcontrol.driving.DrivingSessionRecorder;
import com.mapcontrol.driving.DrivingTrip;
import com.mapcontrol.driving.DrivingTripStore;
import com.mapcontrol.driving.DrivingTrendBarsView;
import com.mapcontrol.driving.DrivingTrends;
import com.mapcontrol.ui.activity.DrivingAnalysisActivity;
import com.mapcontrol.ui.cockpit.CockpitButton;
import com.mapcontrol.ui.cockpit.CockpitCard;
import com.mapcontrol.ui.cockpit.CockpitChip;
import com.mapcontrol.ui.cockpit.CockpitEmptyState;
import com.mapcontrol.ui.cockpit.CockpitLegacyStyler;
import com.mapcontrol.ui.cockpit.CockpitSurfaces;
import com.mapcontrol.ui.cockpit.CockpitTokens;
import com.mapcontrol.ui.cockpit.CockpitToggle;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.util.AppLocaleManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Newest-first list of saved drives; each card opens its full report. */
public class DrivingHistoryTabBuilder {

    private static final String TAG = "DrivingHistoryUI";
    private final Context context;
    private final ScrollView scrollView;
    private final LinearLayout listContainer;
    private int filterDays = 0;

    public DrivingHistoryTabBuilder(Context context) {
        this.context = context;
        scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        content.setPadding(pad, dp(10), pad, dp(24));
        scrollView.addView(content, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        listContainer = new LinearLayout(context);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(listContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        // İlk açılışta tab henüz attach edilmemiş olabilir; görünür olduğunda doldur.
        scrollView.post(this::refresh);
    }

    public ScrollView getScrollView() {
        return scrollView;
    }

    public void onTabVisible() {
        refresh();
    }

    public void onTabHidden() {
    }

    public void refresh() {
        Log.i(TAG, "refresh start");
        listContainer.removeAllViews();
        List<DrivingTrip> allHistory = DrivingTripStore.loadHistory(context);
        List<DrivingTrip> history = filterHistory(allHistory);
        Log.i(TAG, "refresh all=" + allHistory.size() + " filtered=" + history.size()
                + " days=" + filterDays);
        if (!history.isEmpty()) {
            listContainer.addView(buildTrendCard(history));
            Log.i(TAG, "trend added");
        } else {
            // Trend kartı yoksa filtre/yenile kontrolleri yine erişilebilir kalsın.
            listContainer.addView(buildControlsCard());
        }
        DrivingSessionRecorder recorder = DrivingSessionRecorder.get(context);
        DrivingTrip current = recorder.isActive() ? recorder.getDisplayTrip() : recorder.getLastTrip();
        if (current != null) {
            listContainer.addView(sectionLabel(R.string.driving_current_section));
            listContainer.addView(buildCard(current, recorder.isActive()));
        }

        boolean hasHistory = false;
        for (DrivingTrip trip : history) {
            if (current != null && trip.id == current.id) {
                continue;
            }
            if (!hasHistory) {
                listContainer.addView(sectionLabel(R.string.driving_history_section));
                hasHistory = true;
            }
            listContainer.addView(buildCard(trip, false));
        }
        Log.i(TAG, "refresh done children=" + listContainer.getChildCount()
                + " parent=" + (listContainer.getParent() != null)
                + " scroll=" + System.identityHashCode(scrollView)
                + " list=" + System.identityHashCode(listContainer));
        if (current == null && !hasHistory) {
            String message = filterDays > 0
                    ? "Seçilen dönemde kayıtlı sürüş yok."
                    : context.getString(R.string.driving_history_empty);
            CockpitCard emptyCard = new CockpitCard(context);
            emptyCard.addContent(new CockpitEmptyState(context,
                    R.drawable.ic_mdi_road_variant, message));
            LinearLayout.LayoutParams emptyLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            emptyLp.bottomMargin = dp(12);
            listContainer.addView(emptyCard, emptyLp);
        }
        // Yenile/filtre sonrası oluşan yeni TextView'lar da ilk açılıştaki
        // Cockpit tipografisini alsın.
        CockpitLegacyStyler.apply(listContainer);
    }

    private LinearLayout buildControlsRow() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        String[] labels = new String[]{"Tümü", "7 gün", "30 gün"};
        int[] days = new int[]{0, 7, 30};
        for (int i = 0; i < labels.length; i++) {
            final int day = days[i];
            CockpitChip chip = new CockpitChip(context, labels[i]);
            chip.setSelectedState(day == filterDays);
            chip.setOnClickListener(v -> {
                filterDays = day;
                refresh();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    CockpitTokens.dim(context, R.dimen.ck_chip_h));
            lp.setMarginEnd(dp(8));
            row.addView(chip, lp);
        }

        View spacer = new View(context);
        row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        LinearLayout coachGroup = new LinearLayout(context);
        coachGroup.setOrientation(LinearLayout.HORIZONTAL);
        coachGroup.setGravity(Gravity.CENTER_VERTICAL);
        TextView coachLabel = new TextView(context);
        coachLabel.setText(R.string.settings_driving_coach_title);
        CockpitTokens.bodySm(coachLabel);
        coachLabel.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        coachLabel.setPadding(0, 0, dp(8), 0);
        coachGroup.addView(coachLabel);
        CockpitToggle coachToggle = new CockpitToggle(context);
        coachToggle.setChecked(DrivingCoach.isEnabled(context), false);
        coachToggle.setOnCheckedChangeListener((button, checked) ->
                DrivingCoach.setEnabled(context, checked));
        coachGroup.addView(coachToggle);
        LinearLayout.LayoutParams coachLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        coachLp.setMarginEnd(dp(16));
        row.addView(coachGroup, coachLp);

        CockpitButton refresh = CockpitButton.secondary(context,
                context.getString(R.string.driving_history_refresh));
        refresh.setOnClickListener(v -> refresh());
        row.addView(refresh, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private CockpitCard buildControlsCard() {
        CockpitCard card = new CockpitCard(context);
        card.addContent(buildControlsRow());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);
        return card;
    }

    private List<DrivingTrip> filterHistory(List<DrivingTrip> all) {
        if (filterDays <= 0) {
            return all;
        }
        long cutoff = System.currentTimeMillis() - filterDays * 24L * 60L * 60L * 1000L;
        List<DrivingTrip> filtered = new java.util.ArrayList<>();
        for (DrivingTrip trip : all) {
            if (trip.startTimeMs >= cutoff) {
                filtered.add(trip);
            }
        }
        return filtered;
    }

    private LinearLayout buildTrendCard(List<DrivingTrip> history) {
        DrivingTrends.Snapshot trends = DrivingTrends.calculate(history);
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        CockpitSurfaces.level1(card);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(12);
        card.setLayoutParams(cardLp);

        card.addView(buildControlsRow(), controlsLp());

        TextView title = new TextView(context);
        title.setText("EKO TREND");
        CockpitTokens.labelCaps(title);
        title.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        card.addView(title);
        TextView subtitle = new TextView(context);
        subtitle.setText("Bu hafta ve bu ay, önceki dönemle karşılaştırma");
        CockpitTokens.bodySm(subtitle);
        subtitle.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleLp.topMargin = dp(3);
        subtitleLp.bottomMargin = dp(10);
        card.addView(subtitle, subtitleLp);

        LinearLayout periods = new LinearLayout(context);
        periods.setOrientation(isWide() ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        periods.setBaselineAligned(false);
        LinearLayout.LayoutParams weekLp = isWide()
                ? weighted()
                : new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        periods.addView(buildTrendPeriod("BU HAFTA", trends.thisWeek, trends.lastWeek,
                history, true), weekLp);
        LinearLayout.LayoutParams monthLp = isWide()
                ? weighted()
                : new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        monthLp.setMarginStart(isWide() ? dp(10) : 0);
        monthLp.topMargin = isWide() ? 0 : dp(10);
        periods.addView(buildTrendPeriod("BU AY", trends.thisMonth, trends.lastMonth,
                history, false), monthLp);
        card.addView(periods);

        LinearLayout extremes = new LinearLayout(context);
        extremes.setOrientation(isWide() ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        extremes.setBaselineAligned(false);
        LinearLayout.LayoutParams extremesLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        extremesLp.topMargin = dp(10);
        if (isWide()) {
            extremes.addView(buildExtremeCard("EN İYİ SÜRÜŞ", trends.bestTrip), weighted());
            LinearLayout.LayoutParams worstLp = weighted();
            worstLp.setMarginStart(dp(10));
            extremes.addView(buildExtremeCard("EN KÖTÜ SÜRÜŞ", trends.worstTrip), worstLp);
        } else {
            extremes.addView(buildExtremeCard("EN İYİ SÜRÜŞ", trends.bestTrip),
                    new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams worstLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            worstLp.topMargin = dp(10);
            extremes.addView(buildExtremeCard("EN KÖTÜ SÜRÜŞ", trends.worstTrip), worstLp);
        }
        card.addView(extremes, extremesLp);
        return card;
    }

    private LinearLayout buildExtremeCard(String label, DrivingTrip trip) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiStyles.color(context, R.color.surfaceCardInner));
        background.setCornerRadius(dp(10));
        card.setBackground(background);

        TextView eyebrow = new TextView(context);
        eyebrow.setText(label);
        CockpitTokens.labelCaps(eyebrow);
        eyebrow.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        card.addView(eyebrow);

        if (trip != null) {
            TextView value = text(trip.title + "  ·  " + trip.score + " puan",
                    15, gradeColor(trip.grade), true);
            LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            valueLp.topMargin = dp(4);
            card.addView(value, valueLp);

            TextView meta = text(bestMeta(trip), 15,
                    CockpitTokens.color(context, R.color.ck_text_medium), false);
            LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            metaLp.topMargin = dp(2);
            card.addView(meta, metaLp);
        } else {
            card.addView(text("—", 13,
                    UiStyles.color(context, R.color.textHint), false));
        }
        return card;
    }

    private LinearLayout buildTrendPeriod(String label, DrivingTrends.Period current,
            DrivingTrends.Period previous, List<DrivingTrip> history, boolean week) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(UiStyles.color(context, R.color.surfaceCardInner));
        bg.setCornerRadius(dp(10));
        column.setBackground(bg);

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout textColumn = new LinearLayout(context);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        TextView periodLabel = new TextView(context);
        periodLabel.setText(label);
        CockpitTokens.labelCaps(periodLabel);
        periodLabel.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        textColumn.addView(periodLabel);
        TextView score = text(current.hasData() ? current.avgScore + " puan" : "—",
                23, UiStyles.color(context, R.color.textPrimary), true);
        LinearLayout.LayoutParams scoreLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        scoreLp.topMargin = dp(2);
        textColumn.addView(score, scoreLp);
        textColumn.addView(deltaView(current.avgScore, previous.avgScore,
                current.hasData() && previous.hasData(), false, "puan"));

        String consumptionText = current.hasConsumption
                ? String.format(appLocale(), "%.1f L/100 km", current.avgConsumptionL100) : "—";
        TextView consumption = text("Tüketim: " + consumptionText, 15,
                CockpitTokens.color(context, R.color.ck_text_medium), false);
        LinearLayout.LayoutParams consumptionLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        consumptionLp.topMargin = dp(4);
        textColumn.addView(consumption, consumptionLp);
        if (current.hasConsumption && previous.hasConsumption) {
            textColumn.addView(deltaView(current.avgConsumptionL100,
                    previous.avgConsumptionL100, true, true, "L/100 km"));
        }

        TextView meta = text(current.tripCount + " sürüş · "
                        + String.format(appLocale(), "%.1f km", current.distanceKm),
                15, CockpitTokens.color(context, R.color.ck_text_medium), false);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        metaLp.topMargin = dp(4);
        textColumn.addView(meta, metaLp);
        body.addView(textColumn, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout chartColumn = new LinearLayout(context);
        chartColumn.setOrientation(LinearLayout.VERTICAL);
        chartColumn.setGravity(Gravity.END);
        TextView chartLabel = new TextView(context);
        chartLabel.setText(week ? "7 GÜN" : "5 HAFTA");
        CockpitTokens.labelCaps(chartLabel);
        chartLabel.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        chartLabel.setGravity(Gravity.END);
        chartColumn.addView(chartLabel);
        DrivingTrendBarsView bars = new DrivingTrendBarsView(context);
        int[] scores = week
                ? DrivingTrends.weeklyDailyScores(history, System.currentTimeMillis())
                : DrivingTrends.monthlyWeeklyScores(history, System.currentTimeMillis());
        bars.configure(UiStyles.color(context, R.color.oemAccent),
                UiStyles.color(context, R.color.surfaceCardElevated),
                UiStyles.color(context, R.color.textHint), week,
                Typeface.create("sans-serif", Typeface.NORMAL));
        bars.setData(scores);
        StringBuilder description = new StringBuilder(week ? "Son 7 gün puan seyri: " : "Son 5 hafta puan seyri: ");
        for (int i = 0; i < scores.length; i++) {
            if (i > 0) description.append(", ");
            description.append(scores[i] > 0 ? scores[i] + " puan" : "veri yok");
        }
        bars.setContentDescription(description.toString());
        LinearLayout.LayoutParams barsLp = new LinearLayout.LayoutParams(
                isWide() ? dp(176) : dp(112), dp(78));
        barsLp.topMargin = dp(2);
        chartColumn.addView(bars, barsLp);
        LinearLayout.LayoutParams chartLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        chartLp.setMarginStart(dp(10));
        body.addView(chartColumn, chartLp);
        column.addView(body);
        return column;
    }

    private TextView deltaView(double current, double previous, boolean hasComparison,
            boolean lowerIsBetter, String unit) {
        if (!hasComparison) {
            TextView neutral = text("Önceki dönem verisi yok", 15,
                    CockpitTokens.color(context, R.color.ck_text_medium), false);
            neutral.setPadding(0, dp(2), 0, 0);
            return neutral;
        }
        double delta = current - previous;
        boolean better = lowerIsBetter ? delta < 0 : delta > 0;
        int color = Math.abs(delta) < 0.05
                ? UiStyles.color(context, R.color.textHint)
                : (better ? 0xFF2E7D32 : 0xFFC62828);
        String arrow = Math.abs(delta) < 0.05 ? "±" : (delta > 0 ? "▲" : "▼");
        TextView view = text(String.format(appLocale(), "%s %.1f %s", arrow,
                Math.abs(delta), unit), 15, color, true);
        view.setPadding(0, dp(2), 0, 0);
        return view;
    }

    private String bestMeta(DrivingTrip trip) {
        SimpleDateFormat format = new SimpleDateFormat("d MMM", appLocale());
        return format.format(new Date(trip.startTimeMs)) + "  ·  "
                + String.format(appLocale(), "%.1f km", trip.distanceKm);
    }

    private LinearLayout.LayoutParams controlsLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        return lp;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private TextView sectionLabel(int labelResId) {
        TextView label = text(labelResId, 13,
                UiStyles.color(context, R.color.textSecondary), true);
        CockpitTokens.labelCaps(label);
        label.setPadding(0, dp(14), 0, dp(6));
        return label;
    }

    private LinearLayout buildCard(DrivingTrip trip, boolean live) {
        boolean wide = isWide();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        card.setGravity(wide ? Gravity.CENTER_VERTICAL : Gravity.NO_GRAVITY);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setClickable(true);
        card.setFocusable(true);
        CockpitSurfaces.level1(card);

        if (wide) {
            LinearLayout.LayoutParams scoreLp = new LinearLayout.LayoutParams(
                    dp(172), LinearLayout.LayoutParams.WRAP_CONTENT);
            scoreLp.setMarginEnd(dp(16));
            card.addView(buildScorePanel(trip, live, true), scoreLp);

            LinearLayout details = new LinearLayout(context);
            details.setOrientation(LinearLayout.VERTICAL);
            details.addView(dateLine(trip));
            details.addView(titleLine(trip, false));
            details.addView(buildMetrics(trip, true));
            card.addView(details, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView arrow = text("›", 28,
                    UiStyles.color(context, R.color.textHint), false);
            arrow.setGravity(Gravity.CENTER);
            card.addView(arrow, new LinearLayout.LayoutParams(dp(28),
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        } else {
            LinearLayout top = new LinearLayout(context);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(dateLine(trip), new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            top.addView(buildScorePanel(trip, live, false), new LinearLayout.LayoutParams(
                    dp(150), LinearLayout.LayoutParams.WRAP_CONTENT));
            card.addView(top);

            card.addView(titleLine(trip, true));
            card.addView(buildMetrics(trip, false));
        }

        card.setOnClickListener(v -> openReport(live ? 0L : trip.id));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);
        return card;
    }

    private TextView dateLine(DrivingTrip trip) {
        String date = formatDate(trip.startTimeMs);
        if (trip.testData) {
            date = "TEST · " + date;
        }
        return text(date, 15, CockpitTokens.color(context, R.color.ck_text_medium), false);
    }

    private LinearLayout titleLine(DrivingTrip trip, boolean withArrow) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(trip.title != null && !trip.title.isEmpty()
                ? trip.title : context.getString(R.string.side_rail_driving_report),
                22, CockpitTokens.color(context, R.color.ck_text_high), true);
        title.setPadding(0, dp(3), 0, 0);
        row.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (withArrow) {
            TextView arrow = text("›", 28,
                    UiStyles.color(context, R.color.textHint), false);
            row.addView(arrow);
        }
        return row;
    }

    private LinearLayout buildMetrics(DrivingTrip trip, boolean wide) {
        LinearLayout metrics = new LinearLayout(context);
        metrics.setOrientation(LinearLayout.VERTICAL);
        String[] labels = new String[]{
                context.getString(R.string.driving_history_metric_distance),
                context.getString(R.string.driving_history_metric_duration),
                context.getString(R.string.driving_history_metric_avg),
                context.getString(R.string.driving_history_metric_consumption)
        };
        String[] values = new String[]{
                String.format(appLocale(), "%.1f km", trip.distanceKm),
                formatDuration(trip.durationSec),
                String.format(appLocale(), "%d km/h", trip.avgSpeedKmh),
                trip.consumptionL100 > 0
                        ? String.format(appLocale(), "%.1f L/100 km", trip.consumptionL100)
                        : "—"
        };
        int columns = wide ? 4 : 2;
        for (int start = 0; start < labels.length; start += columns) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < columns; i++) {
                int index = start + i;
                LinearLayout cell = new LinearLayout(context);
                cell.setOrientation(LinearLayout.VERTICAL);
                TextView label = new TextView(context);
                label.setText(labels[index]);
                CockpitTokens.labelCaps(label);
                label.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
                cell.addView(label);
                TextView value = text(values[index], 16,
                        CockpitTokens.color(context, R.color.ck_text_high), true);
                value.setMaxLines(1);
                cell.addView(value);
                LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (i > 0) {
                    cellLp.setMarginStart(dp(12));
                }
                row.addView(cell, cellLp);
            }
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.topMargin = dp(5);
            metrics.addView(row, rowLp);
        }
        return metrics;
    }

    private LinearLayout buildScorePanel(DrivingTrip trip, boolean live, boolean wide) {
        int color = live ? gradeColor("A") : gradeColor(trip.grade);
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable background = new GradientDrawable();
        background.setColor((color & 0x00FFFFFF) | 0x16000000);
        background.setCornerRadius(dp(12));
        background.setStroke(Math.max(1, Math.round(dp(1))),
                (color & 0x00FFFFFF) | 0x66000000);
        panel.setBackground(background);

        if (live) {
            LinearLayout group = new LinearLayout(context);
            group.setOrientation(LinearLayout.VERTICAL);
            group.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView liveValue = text(R.string.driving_history_live,
                    wide ? 18 : 16, color, true);
            liveValue.setLetterSpacing(0.08f);
            group.addView(liveValue);
            TextView liveLabel = new TextView(context);
            liveLabel.setText(R.string.driving_history_live_now);
            CockpitTokens.labelCaps(liveLabel);
            liveLabel.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
            group.addView(liveLabel);
            panel.addView(group);
            return panel;
        }

        LinearLayout points = new LinearLayout(context);
        points.setOrientation(LinearLayout.VERTICAL);
        points.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView pointsValue = text(String.valueOf(trip.score), wide ? 42 : 34,
                UiStyles.color(context, R.color.textPrimary), true);
        pointsValue.setIncludeFontPadding(false);
        points.addView(pointsValue);
        TextView pointsLabel = new TextView(context);
        pointsLabel.setText(R.string.driving_history_points_label);
        CockpitTokens.labelCaps(pointsLabel);
        pointsLabel.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        points.addView(pointsLabel);
        panel.addView(points);

        LinearLayout grade = new LinearLayout(context);
        grade.setOrientation(LinearLayout.VERTICAL);
        grade.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams gradeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        gradeLp.setMarginStart(dp(12));
        panel.addView(grade, gradeLp);

        TextView gradeValue = text(trip.grade != null ? trip.grade : "—",
                wide ? 44 : 36, color, true);
        gradeValue.setGravity(Gravity.CENTER);
        gradeValue.setIncludeFontPadding(false);
        gradeValue.setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor((color & 0x00FFFFFF) | 0x22000000);
        pill.setCornerRadius(dp(8));
        pill.setStroke(Math.max(1, Math.round(dp(1))), color);
        gradeValue.setBackground(pill);
        grade.addView(gradeValue);

        TextView gradeLabel = new TextView(context);
        gradeLabel.setText(R.string.driving_history_grade_label);
        CockpitTokens.labelCaps(gradeLabel);
        gradeLabel.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        grade.addView(gradeLabel);
        return panel;
    }

    private boolean isWide() {
        return context.getResources().getConfiguration().screenWidthDp >= 700;
    }

    private int gradeColor(String grade) {
        if (grade == null) {
            return UiStyles.color(context, R.color.textHint);
        }
        switch (grade) {
            case "A":
                return 0xFF2E7D32;
            case "B":
                return 0xFF558B2F;
            case "C":
                return 0xFFF9A825;
            case "D":
                return 0xFFEF6C00;
            case "E":
                return 0xFFD84315;
            case "F":
                return 0xFFC62828;
            default:
                return UiStyles.color(context, R.color.textHint);
        }
    }

    private void openReport(long tripId) {
        Intent intent = new Intent(context, DrivingAnalysisActivity.class);
        if (tripId > 0L) {
            intent.putExtra(DrivingAnalysisActivity.EXTRA_TRIP_ID, tripId);
        }
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    private String formatDate(long wallMs) {
        if (wallMs <= 0L) {
            return context.getString(R.string.driving_history_live);
        }
        SimpleDateFormat format = new SimpleDateFormat("d MMM yyyy · HH:mm",
                appLocale());
        return format.format(new Date(wallMs));
    }

    private String formatDuration(int seconds) {
        int safe = Math.max(0, seconds);
        return context.getString(R.string.driving_history_duration,
                safe / 60, safe % 60);
    }

    private Locale appLocale() {
        return AppLocaleManager.locale(context);
    }

    private TextView text(int resId, int sizeSp, int color, boolean bold) {
        return text(context.getString(resId), sizeSp, color, bold);
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(null, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
