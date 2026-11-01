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
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Her uygulamanın üstünde duran küçük VDBus komut paneli. */
public final class OverlayService extends Service {
    private static final String TAG = "VdbusOverlay";
    private static final String CHANNEL = "vdbus_overlay";

    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private View collapsed;
    private View expanded;
    private boolean expandedVisible;

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
        if (collapsed != null) {
            try { windowManager.removeView(collapsed); } catch (Throwable ignored) {}
            collapsed = null;
        }
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        panel.setBackgroundColor(Color.argb(235, 24, 24, 24));

        TextView title = new TextView(this);
        title.setText("VDBus");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        panel.addView(title);

        LinearLayout powerRow = new LinearLayout(this);
        powerRow.setOrientation(LinearLayout.HORIZONTAL);
        powerRow.addView(button("ON", v -> send(327684, 14, new int[]{2})));
        powerRow.addView(button("STBY", v -> send(327684, 14, new int[]{1})));
        powerRow.addView(button("OFF", v -> send(327684, 14, new int[]{0})));
        panel.addView(powerRow);

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

    private void send(int event, int cmd, int[] values) {
        VdbusCommandSender.send(this, VdbusCommandSender.CARINFO, event, cmd, values);
        Log.i(TAG, "sent event=" + event + " cmd=" + cmd);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
