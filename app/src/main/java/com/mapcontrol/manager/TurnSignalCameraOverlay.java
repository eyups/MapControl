package com.mapcontrol.manager;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
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
    private TextureView textureView;
    private Surface cameraSurface;
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
        int cardWidth = Math.min(CockpitTokens.dp(context, 520),
                Math.max(CockpitTokens.dp(context, 280), metrics.widthPixels * 42 / 100));
        int cardHeight = Math.max(CockpitTokens.dp(context, 170), cardWidth * 9 / 16);
        int cornerRadius = CockpitTokens.dp(context, 18);

        FrameLayout overlayRoot = new FrameLayout(displayContext);
        overlayRoot.setBackgroundColor(Color.TRANSPARENT);

        FrameLayout card = new FrameLayout(displayContext);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE6000000);
        bg.setCornerRadius(cornerRadius);
        bg.setStroke(Math.max(1, CockpitTokens.dp(context, 2)), 0xFF00B4D8);
        card.setBackground(bg);
        applyRoundedOutline(card, cornerRadius);

        TextureView texture = new TextureView(displayContext);
        texture.setOpaque(true);
        applyRoundedOutline(texture, cornerRadius);
        card.addView(texture, new FrameLayout.LayoutParams(
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
                cardWidth, cardHeight, Gravity.CENTER);
        overlayRoot.addView(card, cardLp);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;

        texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                cameraSurface = new Surface(surfaceTexture);
                engine.setBigCam(cam);
                engine.setPreviewSurface(cameraSurface, cardWidth, cardHeight);
                engine.setPreviewEnabled(true);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
                if (cameraSurface != null) {
                    engine.setPreviewSurface(cameraSurface, width, height);
                }
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                engine.clearPreviewSurface();
                if (cameraSurface != null) {
                    cameraSurface.release();
                    cameraSurface = null;
                }
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
            }
        });

        try {
            wm.addView(overlayRoot, params);
            windowManager = wm;
            root = overlayRoot;
            textureView = texture;
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
        textureView = null;
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
        if (cameraSurface != null) {
            cameraSurface.release();
            cameraSurface = null;
        }
    }

    private static void applyRoundedOutline(View view, int radius) {
        view.setClipToOutline(true);
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
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
