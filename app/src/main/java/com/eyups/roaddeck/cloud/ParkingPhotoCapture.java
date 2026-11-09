package com.eyups.roaddeck.cloud;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.location.Location;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

import com.eyups.roaddeck.BuildConfig;
import com.eyups.roaddeck.dvr.CameraHub;
import com.eyups.roaddeck.dvr.HabNative;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Kontak kapanışında dört kameradan tek kare alıp JPEG olarak buluta yükler.
 * Disk yazmaz; kamera erişimi başarısızsa sessizce vazgeçer.
 */
final class ParkingPhotoCapture {

    private static final String TAG = "ParkingPhotoCapture";
    private static final String HUB_TAG = "cloud-park-photo";
    private static final long FRAME_WAIT_MS = 2_500L;
    private static final long MIN_INTERVAL_MS = 60_000L;
    private static final int MAX_UPLOAD_BYTES = 900 * 1024;

    private final Context context;
    private final CloudConfig config;
    private final CloudHttp http;
    private HandlerThread thread;
    private Handler handler;
    private volatile boolean capturing;
    private volatile long lastCaptureAt;

    ParkingPhotoCapture(Context context, CloudConfig config, CloudHttp http) {
        this.context = context.getApplicationContext();
        this.config = config;
        this.http = http;
    }

    synchronized void start() {
        // İş parçacığı yalnız kontak kapanışında park fotoğrafı çekilirken açılır.
    }

    private synchronized Handler ensureHandler() {
        if (handler == null) {
            thread = new HandlerThread("RoadDeckParkPhoto");
            thread.start();
            handler = new Handler(thread.getLooper());
        }
        return handler;
    }

    synchronized void stop() {
        HandlerThread active = thread;
        thread = null;
        handler = null;
        if (active != null) {
            active.quitSafely();
        }
    }

    void captureOnce() {
        if (capturing || !config.parkPhotoEnabled()) {
            return;
        }
        Handler target = ensureHandler();
        long now = SystemClock.elapsedRealtime();
        if (now - lastCaptureAt < MIN_INTERVAL_MS) {
            return;
        }
        lastCaptureAt = now;
        capturing = true;
        target.post(() -> {
            try {
                captureAndUpload();
            } catch (Throwable error) {
                Log.w(TAG, "park fotoğrafı alınamadı: " + error.getClass().getSimpleName());
            } finally {
                capturing = false;
            }
        });
    }

    private void captureAndUpload() {
        long startedAt = SystemClock.elapsedRealtime();
        List<byte[]> jpegs = new ArrayList<>();
        CameraHub hub = CameraHub.get();
        try {
            hub.acquire(HUB_TAG, null);
            for (int cam = 0; cam < CameraHub.CAM_COUNT; cam++) {
                hub.setDemand(HUB_TAG, cam, HabNative.COPY_FULL, false);
            }
            FrameSet frames = waitForFrames(hub);
            long captureMs = SystemClock.elapsedRealtime() - startedAt;
            if (frames == null) {
                Log.i(TAG, "park fotoğrafı kare yok; captureMs=" + captureMs);
                return;
            }
            long jpegStartedAt = SystemClock.elapsedRealtime();
            for (int cam = 0; cam < CameraHub.CAM_COUNT; cam++) {
                byte[] frame = frames.data[cam];
                int width = frames.widths[cam];
                int height = frames.heights[cam];
                if (frame == null || width <= 0 || height <= 0) {
                    continue;
                }
                int step = Math.max(1, (width + 799) / 800);
                byte[] jpeg = YuyvJpeg.encode(frame, width, height, step, 70);
                if (jpeg != null && jpeg.length > 0) {
                    jpegs.add(jpeg);
                }
            }
            long totalBytes = 0L;
            for (byte[] jpeg : jpegs) {
                totalBytes += jpeg.length;
            }
            Log.i(TAG, "park fotoğrafı hazır: frames=" + frames.count()
                    + " images=" + jpegs.size()
                    + " bytes=" + totalBytes
                    + " captureMs=" + captureMs
                    + " jpegMs=" + (SystemClock.elapsedRealtime() - jpegStartedAt));
        } finally {
            hub.clearDemand(HUB_TAG);
            hub.release(HUB_TAG);
        }

        if (jpegs.isEmpty()) {
            return;
        }
        Double lat = null;
        Double lon = null;
        if (config.shareLocation()) {
            Location location = CloudLocationProvider.lastKnown(context);
            if (location != null) {
                lat = location.getLatitude();
                lon = location.getLongitude();
            }
        }
        long uploadStartedAt = SystemClock.elapsedRealtime();
        try {
            CloudHttp.Response response = http.postParkingPhotos(
                    "/api/v1/car/parking-photos",
                    config.token(),
                    System.currentTimeMillis(),
                    BuildConfig.VERSION_NAME,
                    lat,
                    lon,
                    jpegs.toArray(new byte[0][]),
                    MAX_UPLOAD_BYTES);
            Log.i(TAG, "park fotoğrafı yüklendi: code=" + response.code
                    + " images=" + jpegs.size()
                    + " uploadMs=" + (SystemClock.elapsedRealtime() - uploadStartedAt)
                    + " totalMs=" + (SystemClock.elapsedRealtime() - startedAt));
        } catch (Exception error) {
            Log.w(TAG, "park fotoğrafı yüklenemedi: " + error.getClass().getSimpleName()
                    + " uploadMs=" + (SystemClock.elapsedRealtime() - uploadStartedAt));
        }
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
            int outWidth = outputWidth(width, step);
            int outHeight = outputHeight(height, step);
            byte[] nv21 = toNv21(source, width, height, step);
            if (nv21 == null) {
                return null;
            }
            try {
                YuvImage image = new YuvImage(nv21, ImageFormat.NV21, outWidth, outHeight, null);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                image.compressToJpeg(new Rect(0, 0, outWidth, outHeight), quality, output);
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
