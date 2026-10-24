package com.mapcontrol.tts;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Türkçe TTS için sayı biçimlendirme. */
public final class TurkishSpeechText {
    private static final String[] ONES = {
            "", "bir", "iki", "üç", "dört", "beş", "altı", "yedi", "sekiz", "dokuz"
    };
    private static final String[] TENS = {
            "", "on", "yirmi", "otuz", "kırk", "elli", "altmış", "yetmiş", "seksen", "doksan"
    };
    private static final Pattern DECIMAL =
            Pattern.compile("(?<!\\d)(\\d+)[.,](\\d+)(?!\\d)");

    private TurkishSpeechText() {
    }

    /** 1.9 -> "bir nokta dokuz", 3.5 -> "üç buçuk", 14.0 -> "on dört". */
    public static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        Matcher matcher = DECIMAL.matcher(text);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String integer = matcher.group(1);
            String fraction = matcher.group(2);
            String spoken;
            if (fraction.matches("0+")) {
                spoken = numberToWords(Integer.parseInt(integer));
            } else if (fraction.equals("5")) {
                int whole = Integer.parseInt(integer);
                spoken = whole == 0 ? "yarım" : numberToWords(whole) + " buçuk";
            } else {
                spoken = numberToWords(Integer.parseInt(integer))
                        + " nokta " + numberToWords(Integer.parseInt(fraction));
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(spoken));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public static String numberToWords(int number) {
        if (number < 0) {
            return "eksi " + numberToWords(-number);
        }
        if (number == 0) {
            return "sıfır";
        }
        if (number < 10) {
            return ONES[number];
        }
        if (number < 100) {
            int rest = number % 10;
            return TENS[number / 10] + (rest == 0 ? "" : " " + ONES[rest]);
        }
        if (number < 1000) {
            int hundreds = number / 100;
            int rest = number % 100;
            String prefix = hundreds == 1 ? "yüz" : ONES[hundreds] + " yüz";
            return prefix + (rest == 0 ? "" : " " + numberToWords(rest));
        }
        if (number < 1_000_000) {
            int thousands = number / 1000;
            int rest = number % 1000;
            String prefix = thousands == 1 ? "bin" : numberToWords(thousands) + " bin";
            return prefix + (rest == 0 ? "" : " " + numberToWords(rest));
        }
        return String.valueOf(number);
    }
}
