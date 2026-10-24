package com.mapcontrol.driving;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/** Compact score bars used inside the weekly/monthly trend blocks. */
public final class DrivingTrendBarsView extends View {

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int[] scores = new int[0];
    private int accentColor = 0xFF3568A0;
    private int trackColor = 0xFFE1E7EF;
    private int labelColor = 0xFF829394;
    private boolean weekly = true;
    private Typeface typeface;

    public DrivingTrendBarsView(Context context) {
        this(context, null);
    }

    public DrivingTrendBarsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        barPaint.setStyle(Paint.Style.FILL);
        trackPaint.setStyle(Paint.Style.FILL);
        labelPaint.setTextSize(sp(10f));
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void configure(int accent, int track, int label, boolean weekly, Typeface typeface) {
        this.accentColor = accent;
        this.trackColor = track;
        this.labelColor = label;
        this.weekly = weekly;
        this.typeface = typeface;
        barPaint.setColor(accent);
        trackPaint.setColor(track);
        labelPaint.setColor(label);
        labelPaint.setTypeface(typeface);
        invalidate();
    }

    public void setData(int[] values) {
        scores = values != null ? values.clone() : new int[0];
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0 || scores.length == 0) {
            return;
        }
        float left = dp(2f);
        float right = width - dp(2f);
        float top = dp(4f);
        float bottom = height - dp(14f);
        float chartW = Math.max(1f, right - left);
        float chartH = Math.max(1f, bottom - top);
        float gap = dp(3f);
        float barW = Math.max(3f, (chartW - gap * (scores.length - 1)) / scores.length);
        for (int i = 0; i < scores.length; i++) {
            float x = left + i * (barW + gap);
            RectF track = new RectF(x, top, x + barW, bottom);
            canvas.drawRoundRect(track, dp(3f), dp(3f), trackPaint);
            int score = scores[i];
            if (score > 0) {
                float h = chartH * Math.max(0.08f, Math.min(1f, score / 100f));
                RectF bar = new RectF(x, bottom - h, x + barW, bottom);
                barPaint.setColor(accentColor);
                canvas.drawRoundRect(bar, dp(3f), dp(3f), barPaint);
            }
            String label = weekly ? dayLabel(i) : "H" + (i + 1);
            canvas.drawText(label, x + barW / 2f, height - dp(2f), labelPaint);
        }
    }

    private String dayLabel(int index) {
        String[] labels = {"PZT", "SAL", "ÇAR", "PER", "CUM", "CMT", "PAZ"};
        return labels[Math.max(0, Math.min(labels.length - 1, index))];
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
