package com.mapcontrol.assistant.audio;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * OEM iFlytek VR'ın sürekli açık mikrofon kaydını geçici olarak bırakır.
 *
 * <p>Araçta {@code com.iflytek.cutefly.speechclient.hmi} 8 kanal MIC kaydını
 * neredeyse kesintisiz tutuyor; bu yüzden uygulamanın yeni açtığı
 * {@code AudioRecord} Android'in eşzamanlı kayıt politikası gereği sessiz
 * (sıfır) veri alıyor. iFlytek'in dışa açık
 * {@code SpeechClientService} bileşeni {@code stopvr}/{@code resumevr}
 * ekstralarıyla kaydı bırakıp geri alıyor; bu sınıf o sözleşmeyi kullanır.</p>
 *
 * <p>Bırakma durumu SharedPreferences'a yazılır: süreç çökerse OEM VR'ın
 * kapalı kalmasını önlemek için bir sonraki açılışta geri verilir.</p>
 */
public final class OemMicArbiter {

    private static final String TAG = "VaMic";
    private static final String PKG = "com.iflytek.cutefly.speechclient.hmi";
    private static final String SERVICE =
            "com.iflytek.auto.speechclient.sdk.SpeechClientService";
    private static final String EXTRA_STOP = "stopvr";
    private static final String EXTRA_RESUME = "resumevr";
    private static final String PREFS = "assistant_mic";
    private static final String KEY_STOPPED = "oem_vr_stopped";

    /** Kaç AudioCaptureEngine örneği mikrofonu ödünç alıyor. */
    private static int holders;
    private static Boolean available;

    private OemMicArbiter() {
    }

    /**
     * OEM VR kaydını bırakır. Başarılıysa mikrofon kısa süre içinde boşa çıkar;
     * çağıran taraf {@link #restore(Context)} ile geri vermekle yükümlüdür.
     */
    public static synchronized boolean release(Context context) {
        if (!isAvailable(context)) {
            return false;
        }
        if (holders == 0) {
            if (!send(context, EXTRA_STOP)) {
                return false;
            }
            prefs(context).edit().putBoolean(KEY_STOPPED, true).apply();
            Log.i(TAG, "OEM VR mikrofonu bırakıldı");
        }
        holders++;
        return true;
    }

    /** OEM VR kaydını geri verir; birkaç kez çağrılması güvenlidir. */
    public static synchronized void restore(Context context) {
        if (holders == 0) {
            return;
        }
        holders--;
        if (holders > 0) {
            return;
        }
        if (send(context, EXTRA_RESUME)) {
            prefs(context).edit().putBoolean(KEY_STOPPED, false).apply();
            Log.i(TAG, "OEM VR mikrofonu geri verildi");
        } else {
            holders = 1; // sonraki denemede tekrar geri verilir
        }
    }

    /**
     * Önceki süreç çökmesinden kalan bırakma durumunu temizler.
     * Yalnız uygulama mikrofonu kullanmıyorken çağrılmalıdır.
     */
    public static synchronized void restoreIfStale(Context context) {
        if (holders > 0) {
            holders = 1;
            restore(context);
            return;
        }
        if (!isAvailable(context)) {
            return;
        }
        if (!prefs(context).getBoolean(KEY_STOPPED, false)) {
            return;
        }
        if (send(context, EXTRA_RESUME)) {
            prefs(context).edit().putBoolean(KEY_STOPPED, false).apply();
            Log.i(TAG, "Bayat OEM VR kayıt durumu geri alındı");
        }
    }

    private static boolean send(Context context, String extra) {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(PKG, SERVICE));
            intent.putExtra(extra, true);
            context.startService(intent);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "iFlytek mikrofon kontrolü başarısız: "
                    + t.getClass().getSimpleName());
            return false;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static boolean isAvailable(Context context) {
        Boolean cached = available;
        if (cached != null) {
            return cached;
        }
        boolean ok;
        try {
            context.getPackageManager().getPackageInfo(PKG, 0);
            ok = true;
        } catch (Throwable t) {
            ok = false;
        }
        available = ok;
        return ok;
    }
}
