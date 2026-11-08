package com.eyups.roaddeck.lyrics;

/** Enhanced LRC (<mm:ss.xx>) kelime zamanlaması. */
public final class LyricWord {
    public final long timeMs;
    public final String text;

    public LyricWord(long timeMs, String text) {
        this.timeMs = timeMs;
        this.text = text;
    }
}
