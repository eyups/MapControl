package com.mapcontrol.debug;

import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.desaysv.ivi.vdb.IVDBus;
import com.desaysv.ivi.vdb.IVDBusCallback;
import com.desaysv.ivi.vdb.IVDBusNotify;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.utils.VDConfigUtil;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Debug-only: CABIN_LAN / DSP / VEHICLE_DEVICE gibi ServiceManager tabanlı
 * sistem servislerini emülatörde yerel fake transport'a yönlendirir.
 * Release'te bu sınıf APK'ya girmez; VDConfigUtil.LOCAL_SYSTEM_SERVICE null kalır.
 */
public final class DebugVdbusHooks {
    private static final String TAG = "DebugVdbus";

    private DebugVdbusHooks() {
    }

    public static void install(Context context) {
        if (VDConfigUtil.LOCAL_SYSTEM_SERVICE == null) {
            VDConfigUtil.LOCAL_SYSTEM_SERVICE = new LocalSystemVdbus();
            Log.i(TAG, "local system VDBus transport installed");
        }
    }

    private static final class LocalSystemVdbus implements IVDBus {
        private final Map<Integer, Bundle> events = new ConcurrentHashMap<>();
        private final List<CallbackRecord> subscribers = new CopyOnWriteArrayList<>();
        private final Map<Integer, List<IVDBusNotify>> custom = new ConcurrentHashMap<>();

        @Override
        public IBinder asBinder() {
            return null;
        }

        @Override
        public VDEvent get(VDEvent request) {
            if (request == null) {
                return new VDEvent(0);
            }
            Bundle payload = request.getPayload();
            Bundle out = payload == null ? new Bundle() : new Bundle(payload);
            Bundle stored = events.get(request.getId());
            if (stored != null) {
                out.putAll(stored);
            }
            return new VDEvent(request.getId(), out);
        }

        @Override
        public void set(VDEvent event) {
            if (event == null) {
                return;
            }
            if (event.getPayload() != null) {
                events.put(event.getId(), new Bundle(event.getPayload()));
            }
            publish(event);
        }

        @Override
        public void subscribe(int[] ids, int pid, String pkg, IVDBusCallback callback) {
            if (callback != null && ids != null) {
                subscribers.add(new CallbackRecord(ids, callback));
            }
        }

        @Override
        public void subscribeCustomizedEvent(VDEvent event, IVDBusNotify notify) {
            if (event == null || notify == null) {
                return;
            }
            custom.computeIfAbsent(event.getId(), k -> new CopyOnWriteArrayList<>()).add(notify);
            Bundle stored = events.get(event.getId());
            if (stored != null) {
                notify.onVDBusNotify(new VDEvent(event.getId(), new Bundle(stored)));
            }
        }

        @Override
        public void unsubscribeCustomizedEvent(VDEvent event, IVDBusNotify notify) {
            if (event == null || notify == null) {
                return;
            }
            List<IVDBusNotify> list = custom.get(event.getId());
            if (list != null) {
                list.remove(notify);
            }
        }

        private void publish(VDEvent event) {
            for (CallbackRecord record : subscribers) {
                if (record.accepts(event.getId())) {
                    try {
                        record.callback.onVDBusNotify(event);
                    } catch (Throwable t) {
                        Log.w(TAG, "subscriber failed", t);
                    }
                }
            }
            List<IVDBusNotify> list = custom.get(event.getId());
            if (list != null) {
                for (IVDBusNotify notify : list) {
                    try {
                        notify.onVDBusNotify(event);
                    } catch (Throwable t) {
                        Log.w(TAG, "custom listener failed", t);
                    }
                }
            }
        }
    }

    private static final class CallbackRecord {
        private final int[] ids;
        private final IVDBusCallback callback;

        CallbackRecord(int[] ids, IVDBusCallback callback) {
            this.ids = ids.clone();
            this.callback = callback;
        }

        boolean accepts(int id) {
            for (int value : ids) {
                if (value == id) {
                    return true;
                }
            }
            return false;
        }
    }
}
