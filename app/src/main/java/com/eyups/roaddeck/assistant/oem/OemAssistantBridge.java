package com.eyups.roaddeck.assistant.oem;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * Aracin OEM iFlytek sesli asistanini (STT + komut uygulama) baslatir.
 *
 * <p>Kendi mikrofonumuzu bırakmak için {@code stopvr}, is bitince
 * {@code resumevr} kullanilabilir; ancak OEM VR acildiginda kaydi kendisi
 * yonetir. Bu sinif yalnizca servis niyetini gonderir; OEM uygulamasi yoksa
 * {@code false} doner ve cagiran taraf Gemini'ye devam edebilir.</p>
 */
public final class OemAssistantBridge {

    private static final String TAG = "OemAssistant";
    public static final String PACKAGE = "com.iflytek.cutefly.speechclient.hmi";
    public static final String SERVICE =
            "com.iflytek.auto.speechclient.sdk.SpeechClientService";

    private OemAssistantBridge() {
    }

    public static boolean isInstalled(Context context) {
        if (context == null) {
            return false;
        }
        try {
            context.getPackageManager().getServiceInfo(
                    new ComponentName(PACKAGE, SERVICE), 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** OEM VR ekranini ve STT/komut oturumunu baslatir. */
    public static boolean startVoiceSession(Context context, String position) {
        if (context == null || !isInstalled(context)) {
            return false;
        }
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(PACKAGE, SERVICE));
            intent.putExtra("startvr", true);
            intent.putExtra("position", position == null || position.isEmpty()
                    ? "all" : position);
            context.startService(intent);
            Log.i(TAG, "OEM VR baslatildi");
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "OEM VR baslatilamadi", t);
            return false;
        }
    }

    /**
     * Emulator/test icin: OEM NLU'ya metin verir ({@code testSr}).
     * Aracta komut yurutmez; sesli kullanim icin {@link #startVoiceSession}.
     */
    public static boolean sendText(Context context, String text) {
        if (context == null || text == null || text.trim().isEmpty()
                || !isInstalled(context)) {
            return false;
        }
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(PACKAGE, SERVICE));
            intent.putExtra("testSr", true);
            intent.putExtra("text", text.trim());
            intent.putExtra("type", "1");
            context.startService(intent);
            Log.i(TAG, "OEM testSr gonderildi");
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "OEM testSr gonderilemedi", t);
            return false;
        }
    }

    /** OEM VR kaydini gecici olarak birakir. */
    public static void releaseRecorder(Context context) {
        sendExtra(context, "stopvr");
    }

    /** OEM VR kaydini geri verir. */
    public static void resumeRecorder(Context context) {
        sendExtra(context, "resumevr");
    }

    private static void sendExtra(Context context, String extra) {
        if (context == null || !isInstalled(context)) {
            return;
        }
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(PACKAGE, SERVICE));
            intent.putExtra(extra, true);
            context.startService(intent);
        } catch (Throwable t) {
            Log.e(TAG, "OEM " + extra + " gonderilemedi", t);
        }
    }
}
