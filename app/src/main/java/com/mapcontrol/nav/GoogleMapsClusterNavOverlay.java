package com.mapcontrol.nav;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
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
import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.util.AppLaunchHelper;

/**
 * Cluster ekranında Google Maps navigasyon özet kartları (bildirim verisi).
 * FLAG_NOT_TOUCHABLE — kadranlara dokunuşu engellemez.
 */
public final class GoogleMapsClusterNavOverlay {

    public static final String PREF_ENABLED = "google_maps_cluster_nav_overlay";
    public static final String PREF_STYLE = "google_cluster_nav_style";
    public static final String PREF_OPACITY = "google_cluster_nav_opacity";
    public static final String STYLE_DUAL = "dual";
    public static final String STYLE_HUD = "hud";
    public static final String STYLE_RADIAL = "radial";
    public static final String STYLE_TRAFFIC = "traffic";
    // Eski kayıtlı değerler için uyumluluk.
    public static final String STYLE_CLASSIC = "classic";
    public static final String STYLE_TWO_LANE = "two_lane";
    public static final String STYLE_SINGLE_ROW = "single_row";
    public static final String STYLE_GLASS = "glass";
    private static final String PREFS = "MapControlPrefs";
    private static final int LAYOUT_VERSION = 4;
    private static final int CARD_WIDTH_DP = 188;
    private static final int TOP_MARGIN_DP = 110;
    private static final int CENTER_GAP_DP = 300;
    /** Kartları ekran genişliğine göre dışa kaydırma (sol/sağ). */
    private static final float CARD_OUTWARD_SHIFT_RATIO = 0.15f;

    private static GoogleMapsClusterNavOverlay instance;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private WindowManager windowManager;
    private Context displayContext;
    private View overlayRoot;
    private TextView maneuverDistanceView;
    private TextView nextStreetView;
    private TextView maneuverStatusView;
    private TextView etaDistanceValue;
    private TextView etaArrivalValue;
    private TextView etaTimeValue;
    private TextView etaStatusView;
    private TextView radialDurationView;
    private View trafficProgress;
    private TextView hudEtaDetail;
    private boolean hudDistanceStyle;
    private LinearLayout etaDistanceRow;
    private LinearLayout etaArrivalRow;
    private LinearLayout etaTimeRow;
    private LinearLayout maneuverCard;
    private LinearLayout etaCard;
    private FrameLayout.LayoutParams maneuverCardLp;
    private FrameLayout.LayoutParams etaCardLp;
    private int layoutManeuverLeft;
    private int layoutEtaLeft;
    private int layoutTopMargin;
    private int layoutEtaTopMargin;
    private int layoutCardWidthPx;
    private int layoutScreenWidthPx;
    private WindowManager.LayoutParams overlayLayoutParams;
    private int appliedLayoutVersion = -1;
    private long lastZOrderRaiseUptimeMs = 0L;
    private static final long Z_ORDER_RAISE_MIN_INTERVAL_MS = 250L;

    @Nullable
    private GoogleMapsNavSnapshot lastApplied;

