package com.eyups.roaddeck.cloud;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.location.Location;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import com.eyups.roaddeck.BuildConfig;
import com.eyups.roaddeck.dvr.CameraHub;
import com.eyups.roaddeck.dvr.HabNative;
import com.eyups.roaddeck.vehicle.VehicleMetricsSnapshot;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Park fotoğrafı: vites P + hız 0'da dört kameradan tam çözünürlüklü kare alır, önizleme ve
 * tam katmanı kontak kapanmadan taslak olarak yükler; kontak kapanınca küçük bir onay gönderir.
 * Tam katman ve onay tamamlanınca kısa bir ses çalar ("inebilirsin").
 *
 * <p>Kapı açılınca Wi-Fi kesildiği için ağır baytlar P anında yola çıkar. Disk yazılmaz
 * (PLAN §10.2); yarım kalan iş bellekte bekler ve bir sonraki kontakta ilk başarılı heartbeat'te
 * tamamlanır. Tasarım: mapcontrol-cloud/docs/parking_photo_quality.md
 */
final class ParkingPhotoCapture {

    private static final String TAG = "ParkingPhotoCapture";
    /** Faz 0 ölçüm logları: `adb logcat -s ParkTiming`. */
    private static final String TIMING = "ParkTiming";
    private static final String HUB_TAG = "cloud-park-photo";
    private static final String UPLOAD_PATH = "/api/v1/car/parking-photos";
    private static final long FRAME_WAIT_MS = 2_500L;
    private static final int COMMIT_READ_TIMEOUT_MS = 8_000;
    private static final int MAX_PENDING = 2;

    static final String TIER_PREVIEW = "preview";
    static final String TIER_FULL = "full";

    private final Context context;
    private final CloudConfig config;
    private final CloudHttp http;
    private final ParkPhotoPolicy.ParkEpisode episode = new ParkPhotoPolicy.ParkEpisode();
    private final List<Pending> pendings = new ArrayList<>();

    private HandlerThread captureThread;
    private Handler captureHandler;
    private HandlerThread uploadThread;
    private Handler uploadHandler;
    private HandlerThread commitThread;
    private Handler commitHandler;

    private volatile boolean capturing;
    private volatile boolean commitAfterCapture;
    private volatile boolean discardNextCapture;
    private volatile boolean authRevoked;
    private volatile int lastPowerMode = -1;
    private volatile long parkAtMs = -1L;
    private volatile long ignitionOffAtMs = -1L;
    private boolean lastDoorOpen;
    private Consumer<Boolean> manualCallback;
    private Runnable manualTimeout;
    private static final long MANUAL_TIMEOUT_MS = 90_000L;

    /** Bellekte bekleyen tek park yakalaması. */
    static final class Pending {
        final UUID id;
        final long tsMs;
        final Double lat;
        final Double lon;
        volatile byte[][] preview;
        volatile byte[][] full;
        final int fullQuality;
        volatile boolean previewDone;
        volatile boolean fullDone;
        volatile boolean commitRequested;
        volatile boolean commitDone;
        volatile boolean discarded;
        volatile boolean chimed;
        volatile boolean uploadScheduled;
        volatile boolean commitScheduled;

        Pending(UUID id, long tsMs, Double lat, Double lon, byte[][] preview, byte[][] full,
                int fullQuality) {
            this.id = id;
            this.tsMs = tsMs;
            this.lat = lat;
            this.lon = lon;
            this.preview = preview;
            this.full = full;
            this.fullQuality = fullQuality;
        }

        boolean complete() {
            return previewDone && fullDone && commitDone;
        }
    }

    ParkingPhotoCapture(Context context, CloudConfig config, CloudHttp http) {
        this.context = context.getApplicationContext();
        this.config = config;
        this.http = http;
    }

    synchronized void start() {
        // İş parçacıkları ilk park olayında tembel açılır.
    }

    synchronized void stop() {
        quit(captureThread);
        quit(uploadThread);
        quit(commitThread);
        captureThread = uploadThread = commitThread = null;
        captureHandler = uploadHandler = commitHandler = null;
        synchronized (pendings) {
            pendings.clear();
        }
    }

    private static void quit(HandlerThread thread) {
        if (thread != null) {
            thread.quitSafely();
        }
    }

    private synchronized Handler captureHandler() {
        if (captureHandler == null) {
            captureThread = new HandlerThread("RoadDeckParkPhoto");
            captureThread.start();
            captureHandler = new Handler(captureThread.getLooper());
        }
        return captureHandler;
    }

