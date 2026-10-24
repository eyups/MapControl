package com.mapcontrol.driving;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Small bar chart showing time spent in six speed bands. */
public final class DrivingSpeedDistributionView extends View {

    private static final int CYAN = 0xFF35D8EA;
    private static final int CYAN_DIM = 0xFF1B6B75;
    private static final int GRID = 0xFF263233;
    private static final int LABEL = 0xFF829394;
    private static final String[] BANDS = {"0-20", "20-40", "40-60", "60-80", "80-100", "100+"};

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Integer> speeds = new ArrayList<>();
    private final int[] counts = new int[6];
    private Typeface typeface;

    public DrivingSpeedDistributionView(Context context) {
        this(context, null);
    }

    public DrivingSpeedDistributionView(Context context, AttributeSet attrs) {
        super(context, attrs);
        trackPaint.setStyle(Paint.Style.FILL);
        trackPaint.setColor(0xFF111919);
        barPaint.setStyle(Paint.Style.FILL);
        labelPaint.setColor(LABEL);
        labelPaint.setTextSize(sp(10f));
        valuePaint.setColor(0xFFD5DBDB);
        valuePaint.setTextSize(sp(10f));
        valuePaint.setFakeBoldText(true);
    }

    public void setTypeface(Typeface typeface) {
        this.typeface = typeface;
        labelPaint.setTypeface(typeface);
        valuePaint.setTypeface(typeface);
        invalidate();
    }

    public void setData(List<Integer> samples) {
        speeds.clear();
        if (samples != null) {
            speeds.addAll(samples);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        java.util.Arrays.fill(counts, 0);
        for (Integer sample : speeds) {
            int speed = sample == null ? 0 : Math.max(0, sample);
            int index;
            if (speed < 20) {
                index = 0;
            } else if (speed < 40) {
                index = 1;
            } else if (speed < 60) {
                index = 2;
            } else if (speed < 80) {
                index = 3;
            } else if (speed < 100) {
                index = 4;
            } else {
                index = 5;
            }
            counts[index]++;
        }
        int total = Math.max(1, speeds.size());
        int max = 1;
        for (int count : counts) {
            max = Math.max(max, count);
        }

        float left = dp(6f);
        float right = width - dp(6f);
        float top = dp(26f);
        float bottom = height - dp(30f);
        float chartW = Math.max(1f, right - left);
        float chartH = Math.max(1f, bottom - top);
        labelPaint.setTypeface(typeface);
        valuePaint.setTypeface(typeface);

        canvas.drawLine(left, bottom, right, bottom, gridPaint());
        float gap = dp(6f);
        float barW = Math.max(4f, (chartW - gap * 5f) / 6f);
        for (int i = 0; i < 6; i++) {
            float x = left + i * (barW + gap);
            float h = chartH * counts[i] / (float) max;
            RectF track = new RectF(x, top, x + barW, bottom);
            RectF bar = new RectF(x, bottom - h, x + barW, bottom);
            canvas.drawRoundRect(track, dp(4f), dp(4f), trackPaint);
            barPaint.setShader(new LinearGradient(0f, top, 0f, bottom,
                    i < 2 ? new int[]{CYAN_DIM, 0x4435D8EA} : new int[]{CYAN, 0x5535D8EA},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(bar, dp(4f), dp(4f), barPaint);
            int percent = Math.round(counts[i] * 100f / total);
            canvas.drawText(String.format(Locale.US, "%d%%", percent),
                    x + barW / 2f, Math.max(top + dp(10f), bottom - h - dp(5f)), valuePaint);
            labelPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(BANDS[i], x + barW / 2f, height - dp(8f), labelPaint);
        }
        labelPaint.setTextAlign(Paint.Align.LEFT);
    }

    private Paint gridPaint() {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(GRID);
        paint.setStrokeWidth(dp(1f));
        return paint;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
