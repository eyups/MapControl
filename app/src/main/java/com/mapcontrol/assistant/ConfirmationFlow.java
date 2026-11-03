package com.mapcontrol.assistant;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Onay akışı: tehlikeli araç yazımından önce "Emin misin?" sorusuna verilen
 * kısa sesli yanıtı (evet/hayır) yorumlar. Saf Java; JVM test'li.
 */
public final class ConfirmationFlow {

    public enum Answer {
        YES, NO, UNKNOWN
    }

    public static final class Pending {
        public final String tool;
        public final Map<String, String> args;
        public final String question;

        public Pending(String tool, Map<String, String> args, String question) {
            this.tool = tool;
            this.args = args == null ? new LinkedHashMap<>() : new LinkedHashMap<>(args);
            this.question = question == null ? "" : question;
        }
    }

    private Pending pending;

    public boolean hasPending() {
        return pending != null;
    }

    public void setPending(String tool, Map<String, String> args, String question) {
        pending = new Pending(tool, args, question);
    }

    public Pending peek() {
        return pending;
    }

    public Pending take() {
        Pending value = pending;
        pending = null;
        return value;
    }

    public void clear() {
        pending = null;
    }

    /** "evet/tamam/onayla..." → YES, "hayır/vazgeç/iptal..." → NO. */
    public static Answer parseAnswer(String text) {
        if (text == null) {
            return Answer.UNKNOWN;
        }
        String value = text.trim().toLowerCase(new java.util.Locale("tr", "TR"))
                .replaceAll("[^a-zçğıöşü ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (value.isEmpty()) {
            return Answer.UNKNOWN;
        }
        String[] noWords = {"hayır", "hayir", "vazgeç", "vazgec", "iptal", "yok", "olmaz",
                "dur", "istemiyorum", "gerek yok", "boşver", "bosver"};
        for (String word : noWords) {
            if (value.contains(word)) {
                return Answer.NO;
            }
        }
        String[] yesWords = {"evet", "tamam", "onayla", "onaylıyorum", "onayliyorum", "olur",
                "yap", "aç", "ac", "kapat", "tabii", "kesinlikle", "aynen", "uygun"};
        for (String word : yesWords) {
            if (value.contains(word)) {
                return Answer.YES;
            }
        }
        return Answer.UNKNOWN;
    }
}
