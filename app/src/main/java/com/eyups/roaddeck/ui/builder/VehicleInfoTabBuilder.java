package com.eyups.roaddeck.ui.builder;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import com.eyups.roaddeck.R;
import com.eyups.roaddeck.ui.cockpit.CockpitButton;
import com.eyups.roaddeck.ui.cockpit.CockpitCard;
import com.eyups.roaddeck.ui.cockpit.CockpitGrid;
import com.eyups.roaddeck.ui.cockpit.CockpitSectionHeader;
import com.eyups.roaddeck.ui.cockpit.CockpitTokens;
import com.eyups.roaddeck.ui.cockpit.MetricView;
import com.eyups.roaddeck.ui.cockpit.StatusBadge;
import com.eyups.roaddeck.ui.widget.VehicleBodyStateView;
import com.eyups.roaddeck.vehicle.VehicleMetricsFormatter;
import com.eyups.roaddeck.vehicle.VehicleMetricsRepository;
import com.eyups.roaddeck.vehicle.VehicleMetricsSnapshot;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Araç bilgisi — canlı VDBus verisi. Glanceable metrik kartları + uiContext.getString(R.string.ck_vi_advanced) ham veri kartı.
 */
public class VehicleInfoTabBuilder implements VehicleMetricsRepository.Listener {

    public interface VehicleInfoCallback {
        void log(String message);
    }

    private static final String ENGMODE_PACKAGE = "com.desaysv.engmode";
    private static final String ENGMODE_MAIN_ACTIVITY = "com.desaysv.engmode.MainActivity";

    private final Context uiContext;
    private final VehicleMetricsRepository repository;
    private final VehicleMetricsFormatter formatter;
    private final VehicleInfoCallback callback;

    private ScrollView scrollView;
    private LinearLayout advancedRows;
    private CockpitCard advancedCard;
    private boolean advancedShown;
    private StatusBadge statusBadge;
    private MetricView speedMetric;
    private MetricView gearMetric;
    private MetricView rpmMetric;
    private MetricView fuelMetric;
    private MetricView rangeMetric;
    private MetricView consMetric;
    private MetricView coolantMetric;
    private MetricView batteryMetric;
    private MetricView avgSpeedMetric;
    private MetricView odoMetric;
    private MetricView tripMetric;
    private MetricView sinceStartMetric;
    private MetricView leftSignalMetric;
    private MetricView rightSignalMetric;
    private MetricView hazardMetric;
    private static final Pattern NUMERIC_VALUE = Pattern.compile("^([-+]?\\d[\\d.,]*)\\s*(.*)$");
    private VehicleBodyStateView bodyStateView;
    private TextView bodyStateSummary;
    private volatile boolean tabVisible;

    public VehicleInfoTabBuilder(
            Context context,
            VehicleMetricsRepository repository,
            VehicleInfoCallback callback) {
        this.uiContext = context;
        this.repository = repository;
        this.formatter = new VehicleMetricsFormatter(context);
        this.callback = callback;
        build();
    }

    public ScrollView getScrollView() {
        return scrollView;
    }

    public void rebuild() {
        build();
    }

