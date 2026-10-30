package com.mapcontrol.ui.cockpit;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.mapcontrol.R;

/** 64dp (primary 72dp) cockpit button with pressed/disabled states. */
public class CockpitButton extends AppCompatTextView {

    public static final int STYLE_PRIMARY = 0;
    public static final int STYLE_SECONDARY = 1;
    public static final int STYLE_HAZARD = 2;

    private int buttonStyle = STYLE_SECONDARY;

    public CockpitButton(Context context) {
        super(context);
        setGravity(Gravity.CENTER);
        setClickable(true);
        setFocusable(true);
        setSingleLine(false);
        setMaxLines(2);
        setIncludeFontPadding(false);
        int h = CockpitTokens.dim(context, R.dimen.ck_space_lg);
        setPadding(h, CockpitTokens.dim(context, R.dimen.ck_space_sm), h,
                CockpitTokens.dim(context, R.dimen.ck_space_sm));
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 17);
        setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_SEMIBOLD));
        setMinHeight(CockpitTokens.dim(context, R.dimen.ck_touch_min));
        applyStyle(STYLE_SECONDARY);
    }

    public static CockpitButton primary(Context c, CharSequence text) {
        CockpitButton b = new CockpitButton(c);
        b.setText(text);
        b.applyStyle(STYLE_PRIMARY);
        return b;
    }

    public static CockpitButton secondary(Context c, CharSequence text) {
        CockpitButton b = new CockpitButton(c);
        b.setText(text);
        b.applyStyle(STYLE_SECONDARY);
        return b;
    }

    public static CockpitButton hazard(Context c, CharSequence text) {
        CockpitButton b = new CockpitButton(c);
        b.setText(text);
        b.applyStyle(STYLE_HAZARD);
        return b;
    }

    public static CockpitButton icon(Context c, int drawableRes, CharSequence contentDescription) {
        CockpitButton b = new CockpitButton(c);
        b.applyStyle(STYLE_SECONDARY);
        b.setText("");
        b.setContentDescription(contentDescription);
        Drawable d = ContextCompat.getDrawable(c, drawableRes);
        if (d != null) {
            d = DrawableCompat.wrap(d.mutate());
            DrawableCompat.setTint(d, CockpitTokens.color(c, R.color.ck_text_high));
            b.setCompoundDrawablesRelativeWithIntrinsicBounds(d, null, null, null);
        }
        b.setMinWidth(CockpitTokens.dim(c, R.dimen.ck_touch_primary));
        return b;
    }

    public void applyStyle(int style) {
        buttonStyle = style;
        Context c = getContext();
        if (style == STYLE_PRIMARY) {
            setBackground(CockpitSurfaces.stateFill(c,
                    CockpitTokens.color(c, R.color.ck_accent),
                    CockpitTokens.color(c, R.color.ck_accent_active),
                    CockpitTokens.color(c, R.color.ck_surface_2), 16f));
            setTextColor(CockpitTokens.color(c, R.color.ck_on_accent));
            setTypeface(CockpitTokens.inter(c, CockpitTokens.WEIGHT_BOLD));
            setMinHeight(CockpitTokens.dim(c, R.dimen.ck_touch_primary));
        } else if (style == STYLE_HAZARD) {
            setBackground(CockpitSurfaces.stateFill(c,
                    CockpitTokens.color(c, R.color.ck_critical),
                    CockpitSurfaces.alpha(CockpitTokens.color(c, R.color.ck_critical), 0.82f),
                    CockpitTokens.color(c, R.color.ck_surface_2), 16f));
            setTextColor(0xFFFFFFFF);
            setTypeface(CockpitTokens.inter(c, CockpitTokens.WEIGHT_BOLD));
        } else {
            setBackground(CockpitSurfaces.stateOutlined(c, 16f));
            setTextColor(new ColorStateList(
                    new int[][]{
                            new int[]{-android.R.attr.state_enabled},
                            new int[]{}},
                    new int[]{
                            CockpitTokens.color(c, R.color.ck_text_muted),
                            CockpitTokens.color(c, R.color.ck_text_high)}));
            setTypeface(CockpitTokens.inter(c, CockpitTokens.WEIGHT_SEMIBOLD));
        }
    }

    public void setTextStylePrimary(boolean primary) {
        applyStyle(primary ? STYLE_PRIMARY : STYLE_SECONDARY);
    }

    public int getButtonStyle() {
        return buttonStyle;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1f : 0.55f);
    }

    public void setStartIcon(int drawableRes, int tintColor) {
        Drawable d = ContextCompat.getDrawable(getContext(), drawableRes);
        if (d != null) {
            d = DrawableCompat.wrap(d.mutate());
            DrawableCompat.setTint(d, tintColor);
            DrawableCompat.setTintMode(d, PorterDuff.Mode.SRC_IN);
            setCompoundDrawablesRelativeWithIntrinsicBounds(d, null, null, null);
            setCompoundDrawablePadding(CockpitTokens.dim(getContext(), R.dimen.ck_space_sm));
        }
    }
}
