package com.mapcontrol.manager;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
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
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

import com.mapcontrol.R;
import com.mapcontrol.dvr.CameraGlEngine;
import com.mapcontrol.nav.ClusterNavZOrder;
import com.mapcontrol.ui.cockpit.CockpitTokens;
import com.mapcontrol.util.AppLaunchHelper;

/** Cluster ekranında tek kamera önizlemesini en üst katmanda gösterir. */
public final class TurnSignalCameraOverlay {
    private static final String TAG = "TurnSignalCamera";
    /** Sinyal overlay'i cizim ust siniri; Camera360 onizlemesi 30 fps kalir. */
    private static final int PREVIEW_MAX_FPS = 25;
    /** Ayni anda tek overlay; nav overlay z-sirasi icin sorgulanir. */
    private static volatile TurnSignalCameraOverlay sInstance;

    private final Context context;
    private final CameraGlEngine engine = CameraGlEngine.createSecondary("turn-signal-camera");
    private WindowManager windowManager;
    private FrameLayout root;
    private TextureView textureView;
    private Surface cameraSurface;
    private TextView labelView;
    private TextView fpsView;
    private long lastFpsTextAtMs;
    private WindowManager.LayoutParams windowParams;
    private Display clusterDisplay;
    private int cardWidthPx;
    private int cardHeightPx;
    private int opacityPct = TurnSignalCameraController.OPACITY_MAX;
    private int sizePct = 100;
    private int currentCam = -1;
    private long shownAtMs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable navFrontTask;

    TurnSignalCameraOverlay(Context context) {
        this.context = context.getApplicationContext();
        engine.setPreviewMaxFps(PREVIEW_MAX_FPS);
        sInstance = this;
    }

    public static boolean isShowing() {
        TurnSignalCameraOverlay o = sInstance;
        return o != null && o.root != null;
    }

    /** Overlay'in ekrana geldigi an (uptimeMillis); gorunmuyorsa 0. */
    public static long showingSinceMs() {
        TurnSignalCameraOverlay o = sInstance;
        return o != null && o.root != null ? o.shownAtMs : 0L;
    }

    /** Nav overlay kendi penceresini one aldiysa kamerayi tekrar en uste tasir. */
    public static void bringToFrontIfShowing() {
        TurnSignalCameraOverlay o = sInstance;
        if (o == null || o.root == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            o.bringToFront();
        } else {
            new Handler(Looper.getMainLooper()).post(o::bringToFront);
        }
    }

    /** Ayar degisince kamerayi one al ve nav-on gecikmesini yeniden planla. */
    void onNavSettingsChanged() {
        bringToFront();
        scheduleNavFront();
    }

    private void cancelNavFrontTask() {
        if (navFrontTask != null) {
            mainHandler.removeCallbacks(navFrontTask);
            navFrontTask = null;
        }
    }

    private void scheduleNavFront() {
        cancelNavFrontTask();
        if (root == null || !TurnSignalCameraController.isNavFrontEnabled(context)) return;
        long delay = TurnSignalCameraController.getNavDelayMs(context);
        long elapsed = shownAtMs > 0L ? SystemClock.uptimeMillis() - shownAtMs : 0L;
        long remaining = Math.max(0L, delay - elapsed) + 50L;
        navFrontTask = () -> {
            navFrontTask = null;
            ClusterNavZOrder.raiseIfShowing(context);
        };
        mainHandler.postDelayed(navFrontTask, remaining);
    }

    void bringToFront() {
        FrameLayout overlay = root;
        WindowManager wm = windowManager;
        WindowManager.LayoutParams params = windowParams;
        if (overlay == null || wm == null || params == null) return;
        try {
            wm.removeViewImmediate(overlay);
        } catch (Throwable ignored) {
        }
        try {
            wm.addView(overlay, params);
        } catch (Throwable e) {
            Log.w(TAG, "overlay one alinamadi", e);
        }
    }

