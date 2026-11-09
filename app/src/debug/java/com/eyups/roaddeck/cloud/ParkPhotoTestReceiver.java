package com.eyups.roaddeck.cloud;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Yalnız debug: ağa çıkmadan park fotoğrafı yakalama/kodlamayı ve tamamlanma sesini dener.
 *
 * <pre>
 * adb shell am broadcast -a com.eyups.roaddeck.TEST_PARK_PHOTO -n com.eyups.roaddeck/.cloud.ParkPhotoTestReceiver --es step capture
 * adb shell am broadcast -a com.eyups.roaddeck.TEST_PARK_PHOTO -n com.eyups.roaddeck/.cloud.ParkPhotoTestReceiver --es step chime
 * adb logcat -s ParkTiming ParkPhotoTest
 * </pre>
 */
public class ParkPhotoTestReceiver extends BroadcastReceiver {

    private static final String TAG = "ParkPhotoTest";

    @Override
    public void onReceive(Context context, Intent intent) {
        String step = intent == null ? null : intent.getStringExtra("step");
        Context app = context.getApplicationContext();
        PendingResult result = goAsync();
        new Thread(() -> {
            try {
                if ("chime".equals(step)) {
                    ParkChime.play(app);
                    Log.i(TAG, "chime çalındı");
                    Thread.sleep(900L);
                    return;
                }
                ParkingPhotoCapture capture = new ParkingPhotoCapture(
                        app, new CloudConfig(app), new CloudHttp());
                ParkingPhotoCapture.Pending pending = capture.captureAndEncode();
                if (pending == null) {
                    Log.i(TAG, "yakalama başarısız");
                    return;
                }
                StringBuilder sizes = new StringBuilder();
                if (pending.full != null) {
                    for (byte[] image : pending.full) {
                        sizes.append(image.length / 1024).append("KB ");
                    }
                }
                Log.i(TAG, "tamam q=" + pending.fullQuality + " full=" + sizes.toString().trim()
                        + " preview=" + pending.preview.length + " görsel");
            } catch (Throwable error) {
                Log.w(TAG, "hata", error);
            } finally {
                result.finish();
            }
        }, "ParkPhotoTest").start();
    }
}
