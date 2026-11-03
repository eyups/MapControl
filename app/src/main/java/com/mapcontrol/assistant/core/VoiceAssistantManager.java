package com.mapcontrol.assistant.core;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import com.mapcontrol.assistant.AssistantPrefs;
import com.mapcontrol.assistant.AssistantSpeaker;
import com.mapcontrol.assistant.ConfirmationFlow;
import com.mapcontrol.assistant.audio.AudioCaptureEngine;
import com.mapcontrol.assistant.audio.EnergyGate;
import com.mapcontrol.assistant.audio.UtteranceRecorder;
import com.mapcontrol.assistant.audio.WavCodec;
import com.mapcontrol.assistant.llm.GeminiClient;
import com.mapcontrol.assistant.llm.GeminiRequestBuilder;
import com.mapcontrol.assistant.llm.GeminiResponseParser;
import com.mapcontrol.assistant.llm.GeminiTurn;
import com.mapcontrol.assistant.llm.MiniJson;
import com.mapcontrol.assistant.store.ConversationStore;
import com.mapcontrol.assistant.tools.AssistantSafetyPolicy;
import com.mapcontrol.assistant.tools.AssistantToolExecutor;
import com.mapcontrol.assistant.tools.AssistantToolRegistry;
import com.mapcontrol.assistant.tools.ToolArgumentValidator;
import com.mapcontrol.assistant.tools.ToolResult;
import com.mapcontrol.assistant.tools.ToolSpec;
import com.mapcontrol.assistant.tools.VehicleContext;
import com.mapcontrol.assistant.wake.VoskWakeWordEngine;
import com.mapcontrol.assistant.wake.WakeWordEngine;
import com.mapcontrol.assistant.wake.WakeWordModelStore;
import com.mapcontrol.assistant.wake.WakeWordNormalizer;
import com.mapcontrol.assistant.ui.AssistantListeningPanel;
import com.mapcontrol.assistant.ui.AssistantOverlayButton;
import com.mapcontrol.tts.MapControlSpeech;
import com.mapcontrol.vehicle.ComfortCommands;
import com.mapcontrol.vehicle.VehicleMetricsRepository;
import com.mapcontrol.vehicle.VehicleMetricsSnapshot;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sesli asistan orkestrasyonu. Tek {@link HandlerThread} ("va-core") üzerinde
 * durum makinesi, tek {@link AudioCaptureEngine} (ses "va-audio" thread'inde),
 * Gemini turları, araç araçları ve TTS.
 *
 * <p>Asistan kapalıyken bu sınıfın hiçbir örneği oluşturulmaz; mikrofon açılmaz,
 * model yüklenmez, dosya yazılmaz. Etkinleştirildiğinde bile güç modu ON olduktan
 * en az 20 sn sonra başlar (mevcut 2 sn'lik güç yoklamasına bağlanır).</p>
 */
public final class VoiceAssistantManager {

    private static final String TAG = "VaManager";
    private static final String ACTION_LOG = "com.mapcontrol.LOG_MESSAGE";
    private static final String EXTRA_LOG_MESSAGE = "log_message";

    private static final int CAPTURE_NONE = 0;
    private static final int CAPTURE_WAKE = 1;
    private static final int CAPTURE_UTTERANCE = 2;

    private static final long ON_GRACE_MS = 20000L;
    private static final long WAKE_COOLDOWN_MS = 1500L;
    private static final long WAKE_RESET_SILENCE_MS = 2000L;
    private static final long RECORD_SAFETY_TIMEOUT_MS = 12000L;
    private static final long SESSION_TIMEOUT_MS = 120000L;
    private static final int MAX_MODEL_TURNS = 3;
    private static final int MAX_TOOL_CALLS = 5;
    private static final int MAX_CONSECUTIVE_ERRORS = 10;

    private static volatile VoiceAssistantManager instance;

    public static VoiceAssistantManager get(Context context) {
        if (instance == null) {
            synchronized (VoiceAssistantManager.class) {
                if (instance == null) {
                    instance = new VoiceAssistantManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public static VoiceAssistantManager peek() {
        return instance;
    }

    private final Context appContext;
    private final HandlerThread thread;
    private final Handler handler;
    private final AssistantStateBus states = AssistantStateBus.get();
    private final ConversationStore store;
    private final AssistantSpeaker speaker;
    private final ConfirmationFlow confirmation = new ConfirmationFlow();
    private final GeminiClient gemini = new GeminiClient();
    private final java.util.concurrent.ExecutorService network =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread worker = new Thread(r, "va-net");
                worker.setDaemon(true);
                return worker;
            });
    private AssistantToolRegistry registry;
    private final AssistantToolExecutor executor;

    private AudioCaptureEngine capture;
    private EnergyGate wakeGate;
    private EnergyGate recordGate;
    private UtteranceRecorder recorder;
    private WakeWordEngine wakeEngine;

    private volatile int captureMode = CAPTURE_NONE;
    private volatile float lastLevel;
    private volatile boolean started;
    private volatile boolean released;
    private volatile boolean listeningPaused;
    private volatile boolean turnActive;

    private long onSinceMs;
    private int lastPowerMode = -1;
    private long cooldownUntil;
    private long wakeSilenceSince;
    private static final int WAKE_PREROLL_SAMPLES = 16000; // 1 sn: ilk hece kaybolmasın
    private final short[] wakePreroll = new short[WAKE_PREROLL_SAMPLES];
    private int wakePrerollPos;
    private int wakePrerollCount;
    private boolean wakeSpeechActive;
    private volatile long lastInteractionAt;
    private volatile String sessionId = "";
    private volatile int consecutiveErrors;
    private volatile int turnGeneration;
    private boolean confirmationListening;
    private Runnable pendingRestart;

    private VoiceAssistantManager(Context context) {
        this.appContext = context;
        this.thread = new HandlerThread("va-core");
        this.thread.start();
        this.handler = new Handler(thread.getLooper());
        this.store = new ConversationStore(new File(context.getFilesDir(), "assistant"));
        this.speaker = new AssistantSpeaker(context);
        this.registry = new AssistantToolRegistry(context);
        this.executor = new AssistantToolExecutor(context, new AssistantToolExecutor.UiActions() {
            @Override
            public void openScreen(String screen) {
                broadcastOpenScreen(screen);
            }

            @Override
            public void clearHistory() {
                store.clear();
            }

            @Override
            public void disableAssistant() {
                handler.post(() -> {
                    AssistantPrefs.setEnabled(appContext, false);
                    disableInternal();
                });
            }
        });
    }

    public ConversationStore store() {
        return store;
    }

    public AssistantStateBus stateBus() {
        return states;
    }

    public boolean isStarted() {
        return started;
    }

    /** Canlı ses tayfı için 0..1 arası anlık seviye (ana thread'i bloklamaz). */
    public float level() {
        return lastLevel;
    }

    // ------------------------------------------------------------ lifecycle

    /** Servis güç yoklamasından her 2 sn'de bir çağrılır (yalnız asistan etkinken). */
    public void onPowerMode(int powerMode) {
        if (released) {
            return;
        }
        handler.post(() -> onPowerModeInternal(powerMode));
    }

    private void onPowerModeInternal(int powerMode) {
        if (!AssistantPrefs.isEnabled(appContext)) {
            if (started) {
                disableInternal();
            } else {
                states.set(AssistantState.DISABLED, "");
            }
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (powerMode != lastPowerMode) {
            lastPowerMode = powerMode;
            if (powerMode == 2) {
                onSinceMs = now;
            } else {
                onSinceMs = 0L;
            }
        }
        if (powerMode != 2) {
            if (started) {
                pauseInternal("power");
            }
            return;
        }
        if (onSinceMs == 0L) {
            onSinceMs = now;
        }
        long remaining = ON_GRACE_MS - (now - onSinceMs);
        if (remaining > 0) {
            scheduleStart(remaining);
            return;
        }
        ensureStarted();
        evaluateEnvironment();
    }

    private void scheduleStart(long delayMs) {
        if (pendingRestart != null) {
            return;
        }
        pendingRestart = () -> {
            pendingRestart = null;
            if (AssistantPrefs.isEnabled(appContext) && !released
                    && lastPowerMode == 2) {
                ensureStarted();
                evaluateEnvironment();
            }
        };
        handler.postDelayed(pendingRestart, Math.max(200L, delayMs));
    }

    /** Ayarlar değişti: aç/kapa ve uyandırma kelimesini yeniden değerlendir. */
    public void refreshSettings() {
        if (released) {
            return;
        }
        handler.post(() -> {
            if (!AssistantPrefs.isEnabled(appContext)) {
                disableInternal();
                return;
            }
            registry = new AssistantToolRegistry(appContext);
            if (started) {
                restartWakeEngine();
            } else {
                onPowerModeInternal(lastPowerMode);
            }
        });
    }

    public void disable() {
        if (released) {
            return;
        }
        handler.post(this::disableInternal);
    }

    /** "Oturumu Sıfırla": bağlamı unutur, geçmişi silmez. */
    public void resetSession() {
        if (released) {
            return;
        }
        handler.post(() -> {
            sessionId = "";
            lastInteractionAt = 0L;
            confirmation.clear();
            turnGeneration++;
            states.set(AssistantState.LISTENING_WAKE, "");
        });
    }

    private void ensureStarted() {
        if (started || released || !AssistantPrefs.isEnabled(appContext)) {
            return;
        }
        started = true;
        consecutiveErrors = 0;
        if (capture == null) {
            capture = new AudioCaptureEngine(appContext);
            capture.setListener(new AudioCaptureEngine.Listener() {
                @Override
                public void onFrame(short[] pcm, int length, long timeMs) {
                    handleFrame(pcm, length, timeMs);
                }

                @Override
                public void onError(String message) {
                    handler.post(() -> onCaptureError(message));
                }

                @Override
                public void onSilentInput() {
                    handler.post(() -> onCaptureError("Mikrofon başka uygulamada."));
                }
            });
        }
        if (wakeGate == null) {
            wakeGate = new EnergyGate(AssistantPrefs.sensitivity(appContext));
            recordGate = new EnergyGate(EnergyGate.SENSITIVITY_HIGH);
        }
        if (registry == null) {
            // registry final; ayar değişiminde yeni araç listesi için yeniden kurulamaz.
        }
        if (lastPowerMode == 0 || lastPowerMode == 1) {
            listeningPaused = true;
        }
        if (listeningPaused) {
            // Güç yokken Vosk modelini yükleme; metin turu için yönetici hazır.
            states.set(AssistantState.PAUSED, "power");
        } else {
            startWakeEngine();
            startWakeCapture();
        }
        log("Asistan dinlemeye başladı");
    }

    private void appendWakePreroll(short[] pcm, int length) {
        int n = Math.min(length, pcm.length);
        for (int i = 0; i < n; i++) {
            wakePreroll[wakePrerollPos] = pcm[i];
            wakePrerollPos = (wakePrerollPos + 1) % wakePreroll.length;
            if (wakePrerollCount < wakePreroll.length) {
                wakePrerollCount++;
            }
        }
    }

    private void feedWakePreroll() {
        if (wakeEngine == null || wakePrerollCount <= 0) {
            return;
        }
        short[] ordered = new short[wakePrerollCount];
        int start = (wakePrerollPos - wakePrerollCount + wakePreroll.length)
                % wakePreroll.length;
        for (int i = 0; i < wakePrerollCount; i++) {
            ordered[i] = wakePreroll[(start + i) % wakePreroll.length];
        }
        wakeEngine.feed(ordered, ordered.length);
    }

    private void startWakeEngine() {
        String phrase = WakeWordNormalizer.normalize(AssistantPrefs.wakeWord(appContext));
        File modelDir = WakeWordModelStore.modelDir(appContext);
        if (wakeEngine == null) {
            wakeEngine = new VoskWakeWordEngine(modelDir);
        }
        try {
            if (wakeEngine.isReady()) {
                wakePrerollPos = 0;
                wakePrerollCount = 0;
                wakeSpeechActive = false;
                wakeEngine.start(phrase, this::onWakeTriggered);
            } else {
                Log.i(TAG, "Uyandırma modeli yok; yalnız push-to-talk");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Uyandırma motoru başlatılamadı", t);
        }
    }

    private void restartWakeEngine() {
        if (wakeEngine != null) {
            wakeEngine.stop();
            wakeEngine.release();
            wakeEngine = null;
        }
        startWakeEngine();
        if (captureMode == CAPTURE_NONE && !listeningPaused) {
            startWakeCapture();
        }
    }

    private void startWakeCapture() {
        if (capture == null || released) {
            return;
        }
        if (!AssistantPrefs.isEnabled(appContext)) {
            return;
        }
        if (wakeEngine == null) {
            startWakeEngine();
        }
        if (wakeEngine == null || !wakeEngine.isReady()) {
            // Model yok: mikrofonu boşuna açma; yalnız push-to-talk.
            captureMode = CAPTURE_NONE;
            capture.stop();
            states.set(AssistantState.LISTENING_WAKE, "ptt");
            return;
        }
        captureMode = CAPTURE_WAKE;
        wakeSilenceSince = 0L;
        capture.start();
        Log.i(TAG, "Uyandırma dinlemesi başladı");
        states.set(AssistantState.LISTENING_WAKE, "");
    }

    private void pauseInternal(String reason) {
        boolean busy = isBusy();
        listeningPaused = true;
        if (busy) {
            return;
        }
        captureMode = CAPTURE_NONE;
        if (capture != null) {
            capture.stop();
        }
        states.set(AssistantState.PAUSED, reason);
    }

    private void resumeListening() {
        confirmation.clear();
        if (released || !started) {
            states.set(AssistantState.DISABLED, "");
            return;
        }
        if (listeningPaused) {
            states.set(AssistantState.PAUSED, "");
            return;
        }
        startWakeCapture();
    }

    /** 2 sn'lik güç yoklamasından: arama, geri vites, OEM VR, ekran kapalı. */
    private void evaluateEnvironment() {
        if (!started || released) {
            return;
        }
        if (isBusy()) {
            return;
        }
        String block = environmentBlockReason();
        if (block != null) {
            if (!listeningPaused) {
                pauseInternal(block);
            }
            return;
        }
        if (listeningPaused) {
            listeningPaused = false;
            startWakeCapture();
        } else if (captureMode == CAPTURE_NONE) {
            startWakeCapture();
        }
    }

    private String environmentBlockReason() {
        try {
            AudioManager am = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                int mode = am.getMode();
                if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION) {
                    return "call";
                }
            }
            PowerManager power = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
            if (power != null && !power.isInteractive()) {
                return "screen_off";
            }
            if (am != null && captureMode == CAPTURE_NONE) {
                // Biz kaydederken liste kendi oturumumuzu da içerir; paket adı
                // public SDK'da yok. Bu yüzden yalnız biz susarken başka bir
                // kayıt oturumu varsa (OEM VR) dinlemeyi başlatma/duraklat.
                java.util.List<android.media.AudioRecordingConfiguration> recordings =
                        am.getActiveRecordingConfigurations();
                if (recordings != null && !recordings.isEmpty()) {
                    return "other_recorder";
                }
            }
            VehicleMetricsSnapshot snapshot = VehicleMetricsRepository
                    .getInstance(appContext).getSnapshot();
            if (snapshot != null && snapshot.gearSignal == VehicleContext.GEAR_REVERSE) {
                return "reverse";
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private boolean isBusy() {
        AssistantState state = states.state();
        return state == AssistantState.RECORDING
                || state == AssistantState.THINKING
                || state == AssistantState.CONFIRMING
                || state == AssistantState.SPEAKING;
    }

    private void disableInternal() {
        if (released) {
            return;
        }
        started = false;
        listeningPaused = false;
        captureMode = CAPTURE_NONE;
        if (pendingRestart != null) {
            handler.removeCallbacks(pendingRestart);
            pendingRestart = null;
        }
        if (recorder != null) {
            recorder.cancel("disabled");
            recorder = null;
        }
        if (capture != null) {
            capture.stop();
            capture.release();
            capture = null;
        }
        if (wakeEngine != null) {
            wakeEngine.stop();
            wakeEngine.release();
            wakeEngine = null;
        }
        speaker.stop();
        store.flushNow();
        states.set(AssistantState.DISABLED, "");
        AssistantOverlayButton.sync(appContext);
        AssistantListeningPanel.sync(appContext);
        log("Asistan durduruldu");
    }

    // ---------------------------------------------------------------- audio

    private void handleFrame(short[] pcm, int length, long timeMs) {
        int mode = captureMode;
        if (mode != CAPTURE_NONE) {
            lastLevel = Math.min(1f, EnergyGate.rms(pcm, length) / 6000f);
        }
        if (mode == CAPTURE_WAKE) {
            boolean speech = wakeGate != null && wakeGate.process(pcm, length);
            appendWakePreroll(pcm, length);
            if (speech && wakeEngine != null) {
                wakeSilenceSince = 0L;
                if (!wakeSpeechActive) {
                    // Enerji kapısı açıldığında son 1 sn'yi de ver: "dinle" gibi
                    // ilk hece, eşik altında kaldığı için kaybolmasın.
                    wakeSpeechActive = true;
                    feedWakePreroll();
                } else {
                    wakeEngine.feed(pcm, length);
                }
            } else if (wakeEngine != null) {
                wakeSpeechActive = false;
                if (wakeSilenceSince == 0L) {
                    wakeSilenceSince = timeMs;
                } else if (timeMs - wakeSilenceSince >= WAKE_RESET_SILENCE_MS) {
                    wakeEngine.reset();
                    wakeSilenceSince = timeMs;
                }
            }
        } else if (mode == CAPTURE_UTTERANCE && recorder != null) {
            recorder.feed(pcm, length, timeMs);
        }
    }

    private void onWakeTriggered(String phrase) {
        if (testCallback != null) {
            TestCallback callback = testCallback;
            testCallback = null;
            handler.removeCallbacks(this::finishWakeWordTest);
            if (wakeEngine != null) {
                wakeEngine.stop();
                wakeEngine.release();
                wakeEngine = null;
            }
            startWakeEngine();
            callback.onResult(true);
            return;
        }
        if (SystemClock.elapsedRealtime() < cooldownUntil) {
            return;
        }
        Log.i(TAG, "Uyandırma algılandı: " + phrase);
        cooldownUntil = SystemClock.elapsedRealtime() + WAKE_COOLDOWN_MS;
        handler.post(this::beginListeningAfterWake);
    }

    private void beginListeningAfterWake() {
        if (!started || released || isBusy()) {
            return;
        }
        if (environmentBlockReason() != null) {
            return;
        }
        if (speaker.isSpeaking()) {
            speaker.stop();
        }
        String sound = AssistantPrefs.wakeSound(appContext);
        if (AssistantPrefs.WAKE_SOUND_SPEAK.equals(sound) && AssistantPrefs.voiceReply(appContext)) {
            speaker.speak("Dinliyorum", done -> postStartRecording(false));
        } else {
            if (AssistantPrefs.WAKE_SOUND_TONE.equals(sound)) {
                speaker.beep();
            }
            startRecording(false);
        }
    }

    /** Overlay/tab mikrofon butonu. */
    public void beginPushToTalk() {
        if (released) {
            return;
        }
        handler.post(() -> {
            if (!started) {
                // Servis güç döngüsü henüz başlatmadıysa ilk bas-konuşta başlat.
                ensureStarted();
            }
            if (!started) {
                return;
            }
            if (isBusy()) {
                cancelInternal();
            }
            if (capture == null) {
                return;
            }
            if (speaker.isSpeaking()) {
                speaker.stop();
            }
            startRecording(false);
        });
    }

    public void endPushToTalk() {
        if (released) {
            return;
        }
        handler.post(() -> {
            if (recorder != null && recorder.isActive()) {
                recorder.stopAndComplete();
            }
        });
    }

    private void postStartRecording(boolean confirm) {
        handler.post(() -> startRecording(confirm));
    }

    private void postResume() {
        handler.post(this::resumeListening);
    }

    private void startRecording(boolean confirm) {
        if (capture == null) {
            return;
        }
        if (recordGate == null) {
            recordGate = new EnergyGate(EnergyGate.SENSITIVITY_HIGH);
        }
        confirmationListening = confirm;
        recorder = new UtteranceRecorder(recordGate, new UtteranceRecorder.Listener() {
            @Override
            public void onSpeechStart() {
                // durum aynı; ek log yok
            }

            @Override
            public void onComplete(short[] pcm, int length) {
                handler.post(() -> onUtteranceComplete(pcm, length));
            }

            @Override
            public void onCancelled(String reason) {
                handler.post(() -> onUtteranceCancelled(reason));
            }
        });
        recorder.start(SystemClock.elapsedRealtime());
        captureMode = CAPTURE_UTTERANCE;
        capture.start();
        states.set(confirm ? AssistantState.CONFIRMING : AssistantState.RECORDING, "");
        handler.postDelayed(this::recordTimeout, RECORD_SAFETY_TIMEOUT_MS);
    }

    private void recordTimeout() {
        if (recorder != null && recorder.isActive()) {
            recorder.stopAndComplete();
        }
    }

    private void onUtteranceCancelled(String reason) {
        recorder = null;
        captureMode = CAPTURE_NONE;
        if (confirmationListening) {
            confirmationListening = false;
            confirmation.clear();
            speaker.speak("Vazgeçtim.", done -> postResume());
            return;
        }
        if ("no_speech".equals(reason)) {
            resumeListening();
        } else {
            resumeListening();
        }
    }

    private void onUtteranceComplete(short[] pcm, int length) {
        recorder = null;
        captureMode = CAPTURE_NONE;
        if (pcm == null || length < EnergyGate.SAMPLE_RATE / 4) {
            if (confirmationListening) {
                confirmationListening = false;
                confirmation.clear();
                speaker.speak("Vazgeçtim.", done -> postResume());
            } else {
                resumeListening();
            }
            return;
        }
        byte[] wav = WavCodec.toWav(pcm, length, EnergyGate.SAMPLE_RATE);
        if (confirmationListening) {
            confirmationListening = false;
            handleConfirmationAudio(wav);
            return;
        }
        beginTurn(null, wav);
    }

    // ------------------------------------------------------------ assistant

    /** Tab'daki metin kutusu. Onay bekleniyorsa yanıt olarak yorumlanır. */
    public void submitText(String text) {
        if (released) {
            return;
        }
        final String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            return;
        }
        handler.post(() -> {
            if (!AssistantPrefs.isEnabled(appContext)) {
                states.set(AssistantState.DISABLED, "");
                return;
            }
            if (!started) {
                ensureStarted();
            }
            if (confirmation.hasPending()) {
                handleConfirmationText(value);
                return;
            }
            if (isBusy()) {
                cancelInternal();
            }
            if (speaker.isSpeaking()) {
                speaker.stop();
            }
            beginTurn(value, null);
        });
    }

    /** Barge-in / iptal. */
    public void cancel() {
        if (released) {
            return;
        }
        handler.post(this::cancelInternal);
    }

    private void cancelInternal() {
        turnGeneration++;
        turnActive = false;
        speaker.stop();
        if (recorder != null) {
            recorder.cancel("user");
            recorder = null;
        }
        captureMode = CAPTURE_NONE;
        confirmation.clear();
        confirmationListening = false;
        if (started) {
            resumeListening();
        }
    }

    private void beginTurn(String text, byte[] wav) {
        if (released || !started) {
            return;
        }
        long now = System.currentTimeMillis();
        if (sessionId.isEmpty() || now - lastInteractionAt > SESSION_TIMEOUT_MS) {
            sessionId = "s" + now;
        }
        lastInteractionAt = now;
        if (wav != null) {
            captureMode = CAPTURE_NONE;
            if (capture != null) {
                capture.stop();
            }
        }
        List<ConversationStore.Entry> history = historyForModel();
        ConversationStore.Entry userEntry = store.append(ConversationStore.ROLE_USER,
                text == null ? "" : text, sessionId);
        states.set(AssistantState.THINKING, "");
        final int generation = ++turnGeneration;
        turnActive = true;
        network.execute(() -> {
            try {
                runTurn(generation, text, wav, history, userEntry);
            } catch (Throwable t) {
                if (isCurrentTurn(generation)) {
                    failTurn(generation, "Bir hata oldu, tekrar dene.", userEntry);
                }
            } finally {
                if (isCurrentTurn(generation)) {
                    turnActive = false;
                }
            }
        });
    }

    private boolean isCurrentTurn(int generation) {
        return !released && generation == turnGeneration;
    }

    private List<ConversationStore.Entry> historyForModel() {
        List<ConversationStore.Entry> all = store.snapshot();
        List<ConversationStore.Entry> result = new ArrayList<>();
        int start = Math.max(0, all.size() - 20);
        for (int i = start; i < all.size(); i++) {
            ConversationStore.Entry entry = all.get(i);
            if (ConversationStore.ROLE_USER.equals(entry.role)
                    || ConversationStore.ROLE_ASSISTANT.equals(entry.role)) {
                if (entry.text != null && !entry.text.isEmpty()) {
                    result.add(entry);
                }
            }
        }
        return result;
    }

    private void runTurn(int generation, String text, byte[] wav,
            List<ConversationStore.Entry> history, ConversationStore.Entry userEntry) {
        if (!isCurrentTurn(generation)) {
            return;
        }
        if (!AssistantPrefs.hasApiKey(appContext)) {
            failTurn(generation, "API anahtarı yok. Ayarlardan ekle.", userEntry);
            return;
        }
        if (!MapControlSpeech.isOnline(appContext)) {
            failTurn(generation, "İnternet bağlantısı yok.", userEntry);
            return;
        }
        final String model = GeminiClient.sanitizeModel(AssistantPrefs.model(appContext));
        final String apiKey = AssistantPrefs.apiKey(appContext);
        GeminiRequestBuilder request = new GeminiRequestBuilder()
                .system(systemInstruction())
                .tools(registry.specs());
        for (ConversationStore.Entry entry : history) {
            if (ConversationStore.ROLE_USER.equals(entry.role)) {
                request.userText(entry.text);
            } else {
                request.modelText(entry.text);
            }
        }
        if (wav != null) {
            request.userAudio(wav, null);
        } else {
            request.userText(text);
        }

        int toolCalls = 0;
        String lastToolMessage = "";
        for (int turnIndex = 0; turnIndex < MAX_MODEL_TURNS; turnIndex++) {
            if (!isCurrentTurn(generation)) {
                return;
            }
            GeminiClient.Reply reply = gemini.generateContent(model, apiKey, request.build());
            if (!isCurrentTurn(generation)) {
                return;
            }
            if (!reply.isOk()) {
                failTurn(generation,
                        GeminiClient.describeError(reply.kind, reply.httpCode, reply.error),
                        userEntry);
                return;
            }
            GeminiTurn turn = GeminiResponseParser.parse(reply.body);
            if (turn.isBlocked()) {
                failTurn(generation, "Bu isteği yanıtlayamıyorum.", userEntry);
                return;
            }
            if (turn.hasCalls()) {
                // Model turu thought signature'larıyla birlikte tek parça geri gönderilir.
                request.functionCalls(turn.calls);
                List<GeminiRequestBuilder.Response> responses = new ArrayList<>();
                for (GeminiTurn.FunctionCall call : turn.calls) {
                    if (!isCurrentTurn(generation)) {
                        return;
                    }
                    if ("report_transcript".equals(call.name)) {
                        Object value = call.args.get("text");
                        if (value instanceof String && userEntry != null
                                && (userEntry.text == null || userEntry.text.isEmpty())) {
                            store.updateText(userEntry, (String) value);
                        }
                        responses.add(new GeminiRequestBuilder.Response(call.name,
                                ToolResult.ok("alındı").responseJson()));
                        continue;
                    }
                    if ("assistant_control".equals(call.name)
                            && "disable".equals(call.args.get("action"))) {
                        // Asistanı kapat: yönetici durur, arkasından TTS/yanıt üretilmez.
                        executeCall(call);
                        return;
                    }
                    if ("assistant_control".equals(call.name)
                            && "stop".equals(call.args.get("action"))) {
                        // "Sus/dur/iptal": TTS'i kes, yeni yanıt üretme.
                        speaker.stop();
                        store.appendTool(call.name,
                                GeminiRequestBuilder.argsToJson(call.args),
                                "Durduruldu.", sessionId);
                        postResume();
                        return;
                    }
                    if (toolCalls >= MAX_TOOL_CALLS) {
                        responses.add(new GeminiRequestBuilder.Response(call.name,
                                ToolResult.fail("tur başına araç sınırı").responseJson()));
                        continue;
                    }
                    toolCalls++;
                    ToolResult result = executeCall(call);
                    lastToolMessage = result.message;
                    responses.add(new GeminiRequestBuilder.Response(call.name,
                            result.responseJson()));
                    // Okuma araçları da sohbette eylem kartı olarak görünsün.
                    store.appendTool(call.name,
                            GeminiRequestBuilder.argsToJson(call.args),
                            result.message, sessionId);
                }
                request.functionResponses(responses);
                if (confirmation.hasPending()) {
                    return;
                }
                continue;
            }
            String answer = turn.hasText() ? turn.text
                    : (!lastToolMessage.isEmpty() ? lastToolMessage : "Tamam.");
            finishTurn(generation, answer);
            return;
        }
        finishTurn(generation, !lastToolMessage.isEmpty() ? lastToolMessage : "Tamam.");
    }

    private ToolResult executeCall(GeminiTurn.FunctionCall call) {
        ToolSpec spec = registry.find(call.name);
        if (spec == null) {
            return ToolResult.fail("Bu işlemi yapamıyorum.");
        }
        ToolArgumentValidator.Result validation = ToolArgumentValidator.validate(spec, call.args);
        if (!validation.valid) {
            return ToolResult.fail(validation.error);
        }
        VehicleContext context = vehicleContext();
        AssistantSafetyPolicy.Decision decision = AssistantSafetyPolicy.evaluate(
                call.name, validation.values, context);
        if (decision.isBlocked()) {
            return ToolResult.fail(decision.spoken);
        }
        if (decision.isAllowed() && AssistantSafetyPolicy.isWriteTool(call.name)
                && "strict".equals(AssistantPrefs.prefs(appContext)
                .getString(AssistantPrefs.KEY_CONFIRM_POLICY, "layered"))) {
            askConfirmation(call.name, validation.values, "Onaylıyor musun?");
            return ToolResult.fail("onay bekleniyor");
        }
        if (decision.needsConfirmation()) {
            String question = decision.spoken.isEmpty() ? "Onaylıyor musun?" : decision.spoken;
            askConfirmation(call.name, validation.values, question);
            return ToolResult.fail("onay bekleniyor");
        }
        return executor.execute(call.name, validation.values);
    }

    private void askConfirmation(String tool, Map<String, String> args, String question) {
        confirmation.setPending(tool, args, question);
        states.set(AssistantState.CONFIRMING, question);
        // Soru sohbette de görünsün; sesli yanıt kapalıysa mikrofon açılmaz.
        store.append(ConversationStore.ROLE_ASSISTANT, question, sessionId);
        if (AssistantPrefs.voiceReply(appContext)) {
            speaker.speak(question, done -> postStartRecording(true));
        }
    }

    private void handleConfirmationText(String text) {
        final ConfirmationFlow.Pending pending = confirmation.take();
        if (pending == null) {
            postResume();
            return;
        }
        final int generation = ++turnGeneration;
        turnActive = true;
        network.execute(() -> {
            try {
                applyConfirmation(generation, pending, ConfirmationFlow.parseAnswer(text));
            } finally {
                if (isCurrentTurn(generation)) {
                    turnActive = false;
                }
            }
        });
    }

    private void handleConfirmationAudio(byte[] wav) {
        final ConfirmationFlow.Pending pending = confirmation.peek();
        if (pending == null) {
            postResume();
            return;
        }
        states.set(AssistantState.THINKING, "");
        final int generation = ++turnGeneration;
        turnActive = true;
        final String apiKey = AssistantPrefs.apiKey(appContext);
        final String model = GeminiClient.sanitizeModel(AssistantPrefs.model(appContext));
        network.execute(() -> {
            try {
                if (!AssistantPrefs.hasApiKey(appContext)
                        || !MapControlSpeech.isOnline(appContext)) {
                    confirmation.clear();
                    finishTurn(generation, "Onaylayamadım, vazgeçtim.");
                    return;
                }
                String request = new GeminiRequestBuilder()
                        .system("Kullanıcıya bir araç komutu için onay sorusu sordun. "
                                + "Sesli yanıtı sınıflandır. Yalnızca EVET veya HAYIR yaz.")
                        .userAudio(wav, null)
                        .build();
                GeminiClient.Reply reply = gemini.generateContent(model, apiKey, request);
                confirmation.clear();
                if (!isCurrentTurn(generation)) {
                    return;
                }
                if (!reply.isOk()) {
                    finishTurn(generation, "Onaylayamadım, vazgeçtim.");
                    return;
                }
                GeminiTurn turn = GeminiResponseParser.parse(reply.body);
                applyConfirmation(generation, pending,
                        ConfirmationFlow.parseAnswer(turn.text));
            } catch (Throwable t) {
                if (isCurrentTurn(generation)) {
                    finishTurn(generation, "Onaylayamadım, vazgeçtim.");
                }
            } finally {
                if (isCurrentTurn(generation)) {
                    turnActive = false;
                }
            }
        });
    }

    private void applyConfirmation(int generation, ConfirmationFlow.Pending pending,
            ConfirmationFlow.Answer answer) {
        if (!isCurrentTurn(generation)) {
            return;
        }
        if (answer == ConfirmationFlow.Answer.YES) {
            ToolResult result = executor.execute(pending.tool, pending.args);
            store.appendTool(pending.tool, MiniJson.stringify(pending.args), result.message,
                    sessionId);
            finishTurn(generation, result.ok ? result.message : ("Olmadı: " + result.message));
        } else if (answer == ConfirmationFlow.Answer.NO) {
            finishTurn(generation, "Vazgeçtim.");
        } else {
            finishTurn(generation, "Anlayamadım, vazgeçtim.");
        }
    }

    private void finishTurn(int generation, String answer) {
        if (!isCurrentTurn(generation)) {
            return;
        }
        states.set(AssistantState.SPEAKING, "");
        store.append(ConversationStore.ROLE_ASSISTANT, answer, sessionId);
        lastInteractionAt = System.currentTimeMillis();
        consecutiveErrors = 0;
        if (AssistantPrefs.voiceReply(appContext)) {
            speaker.speak(answer, done -> postResume());
        } else {
            postResume();
        }
    }

    private void failTurn(int generation, String message, ConversationStore.Entry userEntry) {
        if (!isCurrentTurn(generation)) {
            return;
        }
        consecutiveErrors++;
        states.set(AssistantState.ERROR, message);
        store.append(ConversationStore.ROLE_ERROR, message, sessionId);
        if (AssistantPrefs.voiceReply(appContext)) {
            speaker.speak(message, done -> postResume());
        } else {
            postResume();
        }
        log(message);
    }

    private void onCaptureError(String message) {
        consecutiveErrors++;
        states.set(AssistantState.ERROR, message);
        captureMode = CAPTURE_NONE;
        if (capture != null) {
            capture.stop();
        }
        long delay = Math.min(300000L,
                5000L * (1L << Math.min(6, Math.max(0, consecutiveErrors - 1))));
        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
            AssistantPrefs.setEnabled(appContext, false);
            disableInternal();
            return;
        }
        log("Mikrofon hatası: " + message);
        handler.postDelayed(() -> {
            if (started && AssistantPrefs.isEnabled(appContext)) {
                evaluateEnvironment();
            }
        }, delay);
    }

    // -------------------------------------------------------- wake word test

    public interface TestCallback {
        void onResult(boolean detected);
    }

    private TestCallback testCallback;

    /**
     * Ayarlardaki "Kelimeyi test et": 5 sn dinler, kelime algılanırsa true döner.
     * Mevcut wake engine geçici olarak test kelimesiyle yeniden başlatılır.
     */
    public void testWakeWord(String phrase, TestCallback callback) {
        if (released) {
            return;
        }
        handler.post(() -> {
            if (!started) {
                ensureStarted();
            }
            if (capture == null) {
                if (callback != null) {
                    callback.onResult(false);
                }
                return;
            }
            if (isBusy()) {
                if (callback != null) {
                    callback.onResult(false);
                }
                return;
            }
            testCallback = callback;
            String value = WakeWordNormalizer.normalize(phrase);
            if (wakeEngine != null) {
                wakeEngine.stop();
                wakeEngine.release();
            }
            wakeEngine = new VoskWakeWordEngine(WakeWordModelStore.modelDir(appContext));
            try {
                wakeEngine.start(value, this::onWakeTriggered);
            } catch (Throwable t) {
                Log.w(TAG, "Test motoru başlatılamadı", t);
            }
            captureMode = CAPTURE_WAKE;
            capture.start();
            handler.postDelayed(this::finishWakeWordTest, 5000L);
        });
    }

    private void finishWakeWordTest() {
        if (testCallback == null) {
            return;
        }
        TestCallback callback = testCallback;
        testCallback = null;
        if (wakeEngine != null) {
            wakeEngine.stop();
            wakeEngine.release();
            wakeEngine = null;
        }
        startWakeEngine();
        callback.onResult(false);
    }

    // --------------------------------------------------------------- context

    private VehicleContext vehicleContext() {
        try {
            if (!ComfortCommands.isConnected()) {
                return VehicleContext.unknown();
            }
            // UI metrics önbelleği yalnız araç sekmesi açıkken dolar; doğrudan oku.
            int power = ComfortCommands.readRaw(
                    com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo.MODULE_READONLY_INFO,
                    com.desaysv.ivi.extra.project.carinfo.ReadOnlyID.ID_SYSTEM_POWER_MODE);
            int gear = ComfortCommands.readRaw(
                    com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo.MODULE_READONLY_INFO,
                    com.desaysv.ivi.extra.project.carinfo.ReadOnlyID.ID_GEARBOX_STATE);
            int speed = ComfortCommands.readSpeedKmh();
            return new VehicleContext(true,
                    power == ComfortCommands.INVALID_TEMP ? -1 : power,
                    Math.max(0, speed),
                    gear == ComfortCommands.INVALID_TEMP ? -1 : gear,
                    ComfortCommands.isEngineRunning(),
                    ComfortCommands.readOutsideTempC());
        } catch (Throwable t) {
            return VehicleContext.unknown();
        }
    }

    private String contextJson() {
        VehicleContext ctx = vehicleContext();
        StringBuilder b = new StringBuilder(160);
        b.append("{\"speed\":").append(ctx.speedKmh)
                .append(",\"gear\":\"").append(ctx.gearLabel()).append('"')
                .append(",\"powerMode\":").append(ctx.powerMode)
                .append(",\"engineRunning\":").append(ctx.engineRunning);
        try {
            int posi = ComfortCommands.readSunroofPosition();
            int shade = ComfortCommands.readRaw(
                    com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo.MODULE_CAR_SETTING, 10);
            b.append(",\"sunroofOpen\":").append(posi > 1)
                    .append(",\"sunshadeOpen\":").append(shade == 2);
        } catch (Throwable ignored) {
        }
        if (!Float.isNaN(ctx.outsideTempC)) {
            b.append(String.format(Locale.US, ",\"outsideTempC\":%.1f", ctx.outsideTempC));
        }
        b.append('}');
        return b.toString();
    }

    private String systemInstruction() {
        return "Sen RoadDeck adlı araç içi sesli asistansın. Türkçe konuşuyorsun.\n"
                + "Kurallar:\n"
                + "- Yanıtların 1-2 kısa cümle olsun; sürücünün dikkatini dağıtma.\n"
                + "- Markdown, emoji, liste veya kod kullanma; yanıt sesli okunacak.\n"
                + "- Araç ve uygulama işlemleri için yalnız verilen araçları kullan; "
                + "modül, ID veya ham değer uydurma.\n"
                + "- Kullanıcı sesli konuştuysa söylediğini report_transcript ile aynen bildir.\n"
                + "- Araç sonucunu uydurma; araç başarısızsa nedenini söyle.\n"
                + "- Belirsizse tek bir kısa soru sor.\n"
                + "- AEB, FCW, LDW, LDP, ISS gibi güvenlik sistemlerini değiştirme isteklerini "
                + "reddet ve güvenlik nedeniyle yapamadığını söyle.\n"
                + "- Asla API anahtarı veya kişisel veri okuma.\n"
                + "Araç durumu: " + contextJson();
    }

    // ----------------------------------------------------------------- util

    private void broadcastOpenScreen(String screen) {
        Intent intent = new Intent("com.mapcontrol.action.ASSISTANT_OPEN_SCREEN");
        intent.setPackage(appContext.getPackageName());
        intent.putExtra("screen", screen == null ? "" : screen);
        appContext.sendBroadcast(intent);
    }

    private void log(String message) {
        try {
            Intent intent = new Intent(ACTION_LOG);
            intent.setPackage(appContext.getPackageName());
            intent.putExtra(EXTRA_LOG_MESSAGE, "[Asistan] " + message);
            appContext.sendBroadcast(intent);
        } catch (Throwable ignored) {
        }
    }
}
