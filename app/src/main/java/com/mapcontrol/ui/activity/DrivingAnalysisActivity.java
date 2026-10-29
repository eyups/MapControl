package com.mapcontrol.ui.activity;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.res.ResourcesCompat;

import com.mapcontrol.R;
import com.mapcontrol.driving.DrivingEvent;
import com.mapcontrol.driving.DrivingScore;
import com.mapcontrol.driving.DrivingPenaltyBarsView;
import com.mapcontrol.driving.DrivingScoreRingView;
import com.mapcontrol.driving.DrivingSpeedDistributionView;
import com.mapcontrol.driving.DrivingSessionRecorder;
import com.mapcontrol.driving.DrivingSpeedChartView;
import com.mapcontrol.driving.DrivingTrip;
import com.mapcontrol.manager.FloatingBackButtonManager;
import com.mapcontrol.util.AppLocaleManager;
import com.mapcontrol.tts.MapControlSpeech;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Full-screen, read-only trip report: score, cheeky diagnosis and speed trace. */
public class DrivingAnalysisActivity extends Activity {

    public static final String EXTRA_TRIP_ID = "com.mapcontrol.extra.DRIVING_TRIP_ID";
    /** Debug/test: bu extra verilirse rapor açılışında bu metni seslendirir. */
    public static final String EXTRA_SPEAK_TEXT = "com.mapcontrol.extra.SPEAK_TEXT";
    public static final String EXTRA_SPEAK_CACHE_ID = "com.mapcontrol.extra.SPEAK_CACHE_ID";

    private static final int BG = 0xFF080A0A;
    private static final int CARD = 0xFF0E1414;
    private static final int BORDER = 0xFF1E2B2B;
    private static final int AMBER = 0xFFFFB000;
    private static final int CYAN = 0xFF35D8EA;
    private static final int TEXT = 0xFFF4F7F7;
    private static final int MUTED = 0xFF829394;
    private static final int RED_EVENT = 0xFFFF3B4D;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private DrivingSessionRecorder recorder;
    private DrivingTrip selectedTrip;
    private Typeface displayTypeface;
    private DrivingScoreRingView scoreRing;
    private TextView scoreTitle;
    private TextView scoreSummary;
    private TextView scoreGrade;
    private TextView tripDate;
    private TextView distanceValue;
    private TextView durationValue;
    private TextView avgValue;
    private TextView maxValue;
    private TextView consumptionValue;
    private TextView harshValue;
    private TextView insightText;
    private LinearLayout eventList;
    private TextView eventCount;
    private DrivingSpeedChartView speedChart;
    private DrivingSpeedDistributionView speedDistribution;
    private DrivingPenaltyBarsView penaltyBars;
    private Button finishButton;
    private Button startButton;
    private Button speakButton;
    private LinearLayout actionsRow;
    private FloatingBackButtonManager floatingBackButton;
    private boolean floatingBackWasVisible;
    private boolean ttsBusy;

