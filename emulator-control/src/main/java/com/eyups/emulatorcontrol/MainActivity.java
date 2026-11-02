package com.eyups.emulatorcontrol;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Emülatör test kontrol paneli.
 * Fake VDBus servislerine explicit broadcast gönderir; MapControl aktivitelerini açar.
 */
public final class MainActivity extends Activity {
    private static final String CONTROL_ACTION = "com.eyups.fakevdbus.CONTROL";
    private static final String CONTROL_RECEIVER = "com.eyups.fakevdbus.ControlReceiver";
    private static final String MAPCONTROL_PKG = "com.eyups.roaddeck";

    private Spinner targetSpinner;
    private EditText eventInput;
    private EditText cmdInput;
    private EditText valuesInput;
    private TextView output;

    private final String[] targets = new String[]{
            "com.desaysv.ivi.vds.carinfo",
            "com.desaysv.ivi.vds.sms",
            "com.desaysv.ivi.vds.carlan"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        log("Hazır. Fake servisler kurulu olmalı.");
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("VDBus Lab / Emülatör Kontrol");
        title.setTextSize(22);
        title.setGravity(Gravity.START);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Power: 327684 / 14 → 2=ON, 1=STANDBY, 0=OFF");
        hint.setTextSize(13);
        hint.setPadding(0, dp(4), 0, dp(12));
        root.addView(hint);

        targetSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, targets);
        targetSpinner.setAdapter(adapter);
        root.addView(label("Hedef fake paket"));
        root.addView(targetSpinner);

        Button powerOn = button("Power ON (2)");
        powerOn.setOnClickListener(v -> sendEvent(327684, 14, new int[]{2}));
        root.addView(powerOn);

        Button powerAcc = button("Power STANDBY (1)");
        powerAcc.setOnClickListener(v -> sendEvent(327684, 14, new int[]{1}));
        root.addView(powerAcc);

        Button powerOff = button("Power OFF (0)");
        powerOff.setOnClickListener(v -> sendEvent(327684, 14, new int[]{0}));
        root.addView(powerOff);

        root.addView(label("Özel event"));
        eventInput = input("Event ID (decimal)", "327684");
        cmdInput = input("CMD_ID (decimal)", "14");
        valuesInput = input("VALUE (virgüllü)", "2");
        root.addView(eventInput);
        root.addView(cmdInput);
        root.addView(valuesInput);

        Button send = button("Özel event gönder");
        send.setOnClickListener(v -> sendCustom());
        root.addView(send);

        Button clear = button("Fake eventleri temizle");
        clear.setOnClickListener(v -> sendClear());
        root.addView(clear);

        Button leftSignal = button("Sol sinyal");
        leftSignal.setOnClickListener(v -> signal(1));
        root.addView(leftSignal);

        Button rightSignal = button("Sağ sinyal");
        rightSignal.setOnClickListener(v -> signal(2));
        root.addView(rightSignal);

        Button hazard = button("Dörtlü (hazard)");
        hazard.setOnClickListener(v -> signal(3));
        root.addView(hazard);

        Button signalOff = button("Sinyalleri kapat");
        signalOff.setOnClickListener(v -> signal(0));
        root.addView(signalOff);

        Button overlay = button("Overlay aç (tüm uygulamaların üstünde)");
        overlay.setOnClickListener(v -> startOverlay());
        root.addView(overlay);

        Button stopOverlay = button("Overlay'i durdur");
        stopOverlay.setOnClickListener(v -> stopService(new Intent(this, OverlayService.class)));
        root.addView(stopOverlay);

        Button launch = button("RoadDeck'i aç");
        launch.setOnClickListener(v -> launchActivity(
                MAPCONTROL_PKG, "com.mapcontrol.ui.activity.MainActivity"));
        root.addView(launch);

        Button adas = button("ADAS Probe'u aç");
        adas.setOnClickListener(v -> launchActivity(
                MAPCONTROL_PKG, "com.mapcontrol.ui.activity.AdasProbeActivity"));
        root.addView(adas);

        if (android.provider.Settings.canDrawOverlays(this)) {
            startOverlay();
        } else {
            log("Overlay izni yok; 'Overlay aç' ile izin isteyin.");
        }

        output = new TextView(this);
        output.setTextSize(12);
        output.setTextColor(Color.DKGRAY);
        output.setPadding(0, dp(16), 0, 0);
        root.addView(output);

        return scroll;
    }

    private TextView label(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setPadding(0, dp(10), 0, dp(4));
        return v;
    }

    private EditText input(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        e.setSingleLine(true);
        return e;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        return b;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String target() {
        return String.valueOf(targetSpinner.getSelectedItem());
    }

    private void sendCustom() {
        try {
            int event = Integer.parseInt(eventInput.getText().toString().trim());
            int cmd = Integer.parseInt(cmdInput.getText().toString().trim());
            String raw = valuesInput.getText().toString().trim();
            String[] parts = raw.isEmpty() ? new String[]{"0"} : raw.split(",");
            int[] values = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                values[i] = Integer.parseInt(parts[i].trim());
            }
            sendEvent(event, cmd, values);
        } catch (Throwable t) {
            log("HATA: event/cmd/value sayı olmalı: " + t);
        }
    }

    private void sendEvent(int event, int cmd, int[] values) {
        try {
            Intent intent = new Intent(CONTROL_ACTION);
            intent.setComponent(new ComponentName(target(), CONTROL_RECEIVER));
            intent.putExtra("event", event);
            intent.putExtra("cmd", cmd);
            intent.putExtra("values", values);
            sendBroadcast(intent);
            log("Gönderildi → " + target() + " event=" + event + " cmd=" + cmd + " values=" + join(values));
        } catch (Throwable t) {
            log("Gönderim hatası: " + t);
        }
    }

    private void sendClear() {
        try {
            Intent intent = new Intent(CONTROL_ACTION);
            intent.setComponent(new ComponentName(target(), CONTROL_RECEIVER));
            intent.putExtra("json", "{\"clear\":true}");
            sendBroadcast(intent);
            log("Temizleme gönderildi → " + target());
        } catch (Throwable t) {
            log("Temizleme hatası: " + t);
        }
    }

    /** 0=off, 1=left, 2=right, 3=hazard. ReadOnly (327684) sinyal ID'leri. */
    private void signal(int mode) {
        boolean left = mode == 1 || mode == 3;
        boolean right = mode == 2 || mode == 3;
        boolean hazard = mode == 3;
        sendReadOnly(35, left ? 1 : 0);
        sendReadOnly(33, left ? 1 : 0);
        sendReadOnly(34, right ? 1 : 0);
        sendReadOnly(32, right ? 1 : 0);
        sendReadOnly(79, hazard ? 1 : 0);
        log("Sinyal: " + (mode == 1 ? "sol" : mode == 2 ? "sağ" : mode == 3 ? "dörtlü" : "kapalı"));
    }

    private void sendReadOnly(int cmd, int value) {
        VdbusCommandSender.send(this, VdbusCommandSender.CARINFO, 327684, cmd, new int[]{value});
    }

    private void startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            log("Overlay izni yok; ayarlar açılıyor.");
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }
        Intent service = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(service);
        } else {
            startService(service);
        }
        log("Overlay başlatıldı.");
    }

    private void launchActivity(String pkg, String cls) {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(pkg, cls));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            log("Açıldı: " + cls);
        } catch (Throwable t) {
            log("Açma hatası: " + t);
        }
    }

    private String join(int[] values) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) b.append(',');
            b.append(values[i]);
        }
        return b.toString();
    }

    private void log(String message) {
        if (output != null) {
            output.append(message + "\n");
        }
    }
}
