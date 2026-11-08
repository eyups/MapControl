package com.eyups.roaddeck.lyrics;

import android.content.Context;

import com.eyups.roaddeck.media.LauncherMediaController;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Aktif medya oturumunu izler, parça değişiminde sözleri getirir ve
 * pozisyona göre güncel satır/kelimeyi hesaplar.
 */
public final class LyricsEngine implements LauncherMediaController.Listener {

    public interface Listener {
        void onLyricsState(LyricsState state);
    }

    private final LauncherMediaController media;
    private final LyricsRepository repository;
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();

    private LyricsState state = LyricsState.status(LyricsState.Status.NO_SESSION);
    private List<LyricLine> lines = Collections.emptyList();
    private boolean synced;
    private LyricsState.Status baseStatus = LyricsState.Status.NO_SESSION;
    private String trackKey;
    private int lastLine = -1;
    private int lastWord = -1;
    private boolean lastPlaying;
    private boolean started;

    public LyricsEngine(Context context) {
        Context app = context.getApplicationContext();
        this.media = new LauncherMediaController(app);
        this.repository = LyricsRepository.get(app);
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
        listener.onLyricsState(state);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public LyricsState getState() {
        return state;
    }

    public void playPause() {
        media.playPause();
    }

    public void skipPrevious() {
        media.skipToPrevious();
    }

    public void skipNext() {
        media.skipToNext();
    }

    public void seekBy(long deltaMs) {
        media.seekBy(deltaMs);
    }

    public void seekTo(long positionMs) {
        media.seekTo(positionMs);
    }


    public void start() {
        if (started) {
            media.start();
            return;
        }
        started = true;
        media.addListener(this);
        media.start();
    }

    public void stop() {
        if (!started) {
            return;
        }
        started = false;
        media.removeListener(this);
        media.stop();
    }

    @Override
    public void onMediaStateChanged(LauncherMediaController.MediaState newState) {
        if (!newState.sessionAvailable) {
            trackKey = null;
            lines = Collections.emptyList();
            synced = false;
            emit(LyricsState.status(newState.notificationAccessGranted
                    ? LyricsState.Status.NO_SESSION
                    : LyricsState.Status.NO_ACCESS));
            return;
        }
        final String key = (newState.title == null ? "" : newState.title) + '\u0001'
                + (newState.artist == null ? "" : newState.artist);
        if (!key.equals(trackKey)) {
            trackKey = key;
            lines = Collections.emptyList();
            synced = false;
            baseStatus = LyricsState.Status.LOADING;
            lastLine = -1;
            lastWord = -1;
            emit(new LyricsState(LyricsState.Status.LOADING, newState.title, newState.artist,
                    lines, -1, -1, newState.positionMs, newState.durationMs, newState.playing,
                    newState.artwork, newState.source));
            repository.fetch(newState.title, newState.artist, null, newState.durationMs, result -> {
                if (!key.equals(trackKey)) {
                    return; // parça değişti, eski sonucu yoksay
                }
                if (result.error) {
                    baseStatus = LyricsState.Status.ERROR;
                    emitProgress(newState, true);
                    return;
                }
                if (result.notFound) {
                    baseStatus = result.instrumental
                            ? LyricsState.Status.INSTRUMENTAL
                            : LyricsState.Status.NOT_FOUND;
                    emitProgress(newState, true);
                    return;
                }
                lines = result.lines;
                synced = result.synced;
                baseStatus = synced ? LyricsState.Status.SYNCED : LyricsState.Status.PLAIN;
                emitProgress(newState, true);
            });
            return;
        }
        emitProgress(newState, false);
    }

    private void emitProgress(LauncherMediaController.MediaState source, boolean force) {
        int index = synced ? indexFor(lines, source.positionMs) : -1;
        int word = -1;
        if (index >= 0) {
            LyricLine line = lines.get(index);
            if (!line.words.isEmpty()) {
                word = wordIndex(line, source.positionMs);
            }
        }
        if (!force && index == lastLine && word == lastWord
                && source.playing == lastPlaying) {
            return;
        }
        lastLine = index;
        lastWord = word;
        lastPlaying = source.playing;
        LyricsState.Status status = baseStatus;
        if (status == LyricsState.Status.LOADING) {
            status = LyricsState.Status.LOADING;
        }
        emit(new LyricsState(status, source.title, source.artist, lines, index, word,
                source.positionMs, source.durationMs, source.playing,
                source.artwork, source.source));
    }

    private static int indexFor(List<LyricLine> lines, long positionMs) {
        int low = 0;
        int high = lines.size() - 1;
        int found = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (lines.get(mid).timeMs <= positionMs) {
                found = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return found;
    }

    private static int wordIndex(LyricLine line, long positionMs) {
        int found = -1;
        for (int i = 0; i < line.words.size(); i++) {
            if (line.words.get(i).timeMs <= positionMs) {
                found = i;
            } else {
                break;
            }
        }
        return found;
    }

    private void emit(LyricsState newState) {
        state = newState;
        for (Listener listener : listeners) {
            listener.onLyricsState(newState);
        }
    }
}
