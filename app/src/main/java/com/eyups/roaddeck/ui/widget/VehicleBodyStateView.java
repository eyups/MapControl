package com.eyups.roaddeck.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;

import com.eyups.roaddeck.R;
import com.eyups.roaddeck.vehicle.VehicleMetricsSnapshot;

/**
 * Kapı/bagaj/kaput/cam tavan durumunu gösteren hafif 2D üst görünüm.
 *
 * Gerçek araç GLB'si doğru araçla değiştirilene kadar yanlış model göstermemek için
 * model bağımsız şema kullanılır. Veri kaynağı VehicleMetricsSnapshot'tır.
 */
public final class VehicleBodyStateView extends View {

    private static final long PULSE_MS = 900L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF car = new RectF();
    private final RectF roof = new RectF();
    private final RectF hood = new RectF();
    private final RectF trunk = new RectF();
    private final RectF[] doors = {new RectF(), new RectF(), new RectF(), new RectF()};
    private final RectF[] windows = {new RectF(), new RectF(), new RectF(), new RectF()};
    private final RectF sunroof = new RectF();
    private final RectF sunshade = new RectF();

    private boolean hasDoorLf;
    private boolean hasDoorRf;
    private boolean hasDoorLr;
    private boolean hasDoorRr;
    private boolean hasTrunk;
    private boolean hasHood;
    private boolean hasSunroof;
    private boolean hasSunshade;
    private boolean hasWindowFl;
    private boolean hasWindowFr;
    private boolean hasWindowRl;
    private boolean hasWindowRr;

    private boolean doorLfOpen;
    private boolean doorRfOpen;
    private boolean doorLrOpen;
    private boolean doorRrOpen;
    private boolean trunkOpen;
    private boolean hoodOpen;
    private boolean sunroofOpen;
    private boolean sunshadeOpen;
    private boolean windowFlOpen;
    private boolean windowFrOpen;
    private boolean windowRlOpen;
    private boolean windowRrOpen;
    private boolean movingWithOpenBody;

    private ValueAnimator pulseAnimator;
    private float pulse = 1f;

