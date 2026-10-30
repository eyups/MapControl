package com.mapcontrol.ui.cockpit;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mapcontrol.R;

/** LabelCaps section header with optional trailing action. */
public class CockpitSectionHeader extends LinearLayout {

    private final TextView label;
    private final LinearLayout actionHost;

    public CockpitSectionHeader(Context context, String text) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int v = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        setPadding(0, v, 0, v);

        label = new TextView(context);
        CockpitTokens.labelCaps(label);
        label.setText(text == null ? "" : text.toUpperCase(
                new java.util.Locale("tr", "TR")));
        addView(label, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        actionHost = new LinearLayout(context);
        actionHost.setOrientation(HORIZONTAL);
        actionHost.setGravity(Gravity.CENTER_VERTICAL);
        addView(actionHost);
    }

    public CockpitSectionHeader action(View view) {
        actionHost.addView(view);
        return this;
    }

    public void setText(String text) {
        label.setText(text == null ? "" : text.toUpperCase(new java.util.Locale("tr", "TR")));
    }
}
