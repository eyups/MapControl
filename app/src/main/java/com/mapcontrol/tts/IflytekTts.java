package com.mapcontrol.tts;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.iflytek.autofly.voicecore.aidl.ITtsStateListener;
import com.iflytek.autofly.voicecore.aidl.ITtsSvc;
import com.iflytek.autofly.voicecore.aidl.VoiceServiceId;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * iFlytek VoiceCore TTS istemcisi.
 *
 * <p>Aracin sesli asistani, TtsService'i streamType=4 ve innerSpeak=true ile
 * kullaniyor; cikis TTS VR hattina gidiyor. Ayni oturum parametrelerini
 * kullaniyoruz. Boylece iFlytek, asistanin konustugu kanaldan konusuyor.</p>
 */
public final class IflytekTts {

    private static final String TAG = "IflytekTts";
    public static final String PACKAGE = "com.iflytek.cutefly.speechclient.hmi";
    public static final String SERVICE =
            "com.iflytek.autofly.voicecoreservice.tts.TtsService";
    public static final String ACTION = "com.iflytek.autofly.TtsService";

    /** Voice assistant's TtsManager uses stream type 4 and innerSpeak=true. */
    private static final int ASSISTANT_STREAM_TYPE = 4;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService BINDER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "iflytek-tts");
        thread.setDaemon(true);
        return thread;
    });

    private static final Object LOCK = new Object();
    private static final AtomicInteger GENERATION = new AtomicInteger();

    private static Context appContext;
    private static ITtsSvc service;
    private static VoiceServiceId sessionId;
    private static boolean binding;
    private static int activeGeneration;
    private static String pendingText;
    private static ResultListener pendingListener;
    private static Runnable pendingFinished;
    private static Runnable activeFinished;

    private IflytekTts() {
    }

    public interface ResultListener {
        void onResult(boolean ok, String message);
    }

    private static final ServiceConnection CONNECTION = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            ITtsSvc svc = ITtsSvc.Stub.asInterface(binder);
            String text;
            ResultListener listener;
            Runnable finished;
            int generation;
            synchronized (LOCK) {
                service = svc;
                binding = false;
                text = pendingText;
                listener = pendingListener;
                finished = pendingFinished;
                generation = activeGeneration;
            }
            if (svc == null || text == null) {
                report(listener, false, "iFlytek TTS servisi bağlanamadı.");
                runFinished(finished);
                return;
            }
            startSession(svc, text, listener, generation, finished);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            synchronized (LOCK) {
                service = null;
                binding = false;
            }
            Log.w(TAG, "iFlytek TTS servisi bağlantısı koptu");
        }
    };

    public static void speak(Context context, String text, ResultListener listener) {
        speak(context, text, listener, null);
    }

    public static void speak(Context context, String text, ResultListener listener,
                             Runnable onFinished) {
        if (context == null || text == null || text.trim().isEmpty()) {
            report(listener, false, "Metin boş.");
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> speak(context, text, listener, onFinished));
            return;
        }

        String clean = text.trim();
        final int generation = GENERATION.incrementAndGet();
        synchronized (LOCK) {
            appContext = context.getApplicationContext();
            pendingText = clean;
            pendingListener = listener;
            pendingFinished = onFinished;
            activeGeneration = generation;
        }
        finishCurrent(true);

        // finishCurrent() cleared pending state; restore this request.
        synchronized (LOCK) {
            appContext = context.getApplicationContext();
            pendingText = clean;
            pendingListener = listener;
            pendingFinished = onFinished;
            activeGeneration = generation;
        }

        ITtsSvc svc;
        boolean needBind;
        synchronized (LOCK) {
            svc = service;
            needBind = svc == null && !binding;
            if (needBind) {
                binding = true;
            }
        }
        Runnable finished;
        synchronized (LOCK) {
            finished = pendingFinished;
        }
        try {
            TtsAudioFocus.acquireTts(appContext, TtsAudioFocus::release);
        } catch (Throwable t) {
            Log.w(TAG, "TTS car-audio focus alınamadı", t);
        }
        if (svc != null) {
            startSession(svc, clean, listener, generation, finished);
            return;
        }
        if (!needBind) {
            return;
        }

        Intent intent = new Intent(ACTION);
        intent.setComponent(new ComponentName(PACKAGE, SERVICE));
        try {
            boolean ok = appContext.bindService(intent, CONNECTION, Context.BIND_AUTO_CREATE);
            if (!ok) {
                synchronized (LOCK) {
                    binding = false;
                }
                report(listener, false, "iFlytek TTS servisi bağlanamadı.");
            }
        } catch (Throwable t) {
            synchronized (LOCK) {
                binding = false;
            }
            Log.w(TAG, "iFlytek TTS bind failed", t);
            report(listener, false, "iFlytek TTS bağlanamadı: "
                    + t.getClass().getSimpleName());
        }
    }

    private static final class SessionListener extends ITtsStateListener.Stub {
        private final VoiceServiceId id;
        private final Runnable onFinished;
        private final java.util.concurrent.atomic.AtomicBoolean done =
                new java.util.concurrent.atomic.AtomicBoolean();

        SessionListener(VoiceServiceId id, Runnable onFinished) {
            this.id = id;
            this.onFinished = onFinished;
        }

        @Override
        public void onTTSPlayBegin(boolean innerSpeak) {
            Log.i(TAG, "iFlytek TTS başladı (inner=" + innerSpeak + ")");
        }

        @Override
        public void onTTSPlayCompleted(boolean innerSpeak) {
            Log.i(TAG, "iFlytek TTS tamamlandı (inner=" + innerSpeak + ")");
            notifyFinished();
        }

        @Override
        public void onTTSPlayInterrupted(boolean innerSpeak) {
            Log.i(TAG, "iFlytek TTS kesildi (inner=" + innerSpeak + ")");
            notifyFinished();
        }

        private void notifyFinished() {
            if (!done.compareAndSet(false, true)) {
                return;
            }
            MAIN.post(() -> {
                if (finishIfCurrent(id) && onFinished != null) {
                    onFinished.run();
                }
            });
        }

        @Override
        public void onTTSProgressReturn(int progress, int duration) {
        }
    }

    private static void startSession(ITtsSvc svc, String text,
                                     ResultListener listener, int generation,
                                     Runnable onFinished) {
        BINDER.execute(() -> {
            synchronized (LOCK) {
                if (activeGeneration != generation) {
                    return;
                }
            }
            VoiceServiceId id = new VoiceServiceId("com.mapcontrol_" + SystemClock.uptimeMillis());
            try {
                ITtsStateListener stateListener = new SessionListener(id, onFinished);
                int created = svc.createTtsSession(id, ASSISTANT_STREAM_TYPE, stateListener, true);
                if (created != 0) {
                    runFinished(onFinished);
                    report(listener, false, "iFlytek TTS oturumu açılamadı (" + created + ").");
                    return;
                }
                synchronized (LOCK) {
                    if (activeGeneration != generation) {
                        try {
                            svc.destroyTtsSession(id);
                        } catch (Throwable ignored) {
                        }
                        return;
                    }
                    sessionId = id;
                    activeFinished = onFinished;
                }
                int started = svc.startSpeak(id, text);
                if (started != 0) {
                    synchronized (LOCK) {
                        if (id.getId().equals(sessionId != null ? sessionId.getId() : null)) {
                            sessionId = null;
                            activeFinished = null;
                        }
                    }
                    try {
                        svc.destroyTtsSession(id);
                    } catch (Throwable ignored) {
                    }
                    runFinished(onFinished);
                    report(listener, false, "iFlytek TTS başlatılamadı (" + started + ").");
                    return;
                }
                report(listener, true, "iFlytek TTS (sesli asistan kanalı) gönderildi.");
            } catch (Throwable t) {
                Log.w(TAG, "iFlytek TTS start failed", t);
                runFinished(onFinished);
                report(listener, false, "iFlytek TTS hatası: "
                        + t.getClass().getSimpleName());
            }
        });
    }

    public static void stop(Context context, ResultListener listener) {
        GENERATION.incrementAndGet();
        finishCurrent(true);
        report(listener, true, "iFlytek TTS durduruldu.");
    }

    private static boolean finishIfCurrent(VoiceServiceId id) {
        if (id == null) {
            return false;
        }
        synchronized (LOCK) {
            if (sessionId == null || !id.getId().equals(sessionId.getId())) {
                return false;
            }
            sessionId = null;
            activeFinished = null;
        }
        BINDER.execute(() -> destroy(id));
        return true;
    }

    private static void finishCurrent(boolean notifyStop) {
        ITtsSvc svc;
        VoiceServiceId id;
        Runnable finished;
        synchronized (LOCK) {
            svc = service;
            id = sessionId;
            finished = activeFinished;
            sessionId = null;
            activeFinished = null;
            pendingText = null;
            pendingListener = null;
            pendingFinished = null;
        }
        if (id == null) {
            runFinished(finished);
            return;
        }
        runFinished(finished);
        BINDER.execute(() -> {
            if (svc != null) {
                try {
                    svc.stopSpeak(id);
                } catch (Throwable ignored) {
                }
            }
            destroy(id);
        });
    }

    private static void destroy(VoiceServiceId id) {
        ITtsSvc svc;
        synchronized (LOCK) {
            svc = service;
        }
        if (svc == null || id == null) {
            return;
        }
        try {
            svc.destroyTtsSession(id);
        } catch (Throwable ignored) {
        }
    }

    private static void runFinished(Runnable finished) {
        if (finished != null) {
            MAIN.post(finished);
        }
    }

    private static void report(ResultListener listener, boolean ok, String message) {
        if (listener == null) {
            Log.i(TAG, message);
            return;
        }
        MAIN.post(() -> listener.onResult(ok, message));
    }
}
