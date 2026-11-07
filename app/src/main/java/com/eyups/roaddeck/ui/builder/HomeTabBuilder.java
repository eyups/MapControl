package com.eyups.roaddeck.ui.builder;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.eyups.roaddeck.R;
import com.eyups.roaddeck.lyrics.LyricsEngine;
import com.eyups.roaddeck.lyrics.LyricsFullscreenActivity;
import com.eyups.roaddeck.lyrics.LyricsState;
import com.eyups.roaddeck.lyrics.LyricsMediaCardView;
import com.eyups.roaddeck.driving.DrivingScoreRingView;
import com.eyups.roaddeck.driving.DrivingSessionRecorder;
import com.eyups.roaddeck.driving.DrivingTrip;
import com.eyups.roaddeck.driving.DrivingTripStore;
import com.eyups.roaddeck.ui.cockpit.CockpitButton;
import com.eyups.roaddeck.ui.cockpit.CockpitCard;
import com.eyups.roaddeck.ui.cockpit.CockpitGrid;
import com.eyups.roaddeck.ui.cockpit.CockpitNavModel;
import com.eyups.roaddeck.ui.cockpit.CockpitSectionHeader;
import com.eyups.roaddeck.ui.cockpit.CockpitSurfaces;
import com.eyups.roaddeck.ui.cockpit.CockpitTokens;
import com.eyups.roaddeck.ui.cockpit.MetricView;
import com.eyups.roaddeck.ui.widget.VehicleBodyStateView;
import com.eyups.roaddeck.ui.cockpit.Sparkline;
import com.eyups.roaddeck.ui.cockpit.StatusBadge;
import com.eyups.roaddeck.valet.ValetModeManager;
import com.eyups.roaddeck.vehicle.VehicleMetricsFormatter;
import com.eyups.roaddeck.vehicle.VehicleMetricsRepository;
import com.eyups.roaddeck.vehicle.VehicleMetricsSnapshot;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Kokpit (Home): 8 kolonlu telematik kart yerleşimi. Veri kaynakları ve callback'ler korunur.
 */
public class HomeTabBuilder implements VehicleMetricsRepository.Listener {

    public interface HomeCallback {
        void onNavigate(int tabIndex, String title);
        void log(String message);
    }

    private static final int TAB_VEHICLE_INFO = 9;
    private static final int TAB_VALET = 16;
    private static final int TAB_DRIVING_HISTORY = 14;
    private static final int TAB_EQUALIZER = 11;
    private static final String PREFS = "RoadDeckPrefs";
    private static final String KEY_ONBOARDING_DONE = "homeOnboardingDismissed";

    private final Context context;
    private final VehicleMetricsRepository repository;
    private final VehicleMetricsFormatter formatter;
    private final HomeCallback callback;
    private final SharedPreferences prefs;

    /**
     * Kısa pencere (araç: h800dp -> 744dp içerik; emülatör: h880dp -> 824dp) için sıkı yerleşim.
     * false iken kartlar geniş ekran ölçülerini kullanır.
     */
    private boolean dense;

    private ScrollView scrollView;
    private LinearLayout content;
    private CockpitGrid grid;
    private VehicleBodyStateView bodyStateView;
    private TextView bodySummaryView;
    private CockpitCard onboardingCard;
    private View lastDriveCard;
    private CockpitCard lyricsCard;
    private TextView lyricsBadge;
    private LyricsMediaCardView lyricsMedia;
    private LyricsEngine lyricsEngine;
    private volatile boolean tabVisible;

    public HomeTabBuilder(Context context, VehicleMetricsRepository repository, HomeCallback callback) {
        this.context = context;
        this.repository = repository;
        this.formatter = new VehicleMetricsFormatter(context);
        this.callback = callback;
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        build();
    }

    public ScrollView getScrollView() {
        return scrollView;
    }

    public void startListening() {
        tabVisible = true;
        // Last drive is built once with the tab; live trip updates are reflected on next open.
        repository.removeListener(this);
        repository.addListener(this);
        repository.refresh();
        if (lyricsEngine != null) {
            lyricsEngine.start();
        }
    }

