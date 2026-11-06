package com.eyups.roaddeck.assistant.llm;

import com.eyups.roaddeck.assistant.tools.ToolSpec;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class GeminiRequestBuilderTest {

    @Test
    public void includesSystemToolsAndAudio() {
        ToolSpec sunroof = new ToolSpec("sunroof_set", "Cam tavan")
                .param("action", ToolSpec.TYPE_STRING, "işlem",
                        new String[]{"open", "close"}, true);
        byte[] wav = new byte[]{1, 2, 3, 4};
        String body = new GeminiRequestBuilder()
                .system("Sistem")
                .tools(Arrays.asList(sunroof))
                .userAudio(wav, null)
                .build();
        assertTrue(body.contains("\"systemInstruction\""));
        assertTrue(body.contains("\"functionDeclarations\""));
        assertTrue(body.contains("\"mode\":\"AUTO\""));
        assertTrue(body.contains("\"inline_data\""));
        assertTrue(body.contains("\"mime_type\":\"audio/wav\""));
        // Base64(1,2,3,4) = AQIDBA==
        assertTrue(body.contains("AQIDBA=="));
        assertTrue(body.contains("\"enum\":[\"open\",\"close\"]"));
    }

    @Test
    public void replaysFunctionCallsWithSignatureAndId() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("text", "cam tavanı aç");
        List<GeminiTurn.FunctionCall> calls = new ArrayList<>();
        calls.add(new GeminiTurn.FunctionCall("report_transcript", args, "sig-1", "call_7"));
        List<GeminiRequestBuilder.Response> responses = new ArrayList<>();
        responses.add(new GeminiRequestBuilder.Response("report_transcript",
                "{\"ok\":true,\"message\":\"alındı\"}"));

        String body = new GeminiRequestBuilder()
                .system("s")
                .userText("cam tavanı aç")
                .functionCalls(calls)
                .functionResponses(responses)
                .build();
        assertTrue(body.contains("\"name\":\"report_transcript\""));
        assertTrue(body.contains("\"id\":\"call_7\""));
        assertTrue(body.contains("\"thoughtSignature\":\"sig-1\""));
        assertTrue(body.contains("\"functionResponse\""));
        assertTrue(body.contains("\"response\":{\"ok\":true"));
    }

    @Test
    public void historyRolesArePreserved() {
        String body = new GeminiRequestBuilder()
                .userText("bir")
                .modelText("iki")
                .userText("üç")
                .build();
        assertTrue(body.contains("{\"role\":\"user\""));
        assertTrue(body.contains("{\"role\":\"model\""));
        assertFalse(body.contains("\"tools\""));
    }

    @Test
    public void modelNameSanitized() {
        assertTrue(GeminiClient.sanitizeModel("models/gemini-3.5-flash-lite")
                .equals("gemini-3.5-flash-lite"));
        assertTrue(GeminiClient.sanitizeModel("../../evil")
                .equals("gemini-3.5-flash-lite"));
    }
}
