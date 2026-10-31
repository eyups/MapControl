package com.mapcontrol.ui.cockpit;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import com.mapcontrol.R;

/** 8-column viewport grid: add(view, span) wraps rows at 8 columns. */
public class CockpitGrid extends LinearLayout {

    public static final int COLUMNS = 8;

    private final int gutter;
    private LinearLayout currentRow;
    private int used;
    private boolean fillHeight;

    public CockpitGrid(Context context) {
        super(context);
        setOrientation(VERTICAL);
        gutter = CockpitTokens.dim(context, R.dimen.ck_gutter);
    }

    public void setFillHeight(boolean fillHeight) {
        this.fillHeight = fillHeight;
    }

    public void add(View view, int span) {
        if (view == null) {
            return;
        }
        span = Math.max(1, Math.min(COLUMNS, span));
        if (currentRow == null || used + span > COLUMNS) {
            newRow();
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                fillHeight ? LayoutParams.MATCH_PARENT : LayoutParams.WRAP_CONTENT, span);
        lp.rightMargin = gutter;
        currentRow.addView(view, lp);
        used += span;
    }

    /** Full-width row without weight measurement (nested layouts with text). */
    public void addFullWidth(View view) {
        newRow();
        currentRow.addView(view, new LayoutParams(LayoutParams.MATCH_PARENT,
                fillHeight ? LayoutParams.MATCH_PARENT : LayoutParams.WRAP_CONTENT));
        used = COLUMNS;
    }

    public void addRow(View view) {
        newRow();
        currentRow.addView(view, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, COLUMNS));
        used = COLUMNS;
    }

    public void setRowWeights(float... weights) {
        if (weights == null) return;
        for (int i = 0; i < getChildCount() && i < weights.length; i++) {
            View row = getChildAt(i);
            LayoutParams lp = (LayoutParams) row.getLayoutParams();
            lp.weight = weights[i];
            row.setLayoutParams(lp);
        }
    }

    public void finish() {
        if (fillHeight && currentRow != null) {
            LayoutParams lp = (LayoutParams) currentRow.getLayoutParams();
            lp.bottomMargin = 0;
            currentRow.setLayoutParams(lp);
        }
        currentRow = null;
        used = 0;
    }

    private void newRow() {
        currentRow = new LinearLayout(getContext());
        currentRow.setOrientation(HORIZONTAL);
        currentRow.setBaselineAligned(false);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT,
                fillHeight ? 0 : LayoutParams.WRAP_CONTENT);
        if (fillHeight) {
            lp.weight = 1f;
        }
        lp.bottomMargin = gutter;
        addView(currentRow, lp);
        used = 0;
    }

    /** Column width in px for the current viewport (informational). */
    public static int columnWidthPx(Context context, int viewportWidthPx) {
        int gutter = CockpitTokens.dim(context, R.dimen.ck_gutter);
        int usable = viewportWidthPx - gutter * (COLUMNS - 1);
        return Math.max(1, usable / COLUMNS);
    }
}
