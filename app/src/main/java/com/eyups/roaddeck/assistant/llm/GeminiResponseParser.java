package com.eyups.roaddeck.assistant.llm;

import java.util.List;
import java.util.Map;

/**
 * {@code generateContent} yanıtını ayrıştırır. Saf Java: JVM unit test'li.
 *
 * <p>Beklenen şekil: {@code candidates[0].content.parts[*].text} ve
 * {@code .functionCall{name,args}}; hata gövdesi {@code error.message}.</p>
 */
public final class GeminiResponseParser {

    private GeminiResponseParser() {
    }

    public static GeminiTurn parse(String body) {
        GeminiTurn turn = new GeminiTurn();
        Map<String, Object> root = MiniJson.parseObject(body);
        if (root == null) {
            return turn;
        }
        Object promptFeedback = root.get("promptFeedback");
        if (promptFeedback instanceof Map) {
            Object reason = ((Map<?, ?>) promptFeedback).get("blockReason");
            if (reason instanceof String) {
                turn.blockReason = (String) reason;
            }
        }
        Object candidates = root.get("candidates");
        if (candidates instanceof List && !((List<?>) candidates).isEmpty()) {
            Object first = ((List<?>) candidates).get(0);
            if (first instanceof Map) {
                Map<?, ?> candidate = (Map<?, ?>) first;
                Object reason = candidate.get("finishReason");
                if (reason instanceof String) {
                    turn.finishReason = (String) reason;
                }
                Object content = candidate.get("content");
                if (content instanceof Map) {
                    Object parts = ((Map<?, ?>) content).get("parts");
                    if (parts instanceof List) {
                        for (Object part : (List<?>) parts) {
                            if (!(part instanceof Map)) {
                                continue;
                            }
                            Map<?, ?> partMap = (Map<?, ?>) part;
                            Object text = partMap.get("text");
                            if (text instanceof String && !((String) text).trim().isEmpty()) {
                                if (!turn.text.isEmpty()) {
                                    turn.text = turn.text + " " + text;
                                } else {
                                    turn.text = (String) text;
                                }
                            }
                            Object call = partMap.get("functionCall");
                            if (call instanceof Map) {
                                Map<?, ?> callMap = (Map<?, ?>) call;
                                Object name = callMap.get("name");
                                Object args = callMap.get("args");
                                Object id = callMap.get("id");
                                Object signature = partMap.get("thoughtSignature");
                                @SuppressWarnings("unchecked")
                                Map<String, Object> argMap = args instanceof Map
                                        ? (Map<String, Object>) args
                                        : new java.util.LinkedHashMap<>();
                                if (name instanceof String) {
                                    turn.calls.add(new GeminiTurn.FunctionCall(
                                            (String) name, argMap,
                                            signature instanceof String ? (String) signature : "",
                                            id instanceof String ? (String) id : ""));
                                }
                            }
                        }
                    }
                }
            }
        }
        turn.text = turn.text.trim();
        return turn;
    }

    /** API hata gövdesinden okunabilir mesaj; yoksa boş. */
    public static String errorMessage(String body) {
        Map<String, Object> root = MiniJson.parseObject(body);
        if (root == null) {
            return "";
        }
        Object error = root.get("error");
        if (error instanceof Map) {
            Object message = ((Map<?, ?>) error).get("message");
            if (message instanceof String) {
                return (String) message;
            }
        }
        return "";
    }

    /** HTML/boş gövdeyi log'a sızdırmadan kısa özet. */
    public static String preview(String body) {
        if (body == null) {
            return "";
        }
        String value = body.trim().replaceAll("\\s+", " ");
        return value.length() > 180 ? value.substring(0, 180) + "…" : value;
    }
}
