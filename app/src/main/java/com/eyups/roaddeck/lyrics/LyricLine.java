package com.eyups.roaddeck.lyrics;

import java.util.Collections;
import java.util.List;

/** Tek satır: başlangıç zamanı, metin ve varsa kelime zamanlamaları. */
public final class LyricLine {
    public final long timeMs;
    public final long endMs;
    public final String text;
    public final List<LyricWord> words;
    /** Düz metin (zaman damgası olmayan) sözlerde true. */
    public final boolean plain;

    public LyricLine(long timeMs, long endMs, String text, List<LyricWord> words, boolean plain) {
        this.timeMs = timeMs;
        this.endMs = endMs;
        this.text = text;
        this.words = words != null ? words : Collections.emptyList();
        this.plain = plain;
    }
}
