package com.mapcontrol.driving;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Glowing amber score gauge used by the drive report. */
public final class DrivingScoreRingView extends View {

    private static final int AMBER = 0xFFFFB000;
    private static final int TRACK = 0xFF2A2417;
    private static final int BACKGROUND = 0xFF0A0B0B;
    private static final int TEXT = 0xFFF7F4EA;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();
    private final Path marker = new Path();
    private ValueAnimator animator;
    private int score;
    private float animatedScore;
    private Typeface typeface;

    public DrivingScoreRingView(Context context) {
        this(context, null);
    }

    public DrivingScoreRingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.BUTT);
        trackPaint.setColor(TRACK);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.BUTT);
        arcPaint.setColor(AMBER);
        arcPaint.setShadowLayer(28f, 0f, 0f, 0xB3FFB000);

        textPaint.setColor(TEXT);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        labelPaint.setColor(0xFF8A8B80);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(sp(11f));
        labelPaint.setLetterSpacing(0.18f);

        markerPaint.setColor(AMBER);
        markerPaint.setStyle(Paint.Style.STROKE);
        markerPaint.setStrokeWidth(dp(2f));

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(BACKGROUND);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setTypeface(Typeface typeface) {
        this.typeface = typeface;
        textPaint.setTypeface(typeface);
        labelPaint.setTypeface(typeface);
        invalidate();
    }

    /** Rapor dışındaki (ör. Kokpit telemetri) kullanımlar için vurgu rengi. */
    public void setAccentColor(int color) {
        arcPaint.setColor(color);
        arcPaint.setShadowLayer(28f, 0f, 0f, (color & 0x00FFFFFF) | 0xB3000000);
        markerPaint.setColor(color);
        invalidate();
    }

    public void setTrackColor(int color) {
        trackPaint.setColor(color);
        invalidate();
    }

    public void setScore(int score, boolean animate) {
        int clamped = Math.max(0, Math.min(100, score));
        if (clamped == this.score && Math.round(animatedScore) == clamped) {
            return;
        }
        this.score = clamped;
        if (animator != null) {
            animator.cancel();
        }
        if (!animate) {
            animatedScore = clamped;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(animatedScore, clamped);
        animator.setDuration(900L);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            animatedScore = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desired = (int) dp(246f);
        int width = resolveSize(desired, widthMeasureSpec);
        int height = resolveSize(desired, heightMeasureSpec);
        int size = Math.min(width, height);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        float stroke = size * 0.075f;
        trackPaint.setStrokeWidth(stroke);
        arcPaint.setStrokeWidth(stroke);
        float inset = stroke / 2f + size * 0.035f;
        arcBounds.set(inset, inset, size - inset, size - inset);
        float centerX = size / 2f;
        float centerY = size / 2f;
        float radius = (size - inset * 2f) / 2f;

        canvas.drawCircle(centerX, centerY, radius - stroke * 0.9f, fillPaint);
        canvas.drawCircle(centerX, centerY, radius, trackPaint);
        canvas.drawArc(arcBounds, -90f, 360f * (animatedScore / 100f), false, arcPaint);

        textPaint.setTypeface(typeface);
        textPaint.setTextSize(size * 0.33f);
        float baseline = centerY + textPaint.getTextSize() * 0.34f;
        canvas.drawText(String.valueOf(Math.round(animatedScore)), centerX, baseline, textPaint);

        float markerSize = size * 0.026f;
        float markerTop = centerY + size * 0.19f;
        marker.reset();
        marker.moveTo(centerX, markerTop);
        marker.lineTo(centerX + markerSize, markerTop + markerSize);
        marker.lineTo(centerX, markerTop + markerSize * 2f);
        marker.lineTo(centerX - markerSize, markerTop + markerSize);
        marker.close();
        canvas.drawPath(marker, markerPaint);

        canvas.drawText("PUAN", centerX, size * 0.82f, labelPaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
        }
        super.onDetachedFromWindow();
    }
}
