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
import com.mapcontrol.driving.DrivingSessionRecorder;
import com.mapcontrol.driving.DrivingTrip;
import com.mapcontrol.driving.DrivingTripStore;
import com.mapcontrol.driving.DrivingTrendBarsView;
import com.mapcontrol.driving.DrivingTrends;
import com.mapcontrol.ui.activity.DrivingAnalysisActivity;
import com.mapcontrol.ui.cockpit.CockpitButton;
import com.mapcontrol.ui.cockpit.CockpitChip;
import com.mapcontrol.ui.cockpit.CockpitEmptyState;
import com.mapcontrol.ui.cockpit.CockpitSurfaces;
import com.mapcontrol.ui.cockpit.CockpitTokens;
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
    private final LinearLayout filterBar;
    private final java.util.List<TextView> filterChips = new java.util.ArrayList<>();
    private final java.util.List<Integer> filterDaysValues = new java.util.ArrayList<>();
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

        content.addView(buildHeader());
        filterBar = new LinearLayout(context);
        filterBar.setOrientation(LinearLayout.HORIZONTAL);
        filterBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams filterLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        filterLp.bottomMargin = dp(10);
        content.addView(filterBar, filterLp);
        addFilterChip("Tümü", 0);
        addFilterChip("7 gün", 7);
        addFilterChip("30 gün", 30);
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
            CockpitEmptyState empty = new CockpitEmptyState(context,
                    R.drawable.ic_mdi_road_variant, message);
            listContainer.addView(empty, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    private void addFilterChip(String label, int days) {
        CockpitChip chip = new CockpitChip(context, label);
        chip.setOnClickListener(v -> {
            filterDays = days;
            updateFilterChips();
            refresh();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                CockpitTokens.dim(context, R.dimen.ck_chip_h));
        lp.setMarginEnd(dp(8));
        filterBar.addView(chip, lp);
        filterChips.add(chip);
        filterDaysValues.add(days);
        updateFilterChips();
    }

    private void updateFilterChips() {
        for (int i = 0; i < filterChips.size(); i++) {
            TextView chip = filterChips.get(i);
            boolean selected = filterDaysValues.get(i) == filterDays;
            if (chip instanceof CockpitChip) {
                ((CockpitChip) chip).setSelectedState(selected);
            }
        }
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

        TextView title = text("EKO TREND", 13,
                UiStyles.color(context, R.color.textPrimary), true);
        title.setLetterSpacing(0.12f);
        card.addView(title);
        TextView subtitle = text("Bu hafta ve bu ay, önceki dönemle karşılaştırma", 12,
                UiStyles.color(context, R.color.textHint), false);
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

        TextView eyebrow = text(label, 10,
                UiStyles.color(context, R.color.textHint), true);
        eyebrow.setLetterSpacing(0.12f);
        card.addView(eyebrow);

        if (trip != null) {
            TextView value = text(trip.title + "  ·  " + trip.score + " puan",
                    13, gradeColor(trip.grade), true);
            LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            valueLp.topMargin = dp(4);
            card.addView(value, valueLp);

            TextView meta = text(bestMeta(trip), 11,
                    UiStyles.color(context, R.color.textHint), false);
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

        TextView periodLabel = text(label, 10,
                UiStyles.color(context, R.color.textHint), true);
        periodLabel.setLetterSpacing(0.12f);
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
        TextView consumption = text("Tüketim: " + consumptionText, 12,
                UiStyles.color(context, R.color.textSecondary), false);
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
                11, UiStyles.color(context, R.color.textHint), false);
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
        TextView chartLabel = text(week ? "7 GÜN" : "5 HAFTA", 10,
                UiStyles.color(context, R.color.textHint), true);
        chartLabel.setLetterSpacing(0.12f);
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
            TextView neutral = text("Önceki dönem verisi yok", 11,
                    UiStyles.color(context, R.color.textHint), false);
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
                Math.abs(delta), unit), 11, color, true);
        view.setPadding(0, dp(2), 0, 0);
        return view;
    }

    private String bestMeta(DrivingTrip trip) {
        SimpleDateFormat format = new SimpleDateFormat("d MMM", appLocale());
        return format.format(new Date(trip.startTimeMs)) + "  ·  "
                + String.format(appLocale(), "%.1f km", trip.distanceKm);
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

    private LinearLayout buildHeader() {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView subtitle = text(R.string.driving_history_subtitle, 14,
                UiStyles.color(context, R.color.textSecondary), false);
        subtitle.setPadding(0, 0, dp(12), 0);
        header.addView(subtitle, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        CockpitButton refresh = CockpitButton.secondary(context,
                context.getString(R.string.driving_history_refresh));
        refresh.setOnClickListener(v -> refresh());
        header.addView(refresh, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        headerLp.bottomMargin = dp(12);
        header.setLayoutParams(headerLp);
        return header;
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
        return text(date, 13, UiStyles.color(context, R.color.textHint), false);
    }

    private LinearLayout titleLine(DrivingTrip trip, boolean withArrow) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(trip.title != null && !trip.title.isEmpty()
                ? trip.title : context.getString(R.string.side_rail_driving_report),
                22, UiStyles.color(context, R.color.textPrimary), true);
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
                TextView label = text(labels[index], 9,
                        UiStyles.color(context, R.color.textHint), true);
                label.setLetterSpacing(0.1f);
                cell.addView(label);
                TextView value = text(values[index], 16,
                        UiStyles.color(context, R.color.textPrimary), true);
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
            TextView liveLabel = text(R.string.driving_history_live_now,
                    9, UiStyles.color(context, R.color.textHint), true);
            liveLabel.setLetterSpacing(0.12f);
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
        TextView pointsLabel = text(R.string.driving_history_points_label, 9,
                UiStyles.color(context, R.color.textHint), true);
        pointsLabel.setLetterSpacing(0.12f);
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

        TextView gradeLabel = text(R.string.driving_history_grade_label, 9,
                UiStyles.color(context, R.color.textHint), true);
        gradeLabel.setLetterSpacing(0.12f);
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
