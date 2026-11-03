package com.mapcontrol.assistant.core;

/** Sesli asistan durum makinesi; UI ve overlay bu değeri izler. */
public enum AssistantState {
    /** Asistan kapalı veya hiç başlatılmadı. */
    DISABLED,
    /** Uyandırma kelimesi bekleniyor (mikrofon açık, KWS uyur). */
    LISTENING_WAKE,
    /** Komut kaydediliyor (push-to-talk veya uyandırma sonrası). */
    RECORDING,
    /** Gemini yanıtı bekleniyor. */
    THINKING,
    /** Onay bekleniyor ("Emin misin?"). */
    CONFIRMING,
    /** TTS konuşuyor. */
    SPEAKING,
    /** Dinleme duraklatıldı (arama, geri vites, OEM VR, kaynak yok). */
    PAUSED,
    /** Hata; kullanıcıya gösterilir, kısa süre sonra dinlemeye döner. */
    ERROR
}
