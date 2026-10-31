package com.mapcontrol.manager;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.mapcontrol.R;
import com.mapcontrol.dvr.CameraGlEngine;
import com.mapcontrol.ui.cockpit.CockpitTokens;
import com.mapcontrol.util.AppLaunchHelper;

/** Cluster ekranında tek kamera önizlemesini en üst katmanda gösterir. */
final class TurnSignalCameraOverlay {
    private static final String TAG = "TurnSignalCamera";

    private final Context context;
    private final CameraGlEngine engine = CameraGlEngine.createSecondary("turn-signal-camera");
    private WindowManager windowManager;
    private FrameLayout root;
    private SurfaceView surfaceView;
    private TextView labelView;
    private int currentCam = -1;

    TurnSignalCameraOverlay(Context context) {
        this.context = context.getApplicationContext();
    }

    boolean canShow() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context);
    }

    void show(int cam, String label) {
        if (!canShow()) {
            Log.w(TAG, "Overlay izni yok");
            return;
        }
        if (root != null && currentCam == cam) {
            if (labelView != null) labelView.setText(label);
            return;
        }
        hide();
        Display display = findClusterDisplay();
        if (display == null) {
            Log.w(TAG, "Cluster display bulunamadı");
            return;
        }
        Context displayContext = context.createDisplayContext(display);
        WindowManager wm = (WindowManager) displayContext.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            return;
        }
        DisplayMetrics metrics = new DisplayMetrics();
        display.getRealMetrics(metrics);
        int cardWidth = Math.min(CockpitTokens.dp(context, 640),
                Math.max(CockpitTokens.dp(context, 320), metrics.widthPixels * 55 / 100));
        int cardHeight = Math.max(CockpitTokens.dp(context, 190), cardWidth * 9 / 16);

        FrameLayout overlayRoot = new FrameLayout(displayContext);
        overlayRoot.setBackgroundColor(Color.TRANSPARENT);

        FrameLayout card = new FrameLayout(displayContext);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE6000000);
        bg.setCornerRadius(CockpitTokens.dp(context, 16));
        bg.setStroke(Math.max(1, CockpitTokens.dp(context, 2)), 0xFF00B4D8);
        card.setBackground(bg);
        card.setClipToOutline(true);

        SurfaceView surface = new SurfaceView(displayContext);
        surface.setZOrderOnTop(false);
        card.addView(surface, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView labelText = new TextView(displayContext);
        labelText.setText(label);
        labelText.setTextColor(0xFFFFFFFF);
        labelText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18);
        labelText.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_SEMIBOLD));
        labelText.setGravity(Gravity.CENTER);
        labelText.setBackgroundColor(0x99000000);
        FrameLayout.LayoutParams labelLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                CockpitTokens.dp(context, 36), Gravity.TOP);
        card.addView(labelText, labelLp);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                cardWidth, cardHeight, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        cardLp.topMargin = CockpitTokens.dp(context, 16);
        overlayRoot.addView(card, cardLp);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;

        surface.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                engine.setBigCam(cam);
                engine.setPreviewSurface(holder.getSurface(), cardWidth, cardHeight);
                engine.setPreviewEnabled(true);
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                engine.setPreviewSurface(holder.getSurface(), width, height);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                engine.clearPreviewSurface();
            }
        });

        try {
            wm.addView(overlayRoot, params);
            windowManager = wm;
            root = overlayRoot;
            surfaceView = surface;
            labelView = labelText;
            currentCam = cam;
        } catch (Throwable t) {
            Log.w(TAG, "Cluster overlay eklenemedi", t);
        }
    }

    void hide() {
        WindowManager wm = windowManager;
        FrameLayout overlay = root;
        root = null;
        surfaceView = null;
        labelView = null;
        windowManager = null;
        currentCam = -1;
        // Önce pencereyi görünümden kaldır; EGL yüzeyini temizlemek için beklemek
        // overlay'in ekranda kalma süresini uzatmasın.
        if (wm != null && overlay != null) {
            try {
                wm.removeViewImmediate(overlay);
            } catch (Throwable ignored) {
            }
        }
        if (engine != null) {
            engine.setPreviewEnabled(false);
            engine.clearPreviewSurface();
        }
    }

    private Display findClusterDisplay() {
        DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (dm == null) return null;
        int id = AppLaunchHelper.getClusterDisplayId(context);
        for (Display display : dm.getDisplays()) {
            if (display.getDisplayId() == id) return display;
        }
        return null;
    }
}
