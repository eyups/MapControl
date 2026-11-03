package com.mapcontrol.assistant.tools;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ToolArgumentValidatorTest {

    private static ToolSpec sunroof() {
        return new ToolSpec("sunroof_set", "test")
                .param("action", ToolSpec.TYPE_STRING, "işlem",
                        new String[]{"open", "close", "vent"}, true);
    }

    private static ToolSpec seat() {
        return new ToolSpec("seat_heat_set", "test")
                .param("seat", ToolSpec.TYPE_STRING, "koltuk",
                        new String[]{"driver", "passenger"}, true)
                .param("level", ToolSpec.TYPE_INTEGER, "seviye", null, true)
                .range(0, 3);
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    @Test
    public void acceptsValidEnum() {
        ToolArgumentValidator.Result result = ToolArgumentValidator.validate(
                sunroof(), map("action", "open"));
        assertTrue(result.valid);
        assertEquals("open", result.values.get("action"));
    }

    @Test
    public void normalizesEnumCaseAndSeparators() {
        ToolArgumentValidator.Result result = ToolArgumentValidator.validate(
                sunroof(), map("action", " OPEN "));
        assertTrue(result.valid);
        assertEquals("open", result.values.get("action"));
    }

    @Test
    public void rejectsUnknownEnum() {
        ToolArgumentValidator.Result result = ToolArgumentValidator.validate(
                sunroof(), map("action", "fly"));
        assertFalse(result.valid);
    }

    @Test
    public void rejectsMissingRequired() {
        assertFalse(ToolArgumentValidator.validate(sunroof(), map()).valid);
        assertFalse(ToolArgumentValidator.validate(sunroof(), null).valid);
    }

    @Test
    public void coercesNumbersAndChecksRange() {
        ToolArgumentValidator.Result ok = ToolArgumentValidator.validate(
                seat(), map("seat", "driver", "level", 3.0d));
        assertTrue(ok.valid);
        assertEquals("3", ok.values.get("level"));

        assertFalse(ToolArgumentValidator.validate(
                seat(), map("seat", "driver", "level", 5)).valid);
        assertFalse(ToolArgumentValidator.validate(
                seat(), map("seat", "driver", "level", "iki")).valid);
    }

    @Test
    public void coercesBooleansFromTurkish() {
        ToolSpec spec = new ToolSpec("recirculation_set", "test")
                .param("on", ToolSpec.TYPE_BOOLEAN, "aç/kapat", null, true);
        ToolArgumentValidator.Result result = ToolArgumentValidator.validate(
                spec, map("on", "evet"));
        assertTrue(result.valid);
        assertEquals("true", result.values.get("on"));
    }

    @Test
    public void ignoresUnknownArguments() {
        ToolArgumentValidator.Result result = ToolArgumentValidator.validate(
                sunroof(), map("action", "open", "module", 327681));
        assertTrue(result.valid);
        assertFalse(result.values.containsKey("module"));
    }

    @Test
    public void rejectsOverlongValues() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            longText.append('a');
        }
        ToolSpec spec = new ToolSpec("navigate_to", "test")
                .param("destination", ToolSpec.TYPE_STRING, "hedef", null, true);
        assertFalse(ToolArgumentValidator.validate(
                spec, map("destination", longText.toString())).valid);
    }
}
