package com.mapcontrol.ui.cockpit;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mapcontrol.R;

/** Glanceable metric: optional caps label, large tabular value, secondary unit. */
public class MetricView extends LinearLayout {

    private final TextView label;
    private final TextView value;
    private final TextView unit;
    private final TextView caption;

    public MetricView(Context context) {
        this(context, null);
    }

    public MetricView(Context context, String labelText) {
        super(context);
        setOrientation(VERTICAL);
        setMinimumWidth(CockpitTokens.dp(context, 120));

        label = new TextView(context);
        CockpitTokens.labelCaps(label);
        label.setText(labelText == null ? "" : labelText);
        label.setVisibility(labelText == null ? GONE : VISIBLE);
        addView(label);

        LinearLayout valueRow = new LinearLayout(context);
        valueRow.setOrientation(HORIZONTAL);
        valueRow.setGravity(Gravity.BOTTOM);
        LayoutParams rowParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_xs);
        addView(valueRow, rowParams);

        value = new TextView(context);
        CockpitTokens.metricValue(value);
        value.setText("—");
        valueRow.addView(value);

        unit = new TextView(context);
        CockpitTokens.metricUnit(unit);
        LayoutParams unitParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        unitParams.leftMargin = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        unitParams.bottomMargin = CockpitTokens.dp(context, 6);
        valueRow.addView(unit, unitParams);
        unit.setVisibility(GONE);

        caption = new TextView(context);
        CockpitTokens.bodySm(caption);
        caption.setVisibility(GONE);
        LayoutParams capParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        capParams.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_xs);
        addView(caption, capParams);
    }

    public MetricView value(String text) {
        value.setText(TextUtils.isEmpty(text) ? "—" : text);
        return this;
    }

    public MetricView unit(String text) {
        unit.setText(text == null ? "" : text);
        unit.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);
        return this;
    }

    public MetricView caption(String text) {
        caption.setText(text == null ? "" : text);
        caption.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);
        return this;
    }

    public MetricView accent(int color) {
        value.setTextColor(color);
        return this;
    }

    public MetricView compact() {
        CockpitTokens.headlineLg(value);
        label.setVisibility(GONE);
        return this;
    }

    public void setValueTextSize(float sp) {
        value.setTextSize(sp);
    }

    public View getValueView() {
        return value;
    }

    public TextView getLabelView() {
        return label;
    }
}