    private synchronized Handler uploadHandler() {
        if (uploadHandler == null) {
            uploadThread = new HandlerThread("RoadDeckParkUpload");
            uploadThread.start();
            uploadHandler = new Handler(uploadThread.getLooper());
        }
        return uploadHandler;
    }

    private synchronized Handler commitHandler() {
        if (commitHandler == null) {
            commitThread = new HandlerThread("RoadDeckParkCommit");
            commitThread.start();
            commitHandler = new Handler(commitThread.getLooper());
        }
        return commitHandler;
    }

    // ---------------- CloudManager girişleri (RoadDeckCloud iş parçacığı) ----------------

    /** Her bulut döngüsünde (1 sn) çağrılır: P algılama ve kapı ölçümü. */
    void onVehicleState(int powerMode, VehicleMetricsSnapshot snapshot) {
        lastPowerMode = powerMode;
        if (!config.parkPhotoEnabled() || snapshot == null) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        boolean doorOpen = snapshot.isDoorLfOpen();
        if (doorOpen && !lastDoorOpen) {
            timing("door_open", "sinceOffMs=" + since(ignitionOffAtMs, now)
                    + " sinceParkMs=" + since(parkAtMs, now) + " state=" + describeCurrent());
        }
        lastDoorOpen = doorOpen;

        boolean gearValid = VehicleMetricsSnapshot.isValid(snapshot.gearSignal);
        ParkPhotoPolicy.Action action = episode.update(gearValid, snapshot.gearSignal,
                snapshot.preferredSpeed(), powerMode, now);
        if (action == ParkPhotoPolicy.Action.CAPTURE) {
            parkAtMs = now;
            ignitionOffAtMs = -1L;
            discardNextCapture = false;
            timing("park_detected", "gear=" + snapshot.gearSignal);
            requestCapture();
        } else if (action == ParkPhotoPolicy.Action.DISCARD) {
            Pending current = latest();
            if (current != null && !current.commitRequested) {
                current.discarded = true;
                timing("park_left", "capture=" + shortId(current) + " taslak bırakıldı");
            }
            commitAfterCapture = false;
            if (capturing) {
                discardNextCapture = true;
            }
        }
    }

