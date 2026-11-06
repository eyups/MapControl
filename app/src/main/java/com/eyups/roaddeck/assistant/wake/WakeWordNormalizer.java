package com.eyups.roaddeck.assistant.wake;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Uyandırma kelimesi normalizasyonu ve doğrulaması (Türkçe).
 * İ/ı, boşluk, noktalama ve hece sayısı JVM unit test'lidir.
 */
public final class WakeWordNormalizer {

    public static final int CODE_OK = 0;
    public static final int CODE_EMPTY = 1;
    public static final int CODE_TOO_SHORT = 2;
    public static final int CODE_TOO_LONG = 3;
    public static final int CODE_COMMON = 4;

    private static final Locale TURKISH = new Locale("tr", "TR");

    private static final Set<String> COMMON_WORDS = new HashSet<>(Arrays.asList(
            "merhaba", "selam", "tamam", "evet", "hayır", "hayir", "asistan",
            "araba", "müzik", "muzik", "telefon", "cam", "tavan", "klima",
            "hey", "deck", "road", "harita", "navigasyon", "ses", "aç", "kapat"));

    private static final String VOWELS = "aeıioöuü";

    private WakeWordNormalizer() {
    }

    /** Küçük harf (Türkçe), noktalama temizliği, tek boşluk. */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String value = Normalizer.normalize(raw.trim(), Normalizer.Form.NFC)
                .toLowerCase(TURKISH);
        StringBuilder b = new StringBuilder(value.length());
        boolean lastSpace = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                b.append(c);
                lastSpace = false;
            } else if (c == '\'' || c == '’') {
                // kesme işareti yutulur: "deck'i" → "decki"
            } else if (!lastSpace) {
                b.append(' ');
                lastSpace = true;
            }
        }
        return b.toString().trim().replaceAll("\\s+", " ");
    }

    /** Türkçe sesli harf gruplarına göre hece sayısı. */
    public static int syllableCount(String raw) {
        String value = normalize(raw);
        int count = 0;
        boolean inVowel = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean vowel = VOWELS.indexOf(c) >= 0;
            if (vowel && !inVowel) {
                count++;
            }
            inVowel = vowel;
        }
        return count;
    }

    public static boolean isCommonWord(String raw) {
        String value = normalize(raw);
        if (value.isEmpty()) {
            return false;
        }
        if (!value.contains(" ")) {
            return COMMON_WORDS.contains(value);
        }
        // Çok kelimeli ifadede yalnız ifadenin tamamı yaygınsa uyar.
        return COMMON_WORDS.contains(value);
    }

    /** 2-4 hece önerilir; çok yaygın kelimeler yanlış uyandırma üretir. */
    public static int validate(String raw) {
        String value = normalize(raw);
        if (value.isEmpty()) {
            return CODE_EMPTY;
        }
        int syllables = syllableCount(value);
        if (syllables < 2) {
            return CODE_TOO_SHORT;
        }
        if (syllables > 4) {
            return CODE_TOO_LONG;
        }
        if (isCommonWord(value)) {
            return CODE_COMMON;
        }
        return CODE_OK;
    }

    public static String describeCode(int code) {
        switch (code) {
            case CODE_EMPTY:
                return "Uyandırma kelimesi boş.";
            case CODE_TOO_SHORT:
                return "En az 2 hece önerilir.";
            case CODE_TOO_LONG:
                return "En fazla 4 hece önerilir.";
            case CODE_COMMON:
                return "Çok yaygın bir kelime; yanlış uyandırma artabilir.";
            default:
                return "";
        }
    }

    /** Konuşma sonucu uyandırma kelimesini içeriyor mu (Türkçe normalize). */
    public static boolean matches(String recognizedText, String phrase) {
        String text = normalize(recognizedText);
        String target = normalize(phrase);
        if (text.isEmpty() || target.isEmpty()) {
            return false;
        }
        return text.contains(target);
    }
}