    boolean canShow() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context);
    }

    private void updateLabel(String label) {
        if (labelView != null) {
            labelView.setText(label.toUpperCase(Locale.ROOT));
        }
    }

    void show(int cam, String label) {
        if (root != null) {
            if (currentCam == cam) {
                updateLabel(label);
                return;
            }
            // Ayni pencereyi/yuzeyi koru; sadece kamerayi ve etiketi degistir.
            long now = SystemClock.uptimeMillis();
            boolean navWasFront = TurnSignalCameraController.isNavFrontEnabled(context)
                    && shownAtMs > 0L
                    && now - shownAtMs >= TurnSignalCameraController.getNavDelayMs(context);
            currentCam = cam;
            updateLabel(label);
            engine.setBigCam(cam);
            shownAtMs = now;
            if (navWasFront) bringToFront();
            scheduleNavFront();
            return;
        }
        if (!canShow()) {
            Log.w(TAG, "Overlay izni yok");
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
        clusterDisplay = display;
        opacityPct = TurnSignalCameraController.getOpacity(context);
        sizePct = TurnSignalCameraController.getSize(context);
        int cardWidth = computeCardWidth(metrics);
        int cardHeight = Math.max(CockpitTokens.dp(context, 150), cardWidth * 66 / 100);
        cardWidthPx = cardWidth;
        cardHeightPx = cardHeight;
        int cornerRadius = CockpitTokens.dp(context, 28);
        int innerRadius = CockpitTokens.dp(context, 20);
        int edge = CockpitTokens.dp(context, 18);
        int headerHeight = CockpitTokens.dp(context, 58);

        FrameLayout overlayRoot = new FrameLayout(displayContext);
        overlayRoot.setBackgroundColor(Color.TRANSPARENT);

        FrameLayout card = new FrameLayout(displayContext);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF20B0F14);
        bg.setCornerRadius(cornerRadius);
        bg.setStroke(Math.max(1, CockpitTokens.dp(context, 1)), 0xFF4CC9E8);
        card.setBackground(bg);
        applyRoundedOutline(card, cornerRadius);

        TextureView texture = new TextureView(displayContext);
        texture.setOpaque(true);
        applyRoundedOutline(texture, innerRadius);
        FrameLayout.LayoutParams textureLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        textureLp.setMargins(edge, headerHeight, edge, edge);
        card.addView(texture, textureLp);

        LinearLayout header = new LinearLayout(displayContext);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(edge, CockpitTokens.dp(context, 12), edge, 0);
        TextView labelText = createCameraPill(displayContext,
                "●", 0xFF4CC9E8, label.toUpperCase(Locale.ROOT), 13, true);
        header.addView(labelText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, CockpitTokens.dp(context, 34)));
        View spacer = new View(displayContext);
        header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        fpsView = createCameraPill(displayContext,
                "●", 0xFF10B981, PREVIEW_MAX_FPS + " FPS", 12, false);
        header.addView(fpsView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, CockpitTokens.dp(context, 30)));
        FrameLayout.LayoutParams headerLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                CockpitTokens.dp(context, 52), Gravity.TOP);
        card.addView(header, headerLp);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        overlayRoot.addView(card, cardLp);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                cardWidth,
                cardHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER;
        params.alpha = opacityPct / 100f;
        windowParams = params;

        texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                cameraSurface = new Surface(surfaceTexture);
                if (currentCam >= 0) engine.setBigCam(currentCam);
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
                // Main thread EGL yuzeyini beklemesin; Surface/SurfaceTexture motor yuzeyi
                // biraktiktan sonra serbest birakilir (canli EGL yuzeyinin ureticisi kopmasin).
                releaseCameraSurfaceAsync(surfaceTexture);
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
                long now = SystemClock.uptimeMillis();
                if (now - lastFpsTextAtMs < 500L || fpsView == null) return;
                lastFpsTextAtMs = now;
                int fps = currentCam >= 0 ? engine.getLastFps(currentCam) : 0;
                if (fps <= 0) fps = PREVIEW_MAX_FPS;
                fpsView.setText(fps + " FPS");
            }
        });

        try {
            wm.addView(overlayRoot, params);
            windowManager = wm;
            root = overlayRoot;
            textureView = texture;
            labelView = labelText;
            currentCam = cam;
            shownAtMs = SystemClock.uptimeMillis();
            scheduleNavFront();
        } catch (Throwable t) {
            Log.w(TAG, "Cluster overlay eklenemedi", t);
        }
    }

    private int computeCardWidth(DisplayMetrics metrics) {
        int base = Math.min(CockpitTokens.dp(context, 520),
                Math.max(CockpitTokens.dp(context, 280), metrics.widthPixels * 42 / 100));
        int width = base * sizePct / 100;
        int maxWidth = Math.max(CockpitTokens.dp(context, 200), metrics.widthPixels - CockpitTokens.dp(context, 24));
        return Math.max(CockpitTokens.dp(context, 200), Math.min(maxWidth, width));
    }

    /** Opaklık/boyut ayarlarını canlı uygular (pref değişiminde controller çağırır). */
    void applyPreferences() {
        int o = TurnSignalCameraController.getOpacity(context);
        int sz = TurnSignalCameraController.getSize(context);
        boolean sizeChanged = sz != sizePct;
        opacityPct = o;
        sizePct = sz;
        WindowManager wm = windowManager;
        FrameLayout overlay = root;
        if (wm == null || overlay == null) {
            return;
        }
        int w = cardWidthPx;
        int h = cardHeightPx;
        if (sizeChanged && clusterDisplay != null) {
            DisplayMetrics metrics = new DisplayMetrics();
            clusterDisplay.getRealMetrics(metrics);
            w = computeCardWidth(metrics);
            h = Math.max(CockpitTokens.dp(context, 150), w * 66 / 100);
            cardWidthPx = w;
            cardHeightPx = h;
            if (cameraSurface != null) {
                engine.setPreviewSurface(cameraSurface, w, h);
            }
        }
        if (windowParams != null) {
            windowParams.alpha = opacityPct / 100f;
            if (sizeChanged) {
                windowParams.width = w;
                windowParams.height = h;
            }
            try {
                wm.updateViewLayout(overlay, windowParams);
            } catch (Throwable ignored) {
            }
        }
    }

    void hide() {
        WindowManager wm = windowManager;
        FrameLayout overlay = root;
        root = null;
        textureView = null;
        labelView = null;
        fpsView = null;
        lastFpsTextAtMs = 0L;
        windowManager = null;
        currentCam = -1;
        shownAtMs = 0L;
        cancelNavFrontTask();
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
        }
        // TextureView geri cagrisi calismadiysa yuzeyi yine beklemeden birak.
        if (cameraSurface != null) {
            releaseCameraSurfaceAsync(null);
        }
    }

    private void releaseCameraSurfaceAsync(SurfaceTexture surfaceTexture) {
        final Surface surface = cameraSurface;
        cameraSurface = null;
        engine.clearPreviewSurfaceAsync(() -> mainHandler.post(() -> {
            if (surface != null) surface.release();
            if (surfaceTexture != null) surfaceTexture.release();
        }));
    }


    private TextView createCameraPill(Context ctx, String dotText, int dotColor,
            String text, int sp, boolean strong) {
        TextView pill = new TextView(ctx);
        String value = dotText + "  " + text;
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(value);
        int end = dotText.length();
        sb.setSpan(new android.text.style.ForegroundColorSpan(dotColor), 0, end,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new android.text.style.RelativeSizeSpan(0.75f), 0, end,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        pill.setText(sb);
        pill.setTextColor(0xFFE1E2E8);
        pill.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp);
        pill.setTypeface(CockpitTokens.inter(ctx,
                strong ? CockpitTokens.WEIGHT_BOLD : CockpitTokens.WEIGHT_SEMIBOLD));
        pill.setLetterSpacing(0.06f);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setSingleLine(true);
        pill.setPadding(CockpitTokens.dp(ctx, 12), 0, CockpitTokens.dp(ctx, 12), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC101820);
        bg.setCornerRadius(CockpitTokens.dp(ctx, 18));
        bg.setStroke(Math.max(1, CockpitTokens.dp(ctx, 1)), 0x664CC9E8);
        pill.setBackground(bg);
        return pill;
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
