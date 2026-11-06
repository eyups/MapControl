package com.eyups.roaddeck.assistant.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.eyups.roaddeck.R;
import com.eyups.roaddeck.assistant.AssistantPrefs;
import com.eyups.roaddeck.assistant.core.AssistantState;
import com.eyups.roaddeck.assistant.core.AssistantStateBus;
import com.eyups.roaddeck.ui.cockpit.CockpitSurfaces;
import com.eyups.roaddeck.ui.cockpit.CockpitTokens;
import com.eyups.roaddeck.ui.theme.UiStyles;

/**
 * Uyanınca ekranın altında görünen küçük durum paneli: durum + kısa metin.
 * 4 sn sonra kendiliğinden kapanır; dokunulursa Sesli Asistan sekmesi açılır.
 */
public final class AssistantListeningPanel {

    private static final long AUTO_HIDE_MS = 4000L;

    private static AssistantListeningPanel instance;

    public static void sync(Context context) {
        if (context == null) {
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(() -> sync(context));
            return;
        }
        Context app = context.getApplicationContext();
        boolean wanted = AssistantPrefs.isEnabled(app) && Settings.canDrawOverlays(app);
        if (!wanted) {
            hide();
            return;
        }
        if (instance == null) {
            instance = new AssistantListeningPanel(app);
        }
    }

    public static void hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(AssistantListeningPanel::hide);
            return;
        }
        if (instance != null) {
            instance.hideInternal();
            AssistantStateBus.get().removeListener(instance.listener);
            instance = null;
        }
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AssistantStateBus.Listener listener = this::onState;

    private WindowManager windowManager;
    private LinearLayout panel;
    private TextView title;
    private TextView detail;
    private WindowManager.LayoutParams params;
    private final Runnable autoHide = this::hideInternal;

    private AssistantListeningPanel(Context context) {
        this.context = context;
        AssistantStateBus.get().addListener(listener);
        onState(AssistantStateBus.get().state(), AssistantStateBus.get().detail());
    }

    private void onState(AssistantState state, String message) {
        main.post(() -> {
            boolean visible = state == AssistantState.RECORDING
                    || state == AssistantState.THINKING
                    || state == AssistantState.CONFIRMING
                    || state == AssistantState.SPEAKING
                    || state == AssistantState.ERROR;
            if (!visible) {
                hideInternal();
                return;
            }
            ensureView();
            if (panel == null) {
                return;
            }
            title.setText(label(state));
            detail.setText(message == null ? "" : message);
            detail.setVisibility(message == null || message.isEmpty()
                    ? View.GONE : View.VISIBLE);
            panel.setVisibility(View.VISIBLE);
            main.removeCallbacks(autoHide);
            if (state == AssistantState.SPEAKING || state == AssistantState.ERROR) {
                main.postDelayed(autoHide, AUTO_HIDE_MS);
            }
        });
    }

    private void ensureView() {
        if (panel != null) {
            return;
        }
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) {
            return;
        }
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = CockpitTokens.dp(context, 14);
        container.setPadding(pad, CockpitTokens.dp(context, 10), pad,
                CockpitTokens.dp(context, 10));
        container.setBackground(CockpitSurfaces.outlined(context,
                UiStyles.color(context, R.color.ck_surface_1),
                UiStyles.color(context, R.color.ck_accent_stroke_30), 16f));

        title = new TextView(context);
        CockpitTokens.bodyMd(title);
        title.setTextColor(UiStyles.color(context, R.color.ck_text_high));
        container.addView(title);

        detail = new TextView(context);
        CockpitTokens.bodySm(detail);
        detail.setTextColor(UiStyles.color(context, R.color.ck_text_medium));
        detail.setPadding(0, CockpitTokens.dp(context, 2), 0, 0);
        container.addView(detail);

        container.setOnClickListener(v -> openTab());
        panel = container;

        int width = Math.min(
                context.getResources().getDisplayMetrics().widthPixels
                        - CockpitTokens.dp(context, 64),
                CockpitTokens.dp(context, 480));
        params = new WindowManager.LayoutParams(
                width, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.y = CockpitTokens.dp(context, 72);
        try {
            windowManager.addView(panel, params);
        } catch (Throwable t) {
            panel = null;
            windowManager = null;
        }
    }

    private CharSequence label(AssistantState state) {
        int res;
        switch (state) {
            case RECORDING:
                res = R.string.assistant_state_recording;
                break;
            case THINKING:
                res = R.string.assistant_state_thinking;
                break;
            case CONFIRMING:
                res = R.string.assistant_state_confirming;
                break;
            case SPEAKING:
                res = R.string.assistant_state_speaking;
                break;
            case ERROR:
                res = R.string.assistant_state_error;
                break;
            default:
                res = R.string.assistant_state_listening;
        }
        return context.getString(res);
    }

    private void openTab() {
        Intent intent = new Intent("com.eyups.roaddeck.action.ASSISTANT_OPEN_TAB");
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    private void hideInternal() {
        main.removeCallbacks(autoHide);
        if (panel != null && windowManager != null) {
            try {
                windowManager.removeView(panel);
            } catch (Throwable ignored) {
            }
        }
        panel = null;
        windowManager = null;
    }
}
