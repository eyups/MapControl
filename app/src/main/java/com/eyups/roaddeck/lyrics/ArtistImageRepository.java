package com.eyups.roaddeck.lyrics;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Şarkıcı fotoğrafını otomatik getirir.
 * Birincil kaynak Deezer sanatçı API'si (anahtarsız, yüksek çözünürlüklü portreler),
 * yedek kaynak Wikipedia sayfa görseli. Sonuçlar bellek + diskte önbelleğe alınır.
 */
public final class ArtistImageRepository {

    private static final String TAG = "ArtistImageRepo";
    private static final String USER_AGENT = "RoadDeck/1.3 (com.eyups.roaddeck)";
    private static final int MAX_EDGE = 1024;
    /** Yüzü görselin en az bu oranını kaplamıyorsa "sanatçı portresi" sayılmaz. */
    private static final float MIN_PORTRAIT_SCORE = 0.30f;

    public interface Callback {
        void onImage(Bitmap bitmap);
    }

    private static volatile ArtistImageRepository instance;

    public static ArtistImageRepository get(Context context) {
        if (instance == null) {
            synchronized (ArtistImageRepository.class) {
                if (instance == null) {
                    instance = new ArtistImageRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Bitmap> memory = Collections.synchronizedMap(
            new LinkedHashMap<String, Bitmap>(8, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
                    return size() > 6;
                }
            });
    private final Set<String> inFlight = Collections.synchronizedSet(new HashSet<String>());
    private final Set<String> rejected = Collections.synchronizedSet(new HashSet<String>());

    private ArtistImageRepository(Context context) {
        this.context = context;
    }

    public void load(String artist, Callback callback) {
        if (artist == null || artist.trim().isEmpty()) {
            callback.onImage(null);
            return;
        }
        final String key = normalize(artist);
        Bitmap cached = memory.get(key);
        if (cached != null) {
            callback.onImage(cached);
            return;
        }
        if (rejected.contains(key)) {
            callback.onImage(null);
            return;
        }
        synchronized (inFlight) {
            if (!inFlight.add(key)) {
                callback.onImage(null);
                return;
            }
        }
        executor.execute(() -> {
            Bitmap bitmap = readDisk(key);
            if (bitmap == null) {
                bitmap = download(key, artist);
            }
            final Bitmap result = bitmap;
            mainHandler.post(() -> {
                inFlight.remove(key);
                callback.onImage(result);
            });
        });
    }

    private Bitmap download(String key, String artist) {
        java.util.List<String> candidates = new java.util.ArrayList<>();
        addIfMissing(candidates, wikipediaUrl(artist, "tr"));
        addIfMissing(candidates, wikipediaUrl(artist, "en"));
        candidates.addAll(commonsArtistPictures(artist));
        candidates.addAll(deezerArtistPictures(artist));

        Bitmap best = null;
        float bestScore = -1f;
        for (String url : candidates) {
            if (bestScore >= 0.55f) {
                break; // yeterince iyi bir portre bulundu
            }
            Bitmap bitmap;
            try {
                bitmap = decode(url);
            } catch (Exception e) {
                continue;
            }
            if (bitmap == null) {
                continue;
            }
            float score = portraitScore(bitmap);
            Log.d(TAG, "candidate score=" + score + " " + url);
            if (score > bestScore) {
                bestScore = score;
                best = bitmap;
            } else {
                bitmap.recycle();
            }
        }
        if (best == null || bestScore < MIN_PORTRAIT_SCORE) {
            // Portre niteliğinde görsel yok: rastgele/kalabalık fotoğraf göstermek yerine
            // baş harf yer tutucusuna düşülür.
            Log.d(TAG, "no portrait candidate (best=" + bestScore + ")");
            rejected.add(key);
            if (best != null) {
                best.recycle();
            }
            return null;
        }
        writeDisk(key, best);
        return best;
    }

    private static void addIfMissing(java.util.List<String> list, String value) {
        if (value != null && !value.isEmpty() && !list.contains(value)) {
            list.add(value);
        }
    }

    private String wikipediaUrl(String artist, String lang) {
        try {
            String title = URLEncoder.encode(artist.trim().replace(' ', '_'), "UTF-8");
            String url = "https://" + lang + ".wikipedia.org/api/rest_v1/page/summary/" + title;
            String body = httpGet(url);
            if (body == null) {
                return null;
            }
            JSONObject obj = new JSONObject(body);
            JSONObject thumbnail = obj.optJSONObject("thumbnail");
            if (thumbnail == null) {
                return null;
            }
            String source = thumbnail.optString("source", null);
            if (source == null || source.isEmpty()) {
                return null;
            }
            return source.replace("/320px-", "/800px-")
                    .replace("/330px-", "/800px-")
                    .replace("/250px-", "/800px-");
        } catch (Exception e) {
            return null;
        }
    }

    /** Wikimedia Commons'ta sanatçı adına ait fotoğrafları arar (portre/konser kareleri). */
    private java.util.List<String> commonsArtistPictures(String artist) {
        java.util.List<String> pictures = new java.util.ArrayList<>();
        try {
            String url = "https://commons.wikimedia.org/w/api.php?action=query&format=json"
                    + "&generator=search&gsrsearch=" + URLEncoder.encode(artist, "UTF-8")
                    + "&gsrnamespace=6&gsrlimit=6&prop=imageinfo&iiprop=url&iiurlwidth=900";
            String body = httpGet(url);
            if (body == null) {
                return pictures;
            }
            JSONObject query = new JSONObject(body).optJSONObject("query");
            JSONObject pages = query == null ? null : query.optJSONObject("pages");
            if (pages == null) {
                return pictures;
            }
            String wanted = normalizeKey(artist);
            java.util.Iterator<String> keys = pages.keys();
            while (keys.hasNext() && pictures.size() < 6) {
                JSONObject page = pages.optJSONObject(keys.next());
                if (page == null) {
                    continue;
                }
                String title = page.optString("title", "");
                String titleKey = normalizeKey(title);
                if (!wanted.isEmpty() && !titleKey.contains(wanted)) {
                    continue;
                }
                String lower = title.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".svg") || lower.endsWith(".gif") || lower.endsWith(".pdf")) {
                    continue;
                }
                JSONArray info = page.optJSONArray("imageinfo");
                if (info == null || info.length() == 0) {
                    continue;
                }
                JSONObject item = info.optJSONObject(0);
                if (item == null) {
                    continue;
                }
                String picture = item.optString("thumburl", null);
                if (picture == null || picture.isEmpty()) {
                    picture = item.optString("url", null);
                }
                if (picture != null && !picture.isEmpty() && !pictures.contains(picture)) {
                    pictures.add(picture);
                }
            }
        } catch (Exception ignored) {
            // aday yok
        }
        return pictures;
    }

    private static String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9ğüşıöç]", "");
    }

    private java.util.List<String> deezerArtistPictures(String artist) {
        java.util.List<String> pictures = new java.util.ArrayList<>();
        try {
            String url = "https://api.deezer.com/search/artist?q="
                    + URLEncoder.encode(artist, "UTF-8") + "&limit=5";
            String body = httpGet(url);
            if (body == null) {
                return pictures;
            }
            JSONArray data = new JSONObject(body).optJSONArray("data");
            if (data == null) {
                return pictures;
            }
            String wanted = artist.trim().toLowerCase(Locale.ROOT);
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String name = item.optString("name", "").trim().toLowerCase(Locale.ROOT);
                if (!name.equals(wanted)) {
                    continue;
                }
                String picture = item.optString("picture_xl", null);
                if (picture == null || picture.isEmpty()) {
                    picture = item.optString("picture_big", null);
                }
                if (picture != null && !picture.isEmpty() && !pictures.contains(picture)) {
                    pictures.add(picture);
                }
                if (pictures.size() >= 3) {
                    break;
                }
            }
        } catch (Exception ignored) {
            // aday yok
        }
        return pictures;
    }

    /**
     * Görselde belirgin, merkeze yakın bir yüz var mı? Portre/artistik fotoğrafları
     * kalabalık etkinlik karelerinden ayırmak için kullanılır.
     */
    private static com.google.mlkit.vision.face.FaceDetector mlKitDetector;

    private static synchronized com.google.mlkit.vision.face.FaceDetector mlKit() {
        if (mlKitDetector == null) {
            FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setMinFaceSize(0.05f)
                    .build();
            mlKitDetector = FaceDetection.getClient(options);
        }
        return mlKitDetector;
    }

    /**
     * Görselde belirgin, merkeze yakın bir yüz var mı? Portre/artistik fotoğrafları
     * kalabalık etkinlik karelerinden ayırmak için ML Kit yüz tespiti kullanılır.
     */
    private float portraitScore(Bitmap source) {
        try {
            int maxEdge = 640;
            float scale = Math.min(1f,
                    maxEdge / (float) Math.max(source.getWidth(), source.getHeight()));
            int w = Math.max(1, Math.round(source.getWidth() * scale));
            int h = Math.max(1, Math.round(source.getHeight() * scale));
            Bitmap small = scale < 1f
                    ? Bitmap.createScaledBitmap(source, w, h, true) : source;

            List<Face> faces = Tasks.await(
                    mlKit().process(InputImage.fromBitmap(small, 0)),
                    4, TimeUnit.SECONDS);
            if (faces == null || faces.isEmpty()) {
                return 0f;
            }
            float best = 0f;
            for (Face face : faces) {
                Rect box = face.getBoundingBox();
                float ratio = (box.width() * (float) box.height()) / (w * (float) h);
                float dx = (box.exactCenterX() - w / 2f) / (w / 2f);
                float dy = (box.exactCenterY() - h * 0.45f) / (h / 2f);
                float center = 1f - Math.min(1f, (float) Math.sqrt(dx * dx + dy * dy));
                float score = 0.75f * Math.min(1f, ratio / 0.10f) + 0.25f * center;
                if (faces.size() > 6) {
                    score *= 0.7f; // kalabalık kare
                }
                best = Math.max(best, score);
            }
            if (w > h * 1.4f) {
                best *= 0.8f; // yatay kadraj portre değildir
            }
            return best;
        } catch (Throwable t) {
            Log.w(TAG, "face score failed: " + t.getMessage());
            return 0f;
        }
    }

    private Bitmap decode(String urlText) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(urlText).openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return null;
            }
            InputStream input = connection.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            input.close();
            byte[] bytes = out.toByteArray();
            return decodeBytes(bytes);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private Bitmap decodeBytes(byte[] bytes) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        int sample = 1;
        int max = Math.max(bounds.outWidth, bounds.outHeight);
        while (max / sample > MAX_EDGE) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    private String httpGet(String urlText) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(urlText).openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "application/json");
            int code = connection.getResponseCode();
            if (code == 404) {
                return null;
            }
            if (code < 200 || code >= 300) {
                return null;
            }
            InputStream input = connection.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            input.close();
            return out.toString("UTF-8");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private File cacheFile(String key) {
        File dir = new File(context.getCacheDir(), "artist_images_v3");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return new File(dir, Integer.toHexString(key.hashCode()) + ".img");
    }

    private Bitmap readDisk(String key) {
        File file = cacheFile(key);
        if (!file.exists()) {
            return null;
        }
        try {
            byte[] data = new byte[(int) file.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
                int read = in.read(data);
                if (read <= 0) {
                    return null;
                }
            }
            return decodeBytes(data);
        } catch (Exception e) {
            return null;
        }
    }

    private void writeDisk(String key, Bitmap bitmap) {
        try (FileOutputStream out = new FileOutputStream(cacheFile(key))) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out);
        } catch (Exception ignored) {
            // önbellek yazılamazsa sorun değil
        }
    }
}
