package com.mapcontrol.ui.builder;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.vehicle.ComfortCommands;
import com.mapcontrol.vehicle.TemperatureAutomation;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Ayarlar sekmesindeki "Sıcaklık otomasyonu" kartları: canlı dış sıcaklık ve kural başına eşik/seviye/gecikme. */
final class TemperatureAutomationUi {

    private static final int AMBIENT_REFRESH_MS = 3000;
    private static final int DELAY_STEP_S = 10;
    private static final int DELAY_MAX_S = 120;
    private static final ExecutorService AMBIENT_EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "temperature-auto-ambient");
                thread.setDaemon(true);
                return thread;
            });

    private TemperatureAutomationUi() {
    }

    static void addTo(Context context, LinearLayout parent, SharedPreferences prefs, Consumer<String> log) {
        LinearLayout header = createCard(context);
        addText(context, header, context.getString(R.string.automation_title), 18, R.color.textPrimary, true, 0, 8);
        addText(context, header, context.getString(R.string.automation_desc), 13, R.color.textHint, false, 0, 12);

        TextView ambient = addText(context, header, context.getString(R.string.automation_ambient_unknown),
                16, R.color.textPrimary, true, 0, 12);
        bindAmbientRefresh(context, ambient);

        UiStyles.addBinarySegmentedControl(context, header,
                context.getString(R.string.automation_master_title),
                context.getString(R.string.common_on), context.getString(R.string.common_off),
                context.getString(R.string.automation_master_help_on),
                context.getString(R.string.automation_master_help_off),
                prefs.getBoolean(TemperatureAutomation.PREF_ENABLED, false),
                on -> {
                    prefs.edit().putBoolean(TemperatureAutomation.PREF_ENABLED, on).apply();
                    log.accept("Sıcaklık otomasyonu: " + (on ? "Açık" : "Kapalı"));
                });
        parent.addView(header, cardLp(context));

        if (context.getResources().getConfiguration().screenWidthDp >= 1000) {
            LinearLayout columns = new LinearLayout(context);
            columns.setOrientation(LinearLayout.HORIZONTAL);
            columns.setBaselineAligned(false);

            LinearLayout left = createColumn(context);
            LinearLayout right = createColumn(context);
            int gutter = UiStyles.dimenPx(context, R.dimen.spacing_medium);
            LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            leftLp.setMarginEnd(gutter / 2);
            LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            rightLp.setMarginStart(gutter / 2);
            columns.addView(left, leftLp);
            columns.addView(right, rightLp);

            int index = 0;
            for (TemperatureAutomation.Rule rule : TemperatureAutomation.RULES) {
                addRuleCard(context, index % 2 == 0 ? left : right, prefs, log, rule);
                index++;
            }
            parent.addView(columns, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            return;
        }

        for (TemperatureAutomation.Rule rule : TemperatureAutomation.RULES) {
            addRuleCard(context, parent, prefs, log, rule);
        }
    }

    private static LinearLayout createCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = UiStyles.dimenPx(context, R.dimen.oem_card_inner_padding);
        card.setPadding(pad, pad, pad, pad);
        UiStyles.setGlassCardBackground(card);
        return card;
    }

    private static LinearLayout createColumn(Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private static LinearLayout.LayoutParams cardLp(Context context) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UiStyles.dimenPx(context, R.dimen.spacing_medium);
        return lp;
    }

    private static void addRuleCard(Context context, LinearLayout parent, SharedPreferences prefs,
            Consumer<String> log, TemperatureAutomation.Rule rule) {
        LinearLayout card = createCard(context);
        addRuleContent(context, card, prefs, log, rule);
        parent.addView(card, cardLp(context));
    }

    private static void addRuleContent(Context context, LinearLayout parent, SharedPreferences prefs,
            Consumer<String> log, TemperatureAutomation.Rule rule) {
        String title = context.getString(rule.titleRes);
        UiStyles.addBinarySegmentedControl(context, parent, title,
                context.getString(R.string.common_on), context.getString(R.string.common_off),
                context.getString(rule.isHeating()
                        ? R.string.automation_rule_help_on_heat : R.string.automation_rule_help_on_vent),
                context.getString(R.string.automation_rule_help_off),
                prefs.getBoolean(rule.prefEnabled(), false),
                on -> {
                    prefs.edit().putBoolean(rule.prefEnabled(), on).apply();
                    log.accept("Sıcaklık otomasyonu - " + title + ": " + (on ? "Açık" : "Kapalı"));
                });

        int thresholdMin = rule.isHeating() ? -10 : 15;
        int thresholdMax = rule.isHeating() ? 30 : 40;
        addSeekRow(context, parent, thresholdMin, thresholdMax, 1,
                prefs.getInt(rule.prefThreshold(), rule.defaultThreshold),
                v -> context.getString(rule.isHeating()
                        ? R.string.automation_threshold_below : R.string.automation_threshold_above, v),
                v -> {
                    prefs.edit().putInt(rule.prefThreshold(), v).apply();
                    log.accept("Sıcaklık otomasyonu - " + title + " eşik: " + v + "°C");
                });

        if (rule.hasLevel()) {
            addSeekRow(context, parent, 1, ComfortCommands.SEAT_LEVEL_MAX, 1,
                    prefs.getInt(rule.prefLevel(), rule.defaultLevel),
                    v -> context.getString(R.string.automation_level, v),
                    v -> {
                        prefs.edit().putInt(rule.prefLevel(), v).apply();
                        log.accept("Sıcaklık otomasyonu - " + title + " seviye: " + v);
                    });
        }

        addSeekRow(context, parent, 0, DELAY_MAX_S, DELAY_STEP_S,
                prefs.getInt(rule.prefDelay(), 0),
                v -> v == 0
                        ? context.getString(R.string.automation_delay_now)
                        : context.getString(R.string.automation_delay, v),
                v -> {
                    prefs.edit().putInt(rule.prefDelay(), v).apply();
                    log.accept("Sıcaklık otomasyonu - " + title + " gecikme: " + v + " sn");
                });
    }

    private interface LabelFormatter {
        String format(int value);
    }

    private interface ValueListener {
        void onCommit(int value);
    }

    private static void addSeekRow(Context context, LinearLayout parent, int min, int max, int step,
            int current, LabelFormatter formatter, ValueListener listener) {
        TextView label = addText(context, parent, formatter.format(current), 13, R.color.textHint, false, 4, 0);
        SeekBar bar = new SeekBar(context);
        int steps = (max - min) / step;
        bar.setMax(steps);
        bar.setProgress(Math.max(0, Math.min(steps, (current - min) / step)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                label.setText(formatter.format(min + progress * step));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                listener.onCommit(min + seekBar.getProgress() * step);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(16, 0, 16, 8);
        parent.addView(bar, lp);
    }

    private static TextView addText(Context context, LinearLayout parent, String text, int sizeSp,
            int colorRes, boolean bold, int topPad, int bottomPad) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(UiStyles.color(context, colorRes));
        if (bold) {
            view.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        view.setPadding(16, topPad, 16, bottomPad);
        parent.addView(view, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return view;
    }

    /** Görünürken dış sıcaklığı periyodik okur; CarInfo okumaları bloklayabildiği için arka thread'de. */
    private static void bindAmbientRefresh(Context context, TextView label) {
        Runnable[] loop = new Runnable[1];
        loop[0] = () -> {
            if (!label.isAttachedToWindow()) {
                return;
            }
            AMBIENT_EXECUTOR.execute(() -> {
                String text;
                try {
                    float temp = ComfortCommands.isConnected()
                            ? ComfortCommands.readOutsideTempC() : ComfortCommands.INVALID_TEMP_C;
                    text = Float.isNaN(temp)
                            ? context.getString(R.string.automation_ambient_unknown)
                            : context.getString(R.string.automation_ambient, temp);
                } catch (Throwable t) {
                    text = context.getString(R.string.automation_ambient_unknown);
                }
                String shown = text;
                label.post(() -> label.setText(shown));
            });
            label.postDelayed(loop[0], AMBIENT_REFRESH_MS);
        };
        label.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                label.removeCallbacks(loop[0]);
                label.post(loop[0]);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                label.removeCallbacks(loop[0]);
            }
        });
    }
}
