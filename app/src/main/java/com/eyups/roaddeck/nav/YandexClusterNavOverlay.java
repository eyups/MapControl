package com.eyups.roaddeck.nav;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.eyups.roaddeck.R;
import com.eyups.roaddeck.util.AppLaunchHelper;

/**
 * Cluster ekranında Yandex navigasyon özet kartları.
 * Google Maps kartlarıyla aynı tasarım varyantlarını (hud/dual/radial/traffic) kullanır.
 * FLAG_NOT_TOUCHABLE — kadranlara dokunuşu engellemez.
 */
public final class YandexClusterNavOverlay {

    public static final String PREF_ENABLED = "yandex_cluster_nav_overlay";
    private static final String PREFS = "RoadDeckPrefs";
    private static final int LAYOUT_VERSION = 5;
    private static final int TOP_MARGIN_DP = 104;
    private static final int DUAL_CARD_WIDTH_DP = 300;
    private static final int DUAL_CARD_HEIGHT_DP = 154;
    private static final int DUAL_GAP_DP = 20;
    private static final int SINGLE_WIDTH_DP = 470;
    private static final int COLOR_TEXT = 0xFFF8FAFC;
    private static final int COLOR_MUTED = 0xFF94A3B8;
    private static final int COLOR_ACCENT = 0xFF00B4D8;
    private static final int COLOR_NOMINAL = 0xFF10B981;

    private static YandexClusterNavOverlay instance;
    private static volatile boolean powerOff;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideTask = this::hideInternal;

    private WindowManager windowManager;
    private Context displayContext;
    private View overlayRoot;
    private WindowManager.LayoutParams overlayLayoutParams;
    private int appliedLayoutVersion = -1;
    private long lastZOrderRaiseUptimeMs = 0L;
    private static final long Z_ORDER_RAISE_MIN_INTERVAL_MS = 250L;
    /** Kısa scrape boşluklarında kartları hemen söndürme. */
    private static final long HIDE_HOLD_MS = 1500L;

    private String style = GoogleMapsClusterNavOverlay.STYLE_DUAL;
    private boolean dual;
    private LinearLayout topRow;
    private LinearLayout maneuverCard;
    private LinearLayout etaCard;
    private FrameLayout.LayoutParams topRowLp;
    private FrameLayout.LayoutParams singleCardLp;
    private TextView maneuverDistanceView;
    private TextView nextStreetView;
    private TextView maneuverStatusView;
    private TextView etaDistanceValue;
    private TextView etaArrivalValue;
    private TextView etaTimeValue;
    private TextView etaStatusView;
    private TextView hudEtaDetail;
    private View trafficTopPill;
    private View trafficBottomPill;
    private int layoutTopMargin;

    @Nullable
    private YandexNavSnapshot lastApplied;
    /** Güç OFF/ON arasında son geçerli navigasyon verisini korur. */
    @Nullable
    private YandexNavSnapshot lastSnapshot;

