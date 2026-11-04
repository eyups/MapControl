package com.mapcontrol.cloud;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import com.mapcontrol.dvr.DvrClipStore;
import com.mapcontrol.dvr.DvrController;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.TimeZone;
import java.util.function.Consumer;

/** Mevcut DVR kliplerinin metadata ve thumbnail'larını buluta yükler. */
final class DvrClipIndexer {

    private static final String TAG = "DvrClipIndexer";
    private static final int MAX_CLIPS = 100;
    private static final int MAX_INDEX_BYTES = 50 * 1024 * 1024;
    private static final int THUMB_WIDTH = 320;
    private static final int THUMB_HEIGHT = 180;

    private static final Object SIGNATURE_LOCK = new Object();
    private static String lastSignature = "";
    private static final int THUMB_CACHE_MAX = 100;
    private static final Map<String, ThumbCacheEntry> THUMB_CACHE =
            new LinkedHashMap<String, ThumbCacheEntry>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<String, ThumbCacheEntry> eldest) {
                    return size() > THUMB_CACHE_MAX;
                }
            };

    private static final class ThumbCacheEntry {
        final long durationMs;
        final byte[] jpeg;

        ThumbCacheEntry(long durationMs, byte[] jpeg) {
            this.durationMs = durationMs;
            this.jpeg = jpeg;
        }
    }

    private DvrClipIndexer() {
    }

    /** CloudManager tarafından periyodik çağrılır; klip listesi değişmediyse ağa çıkmaz. */
    static void syncIfChanged(Context context, CloudConfig config, CloudHttp http,
            Consumer<String> log, Consumer<Boolean> done) {
        DvrController.get(context).loadClips(clips -> {
            String signature = signature(clips);
            synchronized (SIGNATURE_LOCK) {
                if (signature.equals(lastSignature)) {
                    if (done != null) {
                        done.accept(true);
                    }
                    return;
                }
            }
            sync(context, config, http, log, ok -> {
                if (Boolean.TRUE.equals(ok)) {
                    synchronized (SIGNATURE_LOCK) {
                        lastSignature = signature;
                    }
                }
                if (done != null) {
                    done.accept(ok);
                }
            });
        });
    }

    private static String signature(List<DvrClipStore.Clip> clips) {
        StringBuilder value = new StringBuilder();
        for (DvrClipStore.Clip clip : clips) {
            value.append(clip.base).append(':').append(clip.bytes).append(':');
            if (clip.mosaic != null) {
                value.append('m').append(clip.mosaic.length());
            }
            for (File file : clip.camFiles) {
                value.append(file == null ? '-' : file.length()).append(',');
            }
            value.append(';');
        }
        return value.toString();
    }

    static void sync(Context context, CloudConfig config, CloudHttp http,
            Consumer<String> log, Consumer<Boolean> done) {
        DvrController.get(context).loadClips(clips -> new Thread(() -> {
            boolean ok = false;
            try {
                ok = upload(config, http, clips, log);
            } catch (Exception error) {
                Log.w(TAG, "DVR indeksi yüklenemedi", error);
                log.accept("DVR indeksi: " + error.getClass().getSimpleName());
            } finally {
                if (done != null) {
                    done.accept(ok);
                }
            }
        }, "MapControlDvrIndex").start());
    }

    private static boolean upload(CloudConfig config, CloudHttp http,
            List<DvrClipStore.Clip> clips, Consumer<String> log) throws Exception {
        JSONArray manifest = new JSONArray();
        List<byte[]> thumbnails = new ArrayList<>();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
        format.setTimeZone(TimeZone.getDefault());
        int count = Math.min(clips.size(), MAX_CLIPS);
        for (int index = 0; index < count; index++) {
            DvrClipStore.Clip clip = clips.get(index);
            File primary = clip.primary();
            if (primary == null) {
                continue;
            }
            String thumbKey = clip.base + ':' + primary.length() + ':'
                    + primary.lastModified();
            ThumbCacheEntry cached;
            synchronized (THUMB_CACHE) {
                cached = THUMB_CACHE.get(thumbKey);
            }
            long durationMs = 0L;
            byte[] thumbnail = null;
            if (cached != null) {
                durationMs = cached.durationMs;
                thumbnail = cached.jpeg;
            } else {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                try {
                    retriever.setDataSource(primary.getAbsolutePath());
                    String duration = retriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (duration != null) {
                        durationMs = Long.parseLong(duration);
                    }
                    long frameUs = Math.min(3_000_000L, Math.max(0L, durationMs * 500L));
                    Bitmap bitmap = retriever.getFrameAtTime(
                            frameUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    if (bitmap != null) {
                        Bitmap scaled = Bitmap.createScaledBitmap(
                                bitmap, THUMB_WIDTH, THUMB_HEIGHT, true);
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        scaled.compress(Bitmap.CompressFormat.JPEG, 70, output);
                        if (scaled != bitmap) {
                            scaled.recycle();
                        }
                        bitmap.recycle();
                        thumbnail = output.toByteArray();
                    }
                } catch (Exception ignored) {
                    // Thumbnail/süre okunamazsa metadata yine gönderilir.
                } finally {
                    try {
                        retriever.release();
                    } catch (Exception ignored) {
                    }
                }
                synchronized (THUMB_CACHE) {
                    THUMB_CACHE.put(thumbKey, new ThumbCacheEntry(durationMs, thumbnail));
                }
            }
            JSONObject item = new JSONObject();
            item.put("clip_key", clip.base);
            item.put("started_at", format.format(new Date(clip.timeMs)));
            item.put("duration_ms", Math.max(0L, durationMs));
            item.put("size_bytes", Math.max(0L, clip.bytes));
            int cameraCount = 0;
            for (File file : clip.camFiles) {
                if (file != null) {
                    cameraCount++;
                }
            }
            item.put("camera_count", cameraCount);
            item.put("has_mosaic", clip.mosaic != null);
            item.put("thumb_index", thumbnails.size());
            manifest.put(item);
            thumbnails.add(thumbnail);
        }
        if (manifest.length() == 0) {
            log.accept("DVR indeksi: klip yok");
            return false;
        }
        CloudHttp.Response response = http.postDvrIndex(
                "/api/v1/car/dvr/clips",
                config.token(),
                manifest.toString(),
                thumbnails,
                MAX_INDEX_BYTES);
        boolean ok = response.code == 201;
        String message = "DVR indeksi: " + (ok ? "ok" : "HTTP " + response.code)
                + " (" + manifest.length() + " klip)";
        Log.i(TAG, message);
        log.accept(message);
        return ok;
    }
}
