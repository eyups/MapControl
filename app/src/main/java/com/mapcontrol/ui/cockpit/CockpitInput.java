package com.mapcontrol.ui.cockpit;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;

import androidx.appcompat.widget.AppCompatEditText;

import com.mapcontrol.R;

/** 64dp recessed input with accent focus stroke. */
public class CockpitInput extends AppCompatEditText {

    public CockpitInput(Context context) {
        super(context);
        setSingleLine(true);
        setGravity(Gravity.CENTER_VERTICAL);
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 17);
        setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_REGULAR));
        setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        setHintTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        setInputType(InputType.TYPE_CLASS_TEXT);
        setMinHeight(CockpitTokens.dim(context, R.dimen.ck_input_h));
        int h = CockpitTokens.dim(context, R.dimen.ck_space_md);
        setPadding(h, 0, h, 0);
        setBackground(background(false));
        setOnFocusChangeListener((v, focused) -> setBackground(background(focused)));
    }

    private GradientDrawable background(boolean focused) {
        Context c = getContext();
        GradientDrawable d = CockpitSurfaces.rounded(c,
                CockpitTokens.color(c, R.color.ck_bg_recessed), 12f);
        d.setStroke(CockpitTokens.dp(c, focused ? 2 : 1),
                CockpitTokens.color(c, focused ? R.color.ck_accent : R.color.ck_stroke));
        return d;
    }
}
