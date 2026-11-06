package com.eyups.roaddeck.assistant.wake;

/**
 * Değiştirilebilir uyandırma kelimesi motoru. Uygulama yalnızca bu arayüze bağlıdır;
 * Vosk (varsayılan) veya sherpa-onnx KWS arka planda seçilebilir.
 */
public interface WakeWordEngine {

    interface Listener {
        void onWakeWord(String phrase);
    }

    /** Tanı/log için kısa ad. */
    String id();

    /** Model + kütüphane hazır mı? */
    boolean isReady();

    /**
     * @param phrase normalize edilmiş uyandırma kelimesi
     */
    void start(String phrase, Listener listener) throws Exception;

    /** 16 kHz mono PCM16 çerçevesi. */
    void feed(short[] pcm, int length);

    /** Sessizlikte tanıyıcıyı sıfırlar (bellek büyümesin). */
    void reset();

    void stop();

    void release();
}