    /** Kontak kapanışı (final telemetri anı). */
    void onIgnitionOff() {
        lastPowerMode = 0;
        if (!config.parkPhotoEnabled()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        ignitionOffAtMs = now;
        discardNextCapture = false;
        episode.reset();
        Pending current = latest();
        timing("ignition_off", "sinceParkMs=" + since(parkAtMs, now) + " bootCount=" + bootCount()
                + " state=" + describeCurrent());
        if (current != null && !current.discarded && !current.commitRequested) {
            current.commitRequested = true;
            scheduleCommit(current, 0, 0L);
            scheduleUpload(current, 0, 0L);
        } else if (current == null || current.discarded) {
            // P algılanamadıysa (vites sinyali yok) kamera hâlâ açık: şimdi yakala, hemen onayla.
            commitAfterCapture = true;
            requestCapture();
        }
    }

    /** İlk başarılı heartbeat: önceki parktan yarım kalan işleri tamamlar. */
    void onNetworkAvailable() {
        lastPowerMode = 2;
        authRevoked = false;
        List<Pending> resumable = new ArrayList<>();
        synchronized (pendings) {
            pendings.removeIf(p -> p.discarded || p.complete());
            for (Pending p : pendings) {
                if (p.commitRequested) {
                    resumable.add(p);
                }
            }
        }
        for (Pending p : resumable) {
            timing("resume", "capture=" + shortId(p) + " bootCount=" + bootCount()
                    + " preview=" + p.previewDone + " full=" + p.fullDone + " commit=" + p.commitDone);
            scheduleCommit(p, 0, 0L);
            scheduleUpload(p, 0, 0L);
        }
    }

    /** Send menüsünden manuel park fotoğrafı isteği; çekim + yükleme + onay sonucu döner. */
    void captureNow(Consumer<Boolean> done) {
        if (done == null) {
            return;
        }
        synchronized (this) {
            if (manualCallback != null) {
                done.accept(false);
                return;
            }
            manualCallback = done;
            manualTimeout = () -> finishManual(false);
        }
        captureHandler().postDelayed(manualTimeout, MANUAL_TIMEOUT_MS);
        Pending current = latest();
        if (current != null && !current.discarded) {
            if (current.complete()) {
                finishManual(true);
                return;
            }
            current.commitRequested = true;
            scheduleCommit(current, 0, 0L);
            scheduleUpload(current, 0, 0L);
            return;
        }
        commitAfterCapture = true;
        requestCapture();
    }

    private void finishManual(boolean ok) {
        Consumer<Boolean> callback;
        Runnable timeout;
        synchronized (this) {
            callback = manualCallback;
            manualCallback = null;
            timeout = manualTimeout;
            manualTimeout = null;
        }
        if (timeout != null && captureHandler != null) {
            captureHandler.removeCallbacks(timeout);
        }
        if (callback != null) {
            callback.accept(ok);
        }
    }

    // ---------------- Yakalama ----------------

    private void requestCapture() {
        if (capturing) {
            return;
        }
        capturing = true;
        captureHandler().post(() -> {
            try {
                Pending pending = captureAndEncode();
                if (pending == null) {
                    commitAfterCapture = false;
                    finishManual(false);
                    return;
                }
                if (discardNextCapture && !commitAfterCapture) {
                    discardNextCapture = false;
                    timing("park_left", "capture=" + shortId(pending) + " yakalama bitmeden P'den çıkıldı");
                    return;
                }
                if (commitAfterCapture) {
                    commitAfterCapture = false;
                    pending.commitRequested = true;
                }
                addPending(pending);
                if (pending.commitRequested) {
                    scheduleCommit(pending, 0, 0L);
                }
                scheduleUpload(pending, 0, 0L);
            } catch (Throwable error) {
                Log.w(TAG, "park fotoğrafı alınamadı: " + error.getClass().getSimpleName());
                timing("capture_error", error.getClass().getSimpleName());
                finishManual(false);
            } finally {
                capturing = false;
            }
        });
    }

    /** Paket içi: debug test alıcısı ağa çıkmadan yakalama/kodlamayı ölçer. */
    Pending captureAndEncode() {
        long startedAt = SystemClock.elapsedRealtime();
        FrameSet frames;
        CameraHub hub = CameraHub.get();
        try {
            hub.acquire(HUB_TAG, null);
            for (int cam = 0; cam < CameraHub.CAM_COUNT; cam++) {
                hub.setDemand(HUB_TAG, cam, HabNative.COPY_FULL, false);
            }
            frames = waitForFrames(hub);
        } finally {
            hub.clearDemand(HUB_TAG);
            hub.release(HUB_TAG);
        }
        long captureMs = SystemClock.elapsedRealtime() - startedAt;
        if (frames == null) {
            timing("capture_failed", "captureMs=" + captureMs + " kare yok");
            return null;
        }

        long encodeStartedAt = SystemClock.elapsedRealtime();
        List<byte[]> preview = new ArrayList<>();
        List<byte[]> nv21 = new ArrayList<>();
        List<int[]> sizes = new ArrayList<>();
        for (int cam = 0; cam < CameraHub.CAM_COUNT; cam++) {
            byte[] frame = frames.data[cam];
            int width = frames.widths[cam];
            int height = frames.heights[cam];
            if (frame == null || width <= 1 || height <= 1) {
                continue;
            }
            byte[] small = YuyvJpeg.encode(frame, width, height,
                    ParkPhotoPolicy.previewStep(width), ParkPhotoPolicy.PREVIEW_QUALITY);
            byte[] fullNv21 = YuyvJpeg.toNv21(frame, width, height, 1);
            if (small == null || fullNv21 == null) {
                continue;
            }
            preview.add(small);
            nv21.add(fullNv21);
            sizes.add(new int[] {YuyvJpeg.outputWidth(width, 1), YuyvJpeg.outputHeight(height, 1)});
            frames.data[cam] = null;
        }
        if (preview.isEmpty()) {
            timing("capture_failed", "captureMs=" + captureMs + " kodlanamadı");
            return null;
        }
        // Önizleme JPEG'i + tam katman NV21 dönüşümü.
        long prepMs = SystemClock.elapsedRealtime() - encodeStartedAt;

        // Tam katman: q90'dan başlayıp sunucu bütçesine sığana kadar kaliteyi düşür.
        byte[][] full = null;
        int chosenQuality = -1;
        long fullStartedAt = SystemClock.elapsedRealtime();
        for (int quality : ParkPhotoPolicy.FULL_QUALITY_LADDER) {
            byte[][] attempt = new byte[nv21.size()][];
            int[] bytes = new int[nv21.size()];
            boolean ok = true;
            for (int i = 0; i < nv21.size(); i++) {
                attempt[i] = YuyvJpeg.compressNv21(nv21.get(i), sizes.get(i)[0],
                        sizes.get(i)[1], quality);
                if (attempt[i] == null) {
                    ok = false;
                    break;
                }
                bytes[i] = attempt[i].length;
            }
            if (!ok) {
                break;
            }
            full = attempt;
            chosenQuality = quality;
            if (ParkPhotoPolicy.fitsBudget(bytes, ParkPhotoPolicy.MAX_IMAGE_BYTES,
                    ParkPhotoPolicy.MAX_TIER_BYTES)) {
                break;
            }
        }
        long fullMs = SystemClock.elapsedRealtime() - fullStartedAt;

        Double lat = null;
        Double lon = null;
        if (config.shareLocation()) {
            Location location = CloudLocationProvider.lastKnown(context);
            if (location != null) {
                lat = location.getLatitude();
                lon = location.getLongitude();
            }
        }
        Pending pending = new Pending(UUID.randomUUID(), System.currentTimeMillis(), lat, lon,
                preview.toArray(new byte[0][]), full, chosenQuality);
        if (full == null) {
            pending.fullDone = true;
        }
        timing("captured", "capture=" + shortId(pending)
                + " cams=" + preview.size()
                + " size=" + sizes.get(0)[0] + "x" + sizes.get(0)[1]
                + " captureMs=" + captureMs
                + " prepMs=" + prepMs + " previewKB=" + totalKb(pending.preview)
                + " fullMs=" + fullMs + " fullKB=" + totalKb(full) + " q=" + chosenQuality);
        return pending;
    }

    private FrameSet waitForFrames(CameraHub hub) {
        FrameSet result = new FrameSet();
        long deadline = SystemClock.uptimeMillis() + FRAME_WAIT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            hub.readFrames((frames, widths, heights) ->
                    copyMissing(result, frames, widths, heights));
            if (result.count() == CameraHub.CAM_COUNT) {
                break;
            }
            SystemClock.sleep(60L);
        }
        return result.count() > 0 ? result : null;
    }

