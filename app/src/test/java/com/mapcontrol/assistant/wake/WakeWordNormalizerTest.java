package com.mapcontrol.assistant.wake;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WakeWordNormalizerTest {

    @Test
    public void lowercasesTurkishCorrectly() {
        assertEquals("hey deck", WakeWordNormalizer.normalize("  HEY   DECK "));
        // Türkçe büyük İ → i, büyük I → ı
        assertEquals("istanbul", WakeWordNormalizer.normalize("İSTANBUL"));
        assertEquals("ışık", WakeWordNormalizer.normalize("IŞIK"));
    }

    @Test
    public void stripsPunctuationAndCollapsesSpaces() {
        assertEquals("hey deck", WakeWordNormalizer.normalize("Hey,  Deck!"));
        assertEquals("hey deck", WakeWordNormalizer.normalize("Hey - Deck"));
    }

    @Test
    public void countsSyllables() {
        assertEquals(2, WakeWordNormalizer.syllableCount("hey deck"));
        assertEquals(3, WakeWordNormalizer.syllableCount("merhaba"));
        assertEquals(3, WakeWordNormalizer.syllableCount("araba"));
        assertEquals(0, WakeWordNormalizer.syllableCount(""));
    }

    @Test
    public void validatesHeceRange() {
        assertEquals(WakeWordNormalizer.CODE_OK,
                WakeWordNormalizer.validate("hey deck"));
        assertEquals(WakeWordNormalizer.CODE_TOO_SHORT,
                WakeWordNormalizer.validate("hey"));
        assertEquals(WakeWordNormalizer.CODE_EMPTY, WakeWordNormalizer.validate("   "));
        assertEquals(WakeWordNormalizer.CODE_TOO_LONG,
                WakeWordNormalizer.validate("merhaba kaptanım efendim"));
    }

    @Test
    public void warnsForCommonWords() {
        assertEquals(WakeWordNormalizer.CODE_COMMON,
                WakeWordNormalizer.validate("merhaba"));
        assertEquals(WakeWordNormalizer.CODE_COMMON,
                WakeWordNormalizer.validate("asistan"));
        assertFalse(WakeWordNormalizer.describeCode(
                WakeWordNormalizer.CODE_COMMON).isEmpty());
    }

    @Test
    public void matchesRecognizedText() {
        assertTrue(WakeWordNormalizer.matches("Hey deck, cam tavanı aç", "hey deck"));
        assertTrue(WakeWordNormalizer.matches("HEY DECK", "hey deck"));
        assertFalse(WakeWordNormalizer.matches("cam tavanı aç", "hey deck"));
        assertFalse(WakeWordNormalizer.matches("", "hey deck"));
    }

    @Test
    public void handlesApostrophes() {
        assertEquals("decki aç", WakeWordNormalizer.normalize("Deck'i aç"));
    }
}
