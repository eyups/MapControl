package com.eyups.roaddeck.lyrics;

import android.graphics.Bitmap;

import java.util.Collections;
import java.util.List;

/** Görünüm katmanına verilen anlık söz durumu. */
public final class LyricsState {

    public enum Status {
        NO_ACCESS,
        NO_SESSION,
        LOADING,
        SYNCED,
        PLAIN,
        NOT_FOUND,
        INSTRUMENTAL,
        ERROR
    }

    public final Status status;
    public final String title;
    public final String artist;
    public final List<LyricLine> lines;
    public final int currentLine;
    public final int currentWord;
    public final long positionMs;
    public final long durationMs;
    public final boolean playing;
    public final Bitmap artwork;
    public final String source;

    public LyricsState(Status status, String title, String artist, List<LyricLine> lines,
                       int currentLine, int currentWord, long positionMs, long durationMs,
                       boolean playing) {
        this(status, title, artist, lines, currentLine, currentWord, positionMs, durationMs,
                playing, null, null);
    }

    public LyricsState(Status status, String title, String artist, List<LyricLine> lines,
                       int currentLine, int currentWord, long positionMs, long durationMs,
                       boolean playing, Bitmap artwork, String source) {
        this.status = status;
        this.artwork = artwork;
        this.source = source;
        this.title = title;
        this.artist = artist;
        this.lines = lines != null ? lines : Collections.<LyricLine>emptyList();
        this.currentLine = currentLine;
        this.currentWord = currentWord;
        this.positionMs = positionMs;
        this.durationMs = durationMs;
        this.playing = playing;
    }

    public static LyricsState status(Status status) {
        return new LyricsState(status, null, null, Collections.<LyricLine>emptyList(),
                -1, -1, 0L, 0L, false);
    }

    public boolean hasLines() {
        return !lines.isEmpty();
    }
}
