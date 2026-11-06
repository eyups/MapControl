package com.eyups.emulatorcontrol;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Her uygulamanın üstünde duran küçük VDBus komut paneli. */
public final class OverlayService extends Service {
    private static final String TAG = "VdbusOverlay";
    private static final String CHANNEL = "vdbus_overlay";

    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private View collapsed;
    private View expanded;
    private boolean expandedVisible;

    private final List<BodyToggle> bodyToggles = new ArrayList<>();
    private int downX;
    private int downY;
    private int startX;
    private int startY;
    private boolean dragging;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createChannel();
        showCollapsed();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, notification());
        if (intent != null && "stop".equals(intent.getAction())) {
            stopSelf();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        try {
            if (collapsed != null) windowManager.removeView(collapsed);
            if (expanded != null) windowManager.removeView(expanded);
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    private void createChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL, "VDBus overlay", NotificationManager.IMPORTANCE_LOW));
        }
    }

    private Notification notification() {
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentTitle("VDBus overlay")
                .setContentText("Tüm uygulamaların üstünde")
                .setOngoing(true)
                .build();
    }

    private WindowManager.LayoutParams baseParams(boolean focusable) {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | (focusable ? 0 : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE),
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        p.x = 24;
        p.y = 220;
        return p;
    }

    private void showCollapsed() {
        expandedVisible = false;
        if (expanded != null) {
            try {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(expanded.getWindowToken(), 0);
                }
            } catch (Throwable ignored) {
            }
            try { windowManager.removeView(expanded); } catch (Throwable ignored) {}
            expanded = null;
        }
        if (collapsed != null) {
            try { windowManager.removeView(collapsed); } catch (Throwable ignored) {}
            collapsed = null;
        }
        TextView pill = new TextView(this);
        pill.setText("VDBus");
        pill.setTextColor(Color.WHITE);
        pill.setTextSize(14);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(18), dp(10), dp(18), dp(10));
        pill.setBackgroundColor(Color.rgb(20, 20, 20));
        pill.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = (int) event.getRawX();
                        downY = (int) event.getRawY();
                        startX = params.x;
                        startY = params.y;
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) event.getRawX() - downX;
                        int dy = (int) event.getRawY() - downY;
                        if (Math.abs(dx) > dp(8) || Math.abs(dy) > dp(8)) {
                            dragging = true;
                            params.x = startX + dx;
                            params.y = startY + dy;
                            windowManager.updateViewLayout(collapsed, params);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!dragging) {
                            showExpanded(params.x, params.y);
                        }
                        return true;
                }
                return false;
            }
        });
        collapsed = pill;
        params = baseParams(false);
        windowManager.addView(collapsed, params);
    }

    private void showExpanded(int x, int y) {
        if (expanded != null) {
            try { windowManager.removeView(expanded); } catch (Throwable ignored) {}
            expanded = null;
        }
        if (collapsed != null) {
            try { windowManager.removeView(collapsed); } catch (Throwable ignored) {}
            collapsed = null;
        }
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        panel.setBackgroundColor(Color.argb(235, 24, 24, 24));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("VDBus");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button topHide = button("Gizle", v -> showCollapsed());
        titleRow.addView(topHide);
        panel.addView(titleRow);

        LinearLayout powerRow = new LinearLayout(this);
        powerRow.setOrientation(LinearLayout.HORIZONTAL);
        powerRow.addView(button("ON", v -> send(327684, 14, new int[]{2})));
        powerRow.addView(button("STBY", v -> send(327684, 14, new int[]{1})));
        powerRow.addView(button("OFF", v -> send(327684, 14, new int[]{0})));
        panel.addView(powerRow);

        LinearLayout signalRow = new LinearLayout(this);
        signalRow.setOrientation(LinearLayout.HORIZONTAL);
        signalRow.addView(button("Sol", v -> signal(1)));
        signalRow.addView(button("Sağ", v -> signal(2)));
        signalRow.addView(button("Dörtlü", v -> signal(3)));
        signalRow.addView(button("Sinyal Kapat", v -> signal(0)));
        panel.addView(signalRow);

        LinearLayout fuelRow = new LinearLayout(this);
        fuelRow.setOrientation(LinearLayout.HORIZONTAL);
        EditText fuelValue = edit("yakıt %", "50");
        fuelValue.setInputType(InputType.TYPE_CLASS_NUMBER);
        fuelValue.setSingleLine(true);
        fuelRow.addView(fuelValue, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        fuelRow.addView(button("Yakıt", v -> setFuel(fuelValue)));
        panel.addView(fuelRow);
        panel.addView(button("Gövde testi", v -> showBodyPanel(params.x, params.y)));

        EditText event = edit("event", "327684");
        event.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText cmd = edit("cmd", "14");
        cmd.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText values = edit("values", "2");
        values.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        panel.addView(event);
        panel.addView(cmd);
        panel.addView(values);

        LinearLayout sendRow = new LinearLayout(this);
        sendRow.setOrientation(LinearLayout.HORIZONTAL);
        sendRow.addView(button("Gönder", v -> {
            try {
                int e = Integer.parseInt(event.getText().toString().trim());
                int c = Integer.parseInt(cmd.getText().toString().trim());
                String[] parts = values.getText().toString().trim().split(",");
                int[] arr = new int[parts.length];
                for (int i = 0; i < parts.length; i++) arr[i] = Integer.parseInt(parts[i].trim());
                send(e, c, arr);
            } catch (Throwable t) {
                Log.w(TAG, "invalid event", t);
            }
        }));
        sendRow.addView(button("Temizle", v -> VdbusCommandSender.clear(this, VdbusCommandSender.CARINFO)));
        sendRow.addView(button("Gizle", v -> showCollapsed()));
        sendRow.addView(button("Durdur", v -> stopSelf()));
        panel.addView(sendRow);

        expanded = panel;
        expandedVisible = true;
        params = baseParams(true);
        params.x = Math.max(0, Math.min(x, dp(10)));
        params.y = Math.max(0, y);
        windowManager.addView(expanded, params);
    }

    private void showBodyPanel(int x, int y) {
        if (expanded != null) {
            try { windowManager.removeView(expanded); } catch (Throwable ignored) {}
            expanded = null;
        }
        bodyToggles.clear();

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        panel.setBackgroundColor(Color.argb(240, 24, 24, 24));

        TextView title = new TextView(this);
        title.setText("Gövde testi · fake VDBus");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        panel.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Yalnızca fake VDBus'a sinyal gönderir; gerçek araç actuator'ına komut gitmez.");
        hint.setTextColor(Color.LTGRAY);
        hint.setTextSize(11);
        hint.setPadding(0, dp(2), 0, dp(6));
        panel.addView(hint);

        bodyRow(panel,
                new BodyToggle("Sol ön kapı", 327684, 15, 1, 0),
                new BodyToggle("Sağ ön kapı", 327684, 16, 1, 0));
        bodyRow(panel,
                new BodyToggle("Sol arka kapı", 327684, 17, 1, 0),
                new BodyToggle("Sağ arka kapı", 327684, 18, 1, 0));
        bodyRow(panel,
                new BodyToggle("Sol ön cam", 327681, 100, 1, 0),
                new BodyToggle("Sağ ön cam", 327681, 101, 1, 0));
        bodyRow(panel,
                new BodyToggle("Sol arka cam", 327681, 102, 1, 0),
                new BodyToggle("Sağ arka cam", 327681, 103, 1, 0));
        bodyRow(panel,
                new BodyToggle("Bagaj", 327684, 19, 1, 0),
                new BodyToggle("Kaput", 327684, 47, 1, 0));
        bodyRow(panel,
                new BodyToggle("Cam tavan", 327681, 6, 2, 1),
                new BodyToggle("Güneşlik", 327681, 10, 2, 1));

        LinearLayout speedRow = new LinearLayout(this);
        speedRow.setOrientation(LinearLayout.HORIZONTAL);
        Button speed = button("Hız: 0 km/s", null);
        speed.setOnClickListener(v -> {
            boolean high = Boolean.TRUE.equals(speed.getTag());
            boolean next = !high;
            speed.setTag(next);
            speed.setText(next ? "Hız: 30 km/s" : "Hız: 0 km/s");
            send(327684, 0, new int[]{next ? 30 : 0});
        });
        speedRow.addView(speed, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button closeAll = button("Tümünü kapat", v -> {
            for (BodyToggle toggle : bodyToggles) {
                toggle.setOpen(false);
            }
            send(327684, 0, new int[]{0});
            closeAllBody();
        });
        speedRow.addView(closeAll, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        panel.addView(speedRow);
        panel.addView(button("Geri", v -> showExpanded(x, y)));

        expanded = panel;
        params = baseParams(true);
        params.x = Math.max(0, Math.min(x, dp(10)));
        params.y = Math.max(0, y);
        windowManager.addView(expanded, params);
    }

    private void bodyRow(LinearLayout panel, BodyToggle left, BodyToggle right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        addBodyToggle(row, left);
        addBodyToggle(row, right);
        panel.addView(row);
    }

    private void addBodyToggle(LinearLayout row, BodyToggle toggle) {
        toggle.button = button(toggle.label + ": kapalı", null);
        toggle.button.setOnClickListener(v -> {
            boolean open = !toggle.open;
            toggle.setOpen(open);
            send(toggle.event, toggle.cmd, new int[]{open ? toggle.openValue : toggle.closeValue});
        });
        bodyToggles.add(toggle);
        row.addView(toggle.button, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    }

    private void closeAllBody() {
        send(327684, 15, new int[]{0});
        send(327684, 16, new int[]{0});
        send(327684, 17, new int[]{0});
        send(327684, 18, new int[]{0});
        send(327681, 100, new int[]{0});
        send(327681, 101, new int[]{0});
        send(327681, 102, new int[]{0});
        send(327681, 103, new int[]{0});
        send(327684, 19, new int[]{0});
        send(327684, 47, new int[]{0});
        send(327681, 6, new int[]{1});
        send(327681, 10, new int[]{1});
        send(327684, 0, new int[]{0});
    }

    private static final class BodyToggle {
        final String label;
        final int event;
        final int cmd;
        final int openValue;
        final int closeValue;
        Button button;
        boolean open;

        BodyToggle(String label, int event, int cmd, int openValue, int closeValue) {
            this.label = label;
            this.event = event;
            this.cmd = cmd;
            this.openValue = openValue;
            this.closeValue = closeValue;
        }

        void setOpen(boolean open) {
            this.open = open;
            if (button != null) {
                button.setText(label + (open ? ": açık" : ": kapalı"));
            }
        }
    }

    private Button button(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setOnClickListener(listener);
        return b;
    }

    private EditText edit(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setTextSize(12);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.LTGRAY);
        e.setSingleLine(true);
        return e;
    }

    private void setFuel(EditText input) {
        try {
            int pct = Integer.parseInt(input.getText().toString().trim());
            if (pct < 0 || pct > 100) return;
            send(327684, 68, new int[]{pct});
            Log.i(TAG, "fuel set %" + pct);
        } catch (Throwable t) {
            Log.w(TAG, "invalid fuel", t);
        }
    }

    /** 0=off, 1=left, 2=right, 3=hazard. ReadOnly (327684) sinyal ID'leri. */
    private void signal(int mode) {
        boolean left = mode == 1 || mode == 3;
        boolean right = mode == 2 || mode == 3;
        boolean hazard = mode == 3;
        send(327684, 35, new int[]{left ? 1 : 0});   // ID_DIRECTION_IND_LEFT
        send(327684, 33, new int[]{left ? 1 : 0});   // ID_LH_TURN_LIGHT_STS
        send(327684, 34, new int[]{right ? 1 : 0});  // ID_DIRECTION_IND_RIGHT
        send(327684, 32, new int[]{right ? 1 : 0});  // ID_RH_TURN_LIGHT_STS
        send(327684, 79, new int[]{hazard ? 1 : 0}); // ID_HAZARD_LIGHT
    }

    private void send(int event, int cmd, int[] values) {
        VdbusCommandSender.send(this, VdbusCommandSender.CARINFO, event, cmd, values);
        Log.i(TAG, "sent event=" + event + " cmd=" + cmd);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
