package com.eyups.roaddeck.assistant.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import com.eyups.roaddeck.R;
import com.eyups.roaddeck.assistant.core.VoiceAssistantManager;
import com.eyups.roaddeck.ui.cockpit.CockpitTokens;

/**
 * Küçük canlı giriş tayfı. Yalnızca asistan dinlerken/kaydederken kendini
 * yeniler; boştayken hiçbir çizim döngüsü çalışmaz (sürekli invalidate yok).
 */
public final class AssistantWaveformView extends View {

    private static final int BARS = 22;
    private static final long FRAME_MS = 50L;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] levels = new float[BARS];
    private final Runnable tick = this::onTick;
    private boolean active;
    private int cursor;

    public AssistantWaveformView(Context context) {
        super(context);
        barPaint.setColor(CockpitTokens.color(context, R.color.ck_accent));
        barPaint.setStrokeCap(Paint.Cap.ROUND);
        setMinimumHeight(CockpitTokens.dp(context, 48));
    }

    public void setActive(boolean value) {
        if (active == value) {
            return;
        }
        active = value;
        if (active) {
            postOnAnimation(tick);
        } else {
            removeCallbacks(tick);
            java.util.Arrays.fill(levels, 0f);
            invalidate();
        }
    }

    private void onTick() {
        if (!active) {
            return;
        }
        VoiceAssistantManager manager = VoiceAssistantManager.peek();
        float level = manager == null ? 0f : manager.level();
        levels[cursor] = Math.max(0.04f, Math.min(1f, level));
        cursor = (cursor + 1) % BARS;
        invalidate();
        postDelayed(tick, FRAME_MS);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        barPaint.setAlpha(active ? 255 : 80);
        float gap = width / (float) BARS;
        float stroke = Math.max(2f, gap * 0.45f);
        barPaint.setStrokeWidth(stroke);
        float center = height / 2f;
        for (int i = 0; i < BARS; i++) {
            int index = (cursor + i) % BARS;
            float amplitude = levels[index];
            float half = Math.max(stroke, amplitude * (height * 0.46f));
            float x = i * gap + gap / 2f;
            canvas.drawLine(x, center - half, x, center + half, barPaint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }
}
