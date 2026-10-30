package com.mapcontrol.ui.builder;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mapcontrol.ui.cockpit.CockpitStatusBar;

/** Cockpit status bar adapter; TopBarCallback and public getters are preserved. */
public class TopBarBuilder {
    public interface TopBarCallback {
        void onLogTabToggle(boolean show);
        void log(String message);
    }

    private final Context context;
    private final TopBarCallback callback;
    private final Handler titleClickHandler;
    private final Runnable titleClickReset;
    private int titleClickCount = 0;
    private boolean isLogTabVisible = false;

    private CockpitStatusBar statusBar;
    private TextView titleView;
    private LinearLayout buttonsContainer;

    public TopBarBuilder(Context context, TopBarCallback callback) {
        this.context = context;
        this.callback = callback;
        this.titleClickHandler = new Handler(Looper.getMainLooper());
        this.titleClickReset = () -> titleClickCount = 0;
    }

    public LinearLayout build() {
        releaseInternal();
        statusBar = new CockpitStatusBar(context);
        titleView = statusBar.getTitleView();
        buttonsContainer = statusBar.getActionsContainer();

        titleView.setClickable(true);
        titleView.setFocusable(true);
        titleView.setOnClickListener(v -> {
            titleClickCount++;
            titleClickHandler.removeCallbacks(titleClickReset);
            if (titleClickCount >= 3) {
                isLogTabVisible = !isLogTabVisible;
                callback.onLogTabToggle(isLogTabVisible);
                callback.log("Log sekmesi " + (isLogTabVisible ? "açıldı" : "kapatıldı"));
                titleClickCount = 0;
            } else {
                titleClickHandler.postDelayed(titleClickReset, 1000);
            }
        });
        return statusBar;
    }

    public void setLauncherBackButtonVisible(boolean visible) {
        if (statusBar != null) {
            statusBar.setBackVisible(visible);
        }
    }

    public void setTopBarVisible(boolean visible) {
        if (statusBar != null) {
            statusBar.setBarVisible(visible);
        }
    }

    public void setLauncherBackButtonListener(View.OnClickListener listener) {
        if (statusBar != null) {
            statusBar.setBackListener(listener);
        }
    }

    public void reapplyTheme() {
        if (statusBar != null) {
            statusBar.release();
            statusBar = new CockpitStatusBar(context);
            titleView = statusBar.getTitleView();
            buttonsContainer = statusBar.getActionsContainer();
        }
    }

    public TextView getTitleView() {
        return titleView;
    }

    public LinearLayout getButtonsContainer() {
        return buttonsContainer;
    }

    /** Activity onDestroy: clock receiver'ı bırakır. */
    public void release() {
        if (statusBar != null) {
            statusBar.release();
        }
    }

    private void releaseInternal() {
        if (statusBar != null) {
            statusBar.release();
        }
    }
}