    private final Runnable refreshLoop = new Runnable() {
        @Override
        public void run() {
            if (selectedTrip != null) {
                return;
            }
            render(recorder.getDisplayTrip());
            if (recorder.isActive()) {
                handler.postDelayed(this, 1000L);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_MapControl_Driving);
        super.onCreate(savedInstanceState);
        displayTypeface = loadDisplayTypeface();
        long requestedTripId = getIntent().getLongExtra(EXTRA_TRIP_ID, 0L);
        if (requestedTripId > 0L) {
            selectedTrip = com.mapcontrol.driving.DrivingTripStore.find(this, requestedTripId);
        }
        recorder = DrivingSessionRecorder.get(this);
        if (selectedTrip == null) {
            recorder.start();
        }
        floatingBackButton = FloatingBackButtonManager.getInstance(this);
        floatingBackWasVisible = floatingBackButton.isShowing();
        if (floatingBackWasVisible) {
            floatingBackButton.hide();
        }
        buildUi();
        render(selectedTrip != null ? selectedTrip : recorder.getDisplayTrip());
        String previewSpeak = getIntent().getStringExtra(EXTRA_SPEAK_TEXT);
        if (previewSpeak != null && !previewSpeak.trim().isEmpty()) {
            long previewCacheId = getIntent().getLongExtra(EXTRA_SPEAK_CACHE_ID, 0L);
            MapControlSpeech.speak(this, previewSpeak, previewSpeak, previewCacheId);
        }
        if (selectedTrip == null) {
            handler.postDelayed(refreshLoop, 1000L);
        }
    }

    @Override
    protected void onDestroy() {
        ttsBusy = false;
        MapControlSpeech.stop();
        handler.removeCallbacks(refreshLoop);
        if (floatingBackButton != null && floatingBackWasVisible) {
            floatingBackButton.show();
            floatingBackWasVisible = false;
        }
        super.onDestroy();
    }

    private Typeface loadDisplayTypeface() {
        try {
            Typeface face = ResourcesCompat.getFont(this, R.font.outfit);
            return face != null ? face : Typeface.create("sans-serif", Typeface.NORMAL);
        } catch (Exception ignored) {
            return Typeface.create("sans-serif", Typeface.NORMAL);
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF070909, 0xFF0C0C08, 0xFF070A0A});
        root.setBackground(background);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.TRANSPARENT);
        scroll.setFocusable(false);
        scroll.setFocusableInTouchMode(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setHorizontalScrollBarEnabled(false);
        root.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(44), dp(24), dp(44), dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        content.addView(buildHeader(), headerLp);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(isWide() ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        hero.setGravity(isWide() ? Gravity.CENTER_VERTICAL : Gravity.CENTER_HORIZONTAL);
        hero.setPadding(0, dp(12), 0, dp(12));
        GradientDrawable heroGlow = new GradientDrawable();
        heroGlow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        heroGlow.setColors(new int[]{0x26FFB000, 0x00000000});
        heroGlow.setGradientCenter(0.28f, 0.42f);
        heroGlow.setGradientRadius(dp(330));
        hero.setBackground(heroGlow);

        scoreRing = new DrivingScoreRingView(this);
        scoreRing.setTypeface(displayTypeface);
        int ringSize = isWide() ? dp(232) : dp(200);
        LinearLayout.LayoutParams ringLp = new LinearLayout.LayoutParams(ringSize, ringSize);
        if (isWide()) {
            ringLp.setMarginEnd(dp(30));
        }
        hero.addView(scoreRing, ringLp);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(isWide() ? Gravity.START : Gravity.CENTER_HORIZONTAL);

        TextView eyebrow = text("SÜRÜŞ KARNESİ", 12, AMBER, true);
        eyebrow.setLetterSpacing(0.2f);
        copy.addView(eyebrow);

        scoreTitle = text("", 34, AMBER, true);
        scoreTitle.setLetterSpacing(0.02f);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(8);
        copy.addView(scoreTitle, titleLp);

        scoreGrade = text("", 13, MUTED, true);
        scoreGrade.setLetterSpacing(0.16f);
        copy.addView(scoreGrade);

        scoreSummary = text("", 16, 0xFFD5DBDB, false);
        scoreSummary.setLineSpacing(dp(5), 1f);
        scoreSummary.setMaxWidth(dp(720));
        LinearLayout.LayoutParams summaryLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        summaryLp.topMargin = dp(12);
        copy.addView(scoreSummary, summaryLp);

        tripDate = text("", 12, MUTED, false);
        tripDate.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dateLp.topMargin = dp(14);
        copy.addView(tripDate, dateLp);

        speakButton = actionButton("SESLİ ÖZET", false);
        speakButton.setOnClickListener(v -> speakCurrentReport());
        LinearLayout.LayoutParams speakLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        speakLp.topMargin = dp(12);
        copy.addView(speakButton, speakLp);

        if (isWide()) {
            hero.addView(copy, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        } else {
            LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            copyLp.topMargin = dp(6);
            hero.addView(copy, copyLp);
        }
        content.addView(hero);

        actionsRow = buildActions();
        content.addView(actionsRow);

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams statsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statsLp.topMargin = dp(16);
        content.addView(stats, statsLp);
        buildStats(stats);

        LinearLayout chartCard = card();
        LinearLayout chartHeader = new LinearLayout(this);
        chartHeader.setOrientation(LinearLayout.HORIZONTAL);
        chartHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView chartTitle = text("HIZ GRAFİĞİ", 13, TEXT, true);
        chartTitle.setLetterSpacing(0.12f);
        chartHeader.addView(chartTitle, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView speedLegend = text("● HIZ", 12, CYAN, false);
        chartHeader.addView(speedLegend);
        TextView targetLegend = text("  ---- HEDEF", 12, MUTED, false);
        chartHeader.addView(targetLegend);
        TextView eventLegend = text("  ● FREN/GAZ", 12, RED_EVENT, true);
        chartHeader.addView(eventLegend);
        chartCard.addView(chartHeader);

        speedChart = new DrivingSpeedChartView(this);
        speedChart.setTypeface(displayTypeface);
        LinearLayout.LayoutParams chartLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(220));
        chartLp.topMargin = dp(10);
        chartCard.addView(speedChart, chartLp);
        LinearLayout.LayoutParams chartCardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        chartCardLp.topMargin = dp(10);
        content.addView(chartCard, chartCardLp);

        LinearLayout harshCard = card();
        TextView harshLabel = text("SERT FREN / KALKIŞ", 12, MUTED, true);
        harshLabel.setLetterSpacing(0.08f);
        harshCard.addView(harshLabel);
        harshValue = text("0 / 0", 30, TEXT, false);
        LinearLayout.LayoutParams harshLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        harshLp.topMargin = dp(4);
        harshCard.addView(harshValue, harshLp);

        LinearLayout insightCard = card();
        TextView insightTitle = text("AKILLI ANALİZ", 13, TEXT, true);
        insightTitle.setLetterSpacing(0.12f);
        insightCard.addView(insightTitle);
        insightText = text("", 15, 0xFFD5DBDB, false);
        insightText.setLineSpacing(dp(5), 1f);
        LinearLayout.LayoutParams insightLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        insightLp.topMargin = dp(8);
        insightCard.addView(insightText, insightLp);

        LinearLayout eventsCard = card();
        LinearLayout eventsHeader = new LinearLayout(this);
        eventsHeader.setOrientation(LinearLayout.HORIZONTAL);
        eventsHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView eventsTitle = text("OLAYLAR", 13, TEXT, true);
        eventsTitle.setLetterSpacing(0.12f);
        eventsHeader.addView(eventsTitle, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        eventCount = text("", 11, MUTED, false);
        eventsHeader.addView(eventCount);
        eventsCard.addView(eventsHeader);
        eventList = new LinearLayout(this);
        eventList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams eventListLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        eventListLp.topMargin = dp(6);
        eventsCard.addView(eventList, eventListLp);

        if (isWide()) {
            LinearLayout dashboardRow = new LinearLayout(this);
            dashboardRow.setOrientation(LinearLayout.HORIZONTAL);
            dashboardRow.setBaselineAligned(false);

            LinearLayout.LayoutParams insightColLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.35f);
            dashboardRow.addView(insightCard, insightColLp);

            LinearLayout rightColumn = new LinearLayout(this);
            rightColumn.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams rightColLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            rightColLp.setMarginStart(dp(14));
            dashboardRow.addView(rightColumn, rightColLp);

            rightColumn.addView(harshCard, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams eventsLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            eventsLp.topMargin = dp(10);
            rightColumn.addView(eventsCard, eventsLp);

            LinearLayout.LayoutParams dashboardLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            dashboardLp.topMargin = dp(10);
            content.addView(dashboardRow, dashboardLp);
        } else {
            LinearLayout.LayoutParams insightColLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            insightColLp.topMargin = dp(10);
            content.addView(insightCard, insightColLp);
            LinearLayout.LayoutParams harshLp2 = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            harshLp2.topMargin = dp(10);
            content.addView(harshCard, harshLp2);
            LinearLayout.LayoutParams eventsLp2 = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            eventsLp2.topMargin = dp(10);
            content.addView(eventsCard, eventsLp2);
        }


        LinearLayout distributionCard = card();
        TextView distributionTitle = text("HIZ DAĞILIMI", 13, TEXT, true);
        distributionTitle.setLetterSpacing(0.12f);
        distributionCard.addView(distributionTitle);
        TextView distributionSub = text("Zamanın hangi hız aralığında geçtiği", 11, MUTED, false);
        LinearLayout.LayoutParams distributionSubLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        distributionSubLp.topMargin = dp(3);
        distributionCard.addView(distributionSub, distributionSubLp);
        speedDistribution = new DrivingSpeedDistributionView(this);
        speedDistribution.setTypeface(displayTypeface);
        LinearLayout.LayoutParams distributionLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(150));
        distributionLp.topMargin = dp(8);
        distributionCard.addView(speedDistribution, distributionLp);

        LinearLayout penaltyCard = card();
        TextView penaltyTitle = text("PUAN DÖKÜMÜ", 13, TEXT, true);
        penaltyTitle.setLetterSpacing(0.12f);
        penaltyCard.addView(penaltyTitle);
        TextView penaltySub = text("Puan kaybının kaynağı", 11, MUTED, false);
        LinearLayout.LayoutParams penaltySubLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        penaltySubLp.topMargin = dp(3);
        penaltyCard.addView(penaltySub, penaltySubLp);
        penaltyBars = new DrivingPenaltyBarsView(this);
        penaltyBars.setTypeface(displayTypeface);
        LinearLayout.LayoutParams penaltyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(150));
        penaltyLp.topMargin = dp(8);
        penaltyCard.addView(penaltyBars, penaltyLp);

        if (isWide()) {
            LinearLayout visualRow = new LinearLayout(this);
            visualRow.setOrientation(LinearLayout.HORIZONTAL);
            visualRow.setBaselineAligned(false);
            LinearLayout.LayoutParams leftVisualLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            leftVisualLp.setMarginEnd(dp(5));
            visualRow.addView(distributionCard, leftVisualLp);
            LinearLayout.LayoutParams rightVisualLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            rightVisualLp.setMarginStart(dp(5));
            visualRow.addView(penaltyCard, rightVisualLp);
            LinearLayout.LayoutParams visualRowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            visualRowLp.topMargin = dp(10);
            content.addView(visualRow, visualRowLp);
        } else {
            LinearLayout.LayoutParams distributionCardLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            distributionCardLp.topMargin = dp(10);
            content.addView(distributionCard, distributionCardLp);
            LinearLayout.LayoutParams penaltyCardLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            penaltyCardLp.topMargin = dp(10);
            content.addView(penaltyCard, penaltyCardLp);
        }

        TextView footer = text("VDBus / CarInfo canlı verisi · salt okunur", 11, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        footerLp.topMargin = dp(12);
        content.addView(footer, footerLp);

        setContentView(root);
        scroll.postDelayed(() -> scroll.scrollTo(0, 0), 120L);
    }

    private LinearLayout buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = text("‹", 34, TEXT, false);
        back.setGravity(Gravity.CENTER);
        back.setPadding(0, 0, dp(18), dp(2));
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(32), dp(44)));