    public void startListening() {
        tabVisible = true;
        if (statusBadge != null) {
            statusBadge.setLevel(StatusBadge.NEUTRAL);
            statusBadge.setText(R.string.vehicle_info_loading);
        }
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

    public void refreshSnapshot() {
        if (statusBadge != null) {
            statusBadge.setLevel(StatusBadge.NEUTRAL);
            statusBadge.setText(R.string.vehicle_info_loading);
        }
        repository.refresh();
    }

    @Override
    public void onMetricsUpdated(VehicleMetricsSnapshot snapshot) {
        if (!tabVisible) {
            return;
        }
        if (statusBadge != null) {
            statusBadge.setLevel(snapshot.connected ? StatusBadge.NOMINAL : StatusBadge.ADVISORY);
            statusBadge.setText(snapshot.connected
                    ? R.string.vehicle_info_listening
                    : R.string.vehicle_info_unavailable);
        }
        renderBodyState(snapshot);
        renderMetrics(snapshot);
        if (advancedShown) {
            repaintRows(snapshot);
        }
    }

    private void build() {
        advancedShown = false;
        scrollView = new ScrollView(uiContext);
        scrollView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        scrollView.setFillViewport(true);

        LinearLayout outer = new LinearLayout(uiContext);
        outer.setOrientation(LinearLayout.VERTICAL);
        int m = CockpitTokens.dim(uiContext, R.dimen.ck_margin);
        outer.setPadding(m, CockpitTokens.dim(uiContext, R.dimen.ck_space_md), m, m);

        LinearLayout header = new LinearLayout(uiContext);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(uiContext);
        CockpitTokens.headlineMd(title);
        title.setText(R.string.vehicle_info_title);
        header.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        statusBadge = StatusBadge.neutral(uiContext, uiContext.getString(R.string.vehicle_info_tab_idle));
        header.addView(statusBadge);
        outer.addView(header);

        TextView intro = new TextView(uiContext);
        CockpitTokens.bodySm(intro);
        intro.setText(R.string.vehicle_info_intro);
        LinearLayout.LayoutParams introLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        introLp.bottomMargin = CockpitTokens.dim(uiContext, R.dimen.ck_space_md);
        outer.addView(intro, introLp);

        LinearLayout actions = new LinearLayout(uiContext);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        CockpitButton refresh = CockpitButton.secondary(uiContext,
                uiContext.getString(R.string.vehicle_info_refresh));
        refresh.setOnClickListener(v -> refreshSnapshot());
        refresh.setMinWidth(CockpitTokens.dim(uiContext, R.dimen.ck_action_min_w));
        refresh.setMaxWidth(CockpitTokens.dim(uiContext, R.dimen.ck_action_max_w));
        actions.addView(refresh, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        CockpitButton eng = CockpitButton.secondary(uiContext,
                uiContext.getString(R.string.vehicle_info_open_engmode));
        eng.setOnClickListener(v -> launchEngModeMenu());
        eng.setMinWidth(CockpitTokens.dim(uiContext, R.dimen.ck_action_min_w));
        eng.setMaxWidth(CockpitTokens.dim(uiContext, R.dimen.ck_action_max_w));
        LinearLayout.LayoutParams engLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        engLp.leftMargin = CockpitTokens.dim(uiContext, R.dimen.ck_space_sm);
        actions.addView(eng, engLp);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionsLp.bottomMargin = CockpitTokens.dim(uiContext, R.dimen.ck_space_lg);
        outer.addView(actions, actionsLp);

        CockpitGrid grid = new CockpitGrid(uiContext);
        outer.addView(grid, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        CockpitCard body = new CockpitCard(uiContext,
                uiContext.getString(R.string.ck_body_title)).span(8);
        bodyStateView = new VehicleBodyStateView(uiContext);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, CockpitTokens.dp(uiContext, 235));
        body.addContent(bodyStateView, bodyLp);
        bodyStateSummary = new TextView(uiContext);
        CockpitTokens.bodySm(bodyStateSummary);
        bodyStateSummary.setTextColor(CockpitTokens.color(uiContext, R.color.ck_text_medium));
        LinearLayout.LayoutParams summaryLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        summaryLp.topMargin = CockpitTokens.dim(uiContext, R.dimen.ck_space_sm);
        body.addContent(bodyStateSummary, summaryLp);
        LinearLayout.LayoutParams bodyCardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bodyCardLp.bottomMargin = CockpitTokens.dim(uiContext, R.dimen.ck_space_md);
        outer.addView(body, outer.indexOfChild(grid), bodyCardLp);

        CockpitCard drive = new CockpitCard(uiContext, uiContext.getString(R.string.ck_vi_drive)).span(4);
        LinearLayout driveRow = new LinearLayout(uiContext);
        driveRow.setOrientation(LinearLayout.HORIZONTAL);
        speedMetric = metric(uiContext.getString(R.string.ck_vi_speed));
        gearMetric = metric(uiContext.getString(R.string.ck_vi_gear));
        rpmMetric = metric(uiContext.getString(R.string.ck_vi_rpm));
        driveRow.addView(speedMetric, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        driveRow.addView(gearMetric, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        driveRow.addView(rpmMetric, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        drive.addContent(driveRow);
        grid.add(drive, 4);

        CockpitCard fuel = new CockpitCard(uiContext, uiContext.getString(R.string.ck_vi_fuel)).span(4);
        LinearLayout fuelRow = new LinearLayout(uiContext);
        fuelRow.setOrientation(LinearLayout.HORIZONTAL);
        fuelMetric = metric(uiContext.getString(R.string.ck_vi_level));
        rangeMetric = metric(uiContext.getString(R.string.ck_vi_range));
        consMetric = metric(uiContext.getString(R.string.ck_vi_avg_consumption));
        fuelRow.addView(fuelMetric, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        fuelRow.addView(rangeMetric, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        fuelRow.addView(consMetric, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        fuel.addContent(fuelRow);
        grid.add(fuel, 4);

        CockpitCard motor = new CockpitCard(uiContext, uiContext.getString(R.string.ck_vi_motor)).span(4);
        LinearLayout motorRow = new LinearLayout(uiContext);
        motorRow.setOrientation(LinearLayout.HORIZONTAL);
        coolantMetric = metric(uiContext.getString(R.string.ck_vi_coolant));
        batteryMetric = metric(uiContext.getString(R.string.ck_vi_battery));
        avgSpeedMetric = metric(uiContext.getString(R.string.ck_vi_avg_speed));
        motorRow.addView(coolantMetric, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        motorRow.addView(batteryMetric, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        motorRow.addView(avgSpeedMetric, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        motor.addContent(motorRow);
        grid.add(motor, 4);

        CockpitCard distance = new CockpitCard(uiContext, uiContext.getString(R.string.ck_vi_distance)).span(4);
        LinearLayout distRow = new LinearLayout(uiContext);
        distRow.setOrientation(LinearLayout.HORIZONTAL);
        odoMetric = metric(uiContext.getString(R.string.ck_vi_odo));
        tripMetric = metric(uiContext.getString(R.string.ck_vi_trip));
        sinceStartMetric = metric(uiContext.getString(R.string.ck_vi_since_start));
        distRow.addView(odoMetric, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        distRow.addView(tripMetric, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        distRow.addView(sinceStartMetric, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        distance.addContent(distRow);
        grid.add(distance, 4);

        CockpitCard signals = new CockpitCard(uiContext,
                uiContext.getString(R.string.ck_vi_signals)).span(4);
        LinearLayout signalRow = new LinearLayout(uiContext);
        signalRow.setOrientation(LinearLayout.HORIZONTAL);
        leftSignalMetric = metric(uiContext.getString(R.string.ck_vi_left_signal));
        rightSignalMetric = metric(uiContext.getString(R.string.ck_vi_right_signal));
        hazardMetric = metric(uiContext.getString(R.string.ck_vi_hazard));
        signalRow.addView(leftSignalMetric, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        signalRow.addView(rightSignalMetric, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        signalRow.addView(hazardMetric, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        signals.addContent(signalRow);
        grid.add(signals, 4);

        advancedCard = new CockpitCard(uiContext, uiContext.getString(R.string.ck_vi_advanced)).span(8);
        advancedCard.status(StatusBadge.neutral(uiContext, uiContext.getString(R.string.ck_vi_raw_vdbus)));
        final CockpitButton toggle = CockpitButton.secondary(uiContext, uiContext.getString(R.string.ck_vi_show));
        toggle.setOnClickListener(v -> {
            advancedShown = !advancedShown;
            advancedRows.setVisibility(advancedShown ? View.VISIBLE : View.GONE);
            toggle.setText(advancedShown ? uiContext.getString(R.string.ck_vi_hide) : uiContext.getString(R.string.ck_vi_show));
            if (advancedShown) {
                repository.refresh();
            }
        });
        LinearLayout toggleRow = new LinearLayout(uiContext);
        toggleRow.setGravity(Gravity.END);
        toggleRow.addView(toggle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        advancedCard.addContent(toggleRow);

        advancedRows = new LinearLayout(uiContext);
        advancedRows.setOrientation(LinearLayout.VERTICAL);
        advancedRows.setVisibility(View.GONE);
        advancedCard.addContent(advancedRows);
        grid.add(advancedCard, 8);

        scrollView.addView(outer, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private MetricView metric(String label) {
        MetricView v = new MetricView(uiContext, label);
        v.setValueTextSize(28f);
        return v;
    }

    private void renderMetrics(VehicleMetricsSnapshot snapshot) {
        if (!snapshot.connected) {
            for (MetricView v : new MetricView[]{speedMetric, gearMetric, rpmMetric, fuelMetric,
                    rangeMetric, consMetric, coolantMetric, batteryMetric, avgSpeedMetric,
                    odoMetric, tripMetric, sinceStartMetric, leftSignalMetric, rightSignalMetric,
                    hazardMetric}) {
                v.value("—").unit(null).caption(null);
            }
            return;
        }
        setMetric(speedMetric, formatter.formatSpeed(repository.getCombined(ReadOnlyID.ID_CAR_SPEED)));
        setMetric(gearMetric, formatter.formatGear(repository.getCombined(ReadOnlyID.ID_GEARBOX_STATE)));
        setMetric(rpmMetric, formatter.formatRpm(repository.getCombined(ReadOnlyID.ID_ENGINE_RPM)));
        setMetric(fuelMetric, formatter.formatFuelLevel(repository.getCombined(ReadOnlyID.ID_FUEL_PERCENT)));
        setMetric(rangeMetric, formatter.formatKm(repository.getCombined(ReadOnlyID.ID_ENDURANCE_KM)));
        setMetric(consMetric, formatter.formatConsumption(repository.getCombined(ReadOnlyID.ID_AVG_FUEL_CONS)));
        setMetric(coolantMetric, formatter.formatTemperature(repository.getCombined(ReadOnlyID.ID_WATER_TEMPERATURE)));
        setMetric(batteryMetric, formatter.formatVoltage(repository.getCombined(ReadOnlyID.ID_IBS_VOLTAGE)));
        setMetric(avgSpeedMetric, formatter.formatSpeed(repository.getCombined(ReadOnlyID.ID_AVG_SPEED)));
        setMetric(odoMetric, formatter.formatKm(repository.getCombined(ReadOnlyID.ID_GRAND_TOTAL_KM)));
        setMetric(tripMetric, formatter.formatDeciKm(repository.getCombined(ReadOnlyID.ID_TRIP)));
        setMetric(sinceStartMetric, formatter.formatKm(repository.getCombined(ReadOnlyID.ID_GRAND_TOTAL_KM_AFTER_RUNNING)));
        int leftSignal = repository.getCombined(ReadOnlyID.ID_LH_TURN_LIGHT_STS);
        int rightSignal = repository.getCombined(ReadOnlyID.ID_RH_TURN_LIGHT_STS);
        int hazard = repository.getCombined(ReadOnlyID.ID_HAZARD_LIGHT);
        leftSignalMetric.value(signalLabel(leftSignal)).caption("ID 33: " + formatter.formatRawSignal(leftSignal));
        rightSignalMetric.value(signalLabel(rightSignal)).caption("ID 32: " + formatter.formatRawSignal(rightSignal));
        hazardMetric.value(signalLabel(hazard)).caption("ID 79: " + formatter.formatRawSignal(hazard));
    }

    /**
     * 28 sp metrik alanina uzun cumle basma. Sayisal deger + kisa birim ayrilir;
     * uzun/veri yok metni 15 sp caption'a tasinir. Boylece kart yuksekligi sabit kalir.
     */
    private void setMetric(MetricView view, String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            view.value("—").unit(null).caption(null);
            return;
        }
        Matcher m = NUMERIC_VALUE.matcher(text);
        if (m.matches()) {
            String value = m.group(1);
            String unit = m.group(2) == null ? "" : m.group(2).trim();
            view.value(value);
            if (unit.length() <= 8) {
                view.unit(unit).caption(null);
            } else {
                view.unit(null).caption(unit);
            }
            return;
        }
        if (text.length() <= 12) {
            view.value(text).unit(null).caption(null);
        } else {
            view.value("—").unit(null).caption(text);
        }
    }

    private void renderBodyState(VehicleMetricsSnapshot snapshot) {
        if (bodyStateView == null || bodyStateSummary == null) {
            return;
        }
        bodyStateView.update(snapshot);
        String summary = buildBodySummary(snapshot);
        bodyStateSummary.setText(summary);
        bodyStateView.setContentDescription(summary);
        boolean warning = bodyStateView.hasOpenBody();
        bodyStateSummary.setTextColor(CockpitTokens.color(uiContext,
                warning ? R.color.ck_body_warning : R.color.ck_text_medium));
    }

    private String buildBodySummary(VehicleMetricsSnapshot snapshot) {
        if (!snapshot.connected) {
            return uiContext.getString(R.string.vehicle_info_unavailable);
        }
        boolean hasBodyData = false;
        StringBuilder open = new StringBuilder();
        appendBodyPart(open, snapshot.hasDoorLf(), snapshot.isDoorLfOpen(),
                R.string.ck_body_door_lf);
        appendBodyPart(open, snapshot.hasDoorRf(), snapshot.isDoorRfOpen(),
                R.string.ck_body_door_rf);
        appendBodyPart(open, snapshot.hasDoorLr(), snapshot.isDoorLrOpen(),
                R.string.ck_body_door_lr);
        appendBodyPart(open, snapshot.hasDoorRr(), snapshot.isDoorRrOpen(),
                R.string.ck_body_door_rr);
        appendBodyPart(open, snapshot.hasTrunk(), snapshot.isTrunkOpen(),
                R.string.ck_body_trunk);
        appendBodyPart(open, snapshot.hasHood(), snapshot.isHoodOpen(),
                R.string.ck_body_hood);
        appendBodyPart(open, snapshot.hasWindowFl(), snapshot.isWindowFlOpen(),
                R.string.ck_body_window_lf);
        appendBodyPart(open, snapshot.hasWindowFr(), snapshot.isWindowFrOpen(),
                R.string.ck_body_window_rf);
        appendBodyPart(open, snapshot.hasWindowRl(), snapshot.isWindowRlOpen(),
                R.string.ck_body_window_lr);
        appendBodyPart(open, snapshot.hasWindowRr(), snapshot.isWindowRrOpen(),
                R.string.ck_body_window_rr);
        hasBodyData = snapshot.hasDoorLf() || snapshot.hasDoorRf() || snapshot.hasDoorLr()
                || snapshot.hasDoorRr() || snapshot.hasTrunk() || snapshot.hasHood()
                || snapshot.hasWindowFl() || snapshot.hasWindowFr()
                || snapshot.hasWindowRl() || snapshot.hasWindowRr()
                || snapshot.hasSunroof() || VehicleMetricsSnapshot.isValid(snapshot.sunshade);
        if (snapshot.hasSunroof() && snapshot.isSunroofOpen()) {
            appendOpen(open, uiContext.getString(R.string.ck_body_sunroof));
            hasBodyData = true;
        }
        if (VehicleMetricsSnapshot.isValid(snapshot.sunshade) && snapshot.isSunshadeOpen()) {
            appendOpen(open, uiContext.getString(R.string.ck_body_sunshade));
            hasBodyData = true;
        }
        if (!hasBodyData) {
            return uiContext.getString(R.string.ck_body_waiting);
        }
        if (open.length() == 0) {
            return uiContext.getString(R.string.ck_body_all_closed);
        }
        String result = open.toString();
        if (bodyStateView != null && bodyStateView.isMovingWithOpenBody()) {
            result = uiContext.getString(R.string.ck_body_moving_warning) + " · " + result;
        }
        return result;
    }

    private void appendBodyPart(StringBuilder out, boolean available, boolean open, int nameRes) {
        if (!available || !open) {
            return;
        }
        appendOpen(out, uiContext.getString(nameRes));
    }

    private void appendOpen(StringBuilder out, String name) {
        if (out.length() > 0) {
            out.append(" · ");
        }
        out.append(name).append(' ').append(uiContext.getString(R.string.ck_body_open_suffix));
    }

    private String signalLabel(int raw) {
        if (!VehicleMetricsSnapshot.isValid(raw)) {
            return "—";
        }
        return uiContext.getString(raw == 0 ? R.string.common_off : R.string.common_on);
    }

    private void launchEngModeMenu() {
        try {
            PackageManager pm = uiContext.getPackageManager();
            pm.getPackageInfo(ENGMODE_PACKAGE, 0);
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(ENGMODE_PACKAGE, ENGMODE_MAIN_ACTIVITY));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            uiContext.startActivity(intent);
            log("EngMode menüsü açıldı");
        } catch (PackageManager.NameNotFoundException e) {
            log(uiContext.getString(R.string.vehicle_info_engmode_launch_fail));
        } catch (SecurityException e) {
            log(uiContext.getString(R.string.vehicle_info_engmode_launch_denied) + ": " + e.getMessage());
        } catch (Exception e) {
            log(uiContext.getString(R.string.vehicle_info_engmode_launch_fail) + ": " + e.getMessage());
        }
    }

    private void log(String message) {
        if (callback != null) {
            callback.log(message);
        }
    }

    private void repaintRows(VehicleMetricsSnapshot snapshot) {
        advancedRows.removeAllViews();
        if (!snapshot.connected) {
            addRow(uiContext.getString(R.string.vehicle_info_hint_title),
                    uiContext.getString(R.string.vehicle_info_hint_body));
            return;
        }

        addSection(uiContext.getString(R.string.vehicle_info_section_distance));
        addRow(uiContext.getString(R.string.vehicle_info_odo),
                formatter.formatKm(repository.getCombined(ReadOnlyID.ID_GRAND_TOTAL_KM)));
        addRow(uiContext.getString(R.string.vehicle_info_trip),
                formatter.formatDeciKm(repository.getCombined(ReadOnlyID.ID_TRIP)));
        addRow(uiContext.getString(R.string.vehicle_info_trip_after_reset),
                formatter.formatKm(repository.getCombined(ReadOnlyID.ID_GRAND_TOTAL_KM_AFTER_CLEAR)));
        addRow(uiContext.getString(R.string.vehicle_info_trip_since_start),
                formatter.formatKm(repository.getCombined(ReadOnlyID.ID_GRAND_TOTAL_KM_AFTER_RUNNING)));

        addSection(uiContext.getString(R.string.vehicle_info_section_fuel));
        addRow(uiContext.getString(R.string.vehicle_info_fuel_level),
                formatter.formatFuelLevel(repository.getCombined(ReadOnlyID.ID_FUEL_PERCENT)));
        addRow(uiContext.getString(R.string.vehicle_info_range_remaining),
                formatter.formatKm(repository.getCombined(ReadOnlyID.ID_ENDURANCE_KM)));
        addRow(uiContext.getString(R.string.vehicle_info_range_total),
                formatter.formatKm(repository.getCombined(ReadOnlyID.ID_TOTAL_RANGE)));
        addRow(uiContext.getString(R.string.vehicle_info_fuel_used),
                formatter.formatFuelUsedMl(repository.getCombined(ReadOnlyID.ID_SUM_FUEL)));
        addRow(uiContext.getString(R.string.vehicle_info_low_fuel),
                formatter.formatLowFuel(repository.getCombined(ReadOnlyID.ID_LOW_FUEL_WARNING)));
        addRow(uiContext.getString(R.string.vehicle_info_afc),
                formatter.formatConsumption(repository.getCombined(ReadOnlyID.ID_AVG_FUEL_CONS)));
        addRow(uiContext.getString(R.string.vehicle_info_afc_trip),
                formatter.formatConsumption(repository.getCombined(ReadOnlyID.ID_AVERAGE_FUEL_CONS_AFTER_RUNNING)));

        addSection(uiContext.getString(R.string.vehicle_info_section_motor));
        addRow(uiContext.getString(R.string.vehicle_info_eng_spd),
                formatter.formatRpm(repository.getCombined(ReadOnlyID.ID_ENGINE_RPM)));
        addRow(uiContext.getString(R.string.vehicle_info_col_tmp),
                formatter.formatTemperature(repository.getCombined(ReadOnlyID.ID_WATER_TEMPERATURE)));
        addRow(uiContext.getString(R.string.vehicle_info_battery),
                formatter.formatVoltage(repository.getCombined(ReadOnlyID.ID_IBS_VOLTAGE)));
        addRow(uiContext.getString(R.string.vehicle_info_act_speed),
                formatter.formatSpeed(repository.getCombined(ReadOnlyID.ID_CAR_SPEED)));
        addRow(uiContext.getString(R.string.vehicle_info_dis_speed),
                formatter.formatSpeed(repository.getCombined(ReadOnlyID.ID_SPEED_GAUGE_DISPLAY)));
        addRow(uiContext.getString(R.string.vehicle_info_gear),
                formatter.formatGear(repository.getCombined(ReadOnlyID.ID_GEARBOX_STATE)));
        addRow(uiContext.getString(R.string.vehicle_info_power_mode),
                formatter.formatPowerMode(repository.getCombined(ReadOnlyID.ID_SYSTEM_POWER_MODE)));
        addRow(uiContext.getString(R.string.vehicle_info_avg_speed),
                formatter.formatSpeed(repository.getCombined(ReadOnlyID.ID_AVG_SPEED)));

        addSection(uiContext.getString(R.string.vehicle_info_section_signals));
        addRawSignalRow(uiContext.getString(R.string.vehicle_info_signal_left_sts),
                ReadOnlyID.ID_LH_TURN_LIGHT_STS);
        addRawSignalRow(uiContext.getString(R.string.vehicle_info_signal_right_sts),
                ReadOnlyID.ID_RH_TURN_LIGHT_STS);
        addRawSignalRow(uiContext.getString(R.string.vehicle_info_signal_left_ind),
                ReadOnlyID.ID_DIRECTION_IND_LEFT);
        addRawSignalRow(uiContext.getString(R.string.vehicle_info_signal_right_ind),
                ReadOnlyID.ID_DIRECTION_IND_RIGHT);
        addRawSignalRow(uiContext.getString(R.string.vehicle_info_signal_hazard),
                ReadOnlyID.ID_HAZARD_LIGHT);

        addSection(uiContext.getString(R.string.vehicle_info_section_light));
        int lightRaw = repository.getCombined(ReadOnlyID.ID_LIGHT_DETECTED_IHU);
        addRow(uiContext.getString(R.string.vehicle_info_light_status),
                formatter.formatLightDetected(lightRaw));
        addRow(uiContext.getString(R.string.vehicle_info_light_raw),
                formatter.formatRawSignal(lightRaw));

        addSection(uiContext.getString(R.string.vehicle_info_section_etm_note));
        addRow(uiContext.getString(R.string.vehicle_info_etm_only_title),
                uiContext.getString(R.string.vehicle_info_etm_only_body));
    }

    private void addRawSignalRow(String label, int readOnlyId) {
        int raw = repository.getCombined(readOnlyId);
        String value = formatter.formatRawSignal(raw);
        if (VehicleMetricsSnapshot.isValid(raw)) {
            value += " · " + uiContext.getString(raw == 0 ? R.string.common_off : R.string.common_on);
        }
        addRow(label, value);
    }

    private void addSection(String title) {
        TextView tv = new TextView(uiContext);
        CockpitTokens.labelCaps(tv);
        tv.setText(title.toUpperCase(new java.util.Locale("tr", "TR")));
        int top = CockpitTokens.dim(uiContext, R.dimen.ck_space_md);
        tv.setPadding(0, top, 0, CockpitTokens.dim(uiContext, R.dimen.ck_space_sm));
        advancedRows.addView(tv);
    }

    private void addRow(String label, String value) {
        LinearLayout row = new LinearLayout(uiContext);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int padV = CockpitTokens.dim(uiContext, R.dimen.ck_space_sm);
        row.setPadding(0, padV, 0, padV);

        TextView labelTv = new TextView(uiContext);
        CockpitTokens.bodySm(labelTv);
        labelTv.setText(label);
        labelTv.setTextColor(CockpitTokens.color(uiContext, R.color.ck_text_medium));
        row.addView(labelTv, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueTv = new TextView(uiContext);
        CockpitTokens.mono(valueTv);
        valueTv.setText(value);
        valueTv.setGravity(Gravity.END);
        row.addView(valueTv);

        advancedRows.addView(row);
    }
}
