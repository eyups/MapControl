package com.eyups.roaddeck.lyrics;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.animation.DecelerateInterpolator;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.eyups.roaddeck.R;
import com.eyups.roaddeck.ui.cockpit.CockpitSurfaces;
import com.eyups.roaddeck.ui.cockpit.CockpitTokens;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Tam ekran şarkı sözü ekranı — Stitch "Tam Ekran Şarkı Sözü" tasarımıyla birebir:
 * üstte medya kartına dönüş / canlı senkronizasyon / parça bilgisi / Dolby+saat / kapat,
 * ortada önceki-güncel-sonraki satırlar, sağda punto ve tema kontrolleri,
 * altta arama çubuğu ve kokpit ses kontrolleri.
 */
public final class LyricsFullscreenActivity extends AppCompatActivity {

    private static final String PREFS = "RoadDeckPrefs";
    private static final String KEY_DARK = "lyricsFullscreenDarkV2";

    private static final int DARK_BG = 0xFF0B0E12;
    private static final int DARK_PANEL = 0xFF191C20;
    private static final int DARK_CARD = 0xFF23272E;
    private static final int DARK_BORDER = 0xFF3D494D;
    private static final int DARK_TEXT = 0xFFE1E2E8;
    private static final int DARK_MUTED = 0xFFBCC9CE;
    private static final int DARK_DIM = 0xFF64748B;
    private static final int DARK_FAINT = 0xFF94A3B8;
    private static final int DARK_ACCENT = 0xFF4CD6FB;
    private static final int DARK_SECONDARY = 0xFF46EEE0;

    private static final int LIGHT_BG = 0xFFFFFFFF;
    private static final int LIGHT_PANEL = 0xFFF1F5F9;
    private static final int LIGHT_CARD = 0xFFE2E8F0;
    private static final int LIGHT_BORDER = 0xFFCBD5E1;
    private static final int LIGHT_TEXT = 0xFF0B0E12;
    private static final int LIGHT_MUTED = 0xFF475569;
    private static final int LIGHT_DIM = 0xFF64748B;
    private static final int LIGHT_FAINT = 0xFF64748B;
    private static final int LIGHT_ACCENT = 0xFF0891B2;
    private static final int LIGHT_SECONDARY = 0xFF0D9488;

