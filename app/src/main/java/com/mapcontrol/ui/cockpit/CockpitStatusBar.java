package com.mapcontrol.ui.cockpit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mapcontrol.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Fixed 56dp cockpit status bar: brand + screen title, centered clock, contextual actions. */
public class CockpitStatusBar extends LinearLayout {

    private final TextView titleView;
    private final TextView clockView;
    private final LinearLayout actions;
    private final CockpitButton backButton;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private boolean receiverRegistered;

    private final BroadcastReceiver tickReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateClock();
        }
    };

    public CockpitStatusBar(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundColor(CockpitTokens.color(context, R.color.ck_bg_base));
        setMinimumHeight(CockpitTokens.dim(context, R.dimen.ck_statusbar_h));
        int h = CockpitTokens.dim(context, R.dimen.ck_margin);
        setPadding(h, 0, h, 0);

        LinearLayout left = new LinearLayout(context);
        left.setOrientation(HORIZONTAL);
        left.setGravity(Gravity.CENTER_VERTICAL);
        addView(left, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));

        backButton = CockpitButton.icon(context, R.drawable.ic_mdi_chevron_double_left,
                context.getString(R.string.ck_rail_collapse));
        backButton.setVisibility(GONE);
        backButton.setMinHeight(CockpitTokens.dim(context, R.dimen.ck_touch_min));
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(
                CockpitTokens.dim(context, R.dimen.ck_touch_min),
                CockpitTokens.dim(context, R.dimen.ck_touch_min));
        backLp.rightMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
        left.addView(backButton, backLp);

        TextView brand = new TextView(context);
        CockpitTokens.labelCaps(brand);
        brand.setText("ROAD DECK");
        brand.setTextColor(CockpitTokens.color(context, R.color.ck_accent));
        brand.setLetterSpacing(0.12f);
        left.addView(brand);

        View divider = new View(context);
        divider.setBackgroundColor(CockpitTokens.color(context, R.color.ck_stroke));
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                CockpitTokens.dp(context, 1), CockpitTokens.dp(context, 20));
        dividerLp.leftMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
        dividerLp.rightMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
        left.addView(divider, dividerLp);

        titleView = new TextView(context);
        CockpitTokens.bodyMd(titleView);
        titleView.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        left.addView(titleView, new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT));

        clockView = new TextView(context);
        CockpitTokens.headlineSm(clockView);
        CockpitTokens.tabular(clockView);
        clockView.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        clockView.setGravity(Gravity.CENTER);
        addView(clockView, new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT));

        actions = new LinearLayout(context);
        actions.setOrientation(HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        addView(actions, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));

        updateClock();
        registerClock();
    }

    private void registerClock() {
        if (receiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getContext().registerReceiver(tickReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            getContext().registerReceiver(tickReceiver, filter);
        }
        receiverRegistered = true;
    }

    /** Activity onDestroy'da çağrılır; receiver sızıntısını önler. */
    public void release() {
        if (receiverRegistered) {
            try {
                getContext().unregisterReceiver(tickReceiver);
            } catch (IllegalArgumentException ignored) {
                // already unregistered
            }
            receiverRegistered = false;
        }
    }

    private void updateClock() {
        clockView.setText(timeFormat.format(new Date()));
    }

    public TextView getTitleView() {
        return titleView;
    }

    public LinearLayout getActionsContainer() {
        return actions;
    }

    public void setBackVisible(boolean visible) {
        backButton.setVisibility(visible ? VISIBLE : GONE);
    }

    public void setBackListener(OnClickListener listener) {
        backButton.setOnClickListener(listener);
    }

    public void setBarVisible(boolean visible) {
        setVisibility(visible ? VISIBLE : GONE);
    }
}
