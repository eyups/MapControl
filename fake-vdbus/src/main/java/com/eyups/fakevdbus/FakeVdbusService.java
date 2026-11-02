package com.eyups.fakevdbus;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.desaysv.ivi.vdb.IVDBus;
import com.desaysv.ivi.vdb.IVDBusCallback;
import com.desaysv.ivi.vdb.IVDBusNotify;
import com.desaysv.ivi.vdb.event.VDEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Emülatörde gerçek OEM VDBus servislerinin yerine geçen minimal fake servis.
 * Paket adı flavor ile belirlenir; action'lar manifestte ortaktır.
 */
public final class FakeVdbusService extends Service {
    private static final String TAG = "FakeVdbus";
    private static final String CHANNEL = "fake_vdbus";

    static final Map<Integer, Bundle> EVENTS = new ConcurrentHashMap<>();
    static final Map<String, int[]> VALUES = new ConcurrentHashMap<>();
    private static volatile FakeVdbusService INSTANCE;

    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();
    private final Map<Integer, List<IVDBusNotify>> customNotify = new ConcurrentHashMap<>();

    private final IBinder binder = new BinderImpl();

    static String valueKey(int eventId, int cmdId) {
        return eventId + ":" + cmdId;
    }

    static void putEvent(int eventId, Bundle bundle) {
        EVENTS.put(eventId, bundle);
    }

    static void putValues(int eventId, int cmdId, int[] values) {
        VALUES.put(valueKey(eventId, cmdId), values);
    }

    static void clearAll() {
        EVENTS.clear();
        VALUES.clear();
    }

    static void publishFromControl(VDEvent event) {
        FakeVdbusService service = INSTANCE;
        if (service != null) {
            service.publish(event);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        INSTANCE = this;
        createChannel();
        Log.i(TAG, "fake service created: " + getPackageName());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // startForegroundService sözleşmesi için kısa süre foreground ol, sonra bildirimi kaldır.
        // Servis bağlı/başlatılmış olarak çalışmaya devam eder; bildirim tepsisi temiz kalır.
        try {
            startForeground(1, notification());
            stopForeground(STOP_FOREGROUND_REMOVE);
        } catch (Throwable ignored) {
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        INSTANCE = null;
        super.onDestroy();
    }

    private void createChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL, "Fake VDBus", NotificationManager.IMPORTANCE_LOW));
        }
    }

    private Notification notification() {
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Fake VDBus")
                .setContentText(getPackageName())
                .setOngoing(true);
        return b.build();
    }

    private void publish(VDEvent event) {
        if (event == null) {
            return;
        }
        for (Subscriber s : subscribers) {
            if (s.accepts(event.getId())) {
                try {
                    s.callback.onVDBusNotify(event);
                } catch (Throwable t) {
                    Log.w(TAG, "subscriber notify failed", t);
                }
            }
        }
        List<IVDBusNotify> list = customNotify.get(event.getId());
        if (list != null) {
            for (IVDBusNotify n : list) {
                try {
                    n.onVDBusNotify(event);
                } catch (Throwable t) {
                    Log.w(TAG, "custom notify failed", t);
                }
            }
        }
    }

    private final class BinderImpl extends IVDBus.Stub {
        @Override
        public VDEvent get(VDEvent request) {
            if (request == null) {
                return new VDEvent(0);
            }
            Bundle out = new Bundle();
            if (request.getPayload() != null) {
                out.putAll(request.getPayload());
            }
            int cmd = out.getInt("CMD_ID", -1);
            int[] values = cmd >= 0 ? VALUES.get(valueKey(request.getId(), cmd)) : null;
            if (values == null) {
                values = new int[]{0};
            }
            out.putIntArray("VALUE", values);
            return new VDEvent(request.getId(), out);
        }

        @Override
        public void set(VDEvent event) {
            if (event == null) {
                return;
            }
            Bundle payload = event.getPayload();
            if (payload != null) {
                // CarLan payload'ları özel Parcelable bean'ler içerir; fake APK'nın
                // class loader'ı Bundle'a verilmezse Parcel unmarshal ClassNotFound verir.
                payload.setClassLoader(FakeVdbusService.class.getClassLoader());
                EVENTS.put(event.getId(), new Bundle(payload));
                if (payload.containsKey("CMD_ID")) {
                    int cmd = payload.getInt("CMD_ID", -1);
                    int[] v = payload.getIntArray("VALUE");
                    if (cmd >= 0 && v != null) {
                        VALUES.put(valueKey(event.getId(), cmd), v);
                    }
                }
            }
            publish(event);
        }

        @Override
        public void subscribe(int[] ids, int pid, String pkg, IVDBusCallback callback) {
            if (callback == null) {
                return;
            }
            subscribers.add(new Subscriber(ids, callback));
            for (int id : ids) {
                Bundle b = EVENTS.get(id);
                if (b != null) {
                    try {
                        callback.onVDBusNotify(new VDEvent(id, new Bundle(b)));
                    } catch (Throwable t) {
                        Log.w(TAG, "initial subscribe notify failed", t);
                    }
                }
            }
        }

        @Override
        public void subscribeCustomizedEvent(VDEvent event, IVDBusNotify notify) {
            if (event == null || notify == null) {
                return;
            }
            customNotify.computeIfAbsent(event.getId(), k -> new CopyOnWriteArrayList<>()).add(notify);
            Bundle b = EVENTS.get(event.getId());
            if (b != null) {
                try {
                    notify.onVDBusNotify(new VDEvent(event.getId(), new Bundle(b)));
                } catch (Throwable t) {
                    Log.w(TAG, "initial custom notify failed", t);
                }
            }
        }

        @Override
        public void unsubscribeCustomizedEvent(VDEvent event, IVDBusNotify notify) {
            if (event == null) {
                return;
            }
            List<IVDBusNotify> list = customNotify.get(event.getId());
            if (list != null) {
                list.remove(notify);
            }
        }
    }

    private static final class Subscriber {
        final int[] ids;
        final IVDBusCallback callback;

        Subscriber(int[] ids, IVDBusCallback callback) {
            this.ids = ids != null ? ids.clone() : new int[0];
            this.callback = callback;
        }

        boolean accepts(int id) {
            for (int v : ids) {
                if (v == id) {
                    return true;
                }
            }
            return false;
        }
    }
}