    private LyricsEngine engine;
    private SharedPreferences prefs;
    private boolean darkMode = true;
    private float lyricScale = 1f;
    private LyricsState lastState;
    private boolean userSeeking;
    private boolean compact;
    private boolean splitMode;
    private boolean shuffleOn;
    private int repeatMode;
    private long lastPositionMs;
    private long lastPositionAt;
    private long trackDurationMs;
    private boolean playing;
    private LyricLine currentLyricLine;
    private int lastLineIndex = -1;
    private String lastTrackKey;
    private int lineAnimToken;
    private boolean lineAnimating;
    private boolean frameLoopRunning;
    private int lastSeekProgress = -1;
    private long lastElapsedSecond = -1L;
    private long lastFrameAt;
    private static final long FRAME_INTERVAL_MS = 33L;
    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!frameLoopRunning) {
                return;
            }
            long now = SystemClock.elapsedRealtime();
            if (now - lastFrameAt >= FRAME_INTERVAL_MS) {
                lastFrameAt = now;
                updateLiveProgress();
            }
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private FrameLayout root;
    private LinearLayout topBar;
    private LinearLayout footer;
    private View topDivider;
    private View headerPillDivider;
    private View dividerTag;
    private final int[] gradientRetry = {0};
    private View bottomDivider;
    private LinearLayout backPill;
    private ImageView backIcon;
    private TextView backText;
    private LinearLayout livePill;
    private View liveDot;
    private TextView liveText;
    private ImageView artView;
    private TextView titleView;
    private TextView artistView;
    private ImageView clockIcon;
    private TextView clockText;
    private ImageView closeButton;
    private TextView syncLabel;
    private TextView prevLine;
    private TextView currentLine;
    private TextView ghostLine;
    private FrameLayout textFrame;
    private FrameLayout artistPhotoCard;
    private ImageView artistPhoto;
    private TextView artistInitial;
    private String lastArtistKey;
    private int artistPhotoToken;
    private ValueAnimator heightAnimator;
    private View currentBar;
    private TextView next1;
    private TextView next2;
    private TextView next3;
    private TextView messageView;
    private BarView lineBar;
    private LinearLayout rightStack;
    private TextView biggerButton;
    private TextView smallerButton;
    private ImageView themeButton;
    private SeekBar seekBar;
    private TextView elapsedView;
    private TextView durationView;
    private ImageView speakerIcon;
    private TextView audioTitle;
    private String lastSource;
    private ImageView shuffleButton;
    private ImageView prevButton;
    private ImageView playButton;
    private ImageView nextButton;
    private ImageView repeatButton;

    private int colorBg;
    private int colorPanel;
    private int colorCard;
    private int colorBorder;
    private int colorText;
    private int colorMuted;
    private int colorDim;
    private int colorFaint;
    private int colorAccent;
    private int colorSecondary;

    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat clockFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            if (clockText != null) {
                clockText.setText(clockFormat.format(new Date()));
            }
            clockHandler.postDelayed(this, 30_000L);
        }
    };

    private final LyricsEngine.Listener lyricsListener = this::render;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        compact = getResources().getConfiguration().screenWidthDp < 1000;
        splitMode = getIntent().getBooleanExtra("com.eyups.roaddeck.extra.SPLIT_MODE", false);
        if (!splitMode) {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        }
        if (!prefs.contains(KEY_DARK)) {
            prefs.edit().putBoolean(KEY_DARK, true).apply();
        }
        darkMode = prefs.getBoolean(KEY_DARK, true);
        lyricScale = prefs.getFloat("lyricsFullscreenScale", 1f);
        engine = new LyricsEngine(this);
        buildUi();
        applyTheme();
        applyLyricScale();
        applyCurrentGradient();
        keepScreenOn();
        hideSystemBars();
        clockHandler.post(clockTick);
    }

    @Override
    protected void onStart() {
        super.onStart();
        engine.addListener(lyricsListener);
        engine.start();
        clockHandler.removeCallbacks(clockTick);
        clockHandler.post(clockTick);
        setFrameLoop(false);
    }

    @Override
    protected void onStop() {
        engine.removeListener(lyricsListener);
        engine.stop();
        clockHandler.removeCallbacks(clockTick);
        setFrameLoop(false);
        super.onStop();
    }

    // ------------------------------------------------------------------ UI

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(DARK_BG);

        GlowView glow = new GlowView(this);
        root.addView(glow, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        topBar = buildTopBar();
        column.addView(topBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64)));

        topDivider = new View(this);
        column.addView(topDivider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));

        LinearLayout subtitleRow = new LinearLayout(this);
        subtitleRow.setOrientation(LinearLayout.HORIZONTAL);
        subtitleRow.setPadding(dp(compact ? 24 : (splitMode ? 64 : 96)), 0, 0, 0);
        syncLabel = new TextView(this);
        syncLabel.setText(R.string.ck_fs_sync_label);
        syncLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        syncLabel.setAllCaps(true);
        syncLabel.setLetterSpacing(0.12f);
        syncLabel.setTypeface(CockpitTokens.inter(this, CockpitTokens.WEIGHT_BOLD));
        subtitleRow.addView(syncLabel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams subtitleRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleRowLp.topMargin = dp(10);
        column.addView(subtitleRow, subtitleRowLp);

        LinearLayout stageRow = new LinearLayout(this);
        stageRow.setOrientation(LinearLayout.HORIZONTAL);
        column.addView(stageRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        stageRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1,
                compact ? 0.06f : (splitMode ? 0.10f : 0.16f)));

        LinearLayout stage = new LinearLayout(this);
        stage.setOrientation(LinearLayout.VERTICAL);
        stage.setGravity(Gravity.CENTER_VERTICAL);
        stageRow.addView(stage, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                compact ? 0.88f : (splitMode ? 0.80f : 0.46f)));

        prevLine = lyricLine(22f, false, 1);
        stage.addView(prevLine);

        LinearLayout currentRow = new LinearLayout(this);
        currentRow.setOrientation(LinearLayout.HORIZONTAL);
        currentRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams currentRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        currentRowLp.topMargin = dp(10);
        stage.addView(currentRow, currentRowLp);

        currentBar = new View(this);
        GradientDrawable barBg = new GradientDrawable();
        barBg.setShape(GradientDrawable.RECTANGLE);
        barBg.setCornerRadius(dp(2));
        currentBar.setBackground(barBg);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(4),
                LinearLayout.LayoutParams.MATCH_PARENT);
        barLp.rightMargin = dp(16);
        currentRow.addView(currentBar, barLp);

        textFrame = new FrameLayout(this);
        currentRow.addView(textFrame, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        currentLine = lyricLine(52f, true, 3);
        currentLine.setLetterSpacing(-0.01f);
        textFrame.addView(currentLine, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        ghostLine = lyricLine(52f, true, 3);
        ghostLine.setLetterSpacing(-0.01f);
        ghostLine.setAlpha(0f);
        textFrame.addView(ghostLine, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        lineBar = new BarView(this);
        LinearLayout.LayoutParams lineBarLp = new LinearLayout.LayoutParams(dp(288), dp(6));
        lineBarLp.topMargin = dp(14);
        stage.addView(lineBar, lineBarLp);

        next1 = lyricLine(26f, true, 1);
        LinearLayout.LayoutParams next1Lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        next1Lp.topMargin = dp(18);
        stage.addView(next1, next1Lp);

        next2 = lyricLine(21f, false, 1);
        LinearLayout.LayoutParams next2Lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        next2Lp.topMargin = dp(8);
        stage.addView(next2, next2Lp);

        next3 = lyricLine(18f, false, 1);
        LinearLayout.LayoutParams next3Lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        next3Lp.topMargin = dp(6);
        stage.addView(next3, next3Lp);

        messageView = new TextView(this);
        messageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        messageView.setVisibility(View.GONE);
        stage.addView(messageView);

        stageRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1,
                compact ? 0.06f : (splitMode ? 0.10f : 0.38f)));

        rightStack = buildRightStack();
        if (!compact) {
            LinearLayout.LayoutParams stackLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            stackLp.gravity = Gravity.CENTER_HORIZONTAL;
            stackLp.topMargin = dp(2);
            stackLp.bottomMargin = dp(6);
            column.addView(rightStack, stackLp);
        }

        footer = buildFooter();
        column.addView(footer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        buildArtistPhotoCard();


        if (compact) {
            livePill.setVisibility(View.GONE);
        }

        setContentView(root);
    }

    private void buildArtistPhotoCard() {
        if (compact || splitMode) {
            return;
        }
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int cardW = (int) (screenW * 0.27f);
        int cardH = Math.min((int) (cardW * 1.28f), (int) (screenH * 0.60f));

        artistPhotoCard = new FrameLayout(this);
        GradientDrawable bg = CockpitSurfaces.rounded(this, 0xFF121820, 20f);
        bg.setStroke(dp(1), 0xFF3D494D);
        artistPhotoCard.setBackground(bg);
        artistPhotoCard.setClipToOutline(true);

        artistPhoto = new ImageView(this);
        artistPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artistPhoto.setAlpha(0f);
        artistPhotoCard.addView(artistPhoto, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        artistInitial = new TextView(this);
        artistInitial.setGravity(Gravity.CENTER);
        artistInitial.setTextSize(TypedValue.COMPLEX_UNIT_SP, 110f);
        artistInitial.setTypeface(CockpitTokens.inter(this, CockpitTokens.WEIGHT_BOLD));
        artistInitial.setTextColor(0x554CD6FB);
        artistPhotoCard.addView(artistInitial, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x990B0E12}));
        artistPhotoCard.addView(scrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(120), Gravity.BOTTOM));

        // Söz bloğunun bittiği %62'den ekranın sağına kadar kalan alanda yatay ortalama,
        // dikeyde de üst/alt eşit olacak şekilde ortalama.
        int lyricsEnd = Math.round(screenW * 0.62f);
        int freeArea = Math.max(cardW, screenW - lyricsEnd);
        int leftMargin = lyricsEnd + Math.max(0, (freeArea - cardW) / 2);
        int topMargin = Math.max(dp(16), (screenH - cardH) / 2);
        FrameLayout.LayoutParams photoLp = new FrameLayout.LayoutParams(cardW, cardH);
        photoLp.gravity = Gravity.START | Gravity.TOP;
        photoLp.leftMargin = leftMargin;
        photoLp.topMargin = topMargin;
        root.addView(artistPhotoCard, photoLp);
    }

    private void loadArtistImage(String artist) {
        final int token = ++artistPhotoToken;
        artistInitial.setText(initialOf(artist));
        artistInitial.setAlpha(0.45f);
        artistPhoto.animate().cancel();
        artistPhoto.setAlpha(0f);
        ArtistImageRepository.get(this).load(artist, bitmap -> {
            if (token != artistPhotoToken) {
                return;
            }
            if (bitmap != null) {
                artistPhoto.setImageBitmap(bitmap);
                artistPhoto.setAlpha(0f);
                artistPhoto.animate().alpha(1f).setDuration(420).start();
                artistInitial.animate().alpha(0f).setDuration(280).start();
            } else {
                artistPhoto.setImageDrawable(null);
                artistPhoto.setAlpha(0f);
                artistInitial.animate().alpha(0.45f).setDuration(200).start();
            }
        });
    }

    private static String initialOf(String artist) {
        if (artist == null || artist.trim().isEmpty()) {
            return "♪";
        }
        String trimmed = artist.trim();
        return trimmed.substring(0, 1).toUpperCase(new Locale("tr", "TR"));
    }

    private LinearLayout buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(24), 0, dp(24), 0);

        backPill = new LinearLayout(this);
        backPill.setOrientation(LinearLayout.HORIZONTAL);
        backPill.setGravity(Gravity.CENTER_VERTICAL);
        backPill.setPadding(dp(14), dp(8), dp(18), dp(8));
        backPill.setClickable(true);
        backPill.setFocusable(true);
        backPill.setOnClickListener(v -> finish());
        bar.addView(backPill, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)));

        backIcon = new ImageView(this);
        backIcon.setImageResource(R.drawable.ic_mdi_arrow_back);
        backPill.addView(backIcon, new LinearLayout.LayoutParams(dp(20), dp(20)));

        backText = new TextView(this);
        backText.setText(R.string.ck_fs_back);
        backText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        backText.setTypeface(CockpitTokens.inter(this, CockpitTokens.WEIGHT_SEMIBOLD));
        LinearLayout.LayoutParams backTextLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        backTextLp.leftMargin = dp(8);
        backPill.addView(backText, backTextLp);

        View divider = new View(this);
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(dp(1), dp(24));
        dividerLp.leftMargin = dp(16);
        dividerLp.rightMargin = dp(16);
        bar.addView(divider, dividerLp);
        headerPillDivider = divider;

        livePill = new LinearLayout(this);
        livePill.setOrientation(LinearLayout.HORIZONTAL);
        livePill.setGravity(Gravity.CENTER_VERTICAL);
        livePill.setPadding(dp(12), dp(5), dp(14), dp(5));
        bar.addView(livePill, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        liveDot = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        livePill.addView(liveDot, new LinearLayout.LayoutParams(dp(8), dp(8)));

        liveText = new TextView(this);
        liveText.setText(R.string.ck_fs_live);
        liveText.setAllCaps(true);
        liveText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        liveText.setLetterSpacing(0.1f);
        liveText.setTypeface(CockpitTokens.inter(this, CockpitTokens.WEIGHT_BOLD));
        LinearLayout.LayoutParams liveTextLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        liveTextLp.leftMargin = dp(8);
        livePill.addView(liveText, liveTextLp);

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.HORIZONTAL);
        center.setGravity(Gravity.CENTER);
        bar.addView(center, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        artView = new ImageView(this);
        artView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artView.setImageResource(R.drawable.ic_mdi_music_note);
        artView.setPadding(dp(10), dp(10), dp(10), dp(10));
        center.addView(artView, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        infoLp.leftMargin = dp(12);
        center.addView(info, infoLp);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        info.addView(titleRow);

        titleView = new TextView(this);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        titleView.setTypeface(CockpitTokens.inter(this, CockpitTokens.WEIGHT_BOLD));
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setMaxWidth(dp(260));
        titleRow.addView(titleView);

        artistView = new TextView(this);
        artistView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        artistView.setSingleLine(true);
        artistView.setEllipsize(TextUtils.TruncateAt.END);
        artistView.setMaxWidth(dp(320));
        info.addView(artistView);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.HORIZONTAL);
        right.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(right, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.HORIZONTAL);
        clock.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams clockLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clockLp.leftMargin = dp(18);
        right.addView(clock, clockLp);

        clockIcon = new ImageView(this);
        clockIcon.setImageResource(R.drawable.ic_mdi_schedule);
        clock.addView(clockIcon, new LinearLayout.LayoutParams(dp(18), dp(18)));

        clockText = new TextView(this);
        clockText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        clockText.setTypeface(CockpitTokens.mono(this));
        clockText.setText(clockFormat.format(new Date()));
        LinearLayout.LayoutParams clockTextLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clockTextLp.leftMargin = dp(6);
        clock.addView(clockText, clockTextLp);

        closeButton = iconButton(R.drawable.ic_mdi_close, dp(48), 12f, null);
        closeButton.setContentDescription(getString(R.string.ck_lyrics_fullscreen_close));
        closeButton.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        closeLp.leftMargin = dp(16);
        right.addView(closeButton, closeLp);

        return bar;
    }

    private LinearLayout buildFooter() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(dp(24), dp(8), dp(24), dp(10));

        bottomDivider = new View(this);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        divLp.bottomMargin = dp(6);
        bar.addView(bottomDivider, divLp);

        LinearLayout progressRow = new LinearLayout(this);
        progressRow.setOrientation(LinearLayout.HORIZONTAL);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(progressRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        elapsedView = new TextView(this);
        elapsedView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        elapsedView.setTypeface(CockpitTokens.mono(this));
        elapsedView.setGravity(Gravity.END);
        progressRow.addView(elapsedView, new LinearLayout.LayoutParams(dp(56),
                LinearLayout.LayoutParams.WRAP_CONTENT));

        seekBar = new SeekBar(this);
        seekBar.setMax(1000);
        seekBar.setContentDescription(getString(R.string.ck_fs_seek));
        LinearLayout.LayoutParams seekLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        seekLp.leftMargin = dp(12);
        seekLp.rightMargin = dp(12);
        progressRow.addView(seekBar, seekLp);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && lastState != null && lastState.durationMs > 0) {
                    long ms = lastState.durationMs * progress / 1000L;
                    elapsedView.setText(formatTime(ms));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                userSeeking = false;
                if (lastState != null && lastState.durationMs > 0) {
                    engine.seekTo(lastState.durationMs * seekBar.getProgress() / 1000L);
                }
            }
        });

        durationView = new TextView(this);
        durationView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        durationView.setTypeface(CockpitTokens.mono(this));
        progressRow.addView(durationView, new LinearLayout.LayoutParams(dp(56),
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams controlsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        controlsLp.topMargin = dp(4);
        bar.addView(controls, controlsLp);

        LinearLayout audio = new LinearLayout(this);
        audio.setOrientation(LinearLayout.HORIZONTAL);
        audio.setGravity(Gravity.CENTER_VERTICAL);
        controls.addView(audio, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        speakerIcon = iconButton(R.drawable.ic_mdi_speaker, dp(40), 10f, null);
        audio.addView(speakerIcon, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout audioInfo = new LinearLayout(this);
        audioInfo.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams audioInfoLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        audioInfoLp.leftMargin = dp(10);
        audio.addView(audioInfo, audioInfoLp);

        audioTitle = new TextView(this);
        audioTitle.setText("Medya");
        audioTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        audioTitle.setTypeface(CockpitTokens.inter(this, CockpitTokens.WEIGHT_BOLD));
        audioInfo.addView(audioTitle);

        LinearLayout transport = new LinearLayout(this);
        transport.setOrientation(LinearLayout.HORIZONTAL);
        transport.setGravity(Gravity.CENTER);
        controls.addView(transport, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        shuffleButton = iconButton(R.drawable.ic_mdi_shuffle, dp(48), 10f, null);
        shuffleButton.setContentDescription(getString(R.string.ck_fs_shuffle));
        shuffleButton.setOnClickListener(v -> {
            shuffleOn = !shuffleOn;
            shuffleButton.setColorFilter(shuffleOn ? colorAccent : colorMuted);
        });
        transport.addView(shuffleButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        prevButton = iconButton(R.drawable.ic_mdi_skip_previous, dp(50), 14f,
                new int[]{colorCard, colorBorder});
        prevButton.setContentDescription(getString(R.string.ck_lyrics_prev));
        prevButton.setOnClickListener(v -> engine.skipPrevious());
        LinearLayout.LayoutParams prevLp = new LinearLayout.LayoutParams(dp(50), dp(50));
        prevLp.leftMargin = dp(10);
        transport.addView(prevButton, prevLp);

        playButton = iconButton(R.drawable.ic_mdi_play, dp(58), 16f, null);
        playButton.setContentDescription(getString(R.string.ck_lyrics_play));
        playButton.setOnClickListener(v -> engine.playPause());
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(dp(58), dp(58));
        playLp.leftMargin = dp(10);
        playLp.rightMargin = dp(10);
        transport.addView(playButton, playLp);

        nextButton = iconButton(R.drawable.ic_mdi_skip_next, dp(50), 14f,
                new int[]{colorCard, colorBorder});
        nextButton.setContentDescription(getString(R.string.ck_lyrics_next));
        nextButton.setOnClickListener(v -> engine.skipNext());
        transport.addView(nextButton, new LinearLayout.LayoutParams(dp(50), dp(50)));

        repeatButton = iconButton(R.drawable.ic_mdi_repeat, dp(48), 10f, null);
        repeatButton.setContentDescription(getString(R.string.ck_fs_repeat));
        repeatButton.setOnClickListener(v -> {
            repeatMode = (repeatMode + 1) % 3;
            repeatButton.setColorFilter(repeatMode == 0 ? colorMuted : colorAccent);
        });
        LinearLayout.LayoutParams repeatLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        repeatLp.leftMargin = dp(10);
        transport.addView(repeatButton, repeatLp);

        LinearLayout extras = new LinearLayout(this);
        extras.setOrientation(LinearLayout.HORIZONTAL);
        extras.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        controls.addView(extras, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        return bar;
    }

    private LinearLayout buildRightStack() {
        LinearLayout stack = new LinearLayout(this);
        stack.setOrientation(LinearLayout.HORIZONTAL);
        stack.setGravity(Gravity.CENTER_VERTICAL);
        stack.setPadding(dp(8), dp(8), dp(8), dp(8));

        biggerButton = stackButton("A+");
        biggerButton.setContentDescription(getString(R.string.ck_fs_bigger));
        biggerButton.setOnClickListener(v -> changeLyricScale(0.1f));
        stack.addView(biggerButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        smallerButton = stackButton("A-");
        smallerButton.setContentDescription(getString(R.string.ck_fs_smaller));
        smallerButton.setOnClickListener(v -> changeLyricScale(-0.1f));
        LinearLayout.LayoutParams smallerLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        smallerLp.leftMargin = dp(8);
        stack.addView(smallerButton, smallerLp);

        View divider = new View(this);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(dp(1), dp(26));
        divLp.leftMargin = dp(8);
        divLp.rightMargin = dp(8);
        stack.addView(divider, divLp);
        dividerTag = divider;

        themeButton = new ImageView(this);
        themeButton.setContentDescription(getString(R.string.ck_lyrics_theme_light));
        GradientDrawable themeBg = CockpitSurfaces.rounded(this, DARK_CARD, 12f);
        themeButton.setBackground(themeBg);
        themeButton.setPadding(dp(14), dp(14), dp(14), dp(14));
        themeButton.setClickable(true);
        themeButton.setFocusable(true);
        themeButton.setOnClickListener(v -> {
            darkMode = !darkMode;
            prefs.edit().putBoolean(KEY_DARK, darkMode).apply();
            applyTheme();
            applyCurrentGradient();
        });
        LinearLayout.LayoutParams themeLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        themeLp.leftMargin = dp(8);
        stack.addView(themeButton, themeLp);
        return stack;
    }

    private TextView stackButton(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        view.setTypeface(CockpitTokens.inter(this, CockpitTokens.WEIGHT_BOLD));
        view.setGravity(Gravity.CENTER);
        GradientDrawable bg = CockpitSurfaces.rounded(this, DARK_CARD, 12f);
        view.setBackground(bg);
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private ImageView iconButton(int drawableRes, int sizePx, float radiusDp, int[] background) {
        ImageView view = new ImageView(this);
        view.setImageResource(drawableRes);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setPadding(Math.round(sizePx * 0.28f), Math.round(sizePx * 0.28f),
                Math.round(sizePx * 0.28f), Math.round(sizePx * 0.28f));
        if (background != null) {
            GradientDrawable bg = CockpitSurfaces.rounded(this, background[0], radiusDp);
            if (background.length > 1) {
                bg.setStroke(dp(1), background[1]);
            }
            view.setBackground(bg);
        }
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private TextView lyricLine(float sizeSp, boolean bold, int maxLines) {
        TextView view = new TextView(this);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTypeface(CockpitTokens.inter(this, bold
                ? CockpitTokens.WEIGHT_BOLD
                : CockpitTokens.WEIGHT_MEDIUM));
        view.setMaxLines(maxLines);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setLineSpacing(dp(4), 1f);
        return view;
    }

    // ------------------------------------------------------------- rendering

    private void render(LyricsState state) {
        lastState = state;
        titleView.setText(state.title == null || state.title.isEmpty()
                ? getString(R.string.ck_lyrics_no_track) : state.title);
        artistView.setText(state.artist == null ? "" : state.artist);
        applyArtwork(state.artwork);

        boolean hasLines = !state.lines.isEmpty();
        int visible = hasLines ? View.VISIBLE : View.INVISIBLE;
        prevLine.setVisibility(visible);
        currentLine.setVisibility(visible);
        currentBar.setVisibility(visible);
        lineBar.setVisibility(visible);
        next1.setVisibility(visible);
        next2.setVisibility(visible);
        next3.setVisibility(visible);
        messageView.setVisibility(hasLines ? View.GONE : View.VISIBLE);

        playing = state.playing;
        setFrameLoop(playing);
        trackDurationMs = state.durationMs;
        updateSourceLabel(state.source);
        lastPositionMs = state.positionMs;
        lastPositionAt = SystemClock.elapsedRealtime();

        String trackKey = (state.title == null ? "" : state.title) + "\u0001"
                + (state.artist == null ? "" : state.artist);
        if (!compact && !splitMode && !trackKey.equals(lastArtistKey)) {
            lastArtistKey = trackKey;
            loadArtistImage(state.artist);
        }

        if (!hasLines) {
            currentLyricLine = null;
            messageView.setText(messageFor(state.status));
            playButton.setImageResource(state.playing
                    ? R.drawable.ic_mdi_pause : R.drawable.ic_mdi_play);
            updateProgress(state, 0f);
            return;
        }

        int index = state.currentLine >= 0 ? state.currentLine : 0;
        boolean changed = index != lastLineIndex || !trackKey.equals(lastTrackKey);
        lastLineIndex = index;
        lastTrackKey = trackKey;
        if (changed) {
            animateLineChange(() -> applyLineTexts(state, index));
        } else if (!lineAnimating) {
            restoreLineVisibility();
        }

        LyricLine line = state.lines.get(index);
        currentLyricLine = line;
        float lineProgress = 0f;
        if (!line.plain && line.endMs > line.timeMs) {
            lineProgress = (state.positionMs - line.timeMs)
                    / (float) (line.endMs - line.timeMs);
            lineProgress = Math.max(0f, Math.min(1f, lineProgress));
        }
        lineBar.setProgress(lineProgress);
        updateProgress(state, lineProgress);

        playButton.setImageResource(state.playing
                ? R.drawable.ic_mdi_pause : R.drawable.ic_mdi_play);
        applyCurrentGradient();
    }

    private void applyLineTexts(LyricsState state, int index) {
        prevLine.setText(index > 0 ? state.lines.get(index - 1).text : "");
        currentLine.setText(display(state.lines.get(index).text));
        next1.setText(index + 1 < state.lines.size() ? state.lines.get(index + 1).text : "");
        next2.setText(index + 2 < state.lines.size() ? state.lines.get(index + 2).text : "");
        next3.setText(index + 3 < state.lines.size() ? state.lines.get(index + 3).text : "");
        currentLine.post(this::applyCurrentGradient);
    }

    private void animateLineChange(Runnable update) {
        final int token = ++lineAnimToken;
        lineAnimating = true;

        // Eski büyük satır hayalet kopyada sönerken yenisi alttan yükselir;
        // önceki/sonraki satırlar anında güncellenir, blok komple kaybolmaz.
        ghostLine.animate().cancel();
        ghostLine.getPaint().setShader(currentLine.getPaint().getShader());
        ghostLine.setText(currentLine.getText());
        ghostLine.setAlpha(1f);
        ghostLine.setTranslationY(0f);

        update.run();

        currentLine.animate().cancel();
        currentLine.setAlpha(0f);
        currentLine.setTranslationY(dp(12));
        ghostLine.animate()
                .alpha(0f)
                .translationY(-dp(6))
                .setDuration(240)
                .start();
        currentLine.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(260)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        animateTextHeight();

        currentLine.postDelayed(() -> {
            if (token == lineAnimToken) {
                lineAnimating = false;
                ghostLine.setAlpha(0f);
                ghostLine.setText("");
                ViewGroup.LayoutParams lp = textFrame.getLayoutParams();
                if (lp.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    textFrame.setLayoutParams(lp);
                }
            }
        }, 300);
    }

    /** Metin kısalıp uzarken yüksekliği anlık zıplatmadan, animasyonla geçir. */
    private void animateTextHeight() {
        if (textFrame == null || currentLine.getWidth() <= 0) {
            return;
        }
        int width = currentLine.getWidth();
        currentLine.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int target = currentLine.getMeasuredHeight();
        int start = textFrame.getHeight();
        if (target <= 0 || start <= 0 || start == target) {
            return;
        }
        if (heightAnimator != null) {
            heightAnimator.cancel();
        }
        ViewGroup.LayoutParams lp = textFrame.getLayoutParams();
        lp.height = start;
        textFrame.setLayoutParams(lp);

        heightAnimator = ValueAnimator.ofInt(start, target);
        heightAnimator.setDuration(260);
        heightAnimator.setInterpolator(new DecelerateInterpolator());
        heightAnimator.addUpdateListener(animation -> {
            ViewGroup.LayoutParams params = textFrame.getLayoutParams();
            params.height = (int) animation.getAnimatedValue();
            textFrame.setLayoutParams(params);
        });
        heightAnimator.start();
    }

    private void restoreLineVisibility() {
        currentLine.animate().cancel();
        currentLine.setAlpha(1f);
        currentLine.setTranslationY(0f);
        ghostLine.animate().cancel();
        ghostLine.setAlpha(0f);
        ghostLine.setText("");
        if (heightAnimator != null) {
            heightAnimator.cancel();
        }
        if (textFrame != null) {
            ViewGroup.LayoutParams lp = textFrame.getLayoutParams();
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            textFrame.setLayoutParams(lp);
        }
    }

    private void updateSourceLabel(String packageName) {
        if (packageName == null || packageName.equals(lastSource) || audioTitle == null) {
            return;
        }
        lastSource = packageName;
        try {
            android.content.pm.ApplicationInfo info =
                    getPackageManager().getApplicationInfo(packageName, 0);
            audioTitle.setText(getPackageManager().getApplicationLabel(info));
        } catch (Exception e) {
            audioTitle.setText(packageName);
        }
    }

    private void setFrameLoop(boolean run) {
        if (run) {
            if (!frameLoopRunning) {
                frameLoopRunning = true;
                Choreographer.getInstance().removeFrameCallback(frameCallback);
                Choreographer.getInstance().postFrameCallback(frameCallback);
            }
        } else {
            frameLoopRunning = false;
            Choreographer.getInstance().removeFrameCallback(frameCallback);
        }
    }

    private void updateLiveProgress() {
        if (lastState == null || trackDurationMs <= 0L) {
            return;
        }
        long position = lastPositionMs;
        if (playing) {
            position += SystemClock.elapsedRealtime() - lastPositionAt;
        }
        position = Math.max(0L, Math.min(position, trackDurationMs));

        long seconds = position / 1000L;
        if (seconds != lastElapsedSecond) {
            lastElapsedSecond = seconds;
            elapsedView.setText(formatTime(position));
        }
        if (!userSeeking) {
            int progress = (int) (position * 1000 / trackDurationMs);
            if (progress != lastSeekProgress) {
                lastSeekProgress = progress;
                seekBar.setProgress(progress);
            }
        }
        if (currentLyricLine != null
                && !currentLyricLine.plain
                && currentLyricLine.endMs > currentLyricLine.timeMs) {
            float progress = (position - currentLyricLine.timeMs)
                    / (float) (currentLyricLine.endMs - currentLyricLine.timeMs);
            lineBar.setProgress(Math.max(0f, Math.min(1f, progress)));
        }
    }

    private void updateProgress(LyricsState state, float ignored) {
        long duration = Math.max(0L, state.durationMs);
        long position = Math.max(0L, Math.min(state.positionMs,
                duration > 0 ? duration : state.positionMs));
        elapsedView.setText(formatTime(position));
        durationView.setText(duration > 0 ? formatTime(duration) : "--:--");
        if (!userSeeking && duration > 0) {
            seekBar.setProgress((int) (position * 1000 / duration));
        }
    }

    private void applyArtwork(Bitmap artwork) {
        if (artwork == null) {
            artView.setPadding(dp(10), dp(10), dp(10), dp(10));
            artView.setColorFilter(colorAccent);
            artView.setImageResource(R.drawable.ic_mdi_music_note);
            return;
        }
        RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory.create(
                getResources(), artwork);
        drawable.setCornerRadius(dp(8));
        drawable.setAntiAlias(true);
        artView.setPadding(0, 0, 0, 0);
        artView.clearColorFilter();
        artView.setImageDrawable(drawable);
    }

    private int messageFor(LyricsState.Status status) {
        switch (status) {
            case NO_ACCESS:
                return R.string.ck_lyrics_no_access;
            case NO_SESSION:
                return R.string.ck_lyrics_empty_session;
            case NOT_FOUND:
                return R.string.ck_lyrics_not_found;
            case INSTRUMENTAL:
                return R.string.ck_lyrics_instrumental;
            case ERROR:
                return R.string.ck_lyrics_offline;
            default:
                return R.string.ck_lyrics_loading;
        }
    }

    private String display(String text) {
        return text == null ? "" : text.toUpperCase(new Locale("tr", "TR"));
    }

    private void changeLyricScale(float delta) {
        lyricScale = Math.max(0.8f, Math.min(1.3f, lyricScale + delta));
        prefs.edit().putFloat("lyricsFullscreenScale", lyricScale).apply();
        applyLyricScale();
        applyCurrentGradient();
    }

    private void applyLyricScale() {
        float base = compact ? 0.72f : 1f;
        prevLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f * lyricScale * base);
        currentLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 52f * lyricScale * base);
        ghostLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 52f * lyricScale * base);
        next1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f * lyricScale * base);
        next2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f * lyricScale * base);
        next3.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f * lyricScale * base);
    }

    private void applyCurrentGradient() {
        if (currentLine == null) {
            return;
        }
        if (currentLine.getWidth() == 0) {
            if (gradientRetry[0] < 3) {
                gradientRetry[0]++;
                currentLine.post(this::applyCurrentGradient);
            }
            return;
        }
        gradientRetry[0] = 0;
        int width = Math.max(1, currentLine.getWidth());
        int start = darkMode ? 0xFFFFFFFF : 0xFF0B0E12;
        currentLine.getPaint().setShader(new LinearGradient(
                0, 0, width, 0,
                new int[]{start, colorAccent, colorSecondary},
                null, Shader.TileMode.CLAMP));
        currentLine.invalidate();
    }

    // ---------------------------------------------------------------- theme

    private void applyTheme() {
        colorBg = darkMode ? DARK_BG : LIGHT_BG;
        colorPanel = darkMode ? DARK_PANEL : LIGHT_PANEL;
        colorCard = darkMode ? DARK_CARD : LIGHT_CARD;
        colorBorder = darkMode ? DARK_BORDER : LIGHT_BORDER;
        colorText = darkMode ? DARK_TEXT : LIGHT_TEXT;
        colorMuted = darkMode ? DARK_MUTED : LIGHT_MUTED;
        colorDim = darkMode ? DARK_DIM : LIGHT_DIM;
        colorFaint = darkMode ? DARK_FAINT : LIGHT_FAINT;
        colorAccent = darkMode ? DARK_ACCENT : LIGHT_ACCENT;
        colorSecondary = darkMode ? DARK_SECONDARY : LIGHT_SECONDARY;

        root.setBackgroundColor(colorBg);
        topBar.setBackgroundColor(colorPanel);
        footer.setBackgroundColor(colorPanel);
        topDivider.setBackgroundColor(colorBorder);
        if (headerPillDivider != null) {
            headerPillDivider.setBackgroundColor(colorBorder);
        }
        bottomDivider.setBackgroundColor(colorBorder);

        backPill.setBackground(pillBackground(colorCard, colorBorder, 12f));
        backIcon.setColorFilter(colorText);
        backText.setTextColor(colorText);

        livePill.setBackground(pillBackground(0xFF0D2A2A, darkMode ? 0x6610B981 : 0x6610B981, 99f));
        dotBackground(liveDot, colorSecondary);
        liveText.setTextColor(colorSecondary);

        titleView.setTextColor(colorText);
        clockIcon.setColorFilter(colorAccent);
        clockText.setTextColor(colorText);

        closeButton.setBackground(pillBackground(colorCard, colorBorder, 12f));
        closeButton.setColorFilter(colorMuted);

        syncLabel.setTextColor(colorMuted);
        prevLine.setTextColor(colorMuted);
        currentLine.setShadowLayer(darkMode ? 14f : 0f, 0f, 2f,
                darkMode ? 0x4000B4D8 : 0x00000000);
        ghostLine.setTextColor(colorText);
        next1.setTextColor(colorMuted);
        next2.setTextColor(colorMuted);
        next3.setTextColor(darkMode ? 0xFF94A3B8 : 0xFF64748B);
        messageView.setTextColor(colorMuted);
        currentBar.setBackground(CockpitSurfaces.rounded(this, colorSecondary, 2f));

        lineBar.setColors(darkMode ? 0xFF323539 : 0xFFE2E8F0, colorAccent, colorSecondary);

        seekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(colorAccent));
        seekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
        seekBar.setProgressBackgroundTintList(
                android.content.res.ColorStateList.valueOf(darkMode ? 0xFF323539 : 0xFFE2E8F0));
        elapsedView.setTextColor(colorAccent);
        durationView.setTextColor(colorMuted);

        speakerIcon.setBackground(pillBackground(colorCard, colorBorder, 12f));
        speakerIcon.setColorFilter(colorAccent);
        audioTitle.setTextColor(colorText);
        shuffleButton.setColorFilter(colorMuted);
        repeatButton.setColorFilter(colorMuted);
        prevButton.setBackground(pillBackground(colorCard, colorBorder, 14f));
        prevButton.setColorFilter(colorText);
        nextButton.setBackground(pillBackground(colorCard, colorBorder, 14f));
        nextButton.setColorFilter(colorText);
        playButton.setBackground(pillBackground(colorAccent, colorAccent, 16f));
        playButton.setColorFilter(darkMode ? 0xFF003642 : 0xFFFFFFFF);
        rightStack.setBackground(pillBackground(darkMode ? 0xE6191C20 : 0xFFF1F5F9,
                colorBorder, 16f));
        biggerButton.setBackground(pillBackground(colorCard, colorBorder, 12f));
        smallerButton.setBackground(pillBackground(colorCard, colorBorder, 12f));
        biggerButton.setTextColor(colorAccent);
        smallerButton.setTextColor(colorMuted);
        themeButton.setBackground(pillBackground(colorCard, colorBorder, 12f));
        themeButton.setImageResource(darkMode ? R.drawable.ic_ck_night : R.drawable.ic_ck_sun);
        themeButton.setContentDescription(getString(darkMode
                ? R.string.ck_lyrics_theme_dark : R.string.ck_lyrics_theme_light));
        themeButton.setColorFilter(colorAccent);
        if (dividerTag != null) {
            dividerTag.setBackgroundColor(colorBorder);
        }
        if (artistPhotoCard != null) {
            GradientDrawable cardBg = CockpitSurfaces.rounded(this,
                    darkMode ? 0xFF121820 : 0xFFF1F5F9, 20f);
            cardBg.setStroke(dp(1), colorBorder);
            artistPhotoCard.setBackground(cardBg);
        }
        if (artistInitial != null) {
            artistInitial.setTextColor(darkMode ? 0x554CD6FB : 0x550891B2);
        }
    }

    private GradientDrawable pillBackground(int fill, int stroke, float radiusDp) {
        GradientDrawable drawable = CockpitSurfaces.rounded(this, fill, radiusDp);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private void dotBackground(View view, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        view.setBackground(drawable);
    }

    // ------------------------------------------------------------- lifecycle

    private void keepScreenOn() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void hideSystemBars() {
        if (splitMode) {
            // Freeform pencerede sistem çubukları pencerenin dışında; layout bayrakları
            // yüzeyi pencere sınırından yukarı kaydırdığı için burada uygulanmıyor.
            getWindow().getDecorView().setSystemUiVisibility(0);
            return;
        }
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        if (!darkMode) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    // --------------------------------------------------------------- helpers

    private static String formatTime(long ms) {
        long totalSeconds = Math.max(0L, ms / 1000L);
        return String.format(Locale.getDefault(), "%02d:%02d",
                totalSeconds / 60L, totalSeconds % 60L);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** Arka plandaki hafif turkuaz ışıma. */
    private static final class GlowView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        GlowView(android.content.Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w == 0 || h == 0) {
                return;
            }
            paint.setShader(new RadialGradient(w * 0.26f, h * 0.42f,
                    Math.min(w, h) * 0.55f,
                    new int[]{0x334CD6FB, 0x000B0E12}, null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(new RadialGradient(w * 0.86f, h * 0.86f,
                    Math.min(w, h) * 0.45f,
                    new int[]{0x2246EEE0, 0x000B0E12}, null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
        }
    }

    /** Güncel satırın altındaki ince ilerleme çubuğu. */
    private static final class BarView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float progress;
        private int trackColor = 0xFF323539;
        private int startColor = 0xFF4CD6FB;
        private int endColor = 0xFF46EEE0;

        BarView(android.content.Context context) {
            super(context);
        }

        void setColors(int track, int start, int end) {
            trackColor = track;
            startColor = start;
            endColor = end;
            invalidate();
        }

        void setProgress(float value) {
            float next = Math.max(0f, Math.min(1f, value));
            if (Math.abs(next - progress) < 0.001f) {
                return;
            }
            progress = next;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float h = getHeight();
            float w = getWidth();
            float radius = h / 2f;
            rect.set(0, 0, w, h);
            paint.setShader(null);
            paint.setColor(trackColor);
            canvas.drawRoundRect(rect, radius, radius, paint);
            float filled = w * progress;
            if (filled > 0.5f) {
                rect.set(0, 0, filled, h);
                paint.setShader(new LinearGradient(0, 0, w, 0,
                        new int[]{startColor, endColor}, null, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(rect, radius, radius, paint);
                paint.setShader(null);
            }
        }
    }
}
