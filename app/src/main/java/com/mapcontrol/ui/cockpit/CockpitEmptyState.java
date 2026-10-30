package com.mapcontrol.ui.cockpit;

import android.content.Context;
import android.graphics.PorterDuff;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mapcontrol.R;

/** Icon + message + optional action for empty/offline states. */
public class CockpitEmptyState extends LinearLayout {

    private final TextView message;
    private final LinearLayout actionHost;

    public CockpitEmptyState(Context context, int iconRes, String text) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        int pad = CockpitTokens.dim(context, R.dimen.ck_space_lg);
        setPadding(pad, pad, pad, pad);

        if (iconRes != 0) {
            ImageView icon = new ImageView(context);
            icon.setImageResource(iconRes);
            icon.setColorFilter(CockpitTokens.color(context, R.color.ck_text_muted),
                    PorterDuff.Mode.SRC_IN);
            LayoutParams iconParams = new LayoutParams(CockpitTokens.dp(context, 40),
                    CockpitTokens.dp(context, 40));
            addView(icon, iconParams);
        }

        message = new TextView(context);
        CockpitTokens.bodyMd(message);
        message.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        message.setGravity(Gravity.CENTER);
        message.setText(text == null ? "" : text);
        LayoutParams msgParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        msgParams.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
        addView(message, msgParams);

        actionHost = new LinearLayout(context);
        actionHost.setGravity(Gravity.CENTER);
        LayoutParams actParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        actParams.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
        addView(actionHost, actParams);
    }

    public CockpitEmptyState action(View view) {
        actionHost.addView(view);
        return this;
    }

    public void setMessage(String text) {
        message.setText(text == null ? "" : text);
    }
}
