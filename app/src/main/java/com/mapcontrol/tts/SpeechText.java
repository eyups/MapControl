package com.mapcontrol.tts;

/** Metin temizleme: TTS'in okumaması gereken direktifleri ayıklar. */
public final class SpeechText {
    private SpeechText() {
    }

    /** [etiket] ve (sahne yönergesi) gibi direktifleri kaldırır. */
    public static String plain(String text) {
        if (text == null) {
            return "";
        }
        String value = text
                .replaceAll("\\[[^\\]]*\\]", " ")
                .replaceAll("\\([^)]*\\)", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return value;
    }
}
