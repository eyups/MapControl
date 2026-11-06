package com.eyups.roaddeck.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Eski MapControl prefs adlarının RoadDeck adlarına eşlenmesi ve değer tiplerinin
 * taşınma sırasında korunması.
 */
public class LegacyPrefsMigrationTest {

    /** Sadece yazılan değerleri toplayan minimal Editor. */
    private static final class RecordingEditor implements SharedPreferences.Editor {
        final Map<String, Object> written = new HashMap<>();

        @Override
        public SharedPreferences.Editor putString(String key, String value) {
            written.put(key, value);
            return this;
        }

        @Override
        public SharedPreferences.Editor putStringSet(String key, Set<String> values) {
            written.put(key, values);
            return this;
        }

        @Override
        public SharedPreferences.Editor putInt(String key, int value) {
            written.put(key, value);
            return this;
        }

        @Override
        public SharedPreferences.Editor putLong(String key, long value) {
            written.put(key, value);
            return this;
        }

        @Override
        public SharedPreferences.Editor putFloat(String key, float value) {
            written.put(key, value);
            return this;
        }

        @Override
        public SharedPreferences.Editor putBoolean(String key, boolean value) {
            written.put(key, value);
            return this;
        }

        @Override
        public SharedPreferences.Editor remove(String key) {
            written.remove(key);
            return this;
        }

        @Override
        public SharedPreferences.Editor clear() {
            written.clear();
            return this;
        }

        @Override
        public boolean commit() {
            return true;
        }

        @Override
        public void apply() {
        }
    }

    @Test
    public void roadModeKeyIsRenamed() {
        assertEquals(LegacyPrefsMigration.KEY_ROAD_MODE,
                LegacyPrefsMigration.mapKey(LegacyPrefsMigration.LEGACY_KEY_ROAD_MODE));
        assertEquals("themeName", LegacyPrefsMigration.mapKey("themeName"));
    }

    @Test
    public void valueTypesArePreserved() {
        RecordingEditor editor = new RecordingEditor();
        LegacyPrefsMigration.putValue(editor, "b", Boolean.TRUE);
        LegacyPrefsMigration.putValue(editor, "s", "text");
        LegacyPrefsMigration.putValue(editor, "i", 42);
        LegacyPrefsMigration.putValue(editor, "l", 42L);
        LegacyPrefsMigration.putValue(editor, "f", 1.5f);
        Set<String> set = new HashSet<>();
        set.add("a");
        LegacyPrefsMigration.putValue(editor, "set", set);

        assertEquals(Boolean.TRUE, editor.written.get("b"));
        assertEquals("text", editor.written.get("s"));
        assertEquals(42, editor.written.get("i"));
        assertEquals(42L, editor.written.get("l"));
        assertEquals(1.5f, editor.written.get("f"));
        assertEquals(set, editor.written.get("set"));
        assertEquals(6, editor.written.size());
    }

    @Test
    public void unsupportedTypeIsSkippedWithoutThrowing() {
        RecordingEditor editor = new RecordingEditor();
        LegacyPrefsMigration.putValue(editor, "obj", new Object());
        assertTrue(editor.written.isEmpty());
    }
}
