package com.mapcontrol.ui.cockpit;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mapcontrol.R;

import java.util.List;
import java.util.function.IntConsumer;

/** 2-5 option segmented control, 64dp tall, accent selected state. */
public class CockpitSegmented extends LinearLayout {

    private final IntConsumer listener;
    private int selectedIndex;

    public CockpitSegmented(Context context, List<String> labels, int initialIndex,
            IntConsumer listener) {
        super(context);
        this.listener = listener;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        setBackground(CockpitSurfaces.outlined(context,
                CockpitTokens.color(context, R.color.ck_bg_recessed),
                CockpitTokens.color(context, R.color.ck_stroke), 12f));
        int pad = CockpitTokens.dim(context, R.dimen.ck_space_xs);
        setPadding(pad, pad, pad, pad);
        setMinimumHeight(CockpitTokens.dim(context, R.dimen.ck_touch_min));

        selectedIndex = Math.max(0, Math.min(initialIndex, labels.size() - 1));
        for (int i = 0; i < labels.size(); i++) {
            final int index = i;
            TextView segment = new TextView(context);
            segment.setText(labels.get(i));
            segment.setGravity(Gravity.CENTER);
            segment.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
            segment.setMinHeight(CockpitTokens.dim(context, R.dimen.ck_touch_min) - 2 * pad);
            segment.setClickable(true);
            segment.setFocusable(true);
            segment.setOnClickListener(v -> select(index));
            addView(segment, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        }
        refresh();
        setOnClickListener(v -> {
            int next = (selectedIndex + 1) % getChildCount();
            select(next);
        });
    }

    public void select(int index) {
        if (index < 0 || index >= getChildCount() || index == selectedIndex) {
            refresh();
            return;
        }
        selectedIndex = index;
        refresh();
        if (listener != null) {
            listener.accept(index);
        }
    }

    /** Visual sync without callback. */
    public void syncSelection(int index) {
        if (index < 0 || index >= getChildCount()) {
            return;
        }
        selectedIndex = index;
        refresh();
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    private void refresh() {
        Context c = getContext();
        for (int i = 0; i < getChildCount(); i++) {
            TextView seg = (TextView) getChildAt(i);
            if (i == selectedIndex) {
                seg.setBackground(CockpitSurfaces.rounded(c,
                        CockpitTokens.color(c, R.color.ck_accent), 10f));
                seg.setTextColor(CockpitTokens.color(c, R.color.ck_on_accent));
                seg.setTypeface(CockpitTokens.inter(c, CockpitTokens.WEIGHT_SEMIBOLD));
            } else {
                seg.setBackground(null);
                seg.setTextColor(CockpitTokens.color(c, R.color.ck_text_medium));
                seg.setTypeface(CockpitTokens.inter(c, CockpitTokens.WEIGHT_SEMIBOLD));
            }
        }
    }
}