    // ---------------- Yükleme ----------------

    private void scheduleUpload(Pending p, int attempt, long delayMs) {
        if (p.discarded || (p.previewDone && p.fullDone)) {
            return;
        }
        synchronized (p) {
            if (p.uploadScheduled) {
                return;
            }
            p.uploadScheduled = true;
        }
        uploadHandler().postDelayed(() -> {
            p.uploadScheduled = false;
            uploadNext(p, attempt);
        }, delayMs);
    }

    private void uploadNext(Pending p, int attempt) {
        if (p.discarded || authRevoked) {
            return;
        }
        String tier;
        byte[][] images;
        if (!p.previewDone) {
            tier = TIER_PREVIEW;
            images = p.preview;
        } else if (!p.fullDone) {
            tier = TIER_FULL;
            images = p.full;
        } else {
            return;
        }
        long startedAt = SystemClock.elapsedRealtime();
        long bytes = totalBytes(images);
        try {
            CloudHttp.Response response = http.postParkingPhotos(UPLOAD_PATH, config.token(),
                    p.tsMs, BuildConfig.VERSION_NAME, p.lat, p.lon, images,
                    ParkPhotoPolicy.MAX_TIER_BYTES, p.id.toString(), tier);
            long ms = SystemClock.elapsedRealtime() - startedAt;
            timing("upload", "capture=" + shortId(p) + " tier=" + tier + " code=" + response.code
                    + " kb=" + bytes / 1024 + " ms=" + ms + " kbps=" + kbps(bytes, ms)
                    + " sinceParkMs=" + since(parkAtMs, SystemClock.elapsedRealtime())
                    + " sinceOffMs=" + since(ignitionOffAtMs, SystemClock.elapsedRealtime()));
            if (response.code == 200 || response.code == 201) {
                if (TIER_PREVIEW.equals(tier)) {
                    p.previewDone = true;
                    p.preview = null;
                } else {
                    p.fullDone = true;
                    p.full = null;
                }
                maybeChime(p);
                uploadNext(p, 0);
                return;
            }
            if (response.code == 401) {
                authRevoked = true;
                return;
            }
            if (response.code >= 400 && response.code < 500 && response.code != 429) {
                // Kalıcı ret (ör. 413): bu katman bırakılır, diğer akış sürer.
                if (TIER_PREVIEW.equals(tier)) {
                    p.previewDone = true;
                    p.preview = null;
                } else {
                    p.fullDone = true;
                    p.full = null;
                }
                uploadNext(p, 0);
                return;
            }
        } catch (Exception error) {
            timing("upload_error", "capture=" + shortId(p) + " tier=" + tier + " "
                    + error.getClass().getSimpleName() + " ms="
                    + (SystemClock.elapsedRealtime() - startedAt));
        }
        retryLater(p, attempt, true);
    }

