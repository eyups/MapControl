package com.mapcontrol.ui.builder;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import com.mapcontrol.ui.cockpit.CockpitGrid;
import com.mapcontrol.ui.cockpit.CockpitListRow;
import com.mapcontrol.ui.cockpit.CockpitTokens;
import com.mapcontrol.ui.cockpit.CockpitToggleRow;
import android.widget.TextView;
import android.widget.Toast;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.valet.ValetAlert;
import com.mapcontrol.valet.ValetModeManager;
import com.mapcontrol.valet.ValetMonitor;
import com.mapcontrol.valet.ValetSession;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.function.Consumer;

/** Vale Modu setup, live status and report. */
public class ValetTabBuilder {

    private final Context context;
    private final Consumer<String> log;
    private final Runnable onStateChanged;
    private final ScrollView scrollView;

    public ValetTabBuilder(Context context, Consumer<String> log, Runnable onStateChanged) {
        this.context = context;
        this.log = log;
        this.onStateChanged = onStateChanged;
        this.scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.TRANSPARENT);
        rebuild();
    }

    public ScrollView getScrollView() {
        return scrollView;
    }

    public void onTabVisible() {
        rebuild();
    }

    public void rebuild() {
        scrollView.removeAllViews();
        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);
        int margin = com.mapcontrol.ui.cockpit.CockpitTokens.dim(context, R.dimen.ck_margin);
        outer.setPadding(margin, margin, margin, margin * 2);
        scrollView.addView(outer, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        ValetSession session = ValetModeManager.getSession(context);
        if (session.active) {
            buildActive(outer, session);
        } else {
            buildSettings(outer, session);
        }
    }

    private void buildPinSetup(LinearLayout parent) {
        LinearLayout card = card();
        card.addView(sectionTitle("Vale Modu"));
        card.addView(body("Misafir sürücü için 4 haneli PIN belirle. PIN yalnızca modu bitirirken sorulur."));

        EditText pin = pinField("PIN");
        card.addView(fieldLabel("4 haneli PIN"));
        card.addView(pin);
        EditText confirm = pinField("PIN tekrar");
        card.addView(fieldLabel("PIN tekrar"));
        card.addView(confirm);
        TextView error = smallText("");
        LinearLayout.LayoutParams errorLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        errorLp.topMargin = dp(6);
        card.addView(error, errorLp);

        Button save = button("PIN oluştur", true);
        save.setOnClickListener(v -> {
            String first = pin.getText().toString();
            String second = confirm.getText().toString();
            if (!first.matches("\\d{4}")) {
                error.setText("PIN 4 rakam olmalı.");
                return;
            }
            if (!first.equals(second)) {
                error.setText("PIN'ler eşleşmiyor.");
                return;
            }
            if (ValetModeManager.setPin(context, first)) {
                error.setText("");
                log.accept("Vale PIN'i oluşturuldu.");
                notifyStateChanged();
                rebuild();
            }
        });
        save.setMinWidth(CockpitTokens.dim(context, R.dimen.ck_action_min_w));
        save.setMaxWidth(CockpitTokens.dim(context, R.dimen.ck_action_max_w));
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        saveLp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
        card.addView(save, saveLp);
        parent.addView(card, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void buildSettings(LinearLayout parent, ValetSession session) {
        LinearLayout columns = new LinearLayout(context);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setBaselineAligned(false);
        LinearLayout left = card();
        LinearLayout right = card();

        left.addView(sectionTitle("Vale Modu"));
        left.addView(body("Sürüşü izler ve raporlar; aracın hızını sınırlamaz. Başlatmadan önce aracı park hâlinde bırak."));

        EditText speed = numberField(String.valueOf(session.speedLimitKmh));
        left.addView(fieldLabel("HIZ UYARI EŞİĞİ (KM/H)"));
        left.addView(speed);

        EditText radius = numberField(String.valueOf(session.geofenceRadiusM));
        left.addView(fieldLabel("ALAN SINIRI YARIÇAPI (METRE)"));
        left.addView(radius);

        CockpitToggleRow curfew = switchRow("Saat kısıtı uyarısı", session.curfewEnabled);
        right.addView(curfew);
        LinearLayout timeRow = new LinearLayout(context);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        EditText start = timeField(session.curfewStartMinute);
        EditText end = timeField(session.curfewEndMinute);
        timeRow.addView(start, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams endLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        endLp.setMarginStart(dp(8));
        timeRow.addView(end, endLp);
        right.addView(timeRow);

        CockpitToggleRow hidePersonal = switchRow("Kişisel menüleri gizle", session.hidePersonal);
        right.addView(hidePersonal);

        TextView note = smallText("Konum izni yoksa yalnızca hız ve saat kısıtı izlenir. Veriler cihazda kalır.");
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        noteLp.topMargin = dp(10);
        right.addView(note, noteLp);

        Button startBtn = button("Vale Modu'nu başlat", true);
        startBtn.setMinWidth(CockpitTokens.dim(context, R.dimen.ck_action_min_w));
        startBtn.setMaxWidth(CockpitTokens.dim(context, R.dimen.ck_action_max_w));
        startBtn.setOnClickListener(v -> {
            if (!ValetModeManager.hasPin(context)) {
                showPinSetupDialog();
                return;
            }
            new AlertDialog.Builder(context)
                .setTitle("Vale Modu başlatılsın mı?")
                .setMessage("Araç park hâlinde olmalı. Modu bitirmek için PIN gerekir; "
                        + "hız ve alan sınırı uyarıları cihazda tutulur.")
                .setPositiveButton("Başlat", (dialog, which) -> {
                    session.speedLimitKmh = parseRange(speed, 30, 200, 120);
                    session.geofenceRadiusM = parseRange(radius, 50, 5000, 500);
                    session.curfewEnabled = curfew.getToggle().isChecked();
                    session.curfewStartMinute = parseTime(start, 22 * 60);
                    session.curfewEndMinute = parseTime(end, 6 * 60);
                    session.hidePersonal = hidePersonal.getToggle().isChecked();
                    ValetModeManager.startSession(context, session, currentLocation());
                    ValetMonitor.get(context).start();
                    log.accept("Vale Modu başlatıldı.");
                    notifyStateChanged();
                    rebuild();
                })
                .setNegativeButton("Vazgeç", null)
                .show();
        });
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        startLp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
        left.addView(startBtn, startLp);


        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        leftLp.setMarginEnd(8);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        rightLp.setMarginStart(8);
        columns.addView(left, leftLp);
        columns.addView(right, rightLp);
        parent.addView(columns, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        CockpitGrid pinGrid = new CockpitGrid(context);
        CockpitListRow pinRow = new CockpitListRow(context, R.drawable.ic_mdi_shield_car,
                "PIN değiştir", "Vale modunu bitirmek için kullanılır");
        TextView pinChevron = new TextView(context);
        CockpitTokens.headlineSm(pinChevron);
        pinChevron.setText("›");
        pinChevron.setTextColor(CockpitTokens.color(context, R.color.ck_text_muted));
        pinRow.trailing(pinChevron);
        pinRow.clickRow(v -> showPinSetupDialog());
        pinGrid.add(pinRow, 4);
        pinGrid.add(new View(context), 4);
        LinearLayout.LayoutParams pinGridLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pinGridLp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
        parent.addView(pinGrid, pinGridLp);
    }

    private void buildActive(LinearLayout parent, ValetSession session) {
        LinearLayout card = card();
        card.addView(sectionTitle("VALE MODU AKTİF"));
        card.addView(body("Araç izleniyor. Modu bitirmek için PIN gerekir."));

        LinearLayout metrics = new LinearLayout(context);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.setBaselineAligned(false);
        metrics.addView(metric("SÜRE", formatDuration(session.startTimeMs)), weighted());
        metrics.addView(metric("MESAFE", String.format(Locale.getDefault(), "%.1f km", session.distanceKm)), weighted());
        metrics.addView(metric("MAX HIZ", session.maxSpeedKmh + " km/h"), weighted());
        LinearLayout.LayoutParams metricsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        metricsLp.topMargin = dp(12);
        card.addView(metrics, metricsLp);

        LinearLayout limits = new LinearLayout(context);
        limits.setOrientation(LinearLayout.HORIZONTAL);
        limits.addView(smallText("Hız eşiği: " + session.speedLimitKmh + " km/h"), weighted());
        limits.addView(smallText("Alan sınırı: " + session.geofenceRadiusM + " m"), weighted());
        LinearLayout.LayoutParams limitsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        limitsLp.topMargin = dp(8);
        card.addView(limits, limitsLp);

        TextView alertsTitle = smallText("UYARILAR (" + session.alerts.size() + ")");
        alertsTitle.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams alertTitleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        alertTitleLp.topMargin = dp(16);
        card.addView(alertsTitle, alertTitleLp);
        if (session.alerts.isEmpty()) {
            card.addView(body("Henüz uyarı yok."));
        } else {
            int shown = 0;
            for (ValetAlert alert : session.alerts) {
                if (shown >= 6) {
                    break;
                }
                card.addView(alertRow(alert));
                shown++;
            }
        }

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button refresh = button("Yenile", false);
        refresh.setOnClickListener(v -> rebuild());
        refresh.setMinWidth(CockpitTokens.dim(context, R.dimen.ck_action_min_w));
        refresh.setMaxWidth(CockpitTokens.dim(context, R.dimen.ck_action_max_w));
        buttons.addView(refresh, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        Button end = button("Modu bitir", true);
        end.setOnClickListener(v -> requestPinToEnd());
        LinearLayout.LayoutParams endLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        endLp.setMarginStart(dp(8));
        buttons.addView(end, endLp);
        LinearLayout.LayoutParams buttonsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonsLp.topMargin = dp(16);
        card.addView(buttons, buttonsLp);

        parent.addView(card, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout alertRow(ValetAlert alert) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        TextView type = smallText(alertLabel(alert.type));
        type.setTypeface(null, Typeface.BOLD);
        type.setTextColor(alert.type.equals(ValetAlert.TYPE_SPEED)
                ? 0xFFFFB000 : (alert.type.equals(ValetAlert.TYPE_GEOFENCE)
                ? 0xFFFF3B4D : 0xFFFF8A3D));
        row.addView(type, new LinearLayout.LayoutParams(dp(70),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView message = smallText(alert.message);
        row.addView(message, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView time = smallText(new SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(new Date(alert.timeMs)));
        row.addView(time);
        return row;
    }

    private void requestPinToEnd() {
        EditText input = pinField("PIN");
        new AlertDialog.Builder(context)
                .setTitle("Vale Modu'nu bitir")
                .setMessage("4 haneli PIN'i gir.")
                .setView(input)
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Bitir", (dialog, which) -> {
                    if (!ValetModeManager.verifyPin(context, input.getText().toString())) {
                        Toast.makeText(context, "PIN yanlış.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ValetMonitor.get(context).stop();
                    ValetModeManager.endSession(context);
                    log.accept("Vale Modu bitirildi.");
                    notifyStateChanged();
                    rebuild();
                })
                .show();
    }

    private void showPinSetupDialog() {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), 0);
        EditText current = pinField("Mevcut PIN");
        EditText next = pinField("Yeni PIN");
        EditText confirm = pinField("Yeni PIN tekrar");
        content.addView(current);
        content.addView(next);
        content.addView(confirm);
        new AlertDialog.Builder(context)
                .setTitle("PIN değiştir")
                .setView(content)
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Kaydet", (dialog, which) -> {
                    if (!ValetModeManager.verifyPin(context, current.getText().toString())) {
                        Toast.makeText(context, "Mevcut PIN yanlış.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String pin = next.getText().toString();
                    if (!pin.matches("\\d{4}") || !pin.equals(confirm.getText().toString())) {
                        Toast.makeText(context, "Yeni PIN geçersiz.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ValetModeManager.setPin(context, pin);
                    log.accept("Vale PIN'i güncellendi.");
                    notifyStateChanged();
                })
                .show();
    }

    private void notifyStateChanged() {
        if (onStateChanged != null) {
            onStateChanged.run();
        }
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int inner = CockpitTokens.dim(context, R.dimen.ck_card_padding);
        card.setPadding(inner, inner, inner, inner);
        com.mapcontrol.ui.cockpit.CockpitSurfaces.level1(card);
        return card;
    }

    private TextView sectionTitle(String text) {
        TextView view = new TextView(context);
        CockpitTokens.headlineSm(view);
        view.setText(text);
        return view;
    }

    private TextView body(String text) {
        TextView view = text(text, 15, CockpitTokens.color(context, R.color.ck_text_medium), false);
        view.setLineSpacing(0f, 1.25f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        view.setLayoutParams(lp);
        return view;
    }

    private TextView fieldLabel(String text) {
        TextView view = new TextView(context);
        CockpitTokens.labelCaps(view);
        view.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        lp.bottomMargin = dp(4);
        view.setLayoutParams(lp);
        return view;
    }

    private TextView smallText(String text) {
        TextView v = text(text, 15, CockpitTokens.color(context, R.color.ck_text_medium), false);
        return v;
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(null, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private EditText pinField(String hint) {
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        styleInput(input);
        return input;
    }

    private EditText numberField(String value) {
        EditText input = new EditText(context);
        input.setText(value);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        styleInput(input);
        return input;
    }

    private EditText timeField(int minute) {
        EditText input = new EditText(context);
        input.setText(String.format(Locale.getDefault(), "%02d:%02d", minute / 60, minute % 60));
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        styleInput(input);
        return input;
    }

    private void styleInput(EditText input) {
        input.setTextSize(14);
        input.setTextColor(UiStyles.color(context, R.color.textPrimary));
        input.setHintTextColor(UiStyles.color(context, R.color.textHint));
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(com.mapcontrol.ui.cockpit.CockpitTokens.color(context, R.color.ck_surface_2));
        bg.setCornerRadius(dp(8));
        bg.setStroke(Math.max(1, Math.round(dp(1))),
                UiStyles.color(context, R.color.outline));
        input.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(4);
        input.setLayoutParams(lp);
    }

    private CockpitToggleRow switchRow(String label, boolean checked) {
        CockpitToggleRow row = new CockpitToggleRow(context,
                R.drawable.ic_mdi_shield_car, label, null, checked);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        row.setLayoutParams(lp);
        return row;
    }

    private Button button(String text, boolean primary) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(null, Typeface.BOLD);
        button.setTextColor(UiStyles.color(context, R.color.textPrimary));
        UiStyles.styleOemButton(button, UiStyles.color(context,
                primary ? R.color.buttonPrimary : R.color.buttonSecondaryMuted));
        return button;
    }

    private LinearLayout metric(String label, String value) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(text(label, 10, UiStyles.color(context, R.color.textHint), true));
        TextView valueView = text(value, 20, UiStyles.color(context, R.color.textPrimary), true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(3);
        column.addView(valueView, lp);
        return column;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private int parseRange(EditText field, int min, int max, int fallback) {
        try {
            int value = Integer.parseInt(field.getText().toString().trim());
            return Math.max(min, Math.min(max, value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int parseTime(EditText field, int fallback) {
        String value = field.getText().toString().trim();
        try {
            String[] parts = value.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour >= 0 && hour < 24 && minute >= 0 && minute < 60) {
                return hour * 60 + minute;
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private String formatDuration(long startMs) {
        if (startMs <= 0) {
            return "0 dk";
        }
        long seconds = Math.max(0, (System.currentTimeMillis() - startMs) / 1000L);
        return String.format(Locale.getDefault(), "%d dk %02d sn",
                seconds / 60, seconds % 60);
    }

    private String alertLabel(String type) {
        if (ValetAlert.TYPE_SPEED.equals(type)) {
            return "HIZ";
        }
        if (ValetAlert.TYPE_GEOFENCE.equals(type)) {
            return "ALAN";
        }
        return "SAAT";
    }

    private Location currentLocation() {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        try {
            LocationManager manager = (LocationManager) context.getSystemService(
                    Context.LOCATION_SERVICE);
            if (manager == null) {
                return null;
            }
            Location location = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            return location != null ? location
                    : manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        } catch (SecurityException ignored) {
            return null;
        }
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
