package com.mapcontrol.driving;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

/** Four horizontal bars explaining where the driving score was lost. */
public final class DrivingPenaltyBarsView extends View {

    private static final String[] LABELS = {"FREN", "GAZ", "TÜKETİM", "HIZ"};
    private static final int[] COLORS = {0xFFFF3B4D, 0xFFFF8A3D, 0xFFFFB000, 0xFF35D8EA};
    private static final int LABEL = 0xFF829394;
    private static final int TRACK = 0xFF111919;

    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] values = new int[4];
    private Typeface typeface;

    public DrivingPenaltyBarsView(Context context) {
        this(context, null);
    }

    public DrivingPenaltyBarsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        labelPaint.setColor(LABEL);
        labelPaint.setTextSize(sp(10f));
        valuePaint.setColor(0xFFD5DBDB);
        valuePaint.setTextSize(sp(11f));
        valuePaint.setFakeBoldText(true);
        trackPaint.setColor(TRACK);
        trackPaint.setStyle(Paint.Style.FILL);
        barPaint.setStyle(Paint.Style.FILL);
    }

    public void setTypeface(Typeface typeface) {
        this.typeface = typeface;
        labelPaint.setTypeface(typeface);
        valuePaint.setTypeface(typeface);
        invalidate();
    }

    public void setData(int brake, int accel, int consumption, int speed) {
        values[0] = Math.max(0, brake);
        values[1] = Math.max(0, accel);
        values[2] = Math.max(0, consumption);
        values[3] = Math.max(0, speed);
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
        int max = 10;
        for (int value : values) {
            max = Math.max(max, value);
        }
        float left = dp(6f);
        float right = width - dp(8f);
        float top = dp(10f);
        float bottom = height - dp(8f);
        float rowH = Math.max(1f, (bottom - top) / 4f);
        float labelW = dp(54f);
        float valueW = dp(34f);
        float barLeft = left + labelW;
        float barRight = right - valueW;
        labelPaint.setTypeface(typeface);
        valuePaint.setTypeface(typeface);

        for (int i = 0; i < 4; i++) {
            float centerY = top + rowH * i + rowH / 2f;
            canvas.drawText(LABELS[i], left, centerY + dp(4f), labelPaint);
            RectF track = new RectF(barLeft, centerY - dp(5f), barRight, centerY + dp(5f));
            canvas.drawRoundRect(track, dp(4f), dp(4f), trackPaint);
            float ratio = Math.min(1f, values[i] / (float) max);
            RectF bar = new RectF(barLeft, centerY - dp(5f),
                    barLeft + (barRight - barLeft) * ratio, centerY + dp(5f));
            barPaint.setColor(COLORS[i]);
            canvas.drawRoundRect(bar, dp(4f), dp(4f), barPaint);
            String value = values[i] > 0 ? String.format(Locale.US, "-%d", values[i]) : "0";
            valuePaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(value, right, centerY + dp(4f), valuePaint);
            valuePaint.setTextAlign(Paint.Align.LEFT);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