    private YandexClusterNavOverlay(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static synchronized YandexClusterNavOverlay getInstance(Context context) {
        if (instance == null) {
            instance = new YandexClusterNavOverlay(context);
        }
        return instance;
    }

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREF_ENABLED, true);
    }

    public static void setEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_ENABLED, enabled).apply();
        YandexClusterNavOverlay overlay = getInstance(context);
        if (!enabled) {
            overlay.hide();
        } else {
            overlay.invalidateCache();
            overlay.reapplyLast();
            overlay.raiseToTopIfShowing();
        }
    }

    /** Araç OFF iken kartları kapatır ve yeniden açılmalarını engeller. */
    public static void setPowerOff(Context context, boolean off) {
        if (powerOff == off) return;
        powerOff = off;
        YandexClusterNavOverlay overlay = getInstance(context);
        if (off) {
            overlay.hide();
        } else {
            overlay.invalidateCache();
            overlay.reapplyLast();
        }
    }

    /** Güç geri geldiğinde son navigasyon snapshot'ını yeniden uygular. */
    public void reapplyLast() {
        mainHandler.post(() -> {
            if (!powerOff && isEnabled(appContext) && lastSnapshot != null) {
                applyOnMain(lastSnapshot);
            }
        });
    }

    /** Google kart tasarımı değiştiğinde Yandex kartlarını da yeniden kurar. */
    public void onStyleChanged() {
        YandexNavSnapshot snapshot = lastApplied != null ? lastApplied : lastSnapshot;
        forceRebuild();
        if (snapshot != null) {
            apply(snapshot);
        }
    }

    /** Aynı snapshot tekrar uygulanmasın diye önbelleği temizler (ayar açılınca). */
    public void invalidateCache() {
        mainHandler.post(() -> lastApplied = null);
    }

    /** Zorla yeniden çizim (layout sürümü değişince). */
    public void forceRebuild() {
        mainHandler.post(() -> {
            lastApplied = null;
            if (overlayRoot != null && windowManager != null) {
                try {
                    windowManager.removeView(overlayRoot);
                } catch (Exception ignored) {
                }
                overlayRoot = null;
                clearViewRefs();
                appliedLayoutVersion = -1;
            }
        });
    }

    public void apply(@Nullable YandexNavSnapshot snapshot) {
        mainHandler.post(() -> applyOnMain(snapshot));
    }

    private void applyOnMain(@Nullable YandexNavSnapshot snapshot) {
        if (powerOff || !isEnabled(appContext)) {
            cancelPendingHide();
            hideInternal();
            return;
        }
        if (snapshot == null || !snapshot.guidanceActive) {
            lastSnapshot = null;
            scheduleHide();
            return;
        }
        cancelPendingHide();
        lastSnapshot = snapshot;
        if (lastApplied != null && lastApplied.contentEquals(snapshot)) {
            return;
        }
        boolean hadOverlay = overlayRoot != null;
        ensureOverlay();
        if (overlayRoot == null) {
            return;
        }
        if (!hadOverlay) {
            // Yalnızca ilk oluşturmada bir kez öne al; her scrape'te remove/add flicker yapıyor.
            mainHandler.postDelayed(() -> raiseToTopOnMain(false), 400L);
        }

        boolean any = bind(snapshot);
        if (!any) {
            hideInternal();
            return;
        }
        lastApplied = snapshot;
        applyCardOpacity();
    }

    /**
     * Cluster'da uygulama / pencere değişince Yandex veya launcher üst katmana geçebilir;
     * overlay aynı {@link WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY} tipinde
     * kalırsa eski ekleme sırasında altta kalır. remove + add ile en üste alınır.
     */
    public void raiseToTopIfShowing() {
        mainHandler.post(() -> raiseToTopOnMain(true));
    }

    private void raiseToTopOnMain(boolean throttle) {
        if (overlayRoot == null || windowManager == null || overlayLayoutParams == null) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (throttle && now - lastZOrderRaiseUptimeMs < Z_ORDER_RAISE_MIN_INTERVAL_MS) {
            return;
        }
        View root = overlayRoot;
        WindowManager wm = windowManager;
        WindowManager.LayoutParams params = overlayLayoutParams;
        try {
            wm.removeView(root);
        } catch (Exception ignored) {
        }
        try {
            wm.addView(root, params);
            lastZOrderRaiseUptimeMs = now;
        } catch (Exception ignored) {
            overlayRoot = null;
            windowManager = null;
            displayContext = null;
            overlayLayoutParams = null;
            clearViewRefs();
            appliedLayoutVersion = -1;
            lastApplied = null;
        }
    }

    // ---------- Data binding ----------

    private boolean bind(YandexNavSnapshot snapshot) {
        String distance = snapshot.formatManeuverLine();
        String street = snapshot.nextStreet;
        String status = snapshot.statusPanel;
        if (TextUtils.isEmpty(street)) {
            street = status;
        }
        String etaDistance = snapshot.etaDistance;
        String arrival = snapshot.etaArrival;
        String duration = snapshot.etaTime;

        if (dual) {
            boolean left = set(maneuverDistanceView, distance);
            left |= set(nextStreetView, street);
            boolean right = set(etaArrivalValue, arrival);
            right |= set(etaDistanceValue, etaDistance);
            right |= set(etaTimeValue, duration);
            if (maneuverCard != null) {
                maneuverCard.setVisibility(left ? View.VISIBLE : View.GONE);
            }
            if (etaCard != null) {
                etaCard.setVisibility(right ? View.VISIBLE : View.GONE);
            }
            return left || right;
        }

        boolean any = set(maneuverDistanceView, distance);
        any |= set(nextStreetView, street);
        any |= set(etaTimeValue, duration);
        any |= set(etaDistanceValue, etaDistance);
        any |= set(etaArrivalValue, arrival);
        if (hudEtaDetail != null) {
            String detail = join(etaDistance, arrival);
            set(hudEtaDetail, detail);
            any |= detail != null;
        }
        if (maneuverStatusView != null) {
            set(maneuverStatusView, status);
        }
        return any;
    }

    private static boolean set(TextView view, @Nullable String value) {
        if (view == null) {
            return false;
        }
        if (value == null || value.isEmpty()) {
            view.setVisibility(View.GONE);
            return false;
        }
        view.setText(value);
        view.setVisibility(View.VISIBLE);
        return true;
    }

    @Nullable
    private static String join(@Nullable String first, @Nullable String second) {
        boolean hasFirst = first != null && !first.isEmpty();
        boolean hasSecond = second != null && !second.isEmpty();
        if (!hasFirst && !hasSecond) {
            return null;
        }
        if (!hasFirst) {
            return second;
        }
        if (!hasSecond) {
            return first;
        }
        return first + "  ·  " + second;
    }

    // ---------- Overlay lifecycle ----------

    public void hide() {
        cancelPendingHide();
        mainHandler.post(this::hideInternal);
    }

    private void scheduleHide() {
        mainHandler.removeCallbacks(hideTask);
        mainHandler.postDelayed(hideTask, HIDE_HOLD_MS);
    }

    private void cancelPendingHide() {
        mainHandler.removeCallbacks(hideTask);
    }

    private void hideInternal() {
        cancelPendingHide();
        lastApplied = null;
        appliedLayoutVersion = -1;
        if (overlayRoot != null && windowManager != null) {
            try {
                windowManager.removeView(overlayRoot);
            } catch (Exception ignored) {
            }
        }
        overlayRoot = null;
        windowManager = null;
        displayContext = null;
        overlayLayoutParams = null;
        lastZOrderRaiseUptimeMs = 0L;
        clearViewRefs();
    }

    private void clearViewRefs() {
        maneuverDistanceView = null;
        nextStreetView = null;
        maneuverStatusView = null;
        etaDistanceValue = null;
        etaArrivalValue = null;
        etaTimeValue = null;
        etaStatusView = null;
        hudEtaDetail = null;
        trafficTopPill = null;
        trafficBottomPill = null;
        maneuverCard = null;
        etaCard = null;
        topRow = null;
        topRowLp = null;
        singleCardLp = null;
    }

    /** Yansıtma sekmesindeki X/Y kaydırma; overlay açıksa anında uygulanır. */
    public void applyStoredOffsets() {
        mainHandler.post(this::applyStoredOffsetsOnMain);
    }

    private void applyStoredOffsetsOnMain() {
        if (maneuverCard == null) {
            return;
        }
        Context ctx = displayContext != null ? displayContext : appContext;
        ClusterNavOverlayOffsets.CardLayout maneuver =
                ClusterNavOverlayOffsets.getCardLayout(
                        appContext, ClusterNavOverlayOffsets.Provider.YANDEX,
                        ClusterNavOverlayOffsets.Card.MANEUVER);
        ClusterNavOverlayOffsets.CardLayout eta =
                ClusterNavOverlayOffsets.getCardLayout(
                        appContext, ClusterNavOverlayOffsets.Provider.YANDEX,
                        ClusterNavOverlayOffsets.Card.ETA);

        if (dual) {
            if (topRow != null) {
                topRow.setTranslationX(0f);
                topRow.setTranslationY(0f);
            }
            if (topRowLp != null && topRow != null) {
                topRowLp.topMargin = layoutTopMargin;
                topRow.setLayoutParams(topRowLp);
            }
            ClusterNavOverlayOffsets.applyTranslationAndScale(
                    maneuverCard, ctx,
                    maneuver.offsetXDp, maneuver.offsetYDp, maneuver.scalePercent);
            if (etaCard != null) {
                ClusterNavOverlayOffsets.applyTranslationAndScale(
                        etaCard, ctx,
                        eta.offsetXDp, eta.offsetYDp, eta.scalePercent);
            }
            return;
        }

        if (GoogleMapsClusterNavOverlay.STYLE_TRAFFIC.equals(style)
                && trafficTopPill != null && trafficBottomPill != null) {
            maneuverCard.setTranslationX(0f);
            maneuverCard.setTranslationY(0f);
            maneuverCard.setScaleX(1f);
            maneuverCard.setScaleY(1f);
            ClusterNavOverlayOffsets.applyTranslationAndScale(
                    trafficTopPill, ctx,
                    maneuver.offsetXDp, maneuver.offsetYDp, maneuver.scalePercent);
            ClusterNavOverlayOffsets.applyTranslationAndScale(
                    trafficBottomPill, ctx,
                    eta.offsetXDp, eta.offsetYDp, eta.scalePercent);
            return;
        }

        if (singleCardLp != null) {
            singleCardLp.topMargin = layoutTopMargin;
            maneuverCard.setLayoutParams(singleCardLp);
        }
        ClusterNavOverlayOffsets.applyTranslationAndScale(
                maneuverCard, ctx,
                maneuver.offsetXDp, maneuver.offsetYDp, maneuver.scalePercent);
    }

    private void ensureOverlay() {
        if (overlayRoot != null && windowManager != null
                && appliedLayoutVersion == LAYOUT_VERSION) {
            return;
        }
        if (overlayRoot != null) {
            try {
                windowManager.removeView(overlayRoot);
            } catch (Exception ignored) {
            }
            overlayRoot = null;
            clearViewRefs();
            appliedLayoutVersion = -1;
        }
        try {
            int displayId = AppLaunchHelper.getClusterDisplayId(appContext);
            DisplayManager dm =
                    (DisplayManager) appContext.getSystemService(Context.DISPLAY_SERVICE);
            if (dm == null) {
                return;
            }
            Display target = null;
            for (Display d : dm.getDisplays()) {
                if (d.getDisplayId() == displayId) {
                    target = d;
                    break;
                }
            }
            if (target == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                displayContext = appContext.createDisplayContext(target);
            } else {
                displayContext = appContext;
            }
            windowManager =
                    (WindowManager) displayContext.getSystemService(Context.WINDOW_SERVICE);
            if (windowManager == null) {
                return;
            }

            overlayRoot = buildOverlay(displayContext);
            appliedLayoutVersion = LAYOUT_VERSION;
            overlayLayoutParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            windowManager.addView(overlayRoot, overlayLayoutParams);
            lastZOrderRaiseUptimeMs = SystemClock.uptimeMillis();
        } catch (Exception e) {
            hideInternal();
        }
    }

    private View buildOverlay(Context ctx) {
        style = GoogleMapsClusterNavOverlay.getStyle(appContext);
        dual = GoogleMapsClusterNavOverlay.STYLE_DUAL.equals(style);
        FrameLayout root = new FrameLayout(ctx);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        layoutTopMargin = dp(ctx, TOP_MARGIN_DP);

        if (dual) {
            topRow = new LinearLayout(ctx);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER);
            topRow.setClipChildren(false);
            topRow.setClipToPadding(false);

            maneuverCard = buildDualManeuverCard(ctx);
            etaCard = buildDualEtaCard(ctx);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    dp(ctx, DUAL_CARD_WIDTH_DP), dp(ctx, DUAL_CARD_HEIGHT_DP));
            topRow.addView(maneuverCard, cardLp);
            View gap = new View(ctx);
            topRow.addView(gap, new LinearLayout.LayoutParams(dp(ctx, DUAL_GAP_DP), 1));
            topRow.addView(etaCard, cardLp);

            topRowLp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            topRowLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            topRowLp.topMargin = layoutTopMargin;
            root.addView(topRow, topRowLp);
        } else {
            maneuverCard = buildSingleCard(ctx, style);
            etaCard = maneuverCard;
            int width = GoogleMapsClusterNavOverlay.STYLE_HUD.equals(style)
                    ? dp(ctx, SINGLE_WIDTH_DP) : FrameLayout.LayoutParams.WRAP_CONTENT;
            singleCardLp = new FrameLayout.LayoutParams(
                    width, FrameLayout.LayoutParams.WRAP_CONTENT);
            singleCardLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            singleCardLp.topMargin = layoutTopMargin;
            root.addView(maneuverCard, singleCardLp);
        }

        applyCardOpacity();
        applyStoredOffsetsOnMain();
        return root;
    }

    private LinearLayout buildSingleCard(Context ctx, String style) {
        if (GoogleMapsClusterNavOverlay.STYLE_TRAFFIC.equals(style)) {
            LinearLayout column = new LinearLayout(ctx);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER_HORIZONTAL);

            LinearLayout top = pill(ctx, true);
            trafficTopPill = top;
            top.addView(arrow(ctx, 30), new LinearLayout.LayoutParams(dp(ctx, 30), dp(ctx, 30)));
            maneuverDistanceView = text(ctx, 28, true, COLOR_TEXT);
            LinearLayout.LayoutParams distanceLp = wrapLp();
            distanceLp.leftMargin = dp(ctx, 10);
            top.addView(maneuverDistanceView, distanceLp);
            TextView dot = text(ctx, 16, true, COLOR_MUTED);
            dot.setText("·");
            dot.setPadding(dp(ctx, 8), 0, dp(ctx, 8), 0);
            top.addView(dot);
            nextStreetView = text(ctx, 19, true, COLOR_TEXT);
            nextStreetView.setSingleLine(true);
            nextStreetView.setEllipsize(TextUtils.TruncateAt.END);
            top.addView(nextStreetView, wrapLp());
            column.addView(top, wrapLp());

            LinearLayout bottom = pill(ctx, false);
            trafficBottomPill = bottom;
            LinearLayout.LayoutParams bottomLp = wrapLp();
            bottomLp.topMargin = dp(ctx, 8);
            etaTimeValue = text(ctx, 18, true, COLOR_TEXT);
            bottom.addView(etaTimeValue);
            TextView dot2 = text(ctx, 14, true, COLOR_MUTED);
            dot2.setText("·");
            dot2.setPadding(dp(ctx, 8), 0, dp(ctx, 8), 0);
            bottom.addView(dot2);
            etaDistanceValue = text(ctx, 16, false, COLOR_TEXT);
            bottom.addView(etaDistanceValue);
            TextView dot3 = text(ctx, 14, true, COLOR_MUTED);
            dot3.setText("·");
            dot3.setPadding(dp(ctx, 8), 0, dp(ctx, 8), 0);
            bottom.addView(dot3);
            etaArrivalValue = text(ctx, 18, true, COLOR_NOMINAL);
            bottom.addView(etaArrivalValue);
            column.addView(bottom, bottomLp);
            return column;
        }

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(ctx, 14), dp(ctx, 10), dp(ctx, 14), dp(ctx, 10));
        card.setBackground(cardBackground(ctx,
                GoogleMapsClusterNavOverlay.STYLE_HUD.equals(style) ? COLOR_ACCENT : COLOR_ACCENT, 255));

        ImageView arrow = arrow(ctx, 46);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(ctx, 46), dp(ctx, 46)));

        LinearLayout left = new LinearLayout(ctx);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setPadding(dp(ctx, 10), 0, dp(ctx, 8), 0);
        maneuverDistanceView = text(ctx, 27, true, COLOR_TEXT);
        left.addView(maneuverDistanceView);
        nextStreetView = text(ctx, 14, false, COLOR_MUTED);
        nextStreetView.setSingleLine(true);
        nextStreetView.setEllipsize(TextUtils.TruncateAt.END);
        left.addView(nextStreetView);
        card.addView(left, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1.25f));

        View divider = new View(ctx);
        divider.setBackgroundColor(0xFF2D3748);
        card.addView(divider, new LinearLayout.LayoutParams(dp(ctx, 1), dp(ctx, 46)));

        if (GoogleMapsClusterNavOverlay.STYLE_HUD.equals(style)) {
            LinearLayout right = new LinearLayout(ctx);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.END);
            right.setPadding(dp(ctx, 8), 0, 0, 0);
            etaTimeValue = text(ctx, 21, true, COLOR_TEXT);
            etaTimeValue.setGravity(Gravity.END);
            right.addView(etaTimeValue);
            hudEtaDetail = text(ctx, 12, false, COLOR_MUTED);
            hudEtaDetail.setGravity(Gravity.END);
            right.addView(hudEtaDetail);
            card.addView(right, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        } else {
            card.setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12));
            etaTimeValue = text(ctx, 21, true, COLOR_ACCENT);
            card.addView(etaTimeValue);
            TextView dot1 = text(ctx, 14, true, COLOR_MUTED);
            dot1.setText("·");
            dot1.setPadding(dp(ctx, 8), 0, dp(ctx, 8), 0);
            card.addView(dot1);
            etaDistanceValue = text(ctx, 18, false, COLOR_TEXT);
            card.addView(etaDistanceValue);
            TextView dot2 = text(ctx, 14, true, COLOR_MUTED);
            dot2.setText("·");
            dot2.setPadding(dp(ctx, 8), 0, dp(ctx, 8), 0);
            card.addView(dot2);
            etaArrivalValue = text(ctx, 18, true, COLOR_NOMINAL);
            card.addView(etaArrivalValue);
        }
        return card;
    }

    private LinearLayout buildDualManeuverCard(Context ctx) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14));
        card.setBackground(cardBackground(ctx, COLOR_ACCENT, 255));

        TextView label = text(ctx, 11, true, COLOR_MUTED);
        label.setText("MANEVRA");
        label.setLetterSpacing(0.08f);
        card.addView(label);

        maneuverDistanceView = text(ctx, 32, true, COLOR_TEXT);
        card.addView(maneuverDistanceView, wrapLp());
        nextStreetView = text(ctx, 15, false, COLOR_TEXT);
        nextStreetView.setMaxLines(2);
        card.addView(nextStreetView, wrapLp());
        return card;
    }

    private LinearLayout buildDualEtaCard(Context ctx) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14));
        card.setBackground(cardBackground(ctx, COLOR_NOMINAL, 255));

        TextView label = text(ctx, 11, true, COLOR_MUTED);
        label.setText("VARIŞ SAATİ");
        label.setLetterSpacing(0.08f);
        card.addView(label);

        etaArrivalValue = text(ctx, 32, true, COLOR_TEXT);
        card.addView(etaArrivalValue, wrapLp());

        View divider = new View(ctx);
        divider.setBackgroundColor(0xFF2D3748);
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 1));
        dividerLp.topMargin = dp(ctx, 8);
        card.addView(divider, dividerLp);

        LinearLayout metrics = new LinearLayout(ctx);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.setPadding(0, dp(ctx, 8), 0, 0);

        LinearLayout distanceCol = new LinearLayout(ctx);
        distanceCol.setOrientation(LinearLayout.VERTICAL);
        TextView distanceLabel = text(ctx, 10, false, COLOR_MUTED);
        distanceLabel.setText("KALAN YOL");
        distanceCol.addView(distanceLabel);
        etaDistanceValue = text(ctx, 19, true, COLOR_TEXT);
        distanceCol.addView(etaDistanceValue);
        metrics.addView(distanceCol, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout durationCol = new LinearLayout(ctx);
        durationCol.setOrientation(LinearLayout.VERTICAL);
        TextView durationLabel = text(ctx, 10, false, COLOR_MUTED);
        durationLabel.setText("SÜRE");
        durationLabel.setGravity(Gravity.END);
        durationCol.addView(durationLabel);
        etaTimeValue = text(ctx, 19, true, COLOR_TEXT);
        etaTimeValue.setGravity(Gravity.END);
        durationCol.addView(etaTimeValue);
        metrics.addView(durationCol, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(metrics);
        return card;
    }

    private void applyCardOpacity() {
        int maneuverAlpha = Math.round(255f
                * GoogleMapsClusterNavOverlay.getCardOpacity(appContext, false) / 100f);
        int etaAlpha = Math.round(255f
                * GoogleMapsClusterNavOverlay.getCardOpacity(appContext, true) / 100f);
        if (GoogleMapsClusterNavOverlay.STYLE_TRAFFIC.equals(style)) {
            applyBackgroundAlpha(trafficTopPill, maneuverAlpha);
            applyBackgroundAlpha(trafficBottomPill, etaAlpha);
            return;
        }
        applyCardBackgroundAlpha(maneuverCard, maneuverAlpha, COLOR_ACCENT);
        if (dual) {
            applyCardBackgroundAlpha(etaCard, etaAlpha, COLOR_NOMINAL);
        }
    }

    private void applyCardBackgroundAlpha(@Nullable View card, int alpha, int accent) {
        if (card == null) return;
        Context ctx = displayContext != null ? displayContext : appContext;
        card.setBackground(cardBackground(ctx, accent, alpha));
    }

    private static void applyBackgroundAlpha(@Nullable View view, int alpha) {
        if (view == null || view.getBackground() == null) return;
        view.getBackground().mutate().setAlpha(alpha);
        view.invalidate();
    }

    private static GradientDrawable cardBackground(Context ctx, int accent, int alpha) {
        int fill = (alpha << 24) | 0x000B0F14;
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fill);
        bg.setCornerRadius(dp(ctx, 18));
        bg.setStroke(Math.max(1, dp(ctx, 2)), accent);
        return bg;
    }

    private static LinearLayout pill(Context ctx, boolean accent) {
        LinearLayout pill = new LinearLayout(ctx);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(ctx, 16), dp(ctx, 10), dp(ctx, 16), dp(ctx, 10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF0B0F14);
        bg.setCornerRadius(dp(ctx, 24));
        bg.setStroke(Math.max(1, dp(ctx, 1)), accent ? COLOR_ACCENT : 0xFF2D3748);
        pill.setBackground(bg);
        return pill;
    }

    private static ImageView arrow(Context ctx, int sizeDp) {
        ImageView arrow = new ImageView(ctx);
        arrow.setImageResource(R.drawable.ic_ck_arrow_left);
        arrow.setColorFilter(COLOR_ACCENT);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF252E38);
        bg.setCornerRadius(dp(ctx, 10));
        bg.setStroke(Math.max(1, dp(ctx, 1)), 0x4D00B4D8);
        arrow.setBackground(bg);
        arrow.setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8));
        return arrow;
    }

    private static TextView text(Context ctx, int sp, boolean bold, int color) {
        TextView view = new TextView(ctx);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(null, Typeface.BOLD);
        }
        return view;
    }

    private static LinearLayout.LayoutParams wrapLp() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static int dp(Context ctx, int value) {
        float d = ctx.getResources().getDisplayMetrics().density;
        return Math.round(value * d);
    }
}
