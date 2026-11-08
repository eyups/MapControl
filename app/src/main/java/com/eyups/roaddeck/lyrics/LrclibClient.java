package com.eyups.roaddeck.lyrics;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * lrclib.net istemcisi (auto-lyrics 1.9.6 ile aynı kaynak).
 * Önce /api/get ile birebir eşleşme, olmazsa /api/search ile arama yapılır.
 */
public final class LrclibClient {

    private static final String BASE = "https://lrclib.net/api";
    private static final String USER_AGENT =
            "RoadDeck/1.3 (https://github.com/eyups/MapControl)";

    private LrclibClient() {
    }

    public static final class TrackLyrics {
        public final String syncedLyrics;
        public final String plainLyrics;
        public final boolean instrumental;

        TrackLyrics(String syncedLyrics, String plainLyrics, boolean instrumental) {
            this.syncedLyrics = syncedLyrics;
            this.plainLyrics = plainLyrics;
            this.instrumental = instrumental;
        }

        boolean isEmpty() {
            return (syncedLyrics == null || syncedLyrics.trim().isEmpty())
                    && (plainLyrics == null || plainLyrics.trim().isEmpty());
        }
    }

    /** Bulunamazsa null, ağ hatasında IOException. */
    public static TrackLyrics fetch(String title, String artist, String album, long durationMs)
            throws IOException {
        if (title == null || title.trim().isEmpty()) {
            return null;
        }
        String cleanTitle = clean(title);
        String cleanArtist = cleanArtist(artist);

        TrackLyrics exact = get(title, artist, album, durationMs);
        if (exact != null) {
            return exact;
        }
        if (!cleanTitle.equals(title) || !cleanArtist.equals(artist)) {
            TrackLyrics cleaned = get(cleanTitle, cleanArtist, album, durationMs);
            if (cleaned != null) {
                return cleaned;
            }
        }
        TrackLyrics search = search(title, artist, durationMs);
        if (search != null) {
            return search;
        }
        if (!cleanTitle.equals(title) || !cleanArtist.equals(artist)) {
            return search(cleanTitle, cleanArtist, durationMs);
        }
        return null;
    }

    private static TrackLyrics get(String title, String artist, String album, long durationMs)
            throws IOException {
        StringBuilder url = new StringBuilder(BASE).append("/get?");
        url.append("track_name=").append(enc(title));
        url.append("&artist_name=").append(enc(artist == null ? "" : artist));
        if (album != null && !album.trim().isEmpty()) {
            url.append("&album_name=").append(enc(album));
        }
        if (durationMs > 0) {
            url.append("&duration=").append(durationMs / 1000L);
        }
        String body = httpGet(url.toString());
        if (body == null) {
            return null;
        }
        try {
            TrackLyrics result = fromObject(new JSONObject(body));
            return result != null && !result.isEmpty() ? result : null;
        } catch (org.json.JSONException e) {
            throw new IOException("invalid lrclib response", e);
        }
    }

    private static TrackLyrics search(String title, String artist, long durationMs)
            throws IOException {
        StringBuilder url = new StringBuilder(BASE).append("/search?");
        url.append("track_name=").append(enc(title));
        if (artist != null && !artist.trim().isEmpty()) {
            url.append("&artist_name=").append(enc(artist));
        }
        String body = httpGet(url.toString());
        if (body == null) {
            return null;
        }
        JSONArray array;
        try {
            array = new JSONArray(body);
        } catch (org.json.JSONException e) {
            throw new IOException("invalid lrclib search response", e);
        }
        TrackLyrics best = null;
        long bestScore = Long.MAX_VALUE;
        long targetSec = durationMs > 0 ? durationMs / 1000L : -1L;
        for (int i = 0; i < array.length(); i++) {
            TrackLyrics candidate = fromObject(array.optJSONObject(i));
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            long score = candidate.syncedLyrics != null ? 0L : 1000L;
            JSONObject obj = array.optJSONObject(i);
            long duration = obj != null ? obj.optLong("duration", -1L) : -1L;
            if (targetSec > 0 && duration > 0) {
                score += Math.min(500L, Math.abs(duration - targetSec));
            }
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static TrackLyrics fromObject(JSONObject obj) {
        if (obj == null) {
            return null;
        }
        boolean instrumental = obj.optBoolean("instrumental", false);
        String synced = obj.isNull("syncedLyrics") ? null : obj.optString("syncedLyrics", null);
        String plain = obj.isNull("plainLyrics") ? null : obj.optString("plainLyrics", null);
        if (instrumental && (synced == null || synced.isEmpty())
                && (plain == null || plain.isEmpty())) {
            return new TrackLyrics(null, null, true);
        }
        return new TrackLyrics(synced, plain, instrumental);
    }

    private static String httpGet(String urlText) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(urlText).openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "application/json");
            int code = connection.getResponseCode();
            if (code == 404) {
                return null;
            }
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = read(stream);
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code);
            }
            return body;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String read(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = stream.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        stream.close();
        return out.toString("UTF-8");
    }

    private static String enc(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    /** Parantez içi feat/remaster/live gibi ekleri ve " - ..." kuyruğunu atar. */
    static String clean(String title) {
        if (title == null) {
            return "";
        }
        String s = title.trim();
        s = s.replaceAll("(?i)\\s*[\\[(][^\\])]*(feat|ft\\.?|with|remaster|remastered|live|"
                + "version|edit|mix|acoustic|mono|stereo)[^\\])]*[\\])]", " ");
        s = s.replaceAll("\\s+-\\s+.*$", " ");
        return s.replaceAll("\\s+", " ").trim();
    }

    static String cleanArtist(String artist) {
        if (artist == null) {
            return "";
        }
        String s = artist.trim();
        int cut = s.length();
        for (String separator : new String[]{",", "&", " feat", " ft", " x "}) {
            int index = s.toLowerCase(Locale.ROOT).indexOf(separator);
            if (index > 0 && index < cut) {
                cut = index;
            }
        }
        return s.substring(0, cut).trim();
    }
}
