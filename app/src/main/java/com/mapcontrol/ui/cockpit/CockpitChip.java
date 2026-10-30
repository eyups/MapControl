package com.mapcontrol.ui.cockpit;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import androidx.appcompat.widget.AppCompatTextView;

import com.mapcontrol.R;

/** 48dp filter / sub-tab chip. */
public class CockpitChip extends AppCompatTextView {

    private boolean selectedState;

    public CockpitChip(Context context, CharSequence text) {
        super(context);
        setText(text);
        setGravity(Gravity.CENTER);
        setSingleLine(true);
        setMinHeight(CockpitTokens.dim(context, R.dimen.ck_chip_h));
        int h = CockpitTokens.dim(context, R.dimen.ck_space_md);
        setPadding(h, 0, h, 0);
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_MEDIUM));
        setClickable(true);
        setFocusable(true);
        applyState();
    }

    public void setSelectedState(boolean selected) {
        selectedState = selected;
        applyState();
    }

    public boolean isSelectedState() {
        return selectedState;
    }

    private void applyState() {
        Context c = getContext();
        if (selectedState) {
            setBackground(CockpitSurfaces.outlined(c,
                    CockpitTokens.color(c, R.color.ck_surface_2),
                    CockpitTokens.color(c, R.color.ck_accent), 24f));
            setTextColor(CockpitTokens.color(c, R.color.ck_text_high));
            setTypeface(CockpitTokens.inter(c, CockpitTokens.WEIGHT_MEDIUM));
        } else {
            setBackground(CockpitSurfaces.outlined(c,
                    CockpitTokens.color(c, R.color.ck_surface_1),
                    CockpitTokens.color(c, R.color.ck_stroke), 24f));
            setTextColor(CockpitTokens.color(c, R.color.ck_text_medium));
            setTypeface(CockpitTokens.inter(c, CockpitTokens.WEIGHT_MEDIUM));
        }
    }
}