    // ---------------- Onay ----------------

    private void scheduleCommit(Pending p, int attempt, long delayMs) {
        if (p.discarded || p.commitDone || !p.commitRequested) {
            return;
        }
        synchronized (p) {
            if (p.commitScheduled) {
                return;
            }
            p.commitScheduled = true;
        }
        commitHandler().postDelayed(() -> {
            p.commitScheduled = false;
            commit(p, attempt);
        }, delayMs);
    }

    private void commit(Pending p, int attempt) {
        if (p.discarded || p.commitDone || authRevoked) {
            return;
        }
        long startedAt = SystemClock.elapsedRealtime();
        try {
            JSONObject body = new JSONObject();
            body.put("ts_ms", p.tsMs);
            CloudHttp.Response response = http.postJson(
                    UPLOAD_PATH + "/" + p.id + "/commit", config.token(), body, 4 * 1024,
                    COMMIT_READ_TIMEOUT_MS);
            timing("commit", "capture=" + shortId(p) + " code=" + response.code
                    + " ms=" + (SystemClock.elapsedRealtime() - startedAt)
                    + " sinceOffMs=" + since(ignitionOffAtMs, SystemClock.elapsedRealtime()));
            if (response.code == 200) {
                p.commitDone = true;
                maybeChime(p);
                finishManual(true);
                return;
            }
            if (response.code == 401) {
                authRevoked = true;
                finishManual(false);
                return;
            }
            if (response.code >= 400 && response.code < 500 && response.code != 429) {
                finishManual(false);
                return;
            }
        } catch (Exception error) {
            timing("commit_error", "capture=" + shortId(p) + " "
                    + error.getClass().getSimpleName() + " ms="
                    + (SystemClock.elapsedRealtime() - startedAt));
        }
        retryLater(p, attempt, false);
    }

    private void retryLater(Pending p, int attempt, boolean upload) {
        long now = SystemClock.elapsedRealtime();
        if (!ParkPhotoPolicy.inRetryWindow(lastPowerMode, ignitionOffAtMs, now)) {
            // Kapı açıldı, ağ yok: bir sonraki kontakta onNetworkAvailable tamamlar.
            timing("deferred", "capture=" + shortId(p) + (upload ? " upload" : " commit"));
            finishManual(false);
            return;
        }
        long delay = ParkPhotoPolicy.retryDelayMs(attempt);
        if (upload) {
            scheduleUpload(p, attempt + 1, delay);
        } else {
            scheduleCommit(p, attempt + 1, delay);
        }
    }

    private void maybeChime(Pending p) {
        boolean play;
        synchronized (p) {
            play = ParkPhotoPolicy.shouldChime(p.fullDone, p.commitDone, p.chimed,
                    config.parkPhotoChimeEnabled(), ignitionOffAtMs, SystemClock.elapsedRealtime());
            if (play) {
                p.chimed = true;
            }
        }
        if (play) {
            timing("chime", "capture=" + shortId(p)
                    + " sinceOffMs=" + since(ignitionOffAtMs, SystemClock.elapsedRealtime())
                    + " q=" + p.fullQuality);
            ParkChime.play(context);
        }
    }

    // ---------------- Yardımcılar ----------------

    private void addPending(Pending pending) {
        synchronized (pendings) {
            pendings.removeIf(p -> p.discarded || p.complete());
            for (Pending p : pendings) {
                if (!p.commitRequested) {
                    p.discarded = true;
                }
            }
            pendings.removeIf(p -> p.discarded);
            pendings.add(pending);
            while (pendings.size() > MAX_PENDING) {
                pendings.remove(0).discarded = true;
            }
        }
    }

    private Pending latest() {
        synchronized (pendings) {
            return pendings.isEmpty() ? null : pendings.get(pendings.size() - 1);
        }
    }

    private String describeCurrent() {
        Pending p = latest();
        if (p == null) {
            return capturing ? "capturing" : "none";
        }
        return shortId(p) + "[p=" + p.previewDone + ",f=" + p.fullDone + ",c=" + p.commitDone
                + ",d=" + p.discarded + "]";
    }

