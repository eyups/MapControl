package com.mapcontrol.ui.builder;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mapcontrol.R;
import com.mapcontrol.ui.cockpit.CockpitCard;
import com.mapcontrol.ui.cockpit.CockpitGrid;
import com.mapcontrol.ui.cockpit.CockpitListRow;
import com.mapcontrol.ui.cockpit.CockpitSegmented;
import com.mapcontrol.ui.cockpit.CockpitTokens;
import com.mapcontrol.ui.cockpit.CockpitToggle;
import com.mapcontrol.ui.cockpit.StatusBadge;
import com.mapcontrol.vehicle.ComfortCommands;
import com.mapcontrol.vehicle.RecirculationAutomation;

import java.util.Arrays;
import java.util.function.Consumer;

/** Konfor: açılış cam tavan/güneşlik ve trafik iç sirkülasyonu. */
public class ComfortTabBuilder {

    private final Context context;
    private final SharedPreferences prefs;
    private final Consumer<String> log;
    private final ScrollView scrollView;

    public ComfortTabBuilder(Context context, SharedPreferences prefs, Consumer<String> log) {
        this.context = context;
        this.prefs = prefs;
        this.log = log;
        this.scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.TRANSPARENT);
        rebuild();
    }

    public ScrollView getScrollView() {
        return scrollView;
    }

    public void rebuild() {
        scrollView.removeAllViews();
        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);
        int m = CockpitTokens.dim(context, R.dimen.ck_margin);
        outer.setPadding(m, CockpitTokens.dim(context, R.dimen.ck_space_md), m, m);

        TextView title = new TextView(context);
        CockpitTokens.headlineMd(title);
        title.setText(R.string.settings_comfort_title);
        outer.addView(title);

        TextView desc = new TextView(context);
        CockpitTokens.bodySm(desc);
        desc.setText(R.string.settings_comfort_desc);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descLp.bottomMargin = CockpitTokens.dim(context, R.dimen.ck_space_lg);
        outer.addView(desc, descLp);

        CockpitGrid grid = new CockpitGrid(context);
        outer.addView(grid, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        CockpitCard sunroof = new CockpitCard(context,
                context.getString(R.string.settings_comfort_sunroof_title)).span(4);
        int sunroofMode = prefs.getInt(ComfortCommands.PREF_SUNROOF, -1);
        sunroof.status(badgeForMode(sunroofMode,
                R.string.settings_comfort_sunroof_vent,
                R.string.settings_comfort_sunroof_open));
        CockpitSegmented sunroofSeg = new CockpitSegmented(context, Arrays.asList(
                context.getString(R.string.drive_mode_none),
                context.getString(R.string.settings_comfort_sunroof_vent),
                context.getString(R.string.settings_comfort_sunroof_open)),
                indexForMode(sunroofMode, new int[]{-1, ComfortCommands.SUNROOF_VENT,
                        ComfortCommands.SUNROOF_OPEN}), index -> {
                    int mode = new int[]{-1, ComfortCommands.SUNROOF_VENT,
                            ComfortCommands.SUNROOF_OPEN}[index];
                    prefs.edit().putInt(ComfortCommands.PREF_SUNROOF, mode).apply();
                    log.accept("Cam tavan açılış ayarı: " + mode);
                });
        sunroof.addContent(sunroofSeg);
        TextView sunroofHelp = new TextView(context);
        CockpitTokens.bodySm(sunroofHelp);
        sunroofHelp.setText(R.string.settings_comfort_sunroof_help_none);
        sunroof.addContent(sunroofHelp);
        grid.add(sunroof, 4);

        CockpitCard sunshade = new CockpitCard(context,
                context.getString(R.string.settings_comfort_sunshade_title)).span(4);
        int shadeMode = prefs.getInt(ComfortCommands.PREF_SUNSHADE, -1);
        sunshade.status(badgeForMode(shadeMode,
                R.string.settings_comfort_sunshade_open,
                R.string.settings_comfort_sunshade_close));
        CockpitSegmented shadeSeg = new CockpitSegmented(context, Arrays.asList(
                context.getString(R.string.drive_mode_none),
                context.getString(R.string.settings_comfort_sunshade_open),
                context.getString(R.string.settings_comfort_sunshade_close)),
                indexForMode(shadeMode, new int[]{-1, ComfortCommands.SUNSHADE_OPEN,
                        ComfortCommands.SUNSHADE_CLOSE}), index -> {
                    int mode = new int[]{-1, ComfortCommands.SUNSHADE_OPEN,
                            ComfortCommands.SUNSHADE_CLOSE}[index];
                    prefs.edit().putInt(ComfortCommands.PREF_SUNSHADE, mode).apply();
                    log.accept("Güneşlik açılış ayarı: " + mode);
                });
        sunshade.addContent(shadeSeg);
        TextView shadeHelp = new TextView(context);
        CockpitTokens.bodySm(shadeHelp);
        shadeHelp.setText(R.string.settings_comfort_sunshade_help_none);
        sunshade.addContent(shadeHelp);
        grid.add(sunshade, 4);

        CockpitCard recirc = new CockpitCard(context,
                context.getString(R.string.settings_comfort_recirc_title)).span(8);
        boolean recircOn = prefs.getBoolean(RecirculationAutomation.PREF_ENABLED, false);
        recirc.status(recircOn ? StatusBadge.nominal(context, context.getString(R.string.common_on))
                : StatusBadge.neutral(context, context.getString(R.string.common_off)));
        CockpitListRow row = new CockpitListRow(context, R.drawable.ic_mdi_car_defrost,
                context.getString(R.string.settings_comfort_recirc_title),
                context.getString(recircOn ? R.string.settings_comfort_recirc_help_on
                        : R.string.settings_comfort_recirc_help_off));
        CockpitToggle toggle = new CockpitToggle(context);
        toggle.setChecked(recircOn, false);
        row.trailing(toggle);
        toggle.setOnCheckedChangeListener((t, checked) -> {
            prefs.edit().putBoolean(RecirculationAutomation.PREF_ENABLED, checked).apply();
            log.accept("Trafikte otomatik iç sirkülasyon: " + (checked ? "Açık" : "Kapalı"));
            row.subtitle(context.getString(checked
                    ? R.string.settings_comfort_recirc_help_on
                    : R.string.settings_comfort_recirc_help_off));
        });
        recirc.addContent(row);
        grid.add(recirc, 8);

        scrollView.addView(outer, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private int indexForMode(int mode, int[] modes) {
        for (int i = 0; i < modes.length; i++) {
            if (modes[i] == mode) {
                return i;
            }
        }
        return 0;
    }

    private StatusBadge badgeForMode(int mode, int positive1, int positive2) {
        if (mode == -1) {
            return StatusBadge.neutral(context, context.getString(R.string.drive_mode_none));
        }
        return StatusBadge.nominal(context, mode == positive1
                ? context.getString(positive1) : context.getString(positive2));
    }
}
