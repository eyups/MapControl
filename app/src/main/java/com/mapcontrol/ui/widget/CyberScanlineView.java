package com.mapcontrol.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Pointer-transparent CRT scanlines used by the Cyber skin. */
public final class CyberScanlineView extends View {

    private final Paint paint = new Paint();
    private final float step;

    public CyberScanlineView(Context context) {
        super(context);
        step = Math.max(2f, 3f * context.getResources().getDisplayMetrics().density);
        paint.setColor(0x0C33FF00);
        paint.setStrokeWidth(1f);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (float y = 0f; y < getHeight(); y += step) {
            canvas.drawLine(0f, y, getWidth(), y, paint);
        }
    }
}
