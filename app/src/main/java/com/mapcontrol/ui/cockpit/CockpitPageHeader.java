package com.mapcontrol.ui.cockpit;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mapcontrol.R;

/** Standard page heading: title + description, no decorative icon. */
public final class CockpitPageHeader extends LinearLayout {

    public CockpitPageHeader(Context context, CharSequence title, CharSequence subtitle) {
        super(context);
        setOrientation(VERTICAL);
        int margin = CockpitTokens.dim(context, R.dimen.ck_margin);
        setPadding(margin, CockpitTokens.dim(context, R.dimen.ck_space_sm),
                margin, CockpitTokens.dim(context, R.dimen.ck_space_md));

        TextView titleView = new TextView(context);
        CockpitTokens.headlineMd(titleView);
        titleView.setText(title == null ? "" : title);
        addView(titleView);

        TextView subtitleView = new TextView(context);
        CockpitTokens.bodySm(subtitleView);
        subtitleView.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        subtitleView.setText(subtitle == null ? "" : subtitle);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        lp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_xs);
        addView(subtitleView, lp);
    }
}
