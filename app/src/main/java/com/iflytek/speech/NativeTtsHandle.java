package com.iflytek.speech;

/**
 * iFlytek native handle. Native code accesses both fields and the
 * get/set methods by exact name/signature; do not rename or remove them.
 */
public class NativeTtsHandle {
    public int err_ret = 0;
    public long native_point = 0;

    public int getErr_ret() {
        return err_ret;
    }

    public long getNative_point() {
        return native_point;
    }

    public void reSet() {
        err_ret = 0;
        native_point = 0L;
    }

    public void setErr_ret(int value) {
        err_ret = value;
    }

    public void setNative_point(long value) {
        native_point = value;
    }
}
