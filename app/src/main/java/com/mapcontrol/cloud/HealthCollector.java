package com.mapcontrol.cloud;

import android.app.ActivityManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;

import com.mapcontrol.BuildConfig;

import org.json.JSONObject;

import java.lang.reflect.Method;

/** F3/A5: disk, bellek, sürüm, ağ ve TBox durumunu salt-okunur toplar. */
final class HealthCollector {

    private HealthCollector() {
    }

    static JSONObject collect(Context context) {
        JSONObject health = new JSONObject();
        try {
            health.put("ts_ms", System.currentTimeMillis());
            health.put("app_version", BuildConfig.VERSION_NAME);
            health.put("android_version", Build.VERSION.RELEASE);
            health.put("sdk_int", Build.VERSION.SDK_INT);
            health.put("model", Build.MODEL);
            health.put("uptime_s", SystemClock.elapsedRealtime() / 1000L);

            ActivityManager activityManager =
                    (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager != null) {
                ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
                activityManager.getMemoryInfo(memory);
                health.put("memory_total_kb", memory.totalMem / 1024L);
                health.put("memory_avail_kb", memory.availMem / 1024L);
            }

            StatFs data = new StatFs(context.getFilesDir().getAbsolutePath());
            health.put("disk_total_kb", data.getTotalBytes() / 1024L);
            health.put("disk_free_kb", data.getAvailableBytes() / 1024L);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                if (power != null) {
                    health.put("thermal_status", power.getCurrentThermalStatus());
                }
            }

            health.put("network_type", networkType(context));

            JSONObject tbox = new JSONObject();
            putBoolean(tbox, "sim_registered", prop("sys.vehicle.tbox.sim.state"));
            putInt(tbox, "network_state", prop("sys.vehicle.tbox.network.state"));
            putString(tbox, "network_type", prop("sys.vehicle.tbox.network.type"));
            putInt(tbox, "signal", prop("sys.vehicle.tbox.signal.strength"));
            String activation = prop("sys.tsp.sim.activation");
            if (activation != null && !activation.isEmpty()) {
                putBoolean(tbox, "tsp_activated", "0".equals(activation) ? "true" : "false");
            }
            if (tbox.length() > 0) {
                health.put("tbox", tbox);
            }
        } catch (Exception ignored) {
            // Sağlık raporu eksik alanlarla da gönderilebilir.
        }
        return health;
    }

    private static String networkType(Context context) {
        ConnectivityManager manager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return "";
        }
        android.net.Network network = manager.getActiveNetwork();
        NetworkCapabilities caps = manager.getNetworkCapabilities(network);
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return "offline";
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return "wifi";
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return "cellular";
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return "ethernet";
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            return "vpn";
        }
        return "other";
    }

    private static String prop(String key) {
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            Method get = systemProperties.getMethod("get", String.class, String.class);
            Object value = get.invoke(null, key, "");
            return value == null ? "" : value.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void putBoolean(JSONObject object, String key, String raw) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        try {
            object.put(key, "true".equalsIgnoreCase(raw) || "1".equals(raw));
        } catch (Exception ignored) {
        }
    }

    private static void putInt(JSONObject object, String key, String raw) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        try {
            object.put(key, Integer.parseInt(raw));
        } catch (Exception ignored) {
        }
    }

    private static void putString(JSONObject object, String key, String raw) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        try {
            object.put(key, raw);
        } catch (Exception ignored) {
        }
    }
}
