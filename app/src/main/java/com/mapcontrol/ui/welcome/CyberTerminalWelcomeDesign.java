package com.mapcontrol.ui.welcome;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mapcontrol.R;
import com.mapcontrol.ui.widget.CyberScanlineView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Cyber terminal welcome screen: uppercase custom line, typewriter reveal,
 * blinking block cursor and occasional RGB-ish glitch offsets.
 */
public final class CyberTerminalWelcomeDesign implements WelcomeDesign {

    private static final int GREEN = 0xFF33FF00;
    private static final int GREEN_DIM = 0xFF1F9A1F;
    private static final int GREEN_MUTED = 0xFF1F7A1F;
    private static final int BLACK = 0xFF040704;
    private static final String CURSOR = "▌";
    private static final Random RANDOM = new Random();

    @Override
    public String id() {
        return WelcomeDesignCatalog.ID_CYBER;
    }

    @Override
    public int titleRes() {
        return R.string.welcome_design_cyber;
    }

    @Override
    public int helpRes() {
        return R.string.welcome_design_cyber_help;
    }

    @Override
    public View createView(Context context) {
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        String line = customLine(context);
        if (TextUtils.isEmpty(line)) {
            line = context.getString(R.string.welcome_cyber_fallback);
        }
        final String target = line.toUpperCase(locale);

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(BLACK);

        View glow = new View(context);
        GradientDrawable glowDrawable = new GradientDrawable();
        glowDrawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowDrawable.setGradientCenter(0.5f, 0.44f);
        glowDrawable.setGradientRadius(dp(context, 620));
        glowDrawable.setColors(new int[]{0x2833FF00, 0x0B33FF00, 0x00000000});
        glow.setBackground(glowDrawable);
        root.addView(glow, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        int frameColor = 0x5533FF00;
        root.addView(new TerminalFrameView(context, frameColor), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        CyberScanlineView scanlines = new CyberScanlineView(context);
        root.addView(scanlines, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        column.setPadding(dp(context, 56), 0, dp(context, 56), 0);
        int heroSize = target.length() <= 12 ? 78
                : target.length() <= 24 ? 62
                : target.length() <= 40 ? 48 : 38;

        TextView kicker = mono(context, "> MAPCONTROL // WELCOME_SEQUENCE", 12, GREEN_MUTED);
        kicker.setLetterSpacing(0.16f);
        kicker.setGravity(Gravity.CENTER);
        column.addView(kicker);

        TextView hero = mono(context, "", heroSize, GREEN);
        hero.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        hero.setGravity(Gravity.CENTER);
        hero.setLetterSpacing(0.035f);
        hero.setLineSpacing(dp(context, 4), 1f);
        hero.setMaxLines(3);
        hero.setEllipsize(TextUtils.TruncateAt.END);
        hero.setIncludeFontPadding(false);
        hero.setShadowLayer(dp(context, 18), 0, 0, 0x8833FF00);
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        heroLp.topMargin = dp(context, 18);
        column.addView(hero, heroLp);

        View rule = new View(context);
        GradientDrawable ruleBg = new GradientDrawable();
        ruleBg.setColor(0xAA33FF00);
        rule.setBackground(ruleBg);
        LinearLayout.LayoutParams ruleLp = new LinearLayout.LayoutParams(dp(context, 84), dp(context, 2));
        ruleLp.topMargin = dp(context, 26);
        column.addView(rule, ruleLp);

        TextView status = mono(context, "> SYSTEM ONLINE", 12, GREEN_DIM);
        status.setLetterSpacing(0.14f);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = dp(context, 16);
        column.addView(status, statusLp);

        root.addView(column, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));

        addCornerLabels(context, root);

        final Handler handler = new Handler(Looper.getMainLooper());
        final List<ObjectAnimator> animations = new ArrayList<>();
        final int[] index = {0};
        final boolean[] done = {false};
        final boolean[] cursorOn = {true};
        final long[] lastHoverGlitch = {0L};

        Runnable blink = new Runnable() {
            @Override
            public void run() {
                if (!done[0]) {
                    return;
                }
                cursorOn[0] = !cursorOn[0];
                hero.setText(cursorOn[0] ? target + CURSOR : target);
                handler.postDelayed(this, 520L);
            }
        };

        Runnable glitchTick = new Runnable() {
            @Override
            public void run() {
                glitch(hero, context);
                handler.postDelayed(this, 2800L + RANDOM.nextInt(1800));
            }
        };

        Runnable type = new Runnable() {
            @Override
            public void run() {
                if (index[0] < target.length()) {
                    index[0]++;
                    hero.setText(target.substring(0, index[0]) + CURSOR);
                    handler.postDelayed(this, 62L);
                } else {
                    done[0] = true;
                    hero.setText(target + CURSOR);
                    handler.postDelayed(blink, 520L);
                    handler.postDelayed(glitchTick, 1700L);
                }
            }
        };

        root.setOnHoverListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE) {
                long now = System.currentTimeMillis();
                if (now - lastHoverGlitch[0] > 260L) {
                    lastHoverGlitch[0] = now;
                    glitch(hero, context);
                }
            }
            return false;
        });

        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                handler.removeCallbacks(type);
                handler.removeCallbacks(blink);
                handler.removeCallbacks(glitchTick);
                handler.postDelayed(type, 420L);

