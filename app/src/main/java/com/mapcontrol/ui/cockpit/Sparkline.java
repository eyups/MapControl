package com.mapcontrol.ui.cockpit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import com.mapcontrol.R;

/** Lightweight path sparkline; no allocation in onDraw. */
public class Sparkline extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path fillPath = new Path();
    private float[] data = new float[0];

    public Sparkline(Context context) {
        super(context);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(CockpitTokens.dp(context, 1.5f));
        paint.setColor(CockpitTokens.color(context, R.color.ck_accent));
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(CockpitSurfaces.alpha(
                CockpitTokens.color(context, R.color.ck_accent), 0.12f));
    }

    public void setData(float[] values) {
        data = values != null ? values : new float[0];
        rebuild();
        invalidate();
    }

    public void setColor(int color) {
        paint.setColor(color);
        fillPaint.setColor(CockpitSurfaces.alpha(color, 0.12f));
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuild();
    }

    private void rebuild() {
        path.reset();
        fillPath.reset();
        int w = getWidth();
        int h = getHeight();
        if (data.length < 2 || w <= 0 || h <= 0) {
            return;
        }
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (float v : data) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        float range = max - min;
        if (range <= 0f) {
            range = 1f;
        }
        float pad = CockpitTokens.dp(getContext(), 2);
        float usableH = h - 2 * pad;
        float stepX = (float) w / (data.length - 1);
        for (int i = 0; i < data.length; i++) {
            float x = i * stepX;
            float y = pad + usableH * (1f - (data[i] - min) / range);
            if (i == 0) {
                path.moveTo(x, y);
                fillPath.moveTo(x, h);
                fillPath.lineTo(x, y);
            } else {
                path.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
        }
        fillPath.lineTo(w, h);
        fillPath.close();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (data.length < 2) {
            return;
        }
        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(path, paint);
    }
}
