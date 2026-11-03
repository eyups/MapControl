package com.mapcontrol.assistant.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.mapcontrol.R;
import com.mapcontrol.assistant.AssistantPrefs;
import com.mapcontrol.assistant.core.AssistantState;
import com.mapcontrol.assistant.core.AssistantStateBus;
import com.mapcontrol.assistant.core.VoiceAssistantManager;
import com.mapcontrol.ui.cockpit.CockpitTokens;
import com.mapcontrol.vehicle.VehicleMetricsRepository;
import com.mapcontrol.vehicle.VehicleMetricsSnapshot;
import com.mapcontrol.assistant.tools.VehicleContext;

/**
 * Yüzen asistan butonu: ~56 dp, yalnız ana ekran (display 0), sürüklenebilir,
 * kenara yapışır, konumu prefs'te. Kamera/geri viteste gizlenir; boştayken
 * animasyon durur (sürekli invalidate yok).
 */
public final class AssistantOverlayButton {

    private static final int SIZE_DP = 56;
    private static final long LONG_PRESS_MS = 550L;

    private static AssistantOverlayButton instance;

    public static void sync(Context context) {
        if (context == null) {
            return;
        }
        Context app = context.getApplicationContext();
        boolean wanted = AssistantPrefs.overlayEnabled(app)
                && Settings.canDrawOverlays(app);
        if (!wanted) {
            hide();
            return;
        }
        if (instance == null) {
            instance = new AssistantOverlayButton(app);
        }
        instance.show();
    }

    public static void hide() {
        if (instance != null) {
            instance.hideInternal();
        }
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AssistantStateBus.Listener stateListener = this::onState;

    private WindowManager windowManager;
    private TextView view;
    private WindowManager.LayoutParams params;
    private android.animation.ValueAnimator pulse;
    private VehicleMetricsRepository metricsRepository;
    private VehicleMetricsRepository.Listener metricsListener;
    private boolean reverse;
    private float downRawX;
    private float downRawY;
    private int downX;
    private int downY;
    private boolean dragging;
    private boolean longPressed;
    private final Runnable longPressRunnable = this::onLongPress;

    private AssistantOverlayButton(Context context) {
        this.context = context;
    }

    private void show() {
        if (view != null) {
            return;
        }
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) {
            return;
        }
        int size = CockpitTokens.dp(context, SIZE_DP);
        TextView button = new TextView(context);
        button.setText("◉");
        button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 24);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(context.getString(R.string.assistant_overlay_desc));
        applyBackground(button, AssistantStateBus.get().state());
        view = button;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                android.graphics.PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        int defaultX = screenWidth - size - CockpitTokens.dp(context, 16);
        int defaultY = (int) (screenHeight * 0.62f);
        lp.x = AssistantPrefs.prefs(context).getInt(AssistantPrefs.KEY_OVERLAY_X, defaultX);
        lp.y = AssistantPrefs.prefs(context).getInt(AssistantPrefs.KEY_OVERLAY_Y, defaultY);
        params = lp;

