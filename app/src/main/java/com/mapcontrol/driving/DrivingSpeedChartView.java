package com.mapcontrol.driving;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Cyan filled speed trace with a red target line, styled like the reference report. */
public final class DrivingSpeedChartView extends View {

    private static final int CYAN = 0xFF35D8EA;
    private static final int GRID = 0xFF263233;
    private static final int LABEL = 0xFF829394;
    private static final int RED = 0xFFFF3B4D;
    private static final int TARGET_KMH = 90;

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint areaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint targetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eventPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eventRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eventGuidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Integer> speeds = new ArrayList<>();
    private final List<DrivingEvent> events = new ArrayList<>();
    private int durationSec;
    private Typeface typeface;

    public DrivingSpeedChartView(Context context) {
        this(context, null);
    }

    public DrivingSpeedChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(1f));
        gridPaint.setColor(GRID);

        labelPaint.setColor(LABEL);
        labelPaint.setTextSize(sp(12f));

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(2f));
        linePaint.setColor(CYAN);

        targetPaint.setStyle(Paint.Style.STROKE);
        targetPaint.setStrokeWidth(dp(1.5f));
        targetPaint.setColor(RED);
        targetPaint.setPathEffect(new DashPathEffect(new float[]{dp(8f), dp(6f)}, 0f));

        eventPaint.setStyle(Paint.Style.FILL);
        eventPaint.setColor(RED);
        eventRingPaint.setStyle(Paint.Style.STROKE);
        eventRingPaint.setStrokeWidth(dp(2f));
        eventRingPaint.setColor(0xFFFFD8DC);
        eventGuidePaint.setStyle(Paint.Style.STROKE);
        eventGuidePaint.setStrokeWidth(dp(1f));
        eventGuidePaint.setColor(0x66FF3B4D);
        eventGuidePaint.setPathEffect(new DashPathEffect(new float[]{dp(4f), dp(6f)}, 0f));
    }

    public void setTypeface(Typeface typeface) {
        this.typeface = typeface;
        labelPaint.setTypeface(typeface);
        invalidate();
    }

    public void setData(List<Integer> samples, int durationSec) {
        setData(samples, durationSec, null);
    }

    public void setData(List<Integer> samples, int durationSec, List<DrivingEvent> tripEvents) {
        speeds.clear();
        if (samples != null) {
            speeds.addAll(samples);
        }
        events.clear();
        if (tripEvents != null) {
            events.addAll(tripEvents);
        }
        this.durationSec = Math.max(1, durationSec);
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
        float left = dp(46f);
        float right = width - dp(12f);
        float top = dp(12f);
        float bottom = height - dp(28f);
        float chartW = Math.max(1f, right - left);
        float chartH = Math.max(1f, bottom - top);

        int maxSpeed = 60;
        for (Integer speed : speeds) {
            if (speed != null) {
                maxSpeed = Math.max(maxSpeed, speed);
            }
        }
        int maxY = Math.max(100, Math.min(180, maxSpeed + 15));
        maxY = Math.max(maxY, TARGET_KMH + 10);

        labelPaint.setTypeface(typeface);
        for (int i = 0; i <= 5; i++) {
            float y = bottom - chartH * i / 5f;
            canvas.drawLine(left, y, right, y, gridPaint);
            int value = Math.round(maxY * i / 5f);
            canvas.drawText(String.valueOf(value), dp(4f), y + dp(4f), labelPaint);
        }

        if (speeds.size() > 1) {
            Path line = new Path();
            Path area = new Path();
            area.moveTo(left, bottom);
            for (int i = 0; i < speeds.size(); i++) {
                float x = left + chartW * i / (speeds.size() - 1f);
                float value = Math.max(0, Math.min(maxY, speeds.get(i)));
                float y = bottom - chartH * value / maxY;
                if (i == 0) {
                    line.moveTo(x, y);
                } else {
                    line.lineTo(x, y);
                }
                area.lineTo(x, y);
            }
            area.lineTo(right, bottom);
            area.close();

            areaPaint.setShader(new LinearGradient(0f, top, 0f, bottom,
                    new int[]{0x9935D8EA, 0x4435D8EA, 0x0A35D8EA},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            areaPaint.setStyle(Paint.Style.FILL);
            canvas.drawPath(area, areaPaint);
            canvas.drawPath(line, linePaint);
        }

        float targetY = bottom - chartH * TARGET_KMH / maxY;
        canvas.drawLine(left, targetY, right, targetY, targetPaint);

        float durationMs = Math.max(1000f, durationSec * 1000f);
        for (DrivingEvent event : events) {
            if (event == null) {
                continue;
            }
            float timeRatio = Math.max(0f, Math.min(1f, event.elapsedMs / durationMs));
            float eventX = left + chartW * timeRatio;
            float speedValue = Math.max(0, Math.min(maxY, event.speedKmh));
            float eventY = bottom - chartH * speedValue / maxY;
            canvas.drawLine(eventX, top, eventX, bottom, eventGuidePaint);
            canvas.drawCircle(eventX, eventY, dp(6f), eventPaint);
            canvas.drawCircle(eventX, eventY, dp(6f), eventRingPaint);
        }
        labelPaint.setColor(RED);
        canvas.drawText(String.format(Locale.getDefault(), "%d km/h", TARGET_KMH),
                right - dp(78f), Math.max(top + dp(11f), targetY - dp(6f)), labelPaint);
        labelPaint.setColor(LABEL);

        canvas.drawText("0", left, height - dp(7f), labelPaint);
        String middle = String.format(Locale.getDefault(), "%d dk", durationSec / 2 / 60);
        canvas.drawText(middle, left + chartW / 2f, height - dp(7f), labelPaint);
        String end = String.format(Locale.getDefault(), "%d dk", durationSec / 60);
        labelPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(end, right, height - dp(7f), labelPaint);
        labelPaint.setTextAlign(Paint.Align.LEFT);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
