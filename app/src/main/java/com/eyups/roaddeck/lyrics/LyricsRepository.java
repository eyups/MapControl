package com.eyups.roaddeck.lyrics;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Parça başına söz getirme + bellek/disk önbelleği.
 * Ağ ve dosya işleri tek bir arka plan iş parçacığında yapılır.
 */
public final class LyricsRepository {

    private static final String TAG = "LyricsRepo";
    private static final long CACHE_VERSION = 1L;

    public static final class Result {
        public final List<LyricLine> lines;
        public final boolean synced;
        public final boolean instrumental;
        public final boolean notFound;
        public final boolean error;

        Result(List<LyricLine> lines, boolean synced, boolean instrumental,
               boolean notFound, boolean error) {
            this.lines = lines;
            this.synced = synced;
            this.instrumental = instrumental;
            this.notFound = notFound;
            this.error = error;
        }

        static Result notFound(boolean instrumental) {
            return new Result(new ArrayList<LyricLine>(), false, instrumental, true, false);
        }

        static Result error() {
            return new Result(new ArrayList<LyricLine>(), false, false, false, true);
        }
    }

    public interface Callback {
        void onResult(Result result);
    }

    private static volatile LyricsRepository instance;

    public static LyricsRepository get(Context context) {
        if (instance == null) {
            synchronized (LyricsRepository.class) {
                if (instance == null) {
                    instance = new LyricsRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Result> memory = new HashMap<>();
    private final Set<String> inFlight = new HashSet<>();
    private final Map<String, List<Callback>> waiters = new HashMap<>();

    private LyricsRepository(Context context) {
        this.context = context;
    }

    public void fetch(String title, String artist, String album, long durationMs, Callback callback) {
        if (title == null || title.trim().isEmpty()) {
            callback.onResult(Result.notFound(false));
            return;
        }
        final String key = key(title, artist, durationMs);
        Result cached = memory.get(key);
        if (cached != null) {
            callback.onResult(cached);
            return;
        }
        synchronized (this) {
            List<Callback> pending = waiters.get(key);
            if (pending != null) {
                pending.add(callback);
                return;
            }
            List<Callback> list = new ArrayList<>();
            list.add(callback);
            waiters.put(key, list);
            inFlight.add(key);
        }
        final String fTitle = title;
        final String fArtist = artist;
        final String fAlbum = album;
        final long fDuration = durationMs;
        executor.execute(() -> load(key, fTitle, fArtist, fAlbum, fDuration));
    }

    private void load(String key, String title, String artist, String album, long durationMs) {
        Result result = readDisk(key);
        if (result == null) {
            try {
                LrclibClient.TrackLyrics track = LrclibClient.fetch(title, artist, album, durationMs);
                result = toResult(track);
                writeDisk(key, track);
            } catch (Exception e) {
                Log.w(TAG, "lyrics fetch failed: " + e.getMessage());
                result = Result.error();
            }
        }
        final Result finalResult = result;
        mainHandler.post(() -> {
            List<Callback> callbacks;
            synchronized (LyricsRepository.this) {
                memory.put(key, finalResult);
                inFlight.remove(key);
                callbacks = waiters.remove(key);
            }
            if (callbacks != null) {
                for (Callback callback : callbacks) {
                    callback.onResult(finalResult);
                }
            }
        });
    }

    private Result toResult(LrclibClient.TrackLyrics track) {
        if (track == null) {
            return Result.notFound(false);
        }
        if (track.instrumental) {
            return Result.notFound(true);
        }
        List<LyricLine> lines;
        boolean synced = false;
        if (track.syncedLyrics != null && !track.syncedLyrics.trim().isEmpty()) {
            lines = LrcParser.parse(track.syncedLyrics);
            synced = !lines.isEmpty();
        } else {
            lines = LrcParser.parsePlain(track.plainLyrics);
        }
        if (lines.isEmpty()) {
            return Result.notFound(track.instrumental);
        }
        return new Result(lines, synced, track.instrumental, false, false);
    }

    private static String key(String title, String artist, long durationMs) {
        return normalize(title) + '\u0001' + normalize(artist) + '\u0001' + (durationMs / 1000L);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private File cacheFile(String key) {
        File dir = new File(context.getCacheDir(), "lyrics");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return new File(dir, Integer.toHexString(key.hashCode()) + ".json");
    }

    private Result readDisk(String key) {
        File file = cacheFile(key);
        if (!file.exists()) {
            return null;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int read = in.read(data);
            JSONObject obj = new JSONObject(new String(data, 0, Math.max(read, 0),
                    StandardCharsets.UTF_8));
            if (obj.optLong("v", 0L) != CACHE_VERSION) {
                return null;
            }
            String synced = obj.optString("synced", null);
            String plain = obj.optString("plain", null);
            if ((synced == null || synced.isEmpty()) && (plain == null || plain.isEmpty())) {
                return Result.notFound(obj.optBoolean("instrumental", false));
            }
            List<LyricLine> lines = synced != null && !synced.isEmpty()
                    ? LrcParser.parse(synced)
                    : LrcParser.parsePlain(plain);
            if (lines.isEmpty()) {
                return Result.notFound(obj.optBoolean("instrumental", false));
            }
            return new Result(lines, synced != null && !synced.isEmpty(),
                    obj.optBoolean("instrumental", false), false, false);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writeDisk(String key, LrclibClient.TrackLyrics track) {
        if (track == null) {
            return;
        }
        try {
            JSONObject obj = new JSONObject();
            obj.put("v", CACHE_VERSION);
            obj.put("synced", track.syncedLyrics == null ? "" : track.syncedLyrics);
            obj.put("plain", track.plainLyrics == null ? "" : track.plainLyrics);
            obj.put("instrumental", track.instrumental);
            File file = cacheFile(key);
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(obj.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            // önbellek yazımı başarısızsa çalışmaya devam
        }
    }
}