                glow.setAlpha(0f);
                glow.setScaleX(0.92f);
                glow.setScaleY(0.92f);
                glow.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(900L)
                        .setInterpolator(new AccelerateDecelerateInterpolator()).start();

                ObjectAnimator pulseAlpha = ObjectAnimator.ofFloat(glow, View.ALPHA, 0.62f, 1f);
                ObjectAnimator pulseX = ObjectAnimator.ofFloat(glow, View.SCALE_X, 1f, 1.07f);
                ObjectAnimator pulseY = ObjectAnimator.ofFloat(glow, View.SCALE_Y, 1f, 1.07f);
                for (ObjectAnimator part : new ObjectAnimator[]{pulseAlpha, pulseX, pulseY}) {
                    part.setDuration(2600L);
                    part.setStartDelay(900L);
                    part.setRepeatCount(ValueAnimator.INFINITE);
                    part.setRepeatMode(ValueAnimator.REVERSE);
                    part.setInterpolator(new AccelerateDecelerateInterpolator());
                    animations.add(part);
                    part.start();
                }
                status.setAlpha(0.55f);
                ObjectAnimator statusPulse = ObjectAnimator.ofFloat(status, View.ALPHA, 0.35f, 1f);
                statusPulse.setDuration(1100L);
                statusPulse.setRepeatCount(ValueAnimator.INFINITE);
                statusPulse.setRepeatMode(ValueAnimator.REVERSE);
                animations.add(statusPulse);
                statusPulse.start();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                handler.removeCallbacks(type);
                handler.removeCallbacks(blink);
                handler.removeCallbacks(glitchTick);
                for (ObjectAnimator animator : animations) {
                    animator.cancel();
                }
                animations.clear();
            }
        });
        return root;
    }

    private static void addCornerLabels(Context context, FrameLayout root) {
        TextView topLeft = mono(context, "MAPCONTROL // WELCOME", 11, GREEN_MUTED);
        topLeft.setLetterSpacing(0.16f);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        tl.setMargins(dp(context, 34), dp(context, 28), 0, 0);
        root.addView(topLeft, tl);

        TextView topRight = mono(context, "CYBER / 01", 11, GREEN_MUTED);
        topRight.setLetterSpacing(0.16f);
        FrameLayout.LayoutParams tr = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        tr.setMargins(0, dp(context, 28), dp(context, 34), 0);
        root.addView(topRight, tr);

        TextView bottom = mono(context, "> PRESS TO CONTINUE", 12, GREEN_DIM);
        bottom.setLetterSpacing(0.16f);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bl.setMargins(0, 0, 0, dp(context, 34));
        root.addView(bottom, bl);
    }

    private static void glitch(TextView view, Context context) {
        if (view == null || !view.isAttachedToWindow()) {
            return;
        }
        float dx = (RANDOM.nextFloat() - 0.5f) * dp(context, 5);
        float dy = (RANDOM.nextFloat() - 0.5f) * dp(context, 2);
        view.setTranslationX(dx);
        view.setTranslationY(dy);
        view.postDelayed(() -> {
            view.setTranslationX(0f);
            view.setTranslationY(0f);
        }, 72L);
    }

    private static TextView mono(Context context, String text, int sizeSp, int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        return view;
    }

    private static String customLine(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(
                WelcomeDesignCatalog.PREFS_NAME, Context.MODE_PRIVATE);
        String line = prefs.getString(WelcomeDesignCatalog.PREF_LINE, "");
        return line == null ? "" : line.trim();
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class TerminalFrameView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        TerminalFrameView(Context context, int color) {
            super(context);
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, context.getResources().getDisplayMetrics().density));
            setClickable(false);
            setFocusable(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float inset = 24f * getResources().getDisplayMetrics().density;
            float len = 28f * getResources().getDisplayMetrics().density;
            float right = getWidth() - inset;
            float bottom = getHeight() - inset;
            canvas.drawLine(inset, inset, inset + len, inset, paint);
            canvas.drawLine(inset, inset, inset, inset + len, paint);
            canvas.drawLine(right - len, inset, right, inset, paint);
            canvas.drawLine(right, inset, right, inset + len, paint);
            canvas.drawLine(inset, bottom - len, inset, bottom, paint);
            canvas.drawLine(inset, bottom, inset + len, bottom, paint);
            canvas.drawLine(right - len, bottom, right, bottom, paint);
            canvas.drawLine(right, bottom - len, right, bottom, paint);
        }
    }
}