        button.setOnTouchListener(this::onTouch);
        try {
            windowManager.addView(button, lp);
        } catch (Throwable t) {
            view = null;
            windowManager = null;
            return;
        }
        AssistantStateBus.get().addListener(stateListener);
        try {
            metricsRepository = VehicleMetricsRepository.getInstance(context);
            metricsListener = snapshot -> main.post(() -> onMetrics(snapshot));
            metricsRepository.addListener(metricsListener);
        } catch (Throwable ignored) {
        }
        onState(AssistantStateBus.get().state(), "");
    }

    private void hideInternal() {
        AssistantStateBus.get().removeListener(stateListener);
        stopPulse();
        if (metricsRepository != null) {
            try {
                if (metricsListener != null) {
                    metricsRepository.removeListener(metricsListener);
                }
            } catch (Throwable ignored) {
            }
            metricsRepository = null;
            metricsListener = null;
        }
        if (view != null && windowManager != null) {
            try {
                windowManager.removeView(view);
            } catch (Throwable ignored) {
            }
        }
        view = null;
        windowManager = null;
        params = null;
        instance = null;
    }

    private void onMetrics(VehicleMetricsSnapshot snapshot) {
        boolean reverseNow = snapshot != null
                && snapshot.gearSignal == VehicleContext.GEAR_REVERSE;
        if (reverseNow == reverse) {
            return;
        }
        reverse = reverseNow;
        if (view != null) {
            view.setVisibility(reverse ? View.GONE : View.VISIBLE);
        }
    }

    private void onState(AssistantState state, String detail) {
        if (view == null) {
            return;
        }
        applyBackground(view, state);
        boolean animate = state == AssistantState.RECORDING
                || state == AssistantState.CONFIRMING
                || state == AssistantState.THINKING;
        if (animate) {
            startPulse();
        } else {
            stopPulse();
        }
    }

    private void applyBackground(TextView target, AssistantState state) {
        int fill;
        int glyph;
        switch (state) {
            case RECORDING:
            case CONFIRMING:
                fill = R.color.ck_accent;
                glyph = R.color.ck_on_accent;
                break;
            case THINKING:
                fill = R.color.ck_caution;
                glyph = R.color.ck_on_accent;
                break;
            case SPEAKING:
                fill = R.color.ck_nominal;
                glyph = R.color.ck_on_accent;
                break;
            case ERROR:
                fill = R.color.ck_critical;
                glyph = R.color.ck_text_high;
                break;
            case LISTENING_WAKE:
                fill = R.color.ck_surface_2;
                glyph = R.color.ck_accent;
                break;
            default:
                fill = R.color.ck_surface_1;
                glyph = R.color.ck_text_medium;
        }
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(CockpitTokens.color(context, fill));
        background.setStroke(CockpitTokens.dp(context, 1),
                CockpitTokens.color(context, R.color.ck_stroke));
        target.setBackground(background);
        target.setTextColor(CockpitTokens.color(context, glyph));
        target.setAlpha(1f);
    }

    private void startPulse() {
        if (pulse != null) {
            return;
        }
        pulse = android.animation.ValueAnimator.ofFloat(1f, 0.55f);
        pulse.setDuration(650L);
        pulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        pulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        pulse.addUpdateListener(animation -> {
            if (view != null) {
                view.setAlpha((float) animation.getAnimatedValue());
            }
        });
        pulse.start();
    }

    private void stopPulse() {
        if (pulse != null) {
            pulse.cancel();
            pulse = null;
        }
        if (view != null) {
            view.setAlpha(1f);
        }
    }

    private boolean onTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                downX = params.x;
                downY = params.y;
                dragging = false;
                longPressed = false;
                main.postDelayed(longPressRunnable, LONG_PRESS_MS);
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                if (!dragging && (Math.abs(dx) > CockpitTokens.dp(context, 8)
                        || Math.abs(dy) > CockpitTokens.dp(context, 8))) {
                    dragging = true;
                    main.removeCallbacks(longPressRunnable);
                }
                if (dragging) {
                    params.x = downX + (int) dx;
                    params.y = downY + (int) dy;
                    try {
                        windowManager.updateViewLayout(view, params);
                    } catch (Throwable ignored) {
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                main.removeCallbacks(longPressRunnable);
                if (dragging) {
                    snapToEdge();
                } else if (!longPressed) {
                    onClick();
                }
                return true;
            default:
                return false;
        }
    }

    private void snapToEdge() {
        int size = CockpitTokens.dp(context, SIZE_DP);
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        int margin = CockpitTokens.dp(context, 12);
        int targetX = params.x + size / 2 < screenWidth / 2
                ? margin : screenWidth - size - margin;
        int targetY = Math.max(margin, Math.min(screenHeight - size - margin, params.y));
        params.x = targetX;
        params.y = targetY;
        try {
            windowManager.updateViewLayout(view, params);
        } catch (Throwable ignored) {
        }
        AssistantPrefs.prefs(context).edit()
                .putInt(AssistantPrefs.KEY_OVERLAY_X, targetX)
                .putInt(AssistantPrefs.KEY_OVERLAY_Y, targetY)
                .apply();
    }

    private void onLongPress() {
        longPressed = true;
        openAssistantTab();
    }

    private void onClick() {
        VoiceAssistantManager manager = VoiceAssistantManager.peek();
        if (manager == null) {
            if (!AssistantPrefs.isEnabled(context)) {
                openAssistantTab();
                return;
            }
            manager = VoiceAssistantManager.get(context);
        }
        AssistantState state = AssistantStateBus.get().state();
        if (state == AssistantState.RECORDING || state == AssistantState.CONFIRMING) {
            manager.endPushToTalk();
        } else if (state == AssistantState.THINKING || state == AssistantState.SPEAKING) {
            manager.cancel();
        } else {
            manager.beginPushToTalk();
        }
    }

    private void openAssistantTab() {
        Intent intent = new Intent("com.mapcontrol.action.ASSISTANT_OPEN_TAB");
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    /** Test/teardown. */
    public static boolean isShowing() {
        return instance != null && instance.view != null;
    }
}