    private int bootCount() {
        try {
            return Settings.Global.getInt(context.getContentResolver(),
                    Settings.Global.BOOT_COUNT, -1);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static void timing(String event, String detail) {
        Log.i(TIMING, event + " " + detail);
    }

    private static long since(long markMs, long nowMs) {
        return markMs > 0L ? nowMs - markMs : -1L;
    }

    private static String shortId(Pending p) {
        return p == null ? "-" : p.id.toString().substring(0, 8);
    }

    private static long totalBytes(byte[][] images) {
        long total = 0L;
        if (images != null) {
            for (byte[] image : images) {
                total += image == null ? 0 : image.length;
            }
        }
        return total;
    }

    private static long totalKb(byte[][] images) {
        return totalBytes(images) / 1024;
    }

    private static long kbps(long bytes, long ms) {
        return ms <= 0 ? -1 : bytes * 8L / ms;
    }

    static int copyMissing(FrameSet result, ByteBuffer[] frames,
            int[] widths, int[] heights) {
        // Her kamera icin yalnizca ilk kareyi kopyala; dongu boyunca
        // ayni 4 kareyi tekrar tekrar kopyalamak GC/bellek yuku yapiyordu.
        for (int cam = 0; cam < CameraHub.CAM_COUNT; cam++) {
            if (result.data[cam] != null) {
                continue;
            }
            ByteBuffer buffer = frames[cam];
            if (buffer == null) {
                continue;
            }
            buffer.position(0);
            byte[] copy = new byte[buffer.remaining()];
            buffer.get(copy);
            result.data[cam] = copy;
            result.widths[cam] = widths[cam];
            result.heights[cam] = heights[cam];
        }
        return result.count();
    }

    static final class FrameSet {
        final byte[][] data = new byte[CameraHub.CAM_COUNT][];
        final int[] widths = new int[CameraHub.CAM_COUNT];
        final int[] heights = new int[CameraHub.CAM_COUNT];

        int count() {
            int count = 0;
            for (byte[] item : data) {
                if (item != null) {
                    count++;
                }
            }
            return count;
        }
    }

    /** YUYV kareyi ölçekleyerek NV21'e, ardından JPEG'e çevirir. */
    static final class YuyvJpeg {
        private YuyvJpeg() {
        }

        static byte[] encode(byte[] source, int width, int height, int step, int quality) {
            byte[] nv21 = toNv21(source, width, height, step);
            if (nv21 == null) {
                return null;
            }
            return compressNv21(nv21, outputWidth(width, step), outputHeight(height, step), quality);
        }

        static byte[] compressNv21(byte[] nv21, int width, int height, int quality) {
            try {
                YuvImage image = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
                ByteArrayOutputStream output = new ByteArrayOutputStream(256 * 1024);
                image.compressToJpeg(new Rect(0, 0, width, height), quality, output);
                return output.toByteArray();
            } catch (Throwable error) {
                Log.w(TAG, "JPEG dönüşümü başarısız: " + error.getClass().getSimpleName());
                return null;
            }
        }

        static int outputWidth(int width, int step) {
            return Math.max(2, (width / step) & ~1);
        }

        static int outputHeight(int height, int step) {
            return Math.max(2, (height / step) & ~1);
        }

        static byte[] toNv21(byte[] source, int width, int height, int step) {
            if (source == null || width <= 1 || height <= 1 || step < 1) {
                return null;
            }
            int outWidth = outputWidth(width, step);
            int outHeight = outputHeight(height, step);
            int ySize = outWidth * outHeight;
            byte[] nv21 = new byte[ySize * 3 / 2];
            for (int y = 0; y < outHeight; y++) {
                int sourceY = y * step;
                for (int x = 0; x < outWidth; x++) {
                    int sourceX = x * step;
                    int sourceIndex = (sourceY * width + sourceX) * 2;
                    if (sourceIndex < source.length) {
                        nv21[y * outWidth + x] = source[sourceIndex];
                    }
                }
            }
            for (int y = 0; y < outHeight; y += 2) {
                int sourceY = y * step;
                for (int x = 0; x < outWidth; x += 2) {
                    int sourceX = x * step;
                    int sourceIndex = (sourceY * width + sourceX) * 2;
                    if (sourceIndex + 3 >= source.length) {
                        continue;
                    }
                    int chromaIndex = ySize + (y / 2) * outWidth + x;
                    nv21[chromaIndex] = source[sourceIndex + 3];
                    nv21[chromaIndex + 1] = source[sourceIndex + 1];
                }
            }
            return nv21;
        }
    }
}
