package com.mapcontrol.ui.builder;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.vehicle.ComfortCommands;
import com.mapcontrol.vehicle.RecirculationAutomation;

import java.util.function.Consumer;

/** Standalone comfort tab: startup sunroof/sunshade and traffic recirculation. */
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
        int margin = UiStyles.dimenPx(context, R.dimen.oem_card_margin);
        int inner = UiStyles.dimenPx(context, R.dimen.oem_card_inner_padding);
        outer.setPadding(margin, margin, margin, margin * 2);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(inner, inner, inner, inner);
        UiStyles.setGlassCardBackground(card);

        TextView title = new TextView(context);
        title.setText(R.string.settings_comfort_title);
        title.setTextSize(19);
        title.setTextColor(UiStyles.color(context, R.color.textPrimary));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(title);

        TextView desc = new TextView(context);
        desc.setText(R.string.settings_comfort_desc);
        desc.setTextSize(13);
        desc.setTextColor(UiStyles.color(context, R.color.textHint));
        desc.setLineSpacing(0f, 1.25f);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = 6;
        descLp.bottomMargin = 14;
        card.addView(desc, descLp);

        UiStyles.addTernarySegmentedControl(context, card,
                context.getString(R.string.settings_comfort_sunroof_title),
                new String[]{
                        context.getString(R.string.drive_mode_none),
                        context.getString(R.string.settings_comfort_sunroof_vent),
                        context.getString(R.string.settings_comfort_sunroof_open)},
                new String[]{
                        context.getString(R.string.settings_comfort_sunroof_help_none),
                        context.getString(R.string.settings_comfort_sunroof_help_vent),
                        context.getString(R.string.settings_comfort_sunroof_help_open)},
                new int[]{-1, ComfortCommands.SUNROOF_VENT, ComfortCommands.SUNROOF_OPEN},
                prefs.getInt(ComfortCommands.PREF_SUNROOF, -1),
                mode -> {
                    prefs.edit().putInt(ComfortCommands.PREF_SUNROOF, mode).apply();
                    log.accept("Cam tavan açılış ayarı: " + mode);
                });

        UiStyles.addTernarySegmentedControl(context, card,
                context.getString(R.string.settings_comfort_sunshade_title),
                new String[]{
                        context.getString(R.string.drive_mode_none),
                        context.getString(R.string.settings_comfort_sunshade_open),
                        context.getString(R.string.settings_comfort_sunshade_close)},
                new String[]{
                        context.getString(R.string.settings_comfort_sunshade_help_none),
                        context.getString(R.string.settings_comfort_sunshade_help_open),
                        context.getString(R.string.settings_comfort_sunshade_help_close)},
                new int[]{-1, ComfortCommands.SUNSHADE_OPEN, ComfortCommands.SUNSHADE_CLOSE},
                prefs.getInt(ComfortCommands.PREF_SUNSHADE, -1),
                mode -> {
                    prefs.edit().putInt(ComfortCommands.PREF_SUNSHADE, mode).apply();
                    log.accept("Güneşlik açılış ayarı: " + mode);
                });

        UiStyles.addBinarySegmentedControl(context, card,
                context.getString(R.string.settings_comfort_recirc_title),
                context.getString(R.string.common_on), context.getString(R.string.common_off),
                context.getString(R.string.settings_comfort_recirc_help_on),
                context.getString(R.string.settings_comfort_recirc_help_off),
                prefs.getBoolean(RecirculationAutomation.PREF_ENABLED, false),
                on -> {
                    prefs.edit().putBoolean(RecirculationAutomation.PREF_ENABLED, on).apply();
                    log.accept("Trafikte otomatik iç sirkülasyon: " + (on ? "Açık" : "Kapalı"));
                });

        outer.addView(card, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        scrollView.addView(outer, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }
}
