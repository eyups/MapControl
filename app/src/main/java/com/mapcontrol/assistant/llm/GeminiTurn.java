package com.mapcontrol.assistant.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Gemini yanıtının ayrıştırılmış hâli: metin ve/veya function call'lar. */
public final class GeminiTurn {

    public static final class FunctionCall {
        public final String name;
        public final Map<String, Object> args;
        /** Yeni Gemini API: model turu geri gönderilirken korunması zorunlu. */
        public final String thoughtSignature;
        /** Sağlayıcının çağrı kimliği (varsa). */
        public final String id;

        public FunctionCall(String name, Map<String, Object> args) {
            this(name, args, "", "");
        }

        public FunctionCall(String name, Map<String, Object> args,
                String thoughtSignature, String id) {
            this.name = name;
            this.args = args != null ? args : new LinkedHashMap<>();
            this.thoughtSignature = thoughtSignature == null ? "" : thoughtSignature;
            this.id = id == null ? "" : id;
        }
    }

    public String text = "";
    public String finishReason = "";
    public String blockReason = "";
    public final List<FunctionCall> calls = new ArrayList<>();

    public boolean hasCalls() {
        return !calls.isEmpty();
    }

    public boolean hasText() {
        return text != null && !text.trim().isEmpty();
    }

    public boolean isBlocked() {
        return blockReason != null && !blockReason.isEmpty();
    }
}
