package com.mapcontrol.ui.cockpit;

import android.content.Context;
import android.graphics.PorterDuff;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mapcontrol.R;

/** 72dp list row: icon, title/subtitle, trailing control. */
public class CockpitListRow extends LinearLayout {

    private final TextView title;
    private final TextView subtitle;
    private final LinearLayout trailingHost;
    private final ImageView icon;

    public CockpitListRow(Context context, int iconRes, CharSequence titleText,
            CharSequence subtitleText) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(CockpitTokens.dim(context, R.dimen.ck_touch_primary));
        int h = CockpitTokens.dim(context, R.dimen.ck_space_md);
        int v = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        setPadding(h, v, h, v);
        setBackground(CockpitSurfaces.rounded(context,
                CockpitTokens.color(context, R.color.ck_surface_1), 12f));
        setClickable(true);
        setFocusable(true);

        icon = new ImageView(context);
        if (iconRes != 0) {
            icon.setImageResource(iconRes);
            icon.setColorFilter(CockpitTokens.color(context, R.color.ck_text_medium),
                    PorterDuff.Mode.SRC_IN);
            LayoutParams p = new LayoutParams(CockpitTokens.dp(context, 32),
                    CockpitTokens.dp(context, 32));
            p.rightMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
            addView(icon, p);
        }

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(VERTICAL);
        addView(textCol, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        title = new TextView(context);
        CockpitTokens.bodyLg(title);
        title.setText(titleText == null ? "" : titleText);
        textCol.addView(title);

        subtitle = new TextView(context);
        CockpitTokens.bodySm(subtitle);
        subtitle.setText(subtitleText == null ? "" : subtitleText);
        subtitle.setVisibility(subtitleText == null || subtitleText.length() == 0
                ? GONE : VISIBLE);
        textCol.addView(subtitle);

        trailingHost = new LinearLayout(context);
        trailingHost.setGravity(Gravity.CENTER_VERTICAL);
        addView(trailingHost, new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT));
    }

    public CockpitListRow trailing(View view) {
        trailingHost.removeAllViews();
        trailingHost.addView(view);
        return this;
    }

    public CockpitListRow subtitle(CharSequence text) {
        subtitle.setText(text == null ? "" : text);
        subtitle.setVisibility(text == null || text.length() == 0 ? GONE : VISIBLE);
        return this;
    }

    public void clickRow(View.OnClickListener listener) {
        setOnClickListener(listener);
    }

    public TextView getTitleView() {
        return title;
    }

    public LinearLayout getTrailingHost() {
        return trailingHost;
    }
}