    public void stopListening() {
        tabVisible = false;
        repository.removeListener(this);
        if (lyricsEngine != null) {
            lyricsEngine.stop();
        }
    }

    public void release() {
        stopListening();
    }

    private void build() {
        scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.TRANSPARENT);

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int m = CockpitTokens.dim(context, R.dimen.ck_margin);
        Configuration cfg = context.getResources().getConfiguration();
        boolean fillHeight = cfg.screenWidthDp >= 1000;
        // Gerçek içerik yüksekliği = pencere yüksekliği - üst bar. Araçta 800-56=744dp,
        // emülatörde 880-56=824dp. Kartlar bu alana sığacak şekilde ölçeklenir.
        float density = context.getResources().getDisplayMetrics().density;
        int topBarDp = Math.round(
                context.getResources().getDimension(R.dimen.ck_statusbar_h) / density);
        dense = cfg.screenHeightDp - topBarDp < 860;
        int topPad = CockpitTokens.dim(context,
                dense ? R.dimen.ck_space_sm : R.dimen.ck_space_md);
        int bottomPad = fillHeight
                ? CockpitTokens.dim(context, R.dimen.ck_space_sm)
                : m;
        content.setPadding(m, topPad, 0, bottomPad);
        scrollView.addView(content, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                fillHeight ? LinearLayout.LayoutParams.MATCH_PARENT
                        : LinearLayout.LayoutParams.WRAP_CONTENT));

        grid = new CockpitGrid(context);
        grid.setFillHeight(fillHeight);
        content.addView(grid, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                fillHeight ? 0 : LinearLayout.LayoutParams.WRAP_CONTENT,
                fillHeight ? 1f : 0f));

        LinearLayout leftColumn = new LinearLayout(context);
        leftColumn.setOrientation(LinearLayout.VERTICAL);
        leftColumn.addView(buildLyricsCard(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        View leftGap = new View(context);
        leftColumn.addView(leftGap, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                CockpitTokens.dim(context, R.dimen.ck_gutter)));
        leftColumn.addView(buildLastDriveCard(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout rightColumn = new LinearLayout(context);
        rightColumn.setOrientation(LinearLayout.VERTICAL);
        rightColumn.addView(buildVehicleCard(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // 5/3: medya birincil, araç durumu ikincil.
        grid.add(leftColumn, 5);
        grid.add(rightColumn, 3);
        View valet = buildValetCard();
        if (valet instanceof CockpitCard) {
            grid.addFullWidth(valet);
        }
        grid.finish();
    }

    private LinearLayout.LayoutParams columnCardParams(boolean fillHeight, float weight) {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                fillHeight ? 0 : LinearLayout.LayoutParams.WRAP_CONTENT,
                fillHeight ? weight : 0f);
    }

    private CockpitCard buildLyricsCard() {
        lyricsCard = new CockpitCard(context, "").span(4);
        lyricsCard.setMinimumHeight(dp(dense ? 300 : 320));

        buildLyricsHeader();
        lyricsEngine = new LyricsEngine(context);
        lyricsMedia = new LyricsMediaCardView(context, lyricsEngine,
                this::openLyricsFullscreen);
        lyricsCard.addContent(lyricsMedia, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(dense ? 220 : 230)));

        int pad = CockpitTokens.dim(context, R.dimen.ck_card_padding);
        View headerDivider = new View(context);
        headerDivider.setBackgroundColor(CockpitTokens.color(context, R.color.ck_stroke));
        LinearLayout.LayoutParams headerDividerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        headerDividerLp.leftMargin = -pad;
        headerDividerLp.rightMargin = -pad;
        headerDividerLp.topMargin = dp(8);
        lyricsCard.addView(headerDivider, 1, headerDividerLp);

        LinearLayout footer = lyricsCard.getFooter();
        footer.removeAllViews();
        footer.setGravity(Gravity.CENTER_VERTICAL);

        TextView fullscreenLink = new TextView(context);
        fullscreenLink.setText(R.string.ck_lyrics_fullscreen);
        styleFooterLink(fullscreenLink);
        fullscreenLink.setOnClickListener(v -> openLyricsFullscreen());
        footer.addView(fullscreenLink);

        TextView separator = new TextView(context);
        separator.setText("|");
        separator.setTextColor(CockpitTokens.color(context, R.color.ck_text_muted));
        separator.setPadding(dp(8), 0, dp(8), 0);
        footer.addView(separator);

        TextView equalizerLink = new TextView(context);
        equalizerLink.setText(R.string.ck_lyrics_equalizer);
        styleFooterLink(equalizerLink);
        equalizerLink.setOnClickListener(v -> callback.onNavigate(TAB_EQUALIZER,
                context.getString(R.string.ck_lyrics_equalizer)));
        footer.addView(equalizerLink);

        View footerDivider = new View(context);
        footerDivider.setBackgroundColor(CockpitTokens.color(context, R.color.ck_stroke));
        LinearLayout.LayoutParams footerDividerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        footerDividerLp.leftMargin = -pad;
        footerDividerLp.rightMargin = -pad;
        footerDividerLp.topMargin = dp(10);
        lyricsCard.addView(footerDivider, lyricsCard.getChildCount() - 1, footerDividerLp);

        lyricsEngine.addListener(this::onLyricsState);
        return lyricsCard;
    }

    private void buildLyricsHeader() {
        LinearLayout header = lyricsCard.getHeaderRow();
        TextView title = lyricsCard.getTitleView();
        header.removeAllViews();

        title.setText(R.string.ck_media_now_playing);
        title.setAllCaps(false);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f);
        title.setLetterSpacing(0f);
        title.setTextColor(CockpitTokens.color(context, R.color.ck_on_surface));
        title.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_SEMIBOLD));
        header.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        header.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1f));

        lyricsBadge = new TextView(context);
        lyricsBadge.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f);
        lyricsBadge.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_BOLD));
        lyricsBadge.setAllCaps(false);
        lyricsBadge.setLetterSpacing(0f);
        lyricsBadge.setGravity(Gravity.CENTER);
        lyricsBadge.setPadding(dp(14), dp(6), dp(14), dp(6));
        header.addView(lyricsBadge, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        updateLyricsBadge(R.string.ck_lyrics_badge_idle,
                CockpitTokens.color(context, R.color.ck_text_medium), CockpitTokens.color(context, R.color.ck_surface_2), CockpitSurfaces.alpha(CockpitTokens.color(context, R.color.ck_text_high), 0.20f));
    }

    private void styleFooterLink(TextView view) {
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f);
        view.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        view.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_SEMIBOLD));
        view.setGravity(android.view.Gravity.CENTER);
        view.setMinHeight(CockpitTokens.dim(context, R.dimen.ck_touch_min));
        view.setMinWidth(CockpitTokens.dim(context, R.dimen.ck_touch_min));
        view.setPadding(dp(8), dp(8), dp(8), dp(8));
        view.setOnClickListener(v -> { });
    }

    private void updateLyricsBadge(int textRes, int color, int background, int stroke) {
        if (lyricsBadge == null) {
            return;
        }
        lyricsBadge.setText(textRes);
        lyricsBadge.setTextColor(color);
        android.graphics.drawable.GradientDrawable bg = CockpitSurfaces.rounded(
                context, background, 999f);
        bg.setStroke(dp(1), stroke);
        lyricsBadge.setBackground(bg);
    }

    private void openLyricsFullscreen() {
        context.startActivity(new Intent(context, LyricsFullscreenActivity.class));
    }

    private void onLyricsState(LyricsState state) {
        if (lyricsMedia == null || lyricsBadge == null) {
            return;
        }
        lyricsMedia.render(state);
        switch (state.status) {
            case SYNCED:
                updateLyricsBadge(R.string.ck_lyrics_badge_synced,
                        CockpitTokens.color(context, R.color.ck_nominal), CockpitSurfaces.alpha(CockpitTokens.color(context, R.color.ck_nominal), 0.15f), CockpitSurfaces.alpha(CockpitTokens.color(context, R.color.ck_nominal), 0.40f));
                break;
            case PLAIN:
                updateLyricsBadge(R.string.ck_lyrics_badge_plain,
                        CockpitTokens.color(context, R.color.ck_caution), CockpitSurfaces.alpha(CockpitTokens.color(context, R.color.ck_caution), 0.15f), CockpitSurfaces.alpha(CockpitTokens.color(context, R.color.ck_caution), 0.40f));
                break;
            case LOADING:
                updateLyricsBadge(R.string.ck_lyrics_badge_loading,
                        CockpitTokens.color(context, R.color.ck_text_medium), CockpitTokens.color(context, R.color.ck_surface_2), CockpitSurfaces.alpha(CockpitTokens.color(context, R.color.ck_text_high), 0.20f));
                break;
            case NO_ACCESS:
                updateLyricsBadge(R.string.ck_lyrics_badge_no_access,
                        CockpitTokens.color(context, R.color.ck_critical), CockpitSurfaces.alpha(CockpitTokens.color(context, R.color.ck_critical), 0.15f), CockpitSurfaces.alpha(CockpitTokens.color(context, R.color.ck_critical), 0.40f));
                break;
            case NO_SESSION:
                updateLyricsBadge(R.string.ck_lyrics_badge_idle,
                        CockpitTokens.color(context, R.color.ck_text_medium), CockpitTokens.color(context, R.color.ck_surface_2), CockpitSurfaces.alpha(CockpitTokens.color(context, R.color.ck_text_high), 0.20f));
                break;
            case ERROR:
                updateLyricsBadge(R.string.ck_lyrics_badge_offline,
                        CockpitTokens.color(context, R.color.ck_caution), CockpitSurfaces.alpha(CockpitTokens.color(context, R.color.ck_caution), 0.15f), CockpitSurfaces.alpha(CockpitTokens.color(context, R.color.ck_caution), 0.40f));
                break;
            default:
                updateLyricsBadge(R.string.ck_lyrics_badge_none,
                        CockpitTokens.color(context, R.color.ck_text_medium), CockpitTokens.color(context, R.color.ck_surface_2), CockpitSurfaces.alpha(CockpitTokens.color(context, R.color.ck_text_high), 0.20f));
                break;
        }
    }

    private CockpitCard buildVehicleCard() {
        CockpitCard card = new CockpitCard(context, context.getString(R.string.ck_body_title)).span(4);

        bodyStateView = new VehicleBodyStateView(context);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(240));
        card.addContent(bodyStateView, bodyLp);
        LinearLayout.LayoutParams bodyParams = (LinearLayout.LayoutParams) card.getBody().getLayoutParams();
        bodyParams.height = dp(240);
        bodyParams.weight = 0f;
        card.getBody().setLayoutParams(bodyParams);

        bodySummaryView = new TextView(context);
        CockpitTokens.bodySm(bodySummaryView);
        bodySummaryView.setTextColor(CockpitTokens.color(context, R.color.ck_nominal));
        LinearLayout.LayoutParams summaryLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        card.getFooter().addView(bodySummaryView, summaryLp);

        return card;
    }

    private CockpitButton quick(String label, int tab, String title) {
        CockpitButton b = CockpitButton.secondary(context, label);
        b.setMinHeight(CockpitTokens.dim(context, R.dimen.ck_touch_primary));
        b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
        b.setOnClickListener(v -> callback.onNavigate(tab, title));
        return b;
    }

    private View buildValetCard() {
        if (!ValetModeManager.isActive(context)) {
            return new View(context);
        }
        CockpitCard card = new CockpitCard(context, context.getString(R.string.ck_home_valet)).span(8);
        card.status(StatusBadge.advisory(context, context.getString(R.string.ck_home_active)));
        TextView body = new TextView(context);
        CockpitTokens.bodyMd(body);
        body.setText(context.getString(R.string.ck_home_valet_body));
        body.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        card.addContent(body);
        return card;
    }

    private CockpitCard buildOnboardingCard() {
        onboardingCard = new CockpitCard(context, context.getString(R.string.ck_home_onboarding_title)).span(8);
        onboardingCard.status(StatusBadge.nominal(context, context.getString(R.string.ck_home_setup)));
        if (prefs.getBoolean(KEY_ONBOARDING_DONE, false)) {
            onboardingCard.setVisibility(View.GONE);
            return onboardingCard;
        }

        TextView body = new TextView(context);
        CockpitTokens.bodyMd(body);
        body.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        body.setText(context.getString(R.string.ck_home_onboarding_body));
        onboardingCard.addContent(body);

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
        onboardingCard.addContent(actions, actionsLp);

        CockpitButton vehicle = CockpitButton.primary(context, context.getString(R.string.ck_home_vehicle_info));
        vehicle.setOnClickListener(v -> {
            dismissOnboarding();
            callback.onNavigate(TAB_VEHICLE_INFO, context.getString(R.string.ck_home_vehicle_info));
        });
        vehicle.setMinWidth(CockpitTokens.dim(context, R.dimen.ck_action_min_w));
        vehicle.setMaxWidth(CockpitTokens.dim(context, R.dimen.ck_action_max_w));
        actions.addView(vehicle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        CockpitButton start = CockpitButton.secondary(context, context.getString(R.string.ck_home_start));
        start.setOnClickListener(v -> dismissOnboarding());
        start.setMinWidth(CockpitTokens.dim(context, R.dimen.ck_action_min_w));
        start.setMaxWidth(CockpitTokens.dim(context, R.dimen.ck_action_max_w));
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        startLp.leftMargin = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        actions.addView(start, startLp);
        return onboardingCard;
    }

    private CockpitCard buildLastDriveCard() {
        CockpitCard card = new CockpitCard(context,
                context.getString(R.string.ck_home_last_drive)).span(4);
        card.getTitleView().setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18);
        if (dense) {
            int pad = dp(16);
            card.setPadding(pad, pad, pad, pad);
        }
        DrivingSessionRecorder recorder = DrivingSessionRecorder.get(context);
        DrivingTrip trip = recorder.isActive() ? recorder.getDisplayTrip() : recorder.getLastTrip();
        if (trip == null) {
            List<DrivingTrip> history = DrivingTripStore.loadHistory(context);
            if (!history.isEmpty()) {
                trip = history.get(0);
            }
        }
        if (trip == null) {
            TextView empty = new TextView(context);
            CockpitTokens.bodyMd(empty);
            empty.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
            empty.setText(context.getString(R.string.ck_home_no_drive));
            card.addContent(empty);
            card.getFooter().setVisibility(View.GONE);
            return card;
        }

        StatusBadge badge = StatusBadge.nominal(context, recorder.isActive()
                ? context.getString(R.string.ck_home_in_progress)
                : context.getString(R.string.ck_home_completed));

        CockpitButton open = CockpitButton.secondary(context,
                context.getString(R.string.ck_home_view_all) + " →");
        open.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
        open.setMinHeight(dp(dense ? 40 : 48));
        open.setOnClickListener(v -> callback.onNavigate(TAB_DRIVING_HISTORY,
                context.getString(R.string.ck_home_drive_report)));

        android.widget.TextView titleView = card.getTitleView();
        LinearLayout.LayoutParams titleLp = (LinearLayout.LayoutParams) titleView.getLayoutParams();
        titleLp.weight = 0f;
        titleLp.width = LinearLayout.LayoutParams.WRAP_CONTENT;
        titleView.setLayoutParams(titleLp);
        titleView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, dense ? 24 : 27);

        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        badgeLp.leftMargin = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        card.getHeaderRow().addView(badge, badgeLp);

        View headerSpacer = new View(context);
        card.getHeaderRow().addView(headerSpacer, new LinearLayout.LayoutParams(
                0, 1, 1f));
        card.status(open);

        // Telemetri özeti: solda skor halkası + sürüş profili, sağda metrik kartları.
        LinearLayout hero = new LinearLayout(context);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        int heroPad = dp(dense ? 8 : 10);
        hero.setPadding(heroPad, heroPad, heroPad, heroPad);
        GradientDrawable heroBg = new GradientDrawable();
        heroBg.setColor(CockpitTokens.color(context, R.color.ck_surface_2));
        heroBg.setCornerRadius(dp((int) CockpitTokens.RADIUS_CARD_DP));
        hero.setBackground(heroBg);

        DrivingScoreRingView scoreRing = new DrivingScoreRingView(context);
        scoreRing.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_SEMIBOLD));
        scoreRing.setAccentColor(CockpitTokens.color(context, R.color.ck_accent));
        scoreRing.setTrackColor(CockpitTokens.color(context, R.color.ck_surface_1));
        scoreRing.setScore(trip.score, false);
        int ring = dp(dense ? 60 : 78);
        hero.addView(scoreRing, new LinearLayout.LayoutParams(ring, ring));

        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        copyLp.leftMargin = dp(10);
        hero.addView(copy, copyLp);

        TextView profileLabel = new TextView(context);
        CockpitTokens.labelCaps(profileLabel);
        profileLabel.setText("SÜRÜŞ PROFİLİ");
        profileLabel.setTextColor(CockpitTokens.color(context, R.color.ck_accent));
        copy.addView(profileLabel);

        TextView profile = new TextView(context);
        CockpitTokens.title(profile);
        profile.setText(trip.title == null || trip.title.isEmpty()
                ? context.getString(R.string.ck_home_drive) : trip.title);
        copy.addView(profile);

        TextView profileMeta = new TextView(context);
        CockpitTokens.bodySm(profileMeta);
        SimpleDateFormat fmt = new SimpleDateFormat("d MMM · HH:mm", new Locale("tr", "TR"));
        profileMeta.setText(fmt.format(new Date(trip.startTimeMs))
                + " · " + String.format(Locale.getDefault(), "%.1f km", trip.distanceKm));
        profileMeta.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        copy.addView(profileMeta);

        int ecoLevel = trip.score >= 85 ? StatusBadge.NOMINAL
                : (trip.score >= 65 ? StatusBadge.ADVISORY : StatusBadge.NEUTRAL);
        String ecoText = context.getString(trip.score >= 85
                ? R.string.ck_home_eco_high
                : (trip.score >= 65 ? R.string.ck_home_eco_medium : R.string.ck_home_eco_low));
        StatusBadge ecoBadge = StatusBadge.neutral(context, ecoText);
        ecoBadge.setLevel(ecoLevel);
        LinearLayout.LayoutParams ecoLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ecoLp.leftMargin = dp(8);
        hero.addView(ecoBadge, ecoLp);

        card.addContent(hero);

        if (trip.speedSamples != null && trip.speedSamples.size() >= 2) {
            LinearLayout chartHeader = new LinearLayout(context);
            chartHeader.setOrientation(LinearLayout.HORIZONTAL);
            chartHeader.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams chartHeaderLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            chartHeaderLp.topMargin = dp(6);
            card.addContent(chartHeader, chartHeaderLp);

            TextView chartLabel = new TextView(context);
            CockpitTokens.labelCaps(chartLabel);
            chartLabel.setText("HIZ EĞRİSİ");
            chartLabel.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
            chartHeader.addView(chartLabel, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            StatusBadge chartMax = StatusBadge.neutral(context,
                    context.getString(R.string.ck_home_max_speed, trip.maxSpeedKmh));
            chartHeader.addView(chartMax);

            Sparkline sparkline = new Sparkline(context);
            int sampleCount = Math.min(60, trip.speedSamples.size());
            float[] data = new float[sampleCount];
            int stride = Math.max(1, trip.speedSamples.size() / sampleCount);
            for (int i = 0; i < sampleCount; i++) {
                data[i] = trip.speedSamples.get(Math.min(trip.speedSamples.size() - 1, i * stride));
            }
            sparkline.setData(data);
            LinearLayout chartBox = new LinearLayout(context);
            chartBox.setOrientation(LinearLayout.VERTICAL);
            chartBox.setPadding(dp(dense ? 4 : 8), dp(dense ? 4 : 6),
                    dp(dense ? 4 : 8), dp(dense ? 4 : 6));
            CockpitSurfaces.level2(chartBox);
            chartBox.addView(sparkline, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(dense ? 58 : 72)));
            LinearLayout.LayoutParams chartLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            chartLp.topMargin = dp(3);
            card.addContent(chartBox, chartLp);
        }

        LinearLayout metrics = new LinearLayout(context);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.setBaselineAligned(false);
        LinearLayout.LayoutParams metricsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        metricsLp.topMargin = dp(dense ? 4 : 5);
        card.addContent(metrics, metricsLp);

        metrics.addView(buildTelemetryMetric(
                        context.getString(R.string.driving_history_metric_distance),
                        String.format(Locale.getDefault(), "%.1f", trip.distanceKm),
                        "km", "Süre " + formatDuration(trip.durationSec), R.drawable.ic_mdi_road_variant),
                weightedMetric(0));
        metrics.addView(buildTelemetryMetric(
                        context.getString(R.string.driving_history_metric_consumption),
                        trip.consumptionL100 > 0
                                ? String.format(Locale.getDefault(), "%.1f", trip.consumptionL100) : "—",
                        "L/100", "Ortalama", R.drawable.ic_mdi_lightning_bolt),
                weightedMetric(1));
        metrics.addView(buildTelemetryMetric(
                        context.getString(R.string.driving_history_metric_avg),
                        String.valueOf(trip.avgSpeedKmh),
                        "km/h", "Maks " + trip.maxSpeedKmh, R.drawable.ic_mdi_speedometer),
                weightedMetric(2));

        return card;
    }

    private LinearLayout buildTelemetryMetric(String label, String value,
            String unit, String caption, int iconRes) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(dense ? 6 : 8), dp(dense ? 5 : 6),
                dp(dense ? 6 : 8), dp(dense ? 5 : 6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CockpitTokens.color(context, R.color.ck_surface_2));
        bg.setCornerRadius(dp((int) CockpitTokens.RADIUS_CARD_DP));
        box.setBackground(bg);

        LinearLayout labelRow = new LinearLayout(context);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView labelView = new TextView(context);
        CockpitTokens.labelCaps(labelView);
        labelView.setText(label);
        labelView.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        labelRow.addView(labelView, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setColorFilter(CockpitTokens.color(context, R.color.ck_text_medium));
        labelRow.addView(icon, new LinearLayout.LayoutParams(dp(16), dp(16)));
        box.addView(labelRow);

        LinearLayout valueRow = new LinearLayout(context);
        valueRow.setOrientation(LinearLayout.HORIZONTAL);
        valueRow.setGravity(Gravity.BOTTOM);
        TextView valueView = new TextView(context);
        CockpitTokens.title(valueView);
        valueView.setText(value);
        valueView.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        valueRow.addView(valueView);
        TextView unitView = new TextView(context);
        CockpitTokens.bodySm(unitView);
        unitView.setText(unit);
        unitView.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        LinearLayout.LayoutParams unitLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        unitLp.leftMargin = dp(4);
        unitLp.bottomMargin = dp(3);
        valueRow.addView(unitView, unitLp);
        box.addView(valueRow);

        TextView captionView = new TextView(context);
        CockpitTokens.bodySm(captionView);
        captionView.setText(caption);
        captionView.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        box.addView(captionView);
        return box;
    }

    private LinearLayout.LayoutParams weightedMetric(int index) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (index > 0) {
            lp.leftMargin = dp(8);
        }
        return lp;
    }

    private String formatDuration(int seconds) {
        int minutes = Math.max(0, seconds) / 60;
        if (minutes < 60) {
            return minutes + " dk";
        }
        return (minutes / 60) + " sa " + (minutes % 60) + " dk";
    }

    @Override
    public void onMetricsUpdated(VehicleMetricsSnapshot snapshot) {
        if (!tabVisible) {
            return;
        }
        if (bodyStateView != null) {
            bodyStateView.update(snapshot);
        }
        if (!snapshot.connected) {
            if (bodySummaryView != null) {
                bodySummaryView.setText(context.getString(R.string.ck_home_connecting));
                bodySummaryView.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
            }
            return;
        }
        boolean open = bodyStateView != null && bodyStateView.hasOpenBody();
        if (bodySummaryView != null) {
            bodySummaryView.setText(open
                    ? context.getString(R.string.ck_body_panel_open)
                    : context.getString(R.string.ck_body_all_closed));
            bodySummaryView.setTextColor(CockpitTokens.color(context,
                    open ? R.color.ck_body_warning : R.color.ck_nominal));
        }
    }

    private void dismissOnboarding() {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply();
        if (onboardingCard != null) {
            onboardingCard.setVisibility(View.GONE);
        }
    }

    private int dp(int value) {
        return CockpitTokens.dp(context, value);
    }

}
