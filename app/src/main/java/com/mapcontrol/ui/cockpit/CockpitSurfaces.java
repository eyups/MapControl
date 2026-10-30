package com.mapcontrol.ui.cockpit;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;

import com.mapcontrol.R;

/** Tonal surface helpers: Level 0-3, 1dp strokes, cockpit radii. */
public final class CockpitSurfaces {

    private CockpitSurfaces() {
    }

    public static GradientDrawable rounded(Context context, int fillColor, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fillColor);
        d.setCornerRadius(radius(context, radiusDp));
        return d;
    }

    public static GradientDrawable outlined(Context context, int fillColor, int strokeColor,
            float radiusDp) {
        GradientDrawable d = rounded(context, fillColor, radiusDp);
        d.setStroke(CockpitTokens.dim(context, R.dimen.ck_stroke), strokeColor);
        return d;
    }

    public static float radius(Context context, float radiusDp) {
        return radiusDp * context.getResources().getDisplayMetrics().density;
    }

    /** Level 0: chassis foundation, no stroke. */
    public static void level0(View view) {
        Context c = view.getContext();
        view.setBackground(rounded(c, color(c, R.color.ck_bg_base), 0f));
    }

    /** Level 1: structural card/rail. */
    public static void level1(View view) {
        level1(view, R.dimen.ck_radius_lg);
    }

    public static void level1(View view, int radiusDimenRes) {
        Context c = view.getContext();
        view.setBackground(outlined(c, color(c, R.color.ck_surface_1),
                color(c, R.color.ck_stroke), c.getResources().getDimension(radiusDimenRes)
                        / c.getResources().getDisplayMetrics().density));
    }

    /** Level 2: active/floating widget, accent 30% stroke + elevation 8dp. */
    public static void level2(View view) {
        Context c = view.getContext();
        view.setBackground(outlined(c, color(c, R.color.ck_surface_2),
                color(c, R.color.ck_accent_stroke_30), 16f));
        view.setElevation(CockpitTokens.dp(c, 8));
    }

    /** Level 3: modal alert surface, rim highlight + deep shadow. */
    public static void level3(View view) {
        Context c = view.getContext();
        GradientDrawable d = rounded(c, color(c, R.color.ck_bg_recessed), 24f);
        d.setStroke(CockpitTokens.dp(c, 1), color(c, R.color.ck_stroke_modal));
        view.setBackground(d);
        view.setElevation(CockpitTokens.dp(c, 16));
    }

    /** Interaction ripple clipped to a rounded shape. */
    public static void ripple(View view, int fillColor, float radiusDp, int rippleColor) {
        Context c = view.getContext();
        GradientDrawable content = rounded(c, fillColor, radiusDp);
        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf(rippleColor), content, null);
        view.setBackground(ripple);
    }

    /** Two-state filled button background. */
    public static StateListDrawable stateFill(Context c, int normalFill, int pressedFill,
            int disabledFill, float radiusDp) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, rounded(c, pressedFill, radiusDp));
        s.addState(new int[]{-android.R.attr.state_enabled}, rounded(c, disabledFill, radiusDp));
        s.addState(new int[]{}, rounded(c, normalFill, radiusDp));
        return s;
    }

    /** Secondary surface button: fill + stroke, pressed stroke accent. */
    public static StateListDrawable stateOutlined(Context c, float radiusDp) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed},
                outlined(c, color(c, R.color.ck_surface_2), color(c, R.color.ck_accent), radiusDp));
        s.addState(new int[]{-android.R.attr.state_enabled},
                outlined(c, color(c, R.color.ck_surface_1), color(c, R.color.ck_stroke), radiusDp));
        s.addState(new int[]{},
                outlined(c, color(c, R.color.ck_surface_2), color(c, R.color.ck_stroke), radiusDp));
        return s;
    }

    public static int color(Context c, int res) {
        return CockpitTokens.color(c, res);
    }

    public static int alpha(int color, float alpha) {
        return Color.argb(Math.round(alpha * 255), Color.red(color), Color.green(color),
                Color.blue(color));
    }
}
