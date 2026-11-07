package com.eyups.roaddeck.ui.cockpit;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;

import com.eyups.roaddeck.R;

/** 72x40 physical-switch metaphor. */
public class CockpitToggle extends View {

    public interface OnCheckedChangeListener {
        void onCheckedChanged(CockpitToggle toggle, boolean checked);
    }

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();
    private boolean checked;
    private float thumbProgress;
    private ValueAnimator animator;
    private OnCheckedChangeListener listener;
    private CharSequence label;

    public CockpitToggle(Context context) {
        super(context);
        setClickable(true);
        setFocusable(true);
        setDefaultFocusHighlightEnabled(true);
        updateContentDescription();
    }

    /**
     * TalkBack'in anahtarın neyi kontrol ettiğini söylemesi için görünür satır etiketi.
     * Durum (açık/kapalı) ayrı stateDescription ile verilir.
     */
    public void setLabel(CharSequence value) {
        label = value;
        updateContentDescription();
    }

    private void updateContentDescription() {
        String state = getContext().getString(checked ? R.string.ck_common_on : R.string.ck_common_off);
        boolean hasLabel = label != null && label.length() > 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+ stateDescription ayrı okunur; cd'de durumu tekrarlama.
            setContentDescription(hasLabel ? label : state);
            setStateDescription(state);
        } else {
            setContentDescription(hasLabel ? label + ", " + state : state);
        }
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener l) {
        listener = l;
    }

    public boolean isChecked() {
        return checked;
    }

    /** Programmatic update; does not notify listener. */
    public void setChecked(boolean value) {
        setChecked(value, true);
    }

    public void setChecked(boolean value, boolean animate) {
        if (checked == value) {
            thumbProgress = value ? 1f : 0f;
            return;
        }
        checked = value;
        updateContentDescription();
        if (!animate) {
            thumbProgress = value ? 1f : 0f;
            invalidate();
            return;
        }
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(thumbProgress, value ? 1f : 0f);
        animator.setDuration(130);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            thumbProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = CockpitTokens.dim(getContext(), R.dimen.ck_toggle_w);
        int h = CockpitTokens.dim(getContext(), R.dimen.ck_toggle_h);
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Context c = getContext();
        float pad = CockpitTokens.dp(c, 2);
        // Visual track stays 72x40 per DESIGN; the View itself is a 72x64 touch target.
        float trackH = CockpitTokens.dim(c, R.dimen.ck_toggle_track_h);
        float trackTop = (getHeight() - trackH) / 2f;
        float radius = (trackH - 2 * pad) / 2f;
        trackRect.set(pad, trackTop + pad, getWidth() - pad, trackTop + trackH - pad);
        int off = CockpitTokens.color(c, R.color.ck_surface_2);
        int on = CockpitTokens.color(c, R.color.ck_accent);
        trackPaint.setColor(blend(off, on, thumbProgress));
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint);
        if (thumbProgress < 0.5f) {
            // Off track must keep 3:1 against the card; the thumb alone was not enough.
            trackStroke.setStyle(Paint.Style.STROKE);
            trackStroke.setStrokeWidth(CockpitTokens.dp(c, 1));
            trackStroke.setColor(CockpitTokens.color(c, R.color.ck_text_medium));
            canvas.drawRoundRect(trackRect, radius, radius, trackStroke);
        }

        float thumbR = radius - CockpitTokens.dp(c, 2);
        float leftX = pad + CockpitTokens.dp(c, 2) + thumbR;
        float rightX = getWidth() - pad - CockpitTokens.dp(c, 2) - thumbR;
        float cx = leftX + (rightX - leftX) * thumbProgress;
        thumbPaint.setColor(thumbProgress > 0.1f
                ? CockpitTokens.color(c, R.color.ck_on_critical)
                : CockpitTokens.color(c, R.color.ck_text_medium));
        canvas.drawCircle(cx, getHeight() / 2f, thumbR, thumbPaint);
    }

    private static int blend(int a, int b, float f) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * f);
        int g = Math.round(ag + (bg - ag) * f);
        int bl = Math.round(ab + (bb - ab) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    @Override
    public boolean performClick() {
        toggle();
        return super.performClick();
    }

    private void toggle() {
        setChecked(!checked, true);
        if (listener != null) {
            listener.onCheckedChanged(this, checked);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setCheckable(true);
        info.setChecked(checked);
        info.setClassName("android.widget.Switch");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            info.setStateDescription(getContext().getString(checked ? R.string.ck_common_on : R.string.ck_common_off));
        }
    }
}
