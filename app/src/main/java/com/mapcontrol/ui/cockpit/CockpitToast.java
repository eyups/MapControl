package com.mapcontrol.ui.cockpit;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.mapcontrol.R;

/** Single toast component: nominal / advisory / critical / neutral. */
public final class CockpitToast {

    private CockpitToast() {
    }

    public static void nominal(Context context, CharSequence text) {
        show(context, text, StatusBadge.NOMINAL);
    }

    public static void advisory(Context context, CharSequence text) {
        show(context, text, StatusBadge.ADVISORY);
    }

    public static void critical(Context context, CharSequence text) {
        show(context, text, StatusBadge.CRITICAL);
    }

    public static void neutral(Context context, CharSequence text) {
        show(context, text, StatusBadge.NEUTRAL);
    }

    public static void show(Context context, CharSequence text, int level) {
        if (context == null) {
            return;
        }
        Context c = context.getApplicationContext();
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        CockpitSurfaces.level2(row);
        int h = CockpitTokens.dim(c, R.dimen.ck_space_md);
        int v = CockpitTokens.dim(c, R.dimen.ck_space_sm);
        row.setPadding(h, v, h, v);

        TextView dot = new TextView(c);
        dot.setText("●");
        dot.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        dot.setTextColor(level == StatusBadge.NOMINAL
                ? CockpitTokens.color(c, R.color.ck_nominal)
                : level == StatusBadge.ADVISORY
                ? CockpitTokens.color(c, R.color.ck_caution)
                : level == StatusBadge.CRITICAL
                ? CockpitTokens.color(c, R.color.ck_critical)
                : CockpitTokens.color(c, R.color.ck_text_medium));
        row.addView(dot);

        TextView msg = new TextView(c);
        CockpitTokens.bodyMd(msg);
        msg.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = CockpitTokens.dim(c, R.dimen.ck_space_sm);
        row.addView(msg, lp);

        Toast toast = new Toast(c);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setView(row);
        toast.show();
    }
}
