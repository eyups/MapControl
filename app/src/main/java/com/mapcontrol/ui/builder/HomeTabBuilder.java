package com.mapcontrol.ui.builder;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mapcontrol.R;
import com.mapcontrol.driving.DrivingSessionRecorder;
import com.mapcontrol.driving.DrivingTrip;
import com.mapcontrol.driving.DrivingTripStore;
import com.mapcontrol.ui.cockpit.CockpitButton;
import com.mapcontrol.ui.cockpit.CockpitCard;
import com.mapcontrol.ui.cockpit.CockpitGrid;
import com.mapcontrol.ui.cockpit.CockpitSectionHeader;
import com.mapcontrol.ui.cockpit.CockpitSurfaces;
import com.mapcontrol.ui.cockpit.CockpitTokens;
import com.mapcontrol.ui.cockpit.MetricView;
import com.mapcontrol.ui.cockpit.StatusBadge;
import com.mapcontrol.valet.ValetModeManager;
import com.mapcontrol.vehicle.VehicleMetricsFormatter;
import com.mapcontrol.vehicle.VehicleMetricsRepository;
import com.mapcontrol.vehicle.VehicleMetricsSnapshot;

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
    private static final int TAB_COMFORT = 15;
    private static final int TAB_VALET = 16;
    private static final int TAB_DRIVING_HISTORY = 14;
    private static final String PREFS = "MapControlPrefs";
    private static final String KEY_ONBOARDING_DONE = "homeOnboardingDismissed";

    private final Context context;
    private final VehicleMetricsRepository repository;
    private final VehicleMetricsFormatter formatter;
    private final HomeCallback callback;
    private final SharedPreferences prefs;

    private ScrollView scrollView;
    private LinearLayout content;
    private CockpitGrid grid;
    private MetricView fuelValue;
    private MetricView rangeValue;
    private MetricView odoValue;
    private StatusBadge statusBadge;
    private CockpitCard onboardingCard;
    private LinearLayout lastDriveHost;
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
        renderLastDrive();
        repository.removeListener(this);
        repository.addListener(this);
        repository.refresh();
    }

    public void stopListening() {
        tabVisible = false;
        repository.removeListener(this);
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
        content.setPadding(m, CockpitTokens.dim(context, R.dimen.ck_space_md), m, m);
        scrollView.addView(content, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        content.addView(buildHero());

        grid = new CockpitGrid(context);
        content.addView(grid, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        grid.add(buildVehicleCard(), 4);
        grid.add(buildQuickActions(), 4);
        grid.add(buildValetCard(), 8);
        grid.add(buildOnboardingCard(), 8);
        grid.add(buildLastDriveHost(), 8);
        renderLastDrive();
    }

    private View buildHero() {
        LinearLayout hero = new LinearLayout(context);
        hero.setOrientation(LinearLayout.VERTICAL);
        TextView greeting = new TextView(context);
        CockpitTokens.headlineLg(greeting);
        greeting.setText(greeting());
        hero.addView(greeting);

        TextView subtitle = new TextView(context);
        CockpitTokens.bodySm(subtitle);
        subtitle.setText("Tiggo 8 · canlı araç durumu ve hızlı işlemler");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = CockpitTokens.dim(context, R.dimen.ck_space_lg);
        hero.addView(subtitle, lp);
        return hero;
    }

    private CockpitCard buildVehicleCard() {
        CockpitCard card = new CockpitCard(context, "Araç Durumu").span(4);
        statusBadge = StatusBadge.neutral(context, "Bağlantı bekleniyor");
        card.status(statusBadge);

        LinearLayout metrics = new LinearLayout(context);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.setGravity(Gravity.BOTTOM);
        fuelValue = new MetricView(context, "Yakıt");
        rangeValue = new MetricView(context, "Menzil");
        odoValue = new MetricView(context, "Odometre");
        metrics.addView(fuelValue, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        metrics.addView(rangeValue, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        metrics.addView(odoValue, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addContent(metrics);

        CockpitButton detail = CockpitButton.secondary(context, "Araç bilgisini aç");
        detail.setOnClickListener(v -> callback.onNavigate(TAB_VEHICLE_INFO, "Araç Bilgisi"));
        card.addAction(detail);
        return card;
    }

    private CockpitCard buildQuickActions() {
        CockpitCard card = new CockpitCard(context, "Hızlı İşlemler").span(4);
        LinearLayout row1 = new LinearLayout(context);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(quick("Araç Bilgisi", TAB_VEHICLE_INFO, "Araç Bilgisi"),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams gap1 = new LinearLayout.LayoutParams(
                CockpitTokens.dim(context, R.dimen.ck_space_sm), 1);
        row1.addView(new View(context), gap1);
        row1.addView(quick("Sürüş Karnesi", TAB_DRIVING_HISTORY, "Sürüş Karnesi"),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addContent(row1);

        LinearLayout row2 = new LinearLayout(context);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        row2Lp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        card.addContent(row2, row2Lp);
        row2.addView(quick("Konfor", TAB_COMFORT, "Konfor"),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row2.addView(new View(context), new LinearLayout.LayoutParams(
                CockpitTokens.dim(context, R.dimen.ck_space_sm), 1));
        row2.addView(quick("Vale Modu", TAB_VALET, "Vale Modu"),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
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
        CockpitCard card = new CockpitCard(context, "Vale Modu").span(8);
        card.status(StatusBadge.advisory(context, "Aktif"));
        TextView body = new TextView(context);
        CockpitTokens.bodyMd(body);
        body.setText("Kişisel sekmeler gizli. Hız, alan ve saat sınırları uygulanıyor.");
        body.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        card.addContent(body);
        return card;
    }

    private CockpitCard buildOnboardingCard() {
        onboardingCard = new CockpitCard(context, "RoadDeck'e hoş geldin").span(8);
        onboardingCard.status(StatusBadge.nominal(context, "Kurulum"));
        if (prefs.getBoolean(KEY_ONBOARDING_DONE, false)) {
            onboardingCard.setVisibility(View.GONE);
            return onboardingCard;
        }

        TextView body = new TextView(context);
        CockpitTokens.bodyMd(body);
        body.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        body.setText("Önce canlı araç verisini doğrula, sonra sürüşünü analiz et. "
                + "Wi-Fi, konfor ve vale ayarları hazır olduğunda tek dokunuşla açılır.");
        onboardingCard.addContent(body);

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
        onboardingCard.addContent(actions, actionsLp);

        CockpitButton vehicle = CockpitButton.primary(context, "Araç Bilgisi");
        vehicle.setOnClickListener(v -> {
            dismissOnboarding();
            callback.onNavigate(TAB_VEHICLE_INFO, "Araç Bilgisi");
        });
        actions.addView(vehicle, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        CockpitButton start = CockpitButton.secondary(context, "Başla");
        start.setOnClickListener(v -> dismissOnboarding());
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        startLp.leftMargin = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        actions.addView(start, startLp);
        return onboardingCard;
    }

    private View buildLastDriveHost() {
        lastDriveHost = new LinearLayout(context);
        lastDriveHost.setOrientation(LinearLayout.VERTICAL);
        return lastDriveHost;
    }

    private void renderLastDrive() {
        if (lastDriveHost == null) {
            return;
        }
        lastDriveHost.removeAllViews();
        lastDriveHost.addView(buildLastDriveCard(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private CockpitCard buildLastDriveCard() {
        CockpitCard card = new CockpitCard(context, "Son Sürüş").span(8);
        DrivingSessionRecorder recorder = DrivingSessionRecorder.get(context);
        DrivingTrip trip = recorder.isActive() ? recorder.getDisplayTrip() : recorder.getLastTrip();
        if (trip == null) {
            List<DrivingTrip> history = DrivingTripStore.loadHistory(context);
            if (!history.isEmpty()) {
                trip = history.get(0);
            }
        }
        if (trip == null) {
            card.status(StatusBadge.neutral(context, "Kayıt yok"));
            TextView empty = new TextView(context);
            CockpitTokens.bodySm(empty);
            empty.setText("Henüz kayıtlı sürüş yok. İlk yolculuktan sonra puan ve yakıt analizi burada görünür.");
            card.addContent(empty);
            return card;
        }

        card.status(StatusBadge.nominal(context, recorder.isActive() ? "Devam ediyor" : "Tamamlandı"));
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        MetricView score = new MetricView(context, "Puan").value(String.valueOf(trip.score));
        score.setMinimumWidth(CockpitTokens.dp(context, 160));
        row.addView(score);

        LinearLayout info = new LinearLayout(context);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        infoLp.leftMargin = CockpitTokens.dim(context, R.dimen.ck_space_lg);
        row.addView(info, infoLp);

        TextView title = new TextView(context);
        CockpitTokens.title(title);
        title.setText(trip.title.isEmpty() ? "Sürüş" : trip.title);
        info.addView(title);

        SimpleDateFormat fmt = new SimpleDateFormat("d MMM · HH:mm", new Locale("tr", "TR"));
        TextView meta = new TextView(context);
        CockpitTokens.bodySm(meta);
        meta.setText(fmt.format(new Date(trip.startTimeMs))
                + " · " + String.format(Locale.getDefault(), "%.1f km", trip.distanceKm));
        info.addView(meta);

        if (trip.consumptionL100 > 0) {
            TextView cons = new TextView(context);
            CockpitTokens.mono(cons);
            cons.setText(String.format(Locale.getDefault(), "%.1f L/100 km", trip.consumptionL100));
            info.addView(cons);
        }
        card.addContent(row);

        CockpitButton open = CockpitButton.secondary(context, "Tüm sürüşleri gör");
        open.setOnClickListener(v -> callback.onNavigate(TAB_DRIVING_HISTORY, "Sürüş Karnesi"));
        card.addAction(open);
        return card;
    }

    @Override
    public void onMetricsUpdated(VehicleMetricsSnapshot snapshot) {
        if (!tabVisible || fuelValue == null) {
            return;
        }
        if (!snapshot.connected) {
            if (statusBadge != null) {
                statusBadge.setLevel(StatusBadge.ADVISORY);
                statusBadge.setText("Bağlantı bekleniyor");
            }
            fuelValue.value("—");
            rangeValue.value("—");
            odoValue.value("—");
            return;
        }
        if (statusBadge != null) {
            statusBadge.setLevel(StatusBadge.NOMINAL);
            statusBadge.setText("Bağlı");
        }
        fuelValue.value(formatter.formatDashboardFuel(snapshot));
        rangeValue.value(formatter.formatDashboardRange(snapshot));
        odoValue.value(formatter.formatDashboardOdo(snapshot));
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

    private String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 6) return "İyi geceler";
        if (hour < 12) return "Günaydın";
        if (hour < 18) return "İyi günler";
        return "İyi akşamlar";
    }
}
