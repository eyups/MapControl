package com.mapcontrol.ui.cockpit;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.mapcontrol.R;

import java.util.Arrays;

/** Debug gallery: every cockpit component in every state (F2 acceptance). */
public class CockpitGalleryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(CockpitTokens.color(this, R.color.ck_bg_base));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int margin = CockpitTokens.dim(this, R.dimen.ck_margin);
        root.setPadding(margin, margin, margin, margin);
        scroll.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(header("Cockpit telematics · ĞÜŞİÖÇ ığüşöç"));
        root.addView(sub("Inter 400/500/600/700 + JetBrains Mono · 8 kolon · 64dp hedef"));

        root.addView(section("BUTTONS"));
        LinearLayout buttons = row();
        buttons.addView(CockpitButton.primary(this, "Birincil aksiyon"));
        buttons.addView(spacer());
        buttons.addView(CockpitButton.secondary(this, "İkincil"));
        buttons.addView(spacer());
        CockpitButton disabled = CockpitButton.secondary(this, "Devre dışı");
        disabled.setEnabled(false);
        buttons.addView(disabled);
        buttons.addView(spacer());
        buttons.addView(CockpitButton.hazard(this, "KAYIT"));
        root.addView(buttons);

        root.addView(section("TOGGLES · CHIPS · BADGES"));
        LinearLayout toggles = row();
        CockpitToggle on = new CockpitToggle(this);
        on.setChecked(true, false);
        toggles.addView(on);
        toggles.addView(spacer());
        CockpitToggle off = new CockpitToggle(this);
        off.setChecked(false, false);
        toggles.addView(off);
        toggles.addView(spacer());
        CockpitChip chipOn = new CockpitChip(this, "Seçili");
        chipOn.setSelectedState(true);
        toggles.addView(chipOn);
        toggles.addView(spacer());
        toggles.addView(new CockpitChip(this, "Seçili değil"));
        toggles.addView(spacer());
        toggles.addView(StatusBadge.nominal(this, "Normal"));
        toggles.addView(spacer());
        toggles.addView(StatusBadge.advisory(this, "Dikkat"));
        toggles.addView(spacer());
        toggles.addView(StatusBadge.critical(this, "Kritik"));
        root.addView(toggles);

        root.addView(section("GRID · CARDS"));
        CockpitGrid grid = new CockpitGrid(this);
        CockpitCard vehicle = new CockpitCard(this, "Araç Durumu");
        LinearLayout metrics = row();
        metrics.addView(new MetricView(this, "HIZ").value("72").unit("km/h"));
        metrics.addView(spacer());
        metrics.addView(new MetricView(this, "MENZİL").value("412").unit("km"));
        metrics.addView(spacer());
        metrics.addView(new MetricView(this, "YAKIT").value("%62").accent(
                CockpitTokens.color(this, R.color.ck_nominal)));
        vehicle.addContent(metrics);
        vehicle.status(StatusBadge.nominal(this, "Araç hazır"));
        grid.add(vehicle, 4);

        CockpitCard nav = new CockpitCard(this, "Navigasyon");
        nav.addContent(body("Cluster bağlı değil. Hedef uygulama seçilmedi."));
        LinearLayout navActions = row();
        navActions.addView(CockpitButton.primary(this, "Yansıt"));
        navActions.addView(spacer());
        navActions.addView(CockpitButton.secondary(this, "Uygulama seç"));
        nav.addContent(navActions);
        grid.add(nav, 4);

        CockpitCard trend = new CockpitCard(this, "Son 7 sürüş");
        Sparkline spark = new Sparkline(this);
        spark.setData(new float[]{78, 82, 75, 90, 86, 93, 88});
        trend.addContent(spark, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, CockpitTokens.dp(this, 72)));
        grid.add(trend, 6);

        CockpitCard emptyCard = new CockpitCard(this, "Uyarılar");
        CockpitEmptyState empty = new CockpitEmptyState(this, R.drawable.ic_mdi_shield_car,
                "Aktif uyarı yok");
        emptyCard.addContent(empty);
        grid.add(emptyCard, 2);
        root.addView(grid);

        root.addView(section("SEGMENTED · STEPPER · INPUT"));
        LinearLayout controls = row();
        CockpitSegmented seg = new CockpitSegmented(this,
                Arrays.asList("Motor", "Hazır", "Elle"), 1, index -> { });
        LinearLayout.LayoutParams segLp = new LinearLayout.LayoutParams(
                CockpitTokens.dp(this, 420), CockpitTokens.dim(this, R.dimen.ck_touch_min));
        controls.addView(seg, segLp);
        controls.addView(spacer());
        CockpitStepper stepper = new CockpitStepper(this, 21, "°C").range(16, 30);
        controls.addView(stepper, new LinearLayout.LayoutParams(
                CockpitTokens.dp(this, 320), ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(controls);

        CockpitInput input = new CockpitInput(this);
        input.setHint("Şifre girin · ĞÜŞİÖÇ");
        input.setText("örnek metin ığüşöç");
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, CockpitTokens.dim(this, R.dimen.ck_input_h));
        inputLp.topMargin = CockpitTokens.dim(this, R.dimen.ck_space_md);
        root.addView(input, inputLp);

        root.addView(section("LIST ROWS · LOADING"));
        CockpitListRow row1 = new CockpitListRow(this, R.drawable.ic_mdi_wifi,
                "Ekolayzer", "DynamicsProcessing · 20 sn bekçi");
        CockpitToggle rowToggle = new CockpitToggle(this);
        rowToggle.setChecked(true, false);
        row1.trailing(rowToggle);
        root.addView(row1, rowLp());

        CockpitListRow row2 = new CockpitListRow(this, R.drawable.ic_mdi_shield_car,
                "Şerit takip (LDP)", "Araç ayarına yazar · onay gerekir");
        row2.trailing(StatusBadge.nominal(this, "Açık"));
        LinearLayout.LayoutParams row2Lp = rowLp();
        row2Lp.topMargin = CockpitTokens.dim(this, R.dimen.ck_space_sm);
        root.addView(row2, row2Lp);

        root.addView(new CockpitLoading(this, "Ağlar taranıyor…"));

        root.addView(section("MODAL"));
        CockpitButton openModal = CockpitButton.secondary(this, "Modal aç");
        openModal.setOnClickListener(v -> CockpitModal.builder(this)
                .title("Güvenlik ayarını kapat")
                .message("Şerit takip sistemi kapatılacak. Ayarlar yalnız park halinde değiştirilebilir.")
                .negative("Vazgeç", null)
                .positive("Kapat", d -> CockpitToast.advisory(this, "Ayar kapatıldı"))
                .build().show());
        root.addView(openModal, new LinearLayout.LayoutParams(
                CockpitTokens.dp(this, 280), ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(section("EMPTY STATE"));
        CockpitCard stateCard = new CockpitCard(this, "Bağlantı");
        CockpitEmptyState offline = new CockpitEmptyState(this, R.drawable.ic_mdi_wifi,
                "Wi-Fi bağlı değil");
        offline.action(CockpitButton.secondary(this, "Ağları tara"));
        stateCard.addContent(offline);
        root.addView(stateCard);

        setContentView(scroll);
    }

    private TextView header(String text) {
        TextView v = new TextView(this);
        CockpitTokens.headlineLg(v);
        v.setText(text);
        return v;
    }

    private TextView sub(String text) {
        TextView v = new TextView(this);
        CockpitTokens.bodySm(v);
        v.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = CockpitTokens.dim(this, R.dimen.ck_space_sm);
        v.setLayoutParams(lp);
        return v;
    }

    private TextView section(String text) {
        TextView v = new TextView(this);
        CockpitTokens.labelCaps(v);
        v.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = CockpitTokens.dim(this, R.dimen.ck_space_xl);
        lp.bottomMargin = CockpitTokens.dim(this, R.dimen.ck_space_sm);
        v.setLayoutParams(lp);
        return v;
    }

    private TextView body(String text) {
        TextView v = new TextView(this);
        CockpitTokens.bodyMd(v);
        v.setTextColor(CockpitTokens.color(this, R.color.ck_text_medium));
        v.setText(text);
        return v;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private View spacer() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                CockpitTokens.dim(this, R.dimen.ck_space_md), 1));
        return v;
    }

    private LinearLayout.LayoutParams rowLp() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
