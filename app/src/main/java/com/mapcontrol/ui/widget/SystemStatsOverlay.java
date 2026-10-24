package com.mapcontrol.ui.widget;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mapcontrol.R;
import com.mapcontrol.vehicle.ComfortCommands;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tüm MapControl ekranlarının üstünde duran küçük sistem HUD'ı.
 *
 * <p>CPU ve RAM Android/Linux arayüzlerinden okunur. GPU yüzdesi yalnızca cihazda
 * Qualcomm kgsl veya benzeri bir sysfs düğümü varsa gösterilir; yoksa "—" yazılır.
 * Sıcaklıklar thermal sysfs, batarya sticky intent'i ve araç VDBus değerlerinden gelir.
 * Okumalar tek bir arka plan thread'inde yapılır; ana thread yalnızca TextView günceller.</p>
 */
public final class SystemStatsOverlay extends LinearLayout {

    public static final String PREF_ENABLED = "systemStatsOverlayEnabled";

    private static final long REFRESH_MS = 2000L;
    private static final Pattern NUMBER = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?");

    private final Context context;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService executor;
    private final TextView valuesLine;
    private final TextView temperaturesLine;

    private volatile boolean enabled;
    private volatile boolean active;
    private volatile ScheduledFuture<?> scheduled;
    private long lastCpuTotal = -1L;
    private long lastCpuIdle = -1L;
    private long lastProcessCpuMs = -1L;
    private long lastProcessWallMs = -1L;
    private volatile boolean thermalUnavailable;
    private volatile boolean batteryUnavailable;

