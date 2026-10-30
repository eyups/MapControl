package com.mapcontrol.ui.cockpit;

import android.content.Context;
import android.view.Gravity;
import android.widget.TextView;

import com.mapcontrol.R;

/** Nominal / advisory / critical capsule. */
public class StatusBadge extends TextView {

    public static final int NOMINAL = 0;
    public static final int ADVISORY = 1;
    public static final int CRITICAL = 2;
    public static final int NEUTRAL = 3;

    private int level = NEUTRAL;

    public StatusBadge(Context context) {
        super(context);
        setGravity(Gravity.CENTER);
        setSingleLine(true);
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
        setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_BOLD));
        setAllCaps(true);
        int h = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        int v = CockpitTokens.dim(context, R.dimen.ck_space_xs);
        setPadding(h, v, h, v);
        setLevel(NEUTRAL);
    }

    public static StatusBadge nominal(Context c, CharSequence text) {
        StatusBadge b = new StatusBadge(c);
        b.setLevel(NOMINAL);
        b.setText(text);
        return b;
    }

    public static StatusBadge advisory(Context c, CharSequence text) {
        StatusBadge b = new StatusBadge(c);
        b.setLevel(ADVISORY);
        b.setText(text);
        return b;
    }

    public static StatusBadge critical(Context c, CharSequence text) {
        StatusBadge b = new StatusBadge(c);
        b.setLevel(CRITICAL);
        b.setText(text);
        return b;
    }

    public static StatusBadge neutral(Context c, CharSequence text) {
        StatusBadge b = new StatusBadge(c);
        b.setLevel(NEUTRAL);
        b.setText(text);
        return b;
    }

    public void setLevel(int level) {
        this.level = level;
        Context c = getContext();
        int fg;
        int bg;
        if (level == NOMINAL) {
            fg = CockpitTokens.color(c, R.color.ck_nominal);
            bg = CockpitTokens.color(c, R.color.ck_nominal_bg);
        } else if (level == ADVISORY) {
            fg = CockpitTokens.color(c, R.color.ck_caution);
            bg = CockpitTokens.color(c, R.color.ck_caution_bg);
        } else if (level == CRITICAL) {
            fg = CockpitTokens.color(c, R.color.ck_critical);
            bg = CockpitTokens.color(c, R.color.ck_critical_bg);
        } else {
            fg = CockpitTokens.color(c, R.color.ck_text_medium);
            bg = CockpitTokens.color(c, R.color.ck_surface_2);
        }
        setTextColor(fg);
        setBackground(CockpitSurfaces.rounded(c, bg, 999f));
    }

    public int getLevel() {
        return level;
    }
}
