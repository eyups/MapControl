package com.mapcontrol.ui.builder;

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
import com.mapcontrol.R;
import com.mapcontrol.ui.cockpit.CockpitButton;
import com.mapcontrol.ui.cockpit.CockpitCard;
import com.mapcontrol.ui.cockpit.CockpitGrid;
import com.mapcontrol.ui.cockpit.CockpitSectionHeader;
import com.mapcontrol.ui.cockpit.CockpitTokens;
import com.mapcontrol.ui.cockpit.MetricView;
import com.mapcontrol.ui.cockpit.StatusBadge;
import com.mapcontrol.vehicle.VehicleMetricsFormatter;
import com.mapcontrol.vehicle.VehicleMetricsRepository;
import com.mapcontrol.vehicle.VehicleMetricsSnapshot;

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
                    odoMetric, tripMetric, sinceStartMetric}) {
                v.value("—");
            }
            return;
        }
        speedMetric.value(formatter.formatSpeed(repository.getCombined(ReadOnlyID.ID_CAR_SPEED)));
        gearMetric.value(formatter.formatGear(repository.getCombined(ReadOnlyID.ID_GEARBOX_STATE)));
        rpmMetric.value(formatter.formatRpm(repository.getCombined(ReadOnlyID.ID_ENGINE_RPM)));
        fuelMetric.value(formatter.formatFuelLevel(repository.getCombined(ReadOnlyID.ID_FUEL_PERCENT)));
        rangeMetric.value(formatter.formatKm(repository.getCombined(ReadOnlyID.ID_ENDURANCE_KM)));
        consMetric.value(formatter.formatConsumption(repository.getCombined(ReadOnlyID.ID_AVG_FUEL_CONS)));
        coolantMetric.value(formatter.formatTemperature(repository.getCombined(ReadOnlyID.ID_WATER_TEMPERATURE)));
        batteryMetric.value(formatter.formatVoltage(repository.getCombined(ReadOnlyID.ID_IBS_VOLTAGE)));
        avgSpeedMetric.value(formatter.formatSpeed(repository.getCombined(ReadOnlyID.ID_AVG_SPEED)));
        odoMetric.value(formatter.formatKm(repository.getCombined(ReadOnlyID.ID_GRAND_TOTAL_KM)));
        tripMetric.value(formatter.formatDeciKm(repository.getCombined(ReadOnlyID.ID_TRIP)));
        sinceStartMetric.value(formatter.formatKm(repository.getCombined(ReadOnlyID.ID_GRAND_TOTAL_KM_AFTER_RUNNING)));
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
