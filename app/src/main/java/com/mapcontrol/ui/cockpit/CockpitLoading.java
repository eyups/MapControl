package com.mapcontrol.ui.cockpit;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.mapcontrol.R;

/** Thin indeterminate bar instead of a spinner. */
public class CockpitLoading extends LinearLayout {

    public CockpitLoading(Context context, String text) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(0, CockpitTokens.dim(context, R.dimen.ck_space_sm), 0,
                CockpitTokens.dim(context, R.dimen.ck_space_sm));

        ProgressBar bar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        bar.setProgressTintList(ColorStateList.valueOf(
                CockpitTokens.color(context, R.color.ck_accent)));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(
                CockpitTokens.color(context, R.color.ck_surface_2)));
        addView(bar, new LayoutParams(LayoutParams.MATCH_PARENT, CockpitTokens.dp(context, 2)));

        TextView label = new TextView(context);
        CockpitTokens.bodySm(label);
        label.setText(text == null ? "" : text);
        label.setGravity(Gravity.CENTER);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        addView(label, lp);
    }
}