    public SystemStatsOverlay(Context context) {
        super(context);
        this.context = context.getApplicationContext();

        setOrientation(LinearLayout.VERTICAL);
        setGravity(Gravity.START);
        setPadding(dp(12), dp(8), dp(12), dp(8));
        setElevation(dp(16));
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setBackground(createBackground());

        valuesLine = new TextView(context);
        valuesLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        valuesLine.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        valuesLine.setTextColor(Color.WHITE);
        valuesLine.setSingleLine(true);
        addView(valuesLine, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        temperaturesLine = new TextView(context);
        temperaturesLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        temperaturesLine.setTypeface(Typeface.MONOSPACE);
        temperaturesLine.setTextColor(0xFFB8C4D4);
        temperaturesLine.setSingleLine(true);
        LinearLayout.LayoutParams tempLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tempLp.topMargin = dp(2);
        addView(temperaturesLine, tempLp);

        setVisibility(View.GONE);

        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "system-stats-overlay");
            thread.setDaemon(true);
            return thread;
        };
        executor = Executors.newSingleThreadScheduledExecutor(threadFactory);
    }

    /** Ayarlar anahtarından gelen görünürlük durumu. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        setVisibility(enabled ? View.VISIBLE : View.GONE);
        if (!enabled) {
            stopUpdates();
        } else if (active) {
            startUpdates();
        }
    }

    /** Activity ön planda mı? Arka planda ölçüm yapılmaz. */
    public void setActive(boolean active) {
        this.active = active;
        if (active && enabled) {
            startUpdates();
        } else if (!active) {
            stopUpdates();
        }
    }

    public void release() {
        stopUpdates();
        executor.shutdownNow();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        active = true;
        if (enabled) {
            startUpdates();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        active = false;
        stopUpdates();
        super.onDetachedFromWindow();
    }

    private synchronized void startUpdates() {
        if (!enabled || !active || executor.isShutdown()) {
            return;
        }
        if (scheduled != null && !scheduled.isDone()) {
            return;
        }
        lastCpuTotal = -1L;
        lastCpuIdle = -1L;
        lastProcessCpuMs = -1L;
        lastProcessWallMs = -1L;
        scheduled = executor.scheduleWithFixedDelay(this::sample, 0L, REFRESH_MS, TimeUnit.MILLISECONDS);
    }

    private synchronized void stopUpdates() {
        if (scheduled != null) {
            scheduled.cancel(false);
            scheduled = null;
        }
    }

    private void sample() {
        if (!enabled || !active) {
            return;
        }
        try {
            Float cpu = readCpuPercent();
            Float gpu = readGpuPercent();
            Float ram = readRamPercent();
            Float cpuTemp = readThermalTemp(false);
            Float gpuTemp = readThermalTemp(true);
            Float batteryTemp = readBatteryTemp();
            Float waterTemp = readWaterTemp();
            Float outsideTemp = readOutsideTemp();

            String values = context.getString(R.string.system_stats_cpu) + " " + pct(cpu)
                    + "   " + context.getString(R.string.system_stats_gpu) + " " + pct(gpu)
                    + "   " + context.getString(R.string.system_stats_ram) + " " + pct(ram);
            String temperatures = context.getString(R.string.system_stats_cpu) + " " + temp(cpuTemp)
                    + "   " + context.getString(R.string.system_stats_gpu) + " " + temp(gpuTemp)
                    + "   " + context.getString(R.string.system_stats_battery) + " " + temp(batteryTemp)
                    + "   " + context.getString(R.string.system_stats_engine) + " " + temp(waterTemp)
                    + "   " + context.getString(R.string.system_stats_outside) + " " + temp(outsideTemp);

            uiHandler.post(() -> {
                if (!enabled || !active) {
                    return;
                }
                valuesLine.setText(values);
                temperaturesLine.setText(temperatures);
            });
        } catch (Throwable ignored) {
            // HUD asla ana uygulamayı durdurmamalı.
        }
    }

    private Float readCpuPercent() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"))) {
            String line = reader.readLine();
            if (line == null || !line.startsWith("cpu ")) {
                return null;
            }
            String[] parts = line.trim().split("\\s+");
            long total = 0L;
            long idle = 0L;
            for (int i = 1; i < parts.length && i <= 8; i++) {
                long value = Long.parseLong(parts[i]);
                total += value;
                if (i == 4 || i == 5) {
                    idle += value;
                }
            }
            if (lastCpuTotal >= 0 && total > lastCpuTotal) {
                long totalDelta = total - lastCpuTotal;
                long idleDelta = Math.max(0L, idle - lastCpuIdle);
                float percent = (totalDelta - idleDelta) * 100f / totalDelta;
                return clampPercent(percent);
            }
            lastCpuTotal = total;
            lastCpuIdle = idle;
            return readCpuFallback();
        } catch (Throwable ignored) {
            return readCpuFallback();
        }
    }

    private Float readCpuFallback() {
        Float load = readCpuLoadPercent();
        return load != null ? load : readAppCpuPercent();
    }

    private Float readAppCpuPercent() {
        try {
            long cpuMs = android.os.Process.getElapsedCpuTime();
            long wallMs = SystemClock.elapsedRealtime();
            if (lastProcessCpuMs >= 0 && wallMs > lastProcessWallMs) {
                long cpuDelta = Math.max(0L, cpuMs - lastProcessCpuMs);
                long wallDelta = wallMs - lastProcessWallMs;
                int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
                return clampPercent(cpuDelta * 100f / wallDelta / cores);
            }
            lastProcessCpuMs = cpuMs;
            lastProcessWallMs = wallMs;
            return readCpuLoadPercent();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Float readCpuLoadPercent() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/loadavg"))) {
            String line = reader.readLine();
            if (line == null) {
                return null;
            }
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 0) {
                return null;
            }
            float load = Float.parseFloat(parts[0]);
            int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
            return clampPercent(load * 100f / cores);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Float readGpuPercent() {
        String[] paths = {
                "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
                "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_busy_percentage",
                "/sys/kernel/gpu/gpu_busy",
                "/sys/class/devfreq/gpu/load"
        };
        for (String path : paths) {
            Float value = readNumeric(path);
            if (value != null) {
                return clampPercent(value);
            }
        }
        return null;
    }

    private Float readRamPercent() {
        try {
            ActivityManager manager =
                    (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager == null) {
                return null;
            }
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(info);
            if (info.totalMem <= 0) {
                return null;
            }
            return clampPercent((info.totalMem - info.availMem) * 100f / info.totalMem);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Float readThermalTemp(boolean gpu) {
        if (thermalUnavailable) {
            return null;
        }
        File thermalRoot = new File("/sys/class/thermal");
        File[] zones = thermalRoot.listFiles((dir, name) -> name.startsWith("thermal_zone"));
        if (zones == null) {
            thermalUnavailable = true;
            return null;
        }
        for (File zone : zones) {
            String type = readText(new File(zone, "type"));
            if (type == null) {
                continue;
            }
            String lower = type.toLowerCase(Locale.US);
            boolean match = gpu
                    ? (lower.contains("gpu") || lower.contains("kgsl") || lower.contains("adreno"))
                    : (lower.contains("cpu") || lower.contains("soc") || lower.contains("tsens"));
            if (!match) {
                continue;
            }
            Float raw = readNumeric(new File(zone, "temp").getAbsolutePath());
            if (raw != null) {
                float celsius = raw > 1000f ? raw / 1000f : raw;
                if (celsius > -40f && celsius < 150f) {
                    return celsius;
                }
            }
        }
        return null;
    }

    private Float readBatteryTemp() {
        if (batteryUnavailable) {
            return null;
        }
        try {
            Intent intent = context.registerReceiver(
                    null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (intent == null
                    || intent.getIntExtra(BatteryManager.EXTRA_PRESENT, 1) == 0) {
                batteryUnavailable = true;
                return null;
            }
            int tenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            if (tenths == Integer.MIN_VALUE) {
                batteryUnavailable = true;
                return null;
            }
            return tenths / 10f;
        } catch (Throwable ignored) {
            batteryUnavailable = true;
            return null;
        }
    }

    private Float readWaterTemp() {
        try {
            if (!ComfortCommands.isConnected()) {
                return null;
            }
            float value = ComfortCommands.readWaterTempC();
            return Float.isNaN(value) ? null : value;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Float readOutsideTemp() {
        try {
            if (!ComfortCommands.isConnected()) {
                return null;
            }
            float value = ComfortCommands.readOutsideTempC();
            return Float.isNaN(value) ? null : value;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Float readNumeric(String path) {
        String text = readText(new File(path));
        if (text == null) {
            return null;
        }
        Matcher matcher = NUMBER.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Float.parseFloat(matcher.group());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String readText(File file) {
        if (file == null || !file.canRead()) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return reader.readLine();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private float clampPercent(float value) {
        return Math.max(0f, Math.min(100f, value));
    }

    private String pct(Float value) {
        return value == null ? "—" : String.format(Locale.US, "%.0f%%", value);
    }

    private String temp(Float value) {
        return value == null ? "—" : String.format(Locale.US, "%.1f°C", value);
    }

    private GradientDrawable createBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0xD90B0F14);
        drawable.setCornerRadius(dp(14));
        drawable.setStroke(dp(1), 0x6638BDF8);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
