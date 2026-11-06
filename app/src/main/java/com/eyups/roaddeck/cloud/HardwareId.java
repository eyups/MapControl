package com.eyups.roaddeck.cloud;

import android.content.Context;
import android.provider.Settings;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Cihaz yeniden kurulumlarında aynı kalan, sır olmayan tekilleştirme kimliği. */
final class HardwareId {

    private HardwareId() {
    }

    static String get(Context context) {
        try {
            String androidId = Settings.Secure.getString(
                    context.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (androidId == null || androidId.trim().isEmpty()) {
                return "";
            }
            return sha256("mapcontrol-car|" + androidId);
        } catch (Exception error) {
            return "";
        }
    }

    static String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) {
            builder.append(String.format("%02x", item & 0xff));
        }
        return builder.toString();
    }
}
