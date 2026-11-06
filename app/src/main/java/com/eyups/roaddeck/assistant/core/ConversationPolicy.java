package com.eyups.roaddeck.assistant.core;

import java.util.Locale;

/** Sohbet modu: kullanıcı "hayır/yok" dediyse tekrar sorma. */
public final class ConversationPolicy {

    private ConversationPolicy() {
    }

    /** Gemini'nin sessizlik/bip için ürettiği "00:00..." gibi çöp transkriptler. */
    public static boolean isNoiseTranscript(String text) {
        if (text == null) {
            return true;
        }
        String value = text.trim();
        return value.length() < 2
                || value.startsWith("00:00")
                || value.matches("^\\d{2}:\\d{2}.*");
    }

    public static boolean isDecline(String text) {
        if (text == null) {
            return false;
        }
        String value = text.toLowerCase(new Locale("tr", "TR"))
                .replaceAll("\\s+", " ")
                .replaceAll("[.!?,;:]+$", "")
                .trim();
        if (value.isEmpty()) {
            return false;
        }
        if (value.equals("hayır") || value.equals("hayir") || value.equals("yok")
                || value.equals("teşekkürler") || value.equals("tesekkurler")
                || value.equals("istemiyorum") || value.equals("gerek yok")
                || value.equals("başka yok") || value.equals("baska yok")
                || value.equals("yeter") || value.equals("tamam")) {
            return true;
        }
        // "hayır, teşekkürler", "hayır başka yok" gibi kısa reddetmeler.
        return (value.startsWith("hayır") || value.startsWith("hayir"))
                && value.length() <= 32;
    }
}
