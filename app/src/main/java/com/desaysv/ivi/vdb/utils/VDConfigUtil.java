package com.desaysv.ivi.vdb.utils;

import com.desaysv.ivi.vdb.IVDBus;

public class VDConfigUtil {
    public static boolean UNIT_TEST_MODE = false;
    /** Debug/test only: system service binder yerine yerel fake transport. Release'te null. */
    public static IVDBus LOCAL_SYSTEM_SERVICE = null;
}