        TextView title = text("MAPCONTROL", 13, 0xFFAAB4B4, true);
        title.setLetterSpacing(0.22f);
        header.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = text("KAPAT", 12, 0xFFAAB4B4, true);
        close.setLetterSpacing(0.16f);
        close.setPadding(dp(16), dp(8), dp(16), dp(8));
        close.setOnClickListener(v -> finish());
        header.addView(close);
        return header;
    }

    private LinearLayout buildActions() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, 0);

        finishButton = actionButton("Yolculuğu bitir ve kaydet", true);
        finishButton.setOnClickListener(v -> {
            recorder.finishNow();
            render(recorder.getDisplayTrip());
        });
        row.addView(finishButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        startButton = actionButton("Yolculuğu başlat", false);
        startButton.setOnClickListener(v -> {
            recorder.startNow();
            render(recorder.getDisplayTrip());
            handler.removeCallbacks(refreshLoop);
            handler.post(refreshLoop);
        });
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        startLp.setMarginStart(dp(10));
        row.addView(startButton, startLp);

        Button close = actionButton("Kapat", false);
        close.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        closeLp.setMarginStart(dp(10));
        row.addView(close, closeLp);
        return row;
    }

    private void buildStats(LinearLayout parent) {
        int columns = isWide() ? 5 : 2;
        int total = 5;
        for (int start = 0; start < total; start += columns) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setBaselineAligned(false);
            int count = Math.min(columns, total - start);
            for (int i = 0; i < count; i++) {
                int index = start + i;
                LinearLayout card = card();
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (i > 0) {
                    lp.setMarginStart(dp(10));
                }
                row.addView(card, lp);
                switch (index) {
                    case 0:
                        distanceValue = addStat(card, "MESAFE", "0.0 km");
                        break;
                    case 1:
                        durationValue = addStat(card, "SÜRE", "0 dk");
                        break;
                    case 2:
                        avgValue = addStat(card, "ORTALAMA HIZ", "0 km/h");
                        break;
                    case 3:
                        maxValue = addStat(card, "EN YÜKSEK HIZ", "0 km/h");
                        break;
                    default:
                        consumptionValue = addStat(card, "TÜKETİM", "—");
                        break;
                }
            }
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.topMargin = dp(10);
            parent.addView(row, rowLp);
        }
    }

    private TextView addStat(LinearLayout card, String label, String initial) {
        TextView labelView = text(label, 11, MUTED, true);
        labelView.setLetterSpacing(0.08f);
        card.addView(labelView);
        TextView value = text(initial, 25, TEXT, false);
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        valueLp.topMargin = dp(4);
        card.addView(value, valueLp);
        return value;
    }

    private void render(DrivingTrip trip) {
        boolean history = selectedTrip != null;
        boolean active = !history && recorder.isActive();
        if (finishButton != null) {
            finishButton.setVisibility(active ? View.VISIBLE : View.GONE);
        }
        if (startButton != null) {
            startButton.setVisibility(!history && !active ? View.VISIBLE : View.GONE);
        }
        if (actionsRow != null) {
            actionsRow.setVisibility(history ? View.GONE : View.VISIBLE);
        }
        if (speakButton != null) {
            speakButton.setVisibility(trip == null ? View.GONE : View.VISIBLE);
        }
        if (trip == null) {
            scoreRing.setScore(0, true);
            scoreTitle.setText("HENÜZ YOLCULUK YOK");
            scoreGrade.setText("İLK SÜRÜŞÜNÜ BEKLİYORUZ");
            scoreSummary.setText("Yolculuğu başlat, birkaç kilometre sür, sonra buraya dön. "
                    + "Biz de frenlerine, turbo alışkanlıklarına ve yakıt damlalarına bakalım.");
            tripDate.setText("SÜRÜŞ KARNESİ · HAZIR");
            distanceValue.setText("0.0 km");
            durationValue.setText("0 dk");
            avgValue.setText("0 km/h");
            maxValue.setText("0 km/h");
            consumptionValue.setText("—");
            harshValue.setText("0 / 0");
            insightText.setText("Yolculuk başladığında pedal kullanımın, hız aralıkların ve "
                    + "yakıt tüketimin burada analiz edilecek.");
            eventList.removeAllViews();
            eventCount.setText("");
            TextView emptyEvents = text("Henüz olay yok.", 13, MUTED, false);
            eventList.addView(emptyEvents);
            speedChart.setData(null, 1, null);
            speedDistribution.setData(null);
            penaltyBars.setData(0, 0, 0, 0);
            return;
        }
        scoreRing.setScore(trip.score, true);
        scoreTitle.setText(trip.title.toUpperCase(appLocale()));
        String gradeLine = "PUAN " + trip.score + "  ·  NOT " + trip.grade
                + (active ? "  ·  CANLI" : "");
        int gradeIndex = gradeLine.indexOf("NOT ") + 4;
        if (gradeIndex > 3 && gradeIndex < gradeLine.length()) {
            SpannableString gradeSpan = new SpannableString(gradeLine);
            gradeSpan.setSpan(new ForegroundColorSpan(gradeColor(trip.grade)),
                    gradeIndex, gradeIndex + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            scoreGrade.setText(gradeSpan);
        } else {
            scoreGrade.setText(gradeLine);
        }
        scoreSummary.setText(trip.description);
        tripDate.setText(formatDate(trip.startTimeMs, active, history));
        distanceValue.setText(String.format(appLocale(), "%.1f km", trip.distanceKm));
        durationValue.setText(formatDuration(trip.durationSec));
        avgValue.setText(String.format(appLocale(), "%d km/h", trip.avgSpeedKmh));
        maxValue.setText(String.format(appLocale(), "%d km/h", trip.maxSpeedKmh));
        consumptionValue.setText(trip.consumptionL100 > 0
                ? String.format(appLocale(), "%.1f L/100 km", trip.consumptionL100)
                : "—");
        harshValue.setText(trip.harshBrakes + " / " + trip.harshAccels);
        insightText.setText(buildInsights(trip));
        renderEvents(trip);
        speedChart.setData(trip.speedSamples, Math.max(1, trip.durationSec), trip.events);
        speedDistribution.setData(trip.speedSamples);
        int[] penalties = penaltyValues(trip);
        penaltyBars.setData(penalties[0], penalties[1], penalties[2], penalties[3]);
    }

    private void speakCurrentReport() {
        DrivingTrip trip = selectedTrip != null ? selectedTrip : recorder.getDisplayTrip();
        if (trip == null || ttsBusy || MapControlSpeech.isSpeaking()
                || speakButton == null || !speakButton.isEnabled()) {
            return;
        }
        ttsBusy = true;
        speakButton.setEnabled(false);
        speakButton.setText("SESLENDİRİLİYOR…");
        Toast.makeText(this, "Sesli özet hazırlanıyor...", Toast.LENGTH_SHORT).show();
        MapControlSpeech.speak(this, spokenReport(trip), expressiveReport(trip), trip.id,
                () -> runOnUiThread(this::resetSpeakButton));
    }

    private void resetSpeakButton() {
        ttsBusy = false;
        if (speakButton != null) {
            speakButton.setEnabled(true);
            speakButton.setText("SESLİ ÖZET");
        }
    }

    private String spokenReport(DrivingTrip trip) {
        String text = "Sürüşün karnesi hazır. " + trip.title + ". " + trip.description;
        return text
                .replace("km/h", "kilometre saat")
                .replace("L/100 km", "litre bölü yüz kilometre")
                .replace("km", "kilometre");
    }

    /** ElevenLabs v3 için duygu etiketi; Sherpa tarafında SpeechText.plain() ile ayıklanır. */
    private String expressiveReport(DrivingTrip trip) {
        String mood = trip.score >= 80 ? "[excited]" : (trip.score >= 60 ? "[sarcastic]" : "[sighs]");
        // Etiket cümle başında duraklama yaratabiliyor; giriş cümlesinden sonra veriyoruz.
        String text = "Sürüşün karnesi hazır! " + mood + " " + trip.title + "! " + trip.description;
        return text
                .replace("km/h", "kilometre saat")
                .replace("L/100 km", "litre bölü yüz kilometre")
                .replace("km", "kilometre")
                .replace("yaktın.", "yaktın!")
                .replace("şikayetçi.", "şikayetçi!")
                .replace("küstü.", "küstü!")
                .replace("tatilde.", "tatilde!")
                .replace("mutlu.", "mutlu!")
                .replace("aktın.", "aktın!")
                .replace("selam söylüyor.", "selam söylüyor!")
                + " [laughs]";
    }

    private String buildInsights(DrivingTrip trip) {
        StringBuilder text = new StringBuilder();
        List<String> lines = new ArrayList<>();
        int brakes = trip.harshBrakes;
        int accels = trip.harshAccels;
        if (brakes == 0 && accels == 0) {
            lines.add("Sert fren veya ani gazlama yok; pedal geçişlerin akıcı.");
        } else {
            lines.add(String.format(appLocale(), "%d sert fren, %d ani hızlanma kaydedildi.",
                    brakes, accels));
            if (brakes > accels) {
                lines.add("Fren tarafı daha agresif: takip mesafesini artır, gazı daha erken bırak.");
            } else if (accels > brakes) {
                lines.add("Kalkışlar daha agresif: hızlanmayı 2-3 saniyeye yay, turbo tepkisini yumuşat.");
            } else {
                lines.add("Fren ve gaz olayları dengeli; yine de pedal geçişlerini yumuşatabilirsin.");
            }
            DrivingEvent strongest = strongestEvent(trip.events);
            if (strongest != null) {
                lines.add(String.format(appLocale(), "En sert olay: %s, %s sürüşte %d km/h (%.1f m/s²).",
                        strongest.isBrake() ? "sert fren" : "ani gazlama",
                        formatEventTime(strongest.elapsedMs), strongest.speedKmh,
                        Math.abs(strongest.accelerationMps2)));
            }
            int firstHalf = 0;
            int secondHalf = 0;
            long half = Math.max(1L, trip.durationSec * 500L);
            for (DrivingEvent event : trip.events) {
                if (event.elapsedMs <= half) {
                    firstHalf++;
                } else {
                    secondHalf++;
                }
            }
            if (firstHalf > secondHalf + 1) {
                lines.add("Olaylar yolculuğun ilk yarısında yoğunlaşıyor; kalkış ve ilk hızlanmalara dikkat.");
            } else if (secondHalf > firstHalf + 1) {
                lines.add("Olaylar yolculuğun ikinci yarısında artıyor; yorgunluk veya tempo etkisi olabilir.");
            }
        }
        if (trip.consumptionL100 > 0.0) {
            double expected = DrivingScore.expectedConsumptionL100(trip.avgSpeedKmh);
            if (trip.consumptionL100 > expected + 1.0) {
                lines.add(String.format(appLocale(),
                        "Tiggo 8 için bu hız profilinde beklenen ~%.1f L/100 km; sen %.1f yaktın. "
                                + "Sabit hız ve yumuşak gazla farkı kapatabilirsin.",
                        expected, trip.consumptionL100));
            } else {
                lines.add(String.format(appLocale(),
                        "Tiggo 8 için %.1f L/100 km bu hız profilinde normal aralıkta.",
                        trip.consumptionL100));
            }
        }
        if (trip.maxSpeedKmh > 120) {
            lines.add("En yüksek hız 120 km/h üzerinde; otoyolda sabit hız daha verimli.");
        }
        if (trip.avgSpeedKmh < 20 && trip.durationSec >= 600 && trip.distanceKm > 0.1) {
            lines.add("Düşük ortalama hız: dur-kalk yoğun; yumuşak kalkışlar tüketimi düşürür.");
        }
        int[] penalties = penaltyValues(trip);
        lines.add(String.format(appLocale(),
                "Puan dökümü: fren -%d, gaz -%d, tüketim -%d, hız -%d.",
                penalties[0], penalties[1], penalties[2], penalties[3]));
        for (String line : lines) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append("• ").append(line);
        }
        return text.toString();
    }

    private int gradeColor(String grade) {
        if (grade == null) {
            return MUTED;
        }
        switch (grade) {
            case "A":
                return 0xFF2E7D32;
            case "B":
                return 0xFF558B2F;
            case "C":
                return 0xFFF9A825;
            case "D":
                return 0xFFEF6C00;
            case "E":
                return 0xFFD84315;
            case "F":
                return 0xFFC62828;
            default:
                return MUTED;
        }
    }

    private int[] penaltyValues(DrivingTrip trip) {
        int brakePenalty = trip.harshBrakes * 9;
        int accelPenalty = trip.harshAccels * 7;
        int consumptionPenalty = DrivingScore.consumptionPenalty(trip);
        int speedPenalty = (trip.maxSpeedKmh > 110 ? (trip.maxSpeedKmh - 110) / 3 : 0)
                + (trip.avgSpeedKmh > 80 ? (trip.avgSpeedKmh - 80) / 2 : 0);
        return new int[]{brakePenalty, accelPenalty, consumptionPenalty, speedPenalty};
    }

    private DrivingEvent strongestEvent(List<DrivingEvent> events) {
        DrivingEvent strongest = null;
        if (events == null) {
            return null;
        }
        for (DrivingEvent event : events) {
            if (event == null) {
                continue;
            }
            if (strongest == null || Math.abs(event.accelerationMps2)
                    > Math.abs(strongest.accelerationMps2)) {
                strongest = event;
            }
        }
        return strongest;
    }

    private void renderEvents(DrivingTrip trip) {
        eventList.removeAllViews();
        if (trip.events == null || trip.events.isEmpty()) {
            eventCount.setText("0");
            TextView none = text("Sert fren veya ani gazlama yok.", 13, MUTED, false);
            none.setPadding(0, dp(3), 0, 0);
            eventList.addView(none);
            return;
        }
        eventCount.setText(String.valueOf(trip.events.size()));
        int shown = 0;
        for (DrivingEvent event : trip.events) {
            if (event == null) {
                continue;
            }
            if (shown >= 8) {
                break;
            }
            eventList.addView(eventRow(event));
            shown++;
        }
        if (trip.events.size() > shown) {
            TextView more = text("+ " + (trip.events.size() - shown) + " olay daha", 12, MUTED, false);
            more.setPadding(0, dp(4), 0, 0);
            eventList.addView(more);
        }
    }

    private LinearLayout eventRow(DrivingEvent event) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView badge = text(event.isBrake() ? "FREN" : "GAZ", 10, RED_EVENT, true);
        badge.setLetterSpacing(0.08f);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(8), dp(4), dp(8), dp(4));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(0x22FF3B4D);
        badgeBg.setCornerRadius(dp(6));
        badgeBg.setStroke(Math.max(1, Math.round(dp(1))), RED_EVENT);
        badge.setBackground(badgeBg);
        row.addView(badge);

        TextView time = text(formatEventTime(event.elapsedMs), 13, TEXT, false);
        time.setPadding(dp(10), 0, 0, 0);
        row.addView(time, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView speed = text(event.speedKmh + " km/h", 13, MUTED, false);
        row.addView(speed);
        TextView accel = text(String.format(appLocale(), "  %.1f m/s²",
                Math.abs(event.accelerationMps2)), 12, MUTED, false);
        row.addView(accel);
        return row;
    }

    private String formatEventTime(long elapsedMs) {
        long seconds = Math.max(0L, elapsedMs / 1000L);
        return String.format(appLocale(), "%02d:%02d", seconds / 60, seconds % 60);
    }

    private String formatDate(long wallMs, boolean active, boolean history) {
        if (active) {
            return "CANLI KAYIT · ŞİMDİ";
        }
        String prefix = history ? "GEÇMİŞ YOLCULUK" : "SON YOLCULUK";
        if (wallMs <= 0) {
            return prefix;
        }
        SimpleDateFormat format = new SimpleDateFormat("d MMM yyyy · HH:mm", appLocale());
        return prefix + " · " + format.format(new Date(wallMs));
    }

    private Locale appLocale() {
        return AppLocaleManager.locale(this);
    }

    private String formatDuration(int seconds) {
        int safe = Math.max(0, seconds);
        int minutes = safe / 60;
        int secs = safe % 60;
        return String.format(appLocale(), "%d dk %02d sn", minutes, secs);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable background = new GradientDrawable();
        background.setColor(CARD);
        background.setCornerRadius(dp(16));
        background.setStroke(Math.max(1, Math.round(dp(1))), BORDER);
        card.setBackground(background);
        return card;
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(displayTypeface, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private Button actionButton(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTypeface(displayTypeface, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(20), dp(12), dp(20), dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(10));
        if (primary) {
            background.setColor(AMBER);
            button.setTextColor(Color.BLACK);
        } else {
            background.setColor(0xFF111717);
            background.setStroke(Math.max(1, Math.round(dp(1))), 0xFF344141);
            button.setTextColor(TEXT);
        }
        button.setBackground(background);
        return button;
    }

    private boolean isWide() {
        return getResources().getConfiguration().screenWidthDp >= 900;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
