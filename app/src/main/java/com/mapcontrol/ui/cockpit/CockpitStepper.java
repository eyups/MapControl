package com.mapcontrol.ui.cockpit;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mapcontrol.R;

import java.util.function.IntConsumer;

/** Oversized -/+ stepper with centered tabular value. */
public class CockpitStepper extends LinearLayout {

    private final TextView valueView;
    private final CockpitButton minus;
    private final CockpitButton plus;
    private int value;
    private int min = Integer.MIN_VALUE;
    private int max = Integer.MAX_VALUE;
    private int step = 1;
    private IntConsumer listener;

    public CockpitStepper(Context context, int initial, String unit) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        value = initial;

        minus = CockpitButton.secondary(context, "−");
        minus.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 28);
        minus.setMinWidth(CockpitTokens.dim(context, R.dimen.ck_touch_primary));
        minus.setMinHeight(CockpitTokens.dim(context, R.dimen.ck_touch_primary));
        addView(minus, new LayoutParams(CockpitTokens.dim(context, R.dimen.ck_touch_primary),
                CockpitTokens.dim(context, R.dimen.ck_touch_primary)));

        valueView = new TextView(context);
        CockpitTokens.headlineMd(valueView);
        CockpitTokens.tabular(valueView);
        valueView.setGravity(Gravity.CENTER);
        addView(valueView, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        plus = CockpitButton.secondary(context, "+");
        plus.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 28);
        plus.setMinWidth(CockpitTokens.dim(context, R.dimen.ck_touch_primary));
        plus.setMinHeight(CockpitTokens.dim(context, R.dimen.ck_touch_primary));
        addView(plus, new LayoutParams(CockpitTokens.dim(context, R.dimen.ck_touch_primary),
                CockpitTokens.dim(context, R.dimen.ck_touch_primary)));

        minus.setOnClickListener(v -> change(-1));
        plus.setOnClickListener(v -> change(1));
        setUnit(unit);
        render();
    }

    private final String[] unitText = new String[]{""};

    public void setUnit(String unit) {
        unitText[0] = unit == null ? "" : unit;
        render();
    }

    public CockpitStepper range(int min, int max) {
        this.min = min;
        this.max = max;
        return this;
    }

    public CockpitStepper step(int step) {
        this.step = Math.max(1, step);
        return this;
    }

    public CockpitStepper onChange(IntConsumer listener) {
        this.listener = listener;
        return this;
    }

    public void setValue(int value) {
        this.value = clamp(value);
        render();
    }

    public int getValue() {
        return value;
    }

    private void change(int direction) {
        int next = clamp(value + direction * step);
        if (next == value) {
            return;
        }
        value = next;
        render();
        if (listener != null) {
            listener.accept(value);
        }
    }

    private int clamp(int v) {
        return Math.max(min, Math.min(max, v));
    }

    private void render() {
        valueView.setText(value + (unitText[0].isEmpty() ? "" : " " + unitText[0]));
        minus.setEnabled(value > min);
        plus.setEnabled(value < max);
    }
}