    private GoogleMapsClusterNavOverlay(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static synchronized GoogleMapsClusterNavOverlay getInstance(Context context) {
        if (instance == null) {
            instance = new GoogleMapsClusterNavOverlay(context);
        }
        return instance;
    }

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREF_ENABLED, true);
    }

    public static String getStyle(Context context) {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(PREF_STYLE, STYLE_DUAL);
        if (STYLE_CLASSIC.equals(raw)) return STYLE_DUAL;
        if (STYLE_GLASS.equals(raw)) return STYLE_HUD;
        if (STYLE_TWO_LANE.equals(raw)) return STYLE_RADIAL;
        if (STYLE_SINGLE_ROW.equals(raw)) return STYLE_TRAFFIC;
        return raw == null ? STYLE_DUAL : raw;
    }

    public static void setStyle(Context context, String style) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(PREF_STYLE, style).apply();
        getInstance(context).forceRebuild();
        getInstance(context).invalidateCache();
    }

    public static int getOpacity(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(PREF_OPACITY, 100);
    }

    public static void setOpacity(Context context, int opacity) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(PREF_OPACITY, Math.max(20, Math.min(100, opacity))).apply();
        getInstance(context).applyStoredOpacity();
    }

    private void applyStoredOpacity() {
        mainHandler.post(() -> {
            if (overlayRoot != null) {
                overlayRoot.setAlpha(getOpacity(appContext) / 100f);
            }
        });
    }

    public static void setEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_ENABLED, enabled).apply();
        GoogleMapsClusterNavOverlay overlay = getInstance(context);
        if (!enabled) {
            overlay.hide();
        } else {
            overlay.invalidateCache();
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

    public void apply(@Nullable GoogleMapsNavSnapshot snapshot) {
        mainHandler.post(() -> applyOnMain(snapshot));
    }

    private void applyOnMain(@Nullable GoogleMapsNavSnapshot snapshot) {
        if (!isEnabled(appContext)) {
            hideInternal();
            return;
        }
        if (snapshot == null || !snapshot.active) {
            hideInternal();
            return;
        }
        if (lastApplied != null && lastApplied.contentEquals(snapshot)) {
            return;
        }
        ensureOverlay();
        if (overlayRoot == null) {
            return;
        }

        boolean hasManeuver = bindManeuverCard(snapshot);
        boolean hasEta = bindEtaCard(snapshot);
        setVisible(maneuverCard, hasManeuver);
        setVisible(etaCard, hasEta);
        if (!hasManeuver && !hasEta) {
            hideInternal();
            return;
        }
        lastApplied = snapshot;
    }

    /**
     * Cluster'da uygulama / pencere değişince harita veya launcher üst katmana geçebilir;
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
        lastZOrderRaiseUptimeMs = now;
        try {
            windowManager.removeView(overlayRoot);
            windowManager.addView(overlayRoot, overlayLayoutParams);
        } catch (Exception ignored) {
        }
    }

    private boolean bindManeuverCard(GoogleMapsNavSnapshot snapshot) {
        boolean any = false;

        String distance = snapshot.formatManeuverDistanceLine();
        if (distance != null) {
            if (hudDistanceStyle) {
                setHudDistanceText(distance);
            } else {
                setTextIfChanged(maneuverDistanceView, distance);
            }
            setVisible(maneuverDistanceView, true);
            any = true;
        } else {
            setVisible(maneuverDistanceView, false);
        }

        String direction = snapshot.formatManeuverDirectionLine();
        if (direction != null) {
            setTextIfChanged(nextStreetView, direction);
            setVisible(nextStreetView, true);
            any = true;
        } else {
            setVisible(nextStreetView, false);
        }

        if (snapshot.maneuverHint != null && !snapshot.maneuverHint.isEmpty()
                && (direction == null || !direction.equals(snapshot.maneuverHint))) {
            setTextIfChanged(maneuverStatusView, snapshot.maneuverHint);
            setVisible(maneuverStatusView, true);
            any = true;
        } else {
            setVisible(maneuverStatusView, false);
        }

        return any;
    }

    private boolean bindEtaCard(GoogleMapsNavSnapshot snapshot) {
        boolean any = false;
        if (radialDurationView != null) {
            String duration = snapshot.etaDurationLabel();
            radialDurationView.setText(duration == null || duration.isEmpty() ? "—" : duration);
        }

        any |= bindMetricRow(etaDistanceRow, etaDistanceValue, snapshot.etaDistanceLabel());
        any |= bindMetricRow(etaArrivalRow, etaArrivalValue, snapshot.etaArrivalLabel());
        any |= bindMetricRow(etaTimeRow, etaTimeValue, snapshot.etaDurationLabel());
        if (hudEtaDetail != null && etaTimeValue != null) {
            setHudDurationText(snapshot.etaDurationLabel());
        }
        if (hudEtaDetail != null) {
            String distance = snapshot.etaDistanceLabel();
            String arrival = snapshot.etaArrivalLabel();
            StringBuilder detail = new StringBuilder();
            if (distance != null) detail.append(distance);
            if (arrival != null) {
                if (detail.length() > 0) detail.append("  ·  ");
                detail.append(arrival);
            }
            hudEtaDetail.setText(detail);
        }

        if (!any && snapshot.routeSummary != null && !snapshot.routeSummary.isEmpty()) {
            setTextIfChanged(etaStatusView, snapshot.routeSummary);
            setVisible(etaStatusView, true);
            any = true;
        } else {
            setVisible(etaStatusView, false);
        }

        return any;
    }

    private static boolean bindMetricRow(LinearLayout row, TextView valueView, @Nullable String value) {
        if (value == null || value.isEmpty()) {
            setVisible(row, false);
            return false;
        }
        if (valueView != null) {
            setTextIfChanged(valueView, value);
        }
        setVisible(row, true);
        return true;
    }

    private void setHudDistanceText(String line) {
        if (maneuverDistanceView == null || line == null) return;
        int split = line.length();
        while (split > 0 && !Character.isLetter(line.charAt(split - 1))) split--;
        String value = line.substring(0, split).trim();
        String unit = line.substring(split).trim().toUpperCase();
        if (value.isEmpty()) {
            setTextIfChanged(maneuverDistanceView, line);
            return;
        }
        SpannableStringBuilder sb = new SpannableStringBuilder(value);
        if (!unit.isEmpty()) {
            sb.append("  ");
            int start = sb.length();
            sb.append(unit);
            sb.setSpan(new ForegroundColorSpan(UiStyles.color(maneuverDistanceView.getContext(), R.color.ck_accent)),
                    start, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new RelativeSizeSpan(0.55f), start, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        maneuverDistanceView.setText(sb);
    }

    private void setHudDurationText(@Nullable String line) {
        if (etaTimeValue == null || line == null || line.isEmpty()) return;
        int split = 0;
        while (split < line.length() && Character.isDigit(line.charAt(split))) split++;
        String value = line.substring(0, split).trim();
        String unit = line.substring(split).trim();
        if (value.isEmpty()) {
            etaTimeValue.setText(line);
            return;
        }
        SpannableStringBuilder sb = new SpannableStringBuilder(value);
        if (!unit.isEmpty()) {
            sb.append(" ");
            int start = sb.length();
            sb.append(unit);
            sb.setSpan(new ForegroundColorSpan(UiStyles.color(etaTimeValue.getContext(), R.color.textMuted)),
                    start, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new RelativeSizeSpan(0.6f), start, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        etaTimeValue.setText(sb);
    }

    private static void setTextIfChanged(TextView view, String text) {
        if (view == null) return;
        CharSequence current = view.getText();
        if (current == null || !current.toString().contentEquals(text)) {
            view.setText(text);
        }
    }

    private static void setVisible(View view, boolean visible) {
        if (view == null) {
            return;
        }
        int target = visible ? View.VISIBLE : View.GONE;
        if (view.getVisibility() != target) {
            view.setVisibility(target);
        }
    }

    public void hide() {
        mainHandler.post(this::hideInternal);
    }

    private void hideInternal() {
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
        radialDurationView = null;
        trafficProgress = null;
        hudEtaDetail = null;
        hudDistanceStyle = false;
        etaDistanceRow = null;
        etaArrivalRow = null;
        etaTimeRow = null;
        maneuverCard = null;
        etaCard = null;
        maneuverCardLp = null;
        etaCardLp = null;
    }

    /** Yansıtma sekmesindeki X/Y kaydırma; overlay açıksa anında uygulanır. */
    public void applyStoredOffsets() {
        mainHandler.post(this::applyStoredOffsetsOnMain);
    }

    private void applyStoredOffsetsOnMain() {
        if (maneuverCard == null || maneuverCardLp == null) {
            return;
        }
        Context ctx = displayContext != null ? displayContext : appContext;
        ClusterNavOverlayOffsets.CardLayout maneuver =
                ClusterNavOverlayOffsets.getCardLayout(
                        appContext, ClusterNavOverlayOffsets.Provider.GOOGLE,
                        ClusterNavOverlayOffsets.Card.MANEUVER);
        ClusterNavOverlayOffsets.CardLayout eta =
                ClusterNavOverlayOffsets.getCardLayout(
                        appContext, ClusterNavOverlayOffsets.Provider.GOOGLE,
                        ClusterNavOverlayOffsets.Card.ETA);

        maneuverCardLp.leftMargin = layoutManeuverLeft;
        maneuverCardLp.topMargin = layoutTopMargin;
        maneuverCard.setLayoutParams(maneuverCardLp);
        ClusterNavOverlayOffsets.applyTranslationAndScale(
                maneuverCard, ctx,
                maneuver.offsetXDp, maneuver.offsetYDp, maneuver.scalePercent);

        if (etaCard != null && etaCardLp != null) {
            etaCardLp.leftMargin = layoutEtaLeft;
            etaCardLp.topMargin = layoutEtaTopMargin;
            etaCard.setLayoutParams(etaCardLp);
            ClusterNavOverlayOffsets.applyTranslationAndScale(
                    etaCard, ctx,
                    eta.offsetXDp, eta.offsetYDp, eta.scalePercent);
        }
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
            overlayRoot.setAlpha(getOpacity(appContext) / 100f);
            lastZOrderRaiseUptimeMs = SystemClock.uptimeMillis();
        } catch (Exception e) {
            hideInternal();
        }
    }

    private View buildOverlay(Context ctx) {
        FrameLayout root = new FrameLayout(ctx);
        root.setClipChildren(false);
        root.setClipToPadding(false);

        String style = getStyle(appContext);
        boolean singleCard = !STYLE_DUAL.equals(style);
        int screenWidth = ctx.getResources().getDisplayMetrics().widthPixels;
        int topMarginPx = dp(ctx, TOP_MARGIN_DP);
        int cardWidthPx = dp(ctx, CARD_WIDTH_DP);
        int gapPx = dp(ctx, CENTER_GAP_DP);
        int outwardPx = (int) (screenWidth * CARD_OUTWARD_SHIFT_RATIO);
        int pairWidth = cardWidthPx * 2 + gapPx;
        int pairLeft = (screenWidth - pairWidth) / 2;
        int maneuverLeft;
        int etaLeft;
        int etaTopMarginPx = topMarginPx;

        if (singleCard) {
            int width = Math.min(screenWidth - dp(ctx, 32), dp(ctx, 390));
            maneuverLeft = (screenWidth - width) / 2;
            etaLeft = maneuverLeft;
            maneuverCard = createSingleCard(ctx, style);
            buildSingleCardContents(ctx, style);
            maneuverCardLp = new FrameLayout.LayoutParams(width,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            maneuverCardLp.gravity = Gravity.TOP | Gravity.START;
            maneuverCardLp.leftMargin = maneuverLeft;
            maneuverCardLp.topMargin = topMarginPx;
            root.addView(maneuverCard, maneuverCardLp);
            etaCard = new LinearLayout(ctx);
            etaCardLp = null;
        } else {
            maneuverLeft = pairLeft - outwardPx;
            etaLeft = pairLeft + cardWidthPx + gapPx + outwardPx;
            maneuverCard = createManeuverCard(ctx);
            maneuverDistanceView = createManeuverText(ctx, 30, true);
            maneuverCard.addView(maneuverDistanceView, wrapLp());
            nextStreetView = createManeuverText(ctx, 14, false);
            nextStreetView.setMaxLines(2);
            LinearLayout.LayoutParams streetLp = wrapLp();
            streetLp.topMargin = dp(ctx, 4);
            maneuverCard.addView(nextStreetView, streetLp);
            maneuverStatusView = createManeuverText(ctx, 12, false);
            maneuverStatusView.setMaxLines(2);
            LinearLayout.LayoutParams statusLp = wrapLp();
            statusLp.topMargin = dp(ctx, 2);
            maneuverCard.addView(maneuverStatusView, statusLp);

            etaCard = createEtaCard(ctx);
            addEtaRows(ctx, etaCard);

            maneuverCardLp = new FrameLayout.LayoutParams(cardWidthPx,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            maneuverCardLp.gravity = Gravity.TOP | Gravity.START;
            maneuverCardLp.leftMargin = Math.max(0, maneuverLeft);
            maneuverCardLp.topMargin = topMarginPx;
            root.addView(maneuverCard, maneuverCardLp);

            etaCardLp = new FrameLayout.LayoutParams(cardWidthPx,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            etaCardLp.gravity = Gravity.TOP | Gravity.START;
            etaCardLp.leftMargin = Math.min(screenWidth - cardWidthPx, etaLeft);
            etaCardLp.topMargin = etaTopMarginPx;
            root.addView(etaCard, etaCardLp);
        }

        applyCardStyle(ctx, style);
        layoutScreenWidthPx = screenWidth;
        layoutCardWidthPx = cardWidthPx;
        layoutManeuverLeft = Math.max(0, maneuverLeft);
        layoutEtaLeft = Math.max(0, etaLeft);
        layoutTopMargin = topMarginPx;
        layoutEtaTopMargin = etaTopMarginPx;
        applyStoredOffsetsOnMain();
        return root;
    }

    private LinearLayout createSingleCard(Context ctx, String style) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(STYLE_TRAFFIC.equals(style)
                ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_cluster_nav_maneuver));
        int padH = dp(ctx, 14);
        int padV = dp(ctx, 10);
        card.setPadding(padH, padV, padH, padV);
        return card;
    }

    private void buildSingleCardContents(Context ctx, String style) {
        if (STYLE_HUD.equals(style)) {
            hudDistanceStyle = true;
            ImageView arrow = new ImageView(ctx);
            arrow.setImageResource(R.drawable.ic_ck_arrow_left);
            arrow.setColorFilter(UiStyles.color(ctx, R.color.ck_accent));
            GradientDrawable arrowBg = new GradientDrawable();
            arrowBg.setColor(UiStyles.color(ctx, R.color.ck_surface_1));
            arrowBg.setCornerRadius(dp(ctx, 10));
            arrowBg.setStroke(dp(ctx, 1), UiStyles.color(ctx, R.color.ck_accent_stroke_30));
            arrow.setBackground(arrowBg);
            arrow.setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8));
            maneuverCard.addView(arrow, new LinearLayout.LayoutParams(dp(ctx, 48), dp(ctx, 48)));

            LinearLayout left = new LinearLayout(ctx);
            left.setOrientation(LinearLayout.VERTICAL);
            left.setPadding(dp(ctx, 10), 0, dp(ctx, 8), 0);
            maneuverDistanceView = createManeuverText(ctx, 28, true);
            left.addView(maneuverDistanceView);
            nextStreetView = createManeuverText(ctx, 14, false);
            left.addView(nextStreetView);
            maneuverCard.addView(left, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1.35f));

            TextView divider = new TextView(ctx);
            divider.setBackgroundColor(UiStyles.color(ctx, R.color.ck_stroke));
            maneuverCard.addView(divider, new LinearLayout.LayoutParams(1, dp(ctx, 48)));

            LinearLayout right = new LinearLayout(ctx);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.END);
            right.setPadding(dp(ctx, 10), 0, 0, 0);
            LinearLayout durationRow = new LinearLayout(ctx);
            durationRow.setOrientation(LinearLayout.HORIZONTAL);
            durationRow.setGravity(Gravity.CENTER_VERTICAL);
            etaTimeValue = createEtaText(ctx, 22, true);
            durationRow.addView(etaTimeValue);
            TextView dot = new TextView(ctx);
            dot.setText("●");
            dot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            dot.setTextColor(UiStyles.color(ctx, R.color.ck_nominal));
            dot.setPadding(dp(ctx, 6), 0, 0, 0);
            durationRow.addView(dot);
            right.addView(durationRow, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            hudEtaDetail = createEtaText(ctx, 13, false);
            hudEtaDetail.setTextColor(UiStyles.color(ctx, R.color.textMuted));
            hudEtaDetail.setPadding(0, dp(ctx, 3), 0, 0);
            right.addView(hudEtaDetail);
            maneuverCard.addView(right, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            // bindEtaCard için boş alanlar; HUD'da görünür karşılıkları yukarıda.
            etaDistanceRow = new LinearLayout(ctx);
            etaDistanceValue = new TextView(ctx);
            etaArrivalRow = new LinearLayout(ctx);
            etaArrivalValue = new TextView(ctx);
            etaTimeRow = new LinearLayout(ctx);
            etaStatusView = new TextView(ctx);
        } else if (STYLE_RADIAL.equals(style)) {
            radialDurationView = new TextView(ctx);
            radialDurationView.setGravity(Gravity.CENTER);
            radialDurationView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            radialDurationView.setTypeface(null, Typeface.BOLD);
            radialDurationView.setTextColor(UiStyles.color(ctx, R.color.ck_accent));
            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ring.setColor(UiStyles.color(ctx, R.color.ck_surface_1));
            ring.setStroke(dp(ctx, 4), UiStyles.color(ctx, R.color.ck_accent));
            radialDurationView.setBackground(ring);
            maneuverCard.addView(radialDurationView, new LinearLayout.LayoutParams(
                    dp(ctx, 66), dp(ctx, 66)));
            LinearLayout right = new LinearLayout(ctx);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setPadding(dp(ctx, 12), 0, 0, 0);
            maneuverDistanceView = createManeuverText(ctx, 24, true);
            right.addView(maneuverDistanceView);
            nextStreetView = createManeuverText(ctx, 14, false);
            right.addView(nextStreetView);
            addEtaRows(ctx, right);
            maneuverCard.addView(right, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        } else {
            LinearLayout top = new LinearLayout(ctx);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            maneuverDistanceView = createManeuverText(ctx, 24, true);
            top.addView(maneuverDistanceView);
            nextStreetView = createManeuverText(ctx, 14, false);
            LinearLayout.LayoutParams streetLp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            streetLp.leftMargin = dp(ctx, 8);
            top.addView(nextStreetView, streetLp);
            maneuverCard.addView(top, matchWrapLp());
            trafficProgress = new View(ctx);
            trafficProgress.setBackgroundColor(UiStyles.color(ctx, R.color.ck_accent));
            LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 4));
            lineLp.topMargin = dp(ctx, 8);
            lineLp.bottomMargin = dp(ctx, 8);
            maneuverCard.addView(trafficProgress, lineLp);
            LinearLayout etaRow = new LinearLayout(ctx);
            etaRow.setOrientation(LinearLayout.HORIZONTAL);
            addEtaRows(ctx, etaRow);
            maneuverCard.addView(etaRow, matchWrapLp());
        }
    }

    private void addEtaRows(Context ctx, LinearLayout parent) {
        MetricRow distanceRow = addMetricRow(parent, ctx, "Kalan", 0);
        etaDistanceRow = distanceRow.row;
        etaDistanceValue = distanceRow.value;
        MetricRow arrivalRow = addMetricRow(parent, ctx, "Varış", dp(ctx, 6));
        etaArrivalRow = arrivalRow.row;
        etaArrivalValue = arrivalRow.value;
        MetricRow timeRow = addMetricRow(parent, ctx, "Süre", dp(ctx, 6));
        etaTimeRow = timeRow.row;
        etaTimeValue = timeRow.value;
        etaStatusView = createEtaText(ctx, 12, false);
        etaStatusView.setTextColor(UiStyles.color(ctx, R.color.textMuted));
        etaStatusView.setMaxLines(2);
        LinearLayout.LayoutParams statusLp = wrapLp();
        statusLp.topMargin = dp(ctx, 4);
        parent.addView(etaStatusView, statusLp);
    }

    private static LinearLayout.LayoutParams matchWrapLp() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static final class MetricRow {
        final LinearLayout row;
        final TextView value;

        MetricRow(LinearLayout row, TextView value) {
            this.row = row;
            this.value = value;
        }
    }

    private static MetricRow addMetricRow(LinearLayout parent, Context ctx,
            String label, int topMargin) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView labelView = createEtaText(ctx, 13, false);
        labelView.setText(label);
        labelView.setTextColor(UiStyles.color(ctx, R.color.textMuted));
        row.addView(labelView, new LinearLayout.LayoutParams(
                dp(ctx, 52),
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView valueView = createEtaText(ctx, 16, true);
        row.addView(valueView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams rowLp = wrapLp();
        rowLp.topMargin = topMargin;
        parent.addView(row, rowLp);
        return new MetricRow(row, valueView);
    }

    private static TextView createManeuverText(Context ctx, int sp, boolean bold) {
        TextView tv = new TextView(ctx);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) {
            tv.setTypeface(null, Typeface.BOLD);
            tv.setTextColor(UiStyles.color(ctx, R.color.cluster_nav_maneuver_text));
        } else {
            tv.setTextColor(UiStyles.color(ctx, R.color.cluster_nav_maneuver_subtext));
        }
        return tv;
    }

    private static TextView createEtaText(Context ctx, int sp, boolean bold) {
        TextView tv = new TextView(ctx);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) {
            tv.setTypeface(null, Typeface.BOLD);
        }
        tv.setTextColor(UiStyles.color(ctx, R.color.textPrimary));
        return tv;
    }

    private static LinearLayout.LayoutParams wrapLp() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams cardLp(Context ctx) {
        return new LinearLayout.LayoutParams(
                dp(ctx, CARD_WIDTH_DP),
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private void applyCardStyle(Context ctx, String style) {
        if (STYLE_HUD.equals(style)) {
            maneuverCard.setBackground(glassBackground(ctx));
        } else if (STYLE_RADIAL.equals(style)) {
            maneuverCard.setBackground(glassBackground(ctx));
        } else if (STYLE_TRAFFIC.equals(style)) {
            maneuverCard.setBackground(glassBackground(ctx));
        } else if (STYLE_DUAL.equals(style)) {
            maneuverCard.setPadding(dp(ctx, 12), dp(ctx, 9), dp(ctx, 12), dp(ctx, 9));
            etaCard.setPadding(dp(ctx, 12), dp(ctx, 9), dp(ctx, 12), dp(ctx, 9));
        } else if (STYLE_SINGLE_ROW.equals(style)) {
            maneuverCard.setPadding(dp(ctx, 12), dp(ctx, 9), dp(ctx, 12), dp(ctx, 9));
            etaCard.setPadding(dp(ctx, 12), dp(ctx, 9), dp(ctx, 12), dp(ctx, 9));
        }
    }

    private static GradientDrawable glassBackground(Context ctx) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x99161B22);
        bg.setCornerRadius(dp(ctx, 16));
        bg.setStroke(Math.max(1, dp(ctx, 1)), 0x66FFFFFF);
        return bg;
    }

    private static LinearLayout createManeuverCard(Context ctx) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_cluster_nav_maneuver));
        int padH = dp(ctx, 16);
        int padV = dp(ctx, 12);
        card.setPadding(padH, padV, padH, padV);
        return card;
    }

    private static LinearLayout createEtaCard(Context ctx) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_cluster_nav_eta));
        int pad = dp(ctx, 14);
        card.setPadding(pad, pad, pad, pad);
        return card;
    }

    private static int dp(Context ctx, int value) {
        float d = ctx.getResources().getDisplayMetrics().density;
        return Math.round(value * d);
    }
}
