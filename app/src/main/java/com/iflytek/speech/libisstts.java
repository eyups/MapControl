package com.iflytek.speech;

import com.eyups.roaddeck.BuildConfig;

/**
 * iFlytek ISSTTS JNI wrapper. The flavor selects the native library:
 * tts30 -> libissxtts30.so, tts40 -> libissxtts40.so. Both export the same
 * Java_com_iflytek_speech_libisstts_* symbols, so only one may be loaded per
 * process.
 */
public final class libisstts {
    public static final int ISS_TTS_PARAM_SPEAKER = 1280;
    public static final int ISS_TTS_PARAM_VOICE_SPEED = 1282;
    public static final int ISS_TTS_PARAM_VOICE_PITCH = 1283;
    public static final int ISS_TTS_PARAM_VOLUME = 1284;
    public static final int ISS_TTS_PARAM_MODE = 1289;
    public static final int ISS_TTS_MODE_LOCAL = 0;

    static {
        String name = BuildConfig.TTS_LIB_NAME;
        if (name == null || name.isEmpty()) {
            throw new UnsatisfiedLinkError("TTS flavor has no native library name");
        }
        System.loadLibrary(name);
        System.loadLibrary("issauth");
    }

    private libisstts() {
    }

    public static native synchronized int appendText(NativeTtsHandle handle, String text, int state);

    public static native synchronized int create(NativeTtsHandle handle, ITtsListener listener);

    public static native synchronized int destroy(NativeTtsHandle handle);

    public static native int getAudioData(NativeTtsHandle handle, byte[] buffer, int length, int[] info);

    public static native synchronized String getSpeakerList(NativeTtsHandle handle, int flags);

    public static native synchronized String getVersion();

    public static native synchronized int initRes(String path, int mode);

    public static native synchronized int prepare(NativeTtsHandle handle);

    public static native synchronized int setLogCfgParam(int level, String path);

    public static native synchronized int setMachineCode(String code);

    public static native synchronized int setParam(NativeTtsHandle handle, int param, int value);

    public static native synchronized int setParamEx(NativeTtsHandle handle, int param, String value);

    public static native synchronized int start(NativeTtsHandle handle, String text);

    public static native synchronized int stop(NativeTtsHandle handle);

    public static native synchronized int unInitRes();
}
