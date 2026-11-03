package com.mapcontrol.assistant.llm;

import com.mapcontrol.assistant.tools.ToolSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code generateContent} istek gövdesini elle kurar (org.json bağımlılığı yok).
 * Sesli tur tek çağrıda gönderilir: PCM WAV {@code inline_data} olarak eklenir.
 */
public final class GeminiRequestBuilder {

    private static final class Part {
        final String kind; // text | audio | functionCall | functionResponse
        final String text;
        final String mime;
        final byte[] audio;
        final String name;
        final String json; // args veya response gövdesi
        final String id;
        final String signature;

        Part(String kind, String text, String mime, byte[] audio, String name, String json) {
            this(kind, text, mime, audio, name, json, "", "");
        }

        Part(String kind, String text, String mime, byte[] audio, String name, String json,
                String id, String signature) {
            this.kind = kind;
            this.text = text;
            this.mime = mime;
            this.audio = audio;
            this.name = name;
            this.json = json;
            this.id = id == null ? "" : id;
            this.signature = signature == null ? "" : signature;
        }
    }

    /** functionResponse çifti. */
    public static final class Response {
        public final String name;
        public final String json;

        public Response(String name, String json) {
            this.name = name;
            this.json = json;
        }
    }

    private static final class Turn {
        final String role;
        final List<Part> parts = new ArrayList<>();

        Turn(String role) {
            this.role = role;
        }
    }

    private final List<Turn> turns = new ArrayList<>();
    private final List<ToolSpec> tools = new ArrayList<>();
    private String systemInstruction = "";

    public GeminiRequestBuilder system(String instruction) {
        this.systemInstruction = instruction == null ? "" : instruction;
        return this;
    }

    public GeminiRequestBuilder userText(String text) {
        Turn turn = new Turn("user");
        turn.parts.add(new Part("text", text == null ? "" : text, null, null, null, null));
        turns.add(turn);
        return this;
    }

    public GeminiRequestBuilder modelText(String text) {
        Turn turn = new Turn("model");
        turn.parts.add(new Part("text", text == null ? "" : text, null, null, null, null));
        turns.add(turn);
        return this;
    }

    public GeminiRequestBuilder userAudio(byte[] wav, String fallbackText) {
        Turn turn = new Turn("user");
        turn.parts.add(new Part("audio", null, "audio/wav", wav, null, null));
        if (fallbackText != null && !fallbackText.trim().isEmpty()) {
            turn.parts.add(new Part("text", fallbackText, null, null, null, null));
        }
        turns.add(turn);
        return this;
    }

    public GeminiRequestBuilder functionCall(String name, String argsJson) {
        return functionCall(name, argsJson, "", "");
    }

    public GeminiRequestBuilder functionCall(String name, String argsJson, String id,
            String signature) {
        Turn turn = new Turn("model");
        turn.parts.add(new Part("functionCall", null, null, null, name,
                argsJson == null || argsJson.isEmpty() ? "{}" : argsJson, id, signature));
        turns.add(turn);
        return this;
    }

    /** Modelin tüm functionCall parçalarını tek turda geri gönderir (imzalar korunur). */
    public GeminiRequestBuilder functionCalls(List<com.mapcontrol.assistant.llm.GeminiTurn.FunctionCall> calls) {
        if (calls == null || calls.isEmpty()) {
            return this;
        }
        Turn turn = new Turn("model");
        for (com.mapcontrol.assistant.llm.GeminiTurn.FunctionCall call : calls) {
            turn.parts.add(new Part("functionCall", null, null, null, call.name,
                    argsToJson(call.args), call.id, call.thoughtSignature));
        }
        turns.add(turn);
        return this;
    }

    public GeminiRequestBuilder functionResponse(String name, String responseJson) {
        List<Response> list = new ArrayList<>(1);
        list.add(new Response(name, responseJson));
        return functionResponses(list);
    }

    /** Tüm araç sonuçlarını tek kullanıcı turunda gönderir. */
    public GeminiRequestBuilder functionResponses(List<Response> responses) {
        if (responses == null || responses.isEmpty()) {
            return this;
        }
        Turn turn = new Turn("user");
        for (Response response : responses) {
            turn.parts.add(new Part("functionResponse", null, null, null, response.name,
                    response.json == null || response.json.isEmpty() ? "{}" : response.json));
        }
        turns.add(turn);
        return this;
    }

    public GeminiRequestBuilder tools(List<ToolSpec> specs) {
        tools.clear();
        if (specs != null) {
            tools.addAll(specs);
        }
        return this;
    }

    public String build() {
        StringBuilder b = new StringBuilder(1024);
        b.append('{');
        if (!systemInstruction.isEmpty()) {
            b.append("\"systemInstruction\":{\"parts\":[{\"text\":");
            MiniJson.writeString(b, systemInstruction);
            b.append("}]},");
        }
        b.append("\"contents\":[");
        boolean firstTurn = true;
        for (Turn turn : turns) {
            if (!firstTurn) {
                b.append(',');
            }
            firstTurn = false;
            b.append("{\"role\":");
            MiniJson.writeString(b, turn.role);
            b.append(",\"parts\":[");
            boolean firstPart = true;
            for (Part part : turn.parts) {
                if (!firstPart) {
                    b.append(',');
                }
                firstPart = false;
                appendPart(b, part);
            }
            b.append("]}");
        }
        b.append(']');
        if (!tools.isEmpty()) {
            b.append(",\"tools\":[{\"functionDeclarations\":[");
            boolean first = true;
            for (ToolSpec spec : tools) {
                if (!first) {
                    b.append(',');
                }
                first = false;
                b.append("{\"name\":");
                MiniJson.writeString(b, spec.name);
                b.append(",\"description\":");
                MiniJson.writeString(b, spec.description);
                b.append(",\"parameters\":").append(spec.schemaJson()).append('}');
            }
            b.append("]}],\"toolConfig\":{\"functionCallingConfig\":{\"mode\":\"AUTO\"}}");
        }
        b.append(",\"generationConfig\":{\"temperature\":0.4,\"maxOutputTokens\":512,")
                .append("\"candidateCount\":1}");
        b.append('}');
        return b.toString();
    }

    private static void appendPart(StringBuilder b, Part part) {
        switch (part.kind) {
            case "text":
                b.append("{\"text\":");
                MiniJson.writeString(b, part.text);
                b.append('}');
                break;
            case "audio":
                b.append("{\"inline_data\":{\"mime_type\":");
                MiniJson.writeString(b, part.mime);
                b.append(",\"data\":");
                MiniJson.writeString(b, java.util.Base64.getEncoder().encodeToString(part.audio));
                b.append("}}");
                break;
            case "functionCall":
                b.append("{\"functionCall\":{\"name\":");
                MiniJson.writeString(b, part.name);
                b.append(",\"args\":").append(part.json);
                if (!part.id.isEmpty()) {
                    b.append(",\"id\":");
                    MiniJson.writeString(b, part.id);
                }
                b.append('}');
                if (!part.signature.isEmpty()) {
                    b.append(",\"thoughtSignature\":");
                    MiniJson.writeString(b, part.signature);
                }
                b.append('}');
                break;
            case "functionResponse":
                b.append("{\"functionResponse\":{\"name\":");
                MiniJson.writeString(b, part.name);
                b.append(",\"response\":").append(part.json).append("}}");
                break;
            default:
                b.append("{}");
        }
    }

    /** Test/izleme için: function call argümanlarını JSON'a çevirir. */
    public static String argsToJson(Map<String, Object> args) {
        return MiniJson.stringify(args == null ? new java.util.LinkedHashMap<>() : args);
    }
}
