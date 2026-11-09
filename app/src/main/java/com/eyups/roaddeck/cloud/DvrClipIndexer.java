package com.eyups.roaddeck.cloud;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import com.eyups.roaddeck.dvr.DvrClipStore;
import com.eyups.roaddeck.dvr.DvrController;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Mevcut DVR kliplerinin metadata ve thumbnail'larını buluta yükler. */
final class DvrClipIndexer {

    private static final String TAG = "DvrClipIndexer";
    private static final String CACHE_DIR_NAME = "dvr_index_cache";
    private static final int MAX_CLIPS = 100;
    private static final int MAX_INDEX_BYTES = 50 * 1024 * 1024;
    private static final int THUMB_WIDTH = 320;
    private static final int THUMB_HEIGHT = 180;

    private static final Object CACHE_LOCK = new Object();
    private static final AtomicBoolean SYNC_IN_PROGRESS = new AtomicBoolean(false);
    private static DvrIndexCache diskCache;
    private static File diskCacheDir;

    private DvrClipIndexer() {
    }

    private static DvrIndexCache getCache(Context context) {
        File targetDir = new File(context.getApplicationContext().getCacheDir(), CACHE_DIR_NAME);
        synchronized (CACHE_LOCK) {
            if (diskCache == null || !targetDir.equals(diskCacheDir)) {
                diskCacheDir = targetDir;
                diskCache = new DvrIndexCache(targetDir);
            }
            return diskCache;
        }
    }

    /** Kaydı süren klipleri listeden çıkarır; yalnızca tamamlanmış klipler indekslenir. */
    static List<DvrClipStore.Clip> filterCompletedClips(
            List<DvrClipStore.Clip> clips, Set<String> activeBases) {
        List<DvrClipStore.Clip> out = new ArrayList<>();
        if (clips == null) {
            return out;
        }
        for (DvrClipStore.Clip clip : clips) {
            if (clip == null || clip.base == null) {
                continue;
            }
            if (activeBases != null && activeBases.contains(clip.base)) {
                continue;
            }
            out.add(clip);
        }
        return out;
    }

    /** CloudManager tarafından periyodik çağrılır; klip listesi değişmediyse ağa çıkmaz. */
    static void syncIfChanged(Context context, CloudConfig config, CloudHttp http,
            Consumer<String> log, Consumer<Boolean> done) {
        if (!SYNC_IN_PROGRESS.compareAndSet(false, true)) {
            if (done != null) {
                done.accept(false);
            }
            return;
        }
        final Context appContext = context.getApplicationContext();
        DvrController.get(appContext).loadClips(rawClips -> new Thread(() -> {
            boolean ok = false;
            try {
                Set<String> active = DvrController.get(appContext).activeBases();
                List<DvrClipStore.Clip> clips = filterCompletedClips(rawClips, active);
                String signature = signature(clips);
                DvrIndexCache cache = getCache(appContext);
                if (signature.equals(cache.getLastSignature())) {
                    ok = true;
                    return;
                }
                ok = upload(appContext, config, http, clips, cache, signature, true, log);
            } catch (Exception error) {
                Log.w(TAG, "DVR indeksi yüklenemedi", error);
                log.accept("DVR indeksi: " + error.getClass().getSimpleName());
            } finally {
                SYNC_IN_PROGRESS.set(false);
                if (done != null) {
                    done.accept(ok);
                }
            }
        }, "RoadDeckDvrIndex").start());
    }

    private static String signature(List<DvrClipStore.Clip> clips) {
        StringBuilder value = new StringBuilder();
        int count = Math.min(clips.size(), MAX_CLIPS);
        for (int i = 0; i < count; i++) {
            DvrClipStore.Clip clip = clips.get(i);
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
        final Context appContext = context.getApplicationContext();
        DvrController.get(appContext).loadClips(rawClips -> new Thread(() -> {
            boolean ok = false;
            try {
                Set<String> active = DvrController.get(appContext).activeBases();
                List<DvrClipStore.Clip> clips = filterCompletedClips(rawClips, active);
                String signature = signature(clips);
                DvrIndexCache cache = getCache(appContext);
                ok = upload(appContext, config, http, clips, cache, signature, false, log);
            } catch (Exception error) {
                Log.w(TAG, "DVR indeksi yüklenemedi", error);
                log.accept("DVR indeksi: " + error.getClass().getSimpleName());
            } finally {
                if (done != null) {
                    done.accept(ok);
                }
            }
        }, "RoadDeckDvrIndex").start());
    }

    private static boolean upload(Context context, CloudConfig config, CloudHttp http,
            List<DvrClipStore.Clip> clips, DvrIndexCache cache, String signature,
            boolean incremental, Consumer<String> log) throws Exception {
        JSONArray manifest = new JSONArray();
        List<byte[]> thumbnails = new ArrayList<>();
        Set<String> seenThumbKeys = new HashSet<>();
        List<String> sentThumbKeys = new ArrayList<>();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
        format.setTimeZone(TimeZone.getDefault());
        long nowMs = System.currentTimeMillis();
        boolean fullRefresh = !incremental || cache.isFullUploadDue(nowMs);
        int count = Math.min(clips.size(), MAX_CLIPS);
        for (int index = 0; index < count; index++) {
            DvrClipStore.Clip clip = clips.get(index);
            File primary = clip.primary();
            if (primary == null) {
                continue;
            }
            String thumbKey = clip.base + ':' + primary.length() + ':'
                    + primary.lastModified();
            seenThumbKeys.add(thumbKey);
            DvrIndexCache.Entry cached = cache.get(thumbKey);
            long durationMs = 0L;
            byte[] thumbnail = null;
            if (cached != null) {
                durationMs = cached.durationMs;
                if (fullRefresh || !cached.uploaded) {
                    thumbnail = cached.jpeg;
                }
                sentThumbKeys.add(thumbKey);
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
                cache.put(thumbKey, durationMs, thumbnail, false);
                sentThumbKeys.add(thumbKey);
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
            cache.retainOnly(seenThumbKeys);
            cache.save();
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
        if (ok) {
            cache.markUploaded(sentThumbKeys);
            if (fullRefresh) {
                cache.markFullUpload(nowMs);
            }
            cache.setLastSignature(signature);
        }
        cache.retainOnly(seenThumbKeys);
        cache.save();
        String message = "DVR indeksi: " + (ok ? "ok" : "HTTP " + response.code)
                + " (" + manifest.length() + " klip)";
        Log.i(TAG, message);
        log.accept(message);
        return ok;
    }
}
