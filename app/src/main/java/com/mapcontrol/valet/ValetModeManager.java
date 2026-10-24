package com.mapcontrol.valet;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/** Persisted valet PIN and session state. */
public final class ValetModeManager {

    private static final String PREFS = "MapControlPrefs";
    private static final String KEY_PIN_HASH = "valetPinHash";
    private static final String KEY_PIN_SALT = "valetPinSalt";
    private static final String KEY_SESSION = "valetSession";

    private ValetModeManager() {
    }

    public static boolean hasPin(Context context) {
        SharedPreferences prefs = prefs(context);
        return !prefs.getString(KEY_PIN_HASH, "").isEmpty()
                && !prefs.getString(KEY_PIN_SALT, "").isEmpty();
    }

    public static boolean setPin(Context context, String pin) {
        if (pin == null || !pin.matches("\\d{4}")) {
            return false;
        }
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String saltText = Base64.encodeToString(salt, Base64.NO_WRAP);
        prefs(context).edit()
                .putString(KEY_PIN_SALT, saltText)
                .putString(KEY_PIN_HASH, hash(salt, pin))
                .apply();
        return true;
    }

    public static boolean verifyPin(Context context, String pin) {
        SharedPreferences prefs = prefs(context);
        String saltText = prefs.getString(KEY_PIN_SALT, "");
        String expected = prefs.getString(KEY_PIN_HASH, "");
        if (pin == null || saltText.isEmpty() || expected.isEmpty()) {
            return false;
        }
        try {
            byte[] salt = Base64.decode(saltText, Base64.NO_WRAP);
            return expected.equals(hash(salt, pin));
        } catch (Exception ignored) {
            return false;
        }
    }

    public static ValetSession getSession(Context context) {
        String raw = prefs(context).getString(KEY_SESSION, "");
        if (raw == null || raw.isEmpty()) {
            return new ValetSession();
        }
        try {
            return ValetSession.fromJson(new org.json.JSONObject(raw));
        } catch (Exception ignored) {
            return new ValetSession();
        }
    }

    public static void saveSession(Context context, ValetSession session) {
        if (session == null) {
            return;
        }
        prefs(context).edit().putString(KEY_SESSION, session.toJson().toString()).apply();
    }

    public static boolean isActive(Context context) {
        return getSession(context).active;
    }

    public static boolean shouldHidePersonal(Context context) {
        ValetSession session = getSession(context);
        return session.active && session.hidePersonal;
    }

    public static void startSession(Context context, ValetSession session, Location start) {
        session.active = true;
        session.startTimeMs = System.currentTimeMillis();
        session.endTimeMs = 0L;
        session.distanceKm = 0.0;
        session.maxSpeedKmh = 0;
        session.alerts.clear();
        if (start != null) {
            session.startLat = start.getLatitude();
            session.startLon = start.getLongitude();
            session.lastLat = session.startLat;
            session.lastLon = session.startLon;
        }
        saveSession(context, session);
    }

    public static void endSession(Context context) {
        ValetSession session = getSession(context);
        session.active = false;
        session.endTimeMs = System.currentTimeMillis();
        saveSession(context, session);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String hash(byte[] salt, String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            digest.update(pin.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(digest.digest(), Base64.NO_WRAP);
        } catch (Exception ignored) {
            return "";
        }
    }
}