    public VehicleBodyStateView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        paint.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1.2f));
    }

    public void update(@NonNull VehicleMetricsSnapshot snapshot) {
        hasDoorLf = snapshot.hasDoorLf();
        hasDoorRf = snapshot.hasDoorRf();
        hasDoorLr = snapshot.hasDoorLr();
        hasDoorRr = snapshot.hasDoorRr();
        hasTrunk = snapshot.hasTrunk();
        hasHood = snapshot.hasHood();
        hasWindowFl = snapshot.hasWindowFl();
        hasWindowFr = snapshot.hasWindowFr();
        hasWindowRl = snapshot.hasWindowRl();
        hasWindowRr = snapshot.hasWindowRr();

        doorLfOpen = hasDoorLf && snapshot.isDoorLfOpen();
        doorRfOpen = hasDoorRf && snapshot.isDoorRfOpen();
        doorLrOpen = hasDoorLr && snapshot.isDoorLrOpen();
        doorRrOpen = hasDoorRr && snapshot.isDoorRrOpen();
        trunkOpen = hasTrunk && snapshot.isTrunkOpen();
        hoodOpen = hasHood && snapshot.isHoodOpen();
        windowFlOpen = hasWindowFl && snapshot.isWindowFlOpen();
        windowFrOpen = hasWindowFr && snapshot.isWindowFrOpen();
        windowRlOpen = hasWindowRl && snapshot.isWindowRlOpen();
        windowRrOpen = hasWindowRr && snapshot.isWindowRrOpen();

        hasSunroof = snapshot.hasSunroof();
        sunroofOpen = hasSunroof && snapshot.isSunroofOpen();
        hasSunshade = snapshot.isValid(snapshot.sunshade);
        sunshadeOpen = hasSunshade && snapshot.isSunshadeOpen();
        movingWithOpenBody = snapshot.preferredSpeed() > 0 && hasOpenBody();

        updatePulse();
        invalidate();
    }

    public boolean hasOpenBody() {
        return doorLfOpen || doorRfOpen || doorLrOpen || doorRrOpen || trunkOpen || hoodOpen
                || windowFlOpen || windowFrOpen || windowRlOpen || windowRrOpen;
    }

    public boolean isMovingWithOpenBody() {
        return movingWithOpenBody;
    }

    private void updatePulse() {
        if (hasOpenBody() && isAttachedToWindow()) {
            if (pulseAnimator == null) {
                pulseAnimator = ValueAnimator.ofFloat(0.55f, 1f);
                pulseAnimator.setDuration(PULSE_MS);
                pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
                pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
                pulseAnimator.setInterpolator(new LinearInterpolator());
                pulseAnimator.addUpdateListener(a -> {
                    pulse = (float) a.getAnimatedValue();
                    invalidate();
                });
            }
            if (!pulseAnimator.isStarted()) {
                pulseAnimator.start();
            }
        } else if (pulseAnimator != null && pulseAnimator.isStarted()) {
            pulseAnimator.cancel();
            pulse = 1f;
            invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updatePulse();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        float pad = dp(10);
        float availableW = w - pad * 2;
        float availableH = h - pad * 2;
        float carW = Math.min(availableW,
                Math.max(1f, availableH - dp(40)) / 0.46f);
        float carH = carW * 0.46f;
        float left = (w - carW) / 2f;
        float top = (h - carH) / 2f;
        car.set(left, top, left + carW, top + carH);

        int accent = color(R.color.ck_accent);
        int muted = color(R.color.ck_text_muted);
        int surface = color(R.color.ck_surface_2);
        int warning = WARNING_COLOR;
        int closed = withAlpha(muted, 0.78f);

        // Tekerlekler — gövdenin hemen dışında.
        paint.setColor(withAlpha(muted, 0.55f));
        float wheelW = car.width() * 0.115f;
        float wheelH = car.height() * 0.18f;
        for (int i = 0; i < 2; i++) {
            float x = car.left + car.width() * (i == 0 ? 0.18f : 0.72f);
            canvas.drawRoundRect(x, car.top - wheelH * 0.55f,
                    x + wheelW, car.top + wheelH * 0.25f, wheelW * 0.25f, wheelW * 0.25f, paint);
            canvas.drawRoundRect(x, car.bottom - wheelH * 0.25f,
                    x + wheelW, car.bottom + wheelH * 0.55f, wheelW * 0.25f, wheelW * 0.25f, paint);
        }

        // Gövde.
        paint.setColor(surface);
        canvas.drawRoundRect(car, carH * 0.24f, carH * 0.24f, paint);
        stroke.setColor(withAlpha(accent, 0.65f));
        canvas.drawRoundRect(car, carH * 0.24f, carH * 0.24f, stroke);

        // Yan aynalar — ön kapı hizasında, gövdenin üst/alt dışında.
        float mirrorW = car.width() * 0.055f;
        float mirrorH = car.height() * 0.075f;
        float mirrorX = car.left + car.width() * 0.64f;
        float topMirrorY = car.top - mirrorH;
        float bottomMirrorY = car.bottom;
        paint.setColor(surface);
        canvas.drawRoundRect(mirrorX, topMirrorY, mirrorX + mirrorW, topMirrorY + mirrorH,
                mirrorH * 0.35f, mirrorH * 0.35f, paint);
        canvas.drawRoundRect(mirrorX, bottomMirrorY, mirrorX + mirrorW, bottomMirrorY + mirrorH,
                mirrorH * 0.35f, mirrorH * 0.35f, paint);
        stroke.setColor(withAlpha(accent, 0.85f));
        canvas.drawRoundRect(mirrorX, topMirrorY, mirrorX + mirrorW, topMirrorY + mirrorH,
                mirrorH * 0.35f, mirrorH * 0.35f, stroke);
        canvas.drawRoundRect(mirrorX, bottomMirrorY, mirrorX + mirrorW, bottomMirrorY + mirrorH,
                mirrorH * 0.35f, mirrorH * 0.35f, stroke);
        canvas.drawLine(mirrorX + mirrorW * 0.50f, topMirrorY + mirrorH,
                mirrorX + mirrorW * 0.50f, car.top, stroke);
        canvas.drawLine(mirrorX + mirrorW * 0.50f, car.bottom,
                mirrorX + mirrorW * 0.50f, bottomMirrorY, stroke);

        // Ön/arka bölgeler.
        float frontW = car.width() * 0.17f;
        hood.set(car.right - frontW, car.top + car.height() * 0.14f,
                car.right - car.width() * 0.035f, car.bottom - car.height() * 0.14f);
        trunk.set(car.left + car.width() * 0.035f, car.top + car.height() * 0.14f,
                car.left + frontW, car.bottom - car.height() * 0.14f);
        drawZone(canvas, hood, hasHood && hoodOpen, warning, closed, "KAPUT");
        drawZone(canvas, trunk, hasTrunk && trunkOpen, warning, closed, "BAGAJ");

        // Kabin / tavan.
        float roofL = car.left + car.width() * 0.25f;
        float roofR = car.right - car.width() * 0.24f;
        roof.set(roofL, car.top + car.height() * 0.18f,
                roofR, car.bottom - car.height() * 0.18f);
        paint.setColor(withAlpha(muted, 0.30f));
        canvas.drawRoundRect(roof, carH * 0.18f, carH * 0.18f, paint);

        // Cam tavan ve güneşlik.
        sunroof.set(roof.left + roof.width() * 0.29f, roof.top + roof.height() * 0.12f,
                roof.right - roof.width() * 0.10f, roof.bottom - roof.height() * 0.12f);
        paint.setColor(sunroofOpen ? withAlpha(warning, pulse) : withAlpha(accent, 0.28f));
        canvas.drawRoundRect(sunroof, carH * 0.08f, carH * 0.08f, paint);
        stroke.setColor(sunroofOpen ? warning : withAlpha(accent, 0.45f));
        canvas.drawRoundRect(sunroof, carH * 0.08f, carH * 0.08f, stroke);

        sunshade.set(roof.left + roof.width() * 0.20f, roof.centerY() - carH * 0.035f,
                roof.left + roof.width() * 0.22f, roof.centerY() + carH * 0.035f);
        paint.setColor(sunshadeOpen ? withAlpha(warning, pulse) : withAlpha(muted, 0.55f));
        canvas.drawRoundRect(sunshade, carH * 0.02f, carH * 0.02f, paint);

        // Dört kapı — ön sağa bakar. Üst = sol taraf, alt = sağ taraf.
        float doorH = car.height() * 0.10f;
        float doorW = car.width() * 0.16f;
        float frontX = car.right - car.width() * 0.53f;
        float rearX = car.left + car.width() * 0.30f;
        float topY = car.top + dp(1) + doorH / 2f;
        float bottomY = car.bottom - dp(1) - doorH / 2f;
        doors[0].set(frontX, topY - doorH / 2f, frontX + doorW, topY + doorH / 2f);       // LF
        doors[1].set(frontX, bottomY - doorH / 2f, frontX + doorW, bottomY + doorH / 2f);  // RF
        doors[2].set(rearX, topY - doorH / 2f, rearX + doorW, topY + doorH / 2f);         // LR
        doors[3].set(rearX, bottomY - doorH / 2f, rearX + doorW, bottomY + doorH / 2f);    // RR
        drawDoor(canvas, doors[0], hasDoorLf && doorLfOpen, warning, closed, -1f);
        drawDoor(canvas, doors[1], hasDoorRf && doorRfOpen, warning, closed, 1f);
        drawDoor(canvas, doors[2], hasDoorLr && doorLrOpen, warning, closed, -1f);
        drawDoor(canvas, doors[3], hasDoorRr && doorRrOpen, warning, closed, 1f);

        // Camlar — kapıların dış tarafında ince şeritler.
        windows[0].set(doors[0].left + dp(2), doors[0].top + dp(2),
                doors[0].right - dp(2), doors[0].top + dp(6));
        windows[1].set(doors[1].left + dp(2), doors[1].bottom - dp(6),
                doors[1].right - dp(2), doors[1].bottom - dp(2));
        windows[2].set(doors[2].left + dp(2), doors[2].top + dp(2),
                doors[2].right - dp(2), doors[2].top + dp(6));
        windows[3].set(doors[3].left + dp(2), doors[3].bottom - dp(6),
                doors[3].right - dp(2), doors[3].bottom - dp(2));
        drawWindow(canvas, windows[0], hasWindowFl && windowFlOpen, warning, closed);
        drawWindow(canvas, windows[1], hasWindowFr && windowFrOpen, warning, closed);
        drawWindow(canvas, windows[2], hasWindowRl && windowRlOpen, warning, closed);
        drawWindow(canvas, windows[3], hasWindowRr && windowRrOpen, warning, closed);

        // Ön yön işareti.
        paint.setColor(withAlpha(accent, 0.75f));
        float arrowX = car.right + dp(2);
        float arrowY = car.centerY();
        canvas.drawCircle(arrowX, arrowY, dp(2.2f), paint);
        paint.setColor(withAlpha(muted, 0.6f));
        paint.setTextSize(dp(8.5f));
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(getContext().getString(R.string.ck_body_front),
                car.centerX(), car.bottom + dp(12), paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawZone(Canvas canvas, RectF zone, boolean open, int warning,
            int closed, String label) {
        paint.setColor(open ? withAlpha(warning, pulse) : withAlpha(closed, 0.20f));
        canvas.drawRoundRect(zone, dp(4), dp(4), paint);
        if (open) {
            stroke.setColor(withAlpha(warning, 0.95f));
            canvas.drawRoundRect(zone, dp(4), dp(4), stroke);
        }
        paint.setColor(open ? Color.WHITE : withAlpha(closed, 0.70f));
        paint.setTextSize(dp(10.5f));
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(label, zone.centerX(), zone.centerY() + dp(3.2f), paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawWindow(Canvas canvas, RectF zone, boolean open, int warning,
            int closed) {
        paint.setColor(open ? withAlpha(warning, pulse)
                : withAlpha(color(R.color.ck_accent), 0.42f));
        canvas.drawRoundRect(zone, dp(2), dp(2), paint);
        if (open) {
            stroke.setColor(withAlpha(warning, 0.95f));
            canvas.drawRoundRect(zone, dp(2), dp(2), stroke);
        }
    }

    private void drawDoor(Canvas canvas, RectF zone, boolean open, int warning,
            int closed, float side) {
        float offset = open ? side * dp(2.5f) * pulse : 0f;
        RectF draw = new RectF(zone);
        draw.offset(0f, offset);
        if (open) {
            paint.setColor(withAlpha(warning, pulse));
            canvas.drawRoundRect(draw, dp(3), dp(3), paint);
            stroke.setColor(withAlpha(warning, 0.95f));
            canvas.drawRoundRect(draw, dp(3), dp(3), stroke);
        } else {
            paint.setColor(withAlpha(closed, 0.32f));
            canvas.drawRoundRect(draw, dp(3), dp(3), paint);
        }
    }

    private int color(int res) {
        return getContext().getResources().getColor(res, getContext().getTheme());
    }

    private int withAlpha(int color, float alpha) {
        return Color.argb(Math.round(255f * Math.max(0f, Math.min(1f, alpha))),
                Color.red(color), Color.green(color), Color.blue(color));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static final int WARNING_COLOR = Color.parseColor("#F59E0B");
}
