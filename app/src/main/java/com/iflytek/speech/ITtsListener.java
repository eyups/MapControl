package com.iflytek.speech;

/** iFlytek ISSTTS native callback. */
public interface ITtsListener {
    void onProgress(int index, int progress);

    void onTtsMsgProc(int msg, int value, String json);
}
