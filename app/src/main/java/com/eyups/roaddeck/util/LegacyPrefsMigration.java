package com.eyups.roaddeck.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.util.Map;
import java.util.Set;

/**
 * Eski "MapControl*" SharedPreferences dosya ve anahtarlarını "RoadDeck*" adlarına taşır.
 *
 * <p>Yalnızca ilk açılışta gerçek iş yapar: sonraki açılışlarda dosya varlık kontrolü
 * (iki {@code stat}) dışında hiçbir okuma/yazma yapılmaz. Taşıma başarılıysa eski dosya
 * silinir, böylece işlem tekrar etmez.
 */
public final class LegacyPrefsMigration {

    private static final String TAG = "LegacyPrefsMigration";

    /** {eski ad, yeni ad} çiftleri. */
    private static final String[][] FILES = {
            {"MapControlPrefs", "RoadDeckPrefs"},
            {"mapcontrol_locale", "roaddeck_locale"},
            {"mapcontrol_split_screen", "roaddeck_split_screen"},
    };

    static final String LEGACY_KEY_ROAD_MODE = "mapControlKeyEnabled";
    static final String KEY_ROAD_MODE = "roadDeckKeyEnabled";

    private LegacyPrefsMigration() {
    }

    /** Uygulama açılışında, prefs'lere ilk erişimden önce çağrılmalı. */
    public static void run(Context context) {
        Context app = context.getApplicationContext();
        for (String[] pair : FILES) {
            try {
                migrateFile(app, pair[0], pair[1]);
            } catch (Throwable error) {
                Log.w(TAG, "prefs taşınamadı: " + pair[0], error);
            }
        }
    }

    /** Anahtar adı eşlemesi; taşınan anahtar dışındakiler aynen korunur. */
    static String mapKey(String key) {
        return LEGACY_KEY_ROAD_MODE.equals(key) ? KEY_ROAD_MODE : key;
    }

    /** Değeri tipini koruyarak editöre yazar; desteklenmeyen tip sessizce atlanır. */
    @SuppressWarnings("unchecked")
    static void putValue(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof String) {
            editor.putString(key, (String) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else if (value instanceof Set) {
            editor.putStringSet(key, (Set<String>) value);
        }
    }

    private static void migrateFile(Context app, String legacyName, String newName) {
        File dir = new File(app.getApplicationInfo().dataDir, "shared_prefs");
        File legacyFile = new File(dir, legacyName + ".xml");
        File newFile = new File(dir, newName + ".xml");
        if (!legacyFile.isFile() || newFile.exists()) {
            return;
        }

        SharedPreferences legacy = app.getSharedPreferences(legacyName, Context.MODE_PRIVATE);
        Map<String, ?> values = legacy.getAll();
        if (values.isEmpty()) {
            app.deleteSharedPreferences(legacyName);
            return;
        }

        SharedPreferences.Editor editor =
                app.getSharedPreferences(newName, Context.MODE_PRIVATE).edit();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            putValue(editor, mapKey(entry.getKey()), entry.getValue());
        }
        if (!editor.commit()) {
            // Kısmi dosya kalırsa sonraki açılışta "zaten taşındı" sanılmasın.
            app.deleteSharedPreferences(newName);
            Log.w(TAG, "prefs yazılamadı, eski dosya korundu: " + legacyName);
            return;
        }
        app.deleteSharedPreferences(legacyName);
        Log.i(TAG, "prefs taşındı: " + legacyName + " -> " + newName
                + " (" + values.size() + " anahtar)");
    }
}
