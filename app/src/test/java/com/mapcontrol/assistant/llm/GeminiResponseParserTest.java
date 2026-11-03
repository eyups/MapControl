package com.mapcontrol.assistant.llm;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GeminiResponseParserTest {

    @Test
    public void parsesTextResponse() {
        String json = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Cam tavanı açıyorum.\"}],"
                + "\"role\":\"model\"},\"finishReason\":\"STOP\"}]}";
        GeminiTurn turn = GeminiResponseParser.parse(json);
        assertTrue(turn.hasText());
        assertEquals("Cam tavanı açıyorum.", turn.text);
        assertEquals("STOP", turn.finishReason);
        assertFalse(turn.hasCalls());
    }

    @Test
    public void parsesFunctionCall() {
        String json = "{\"candidates\":[{\"content\":{\"parts\":[{\"functionCall\":{"
                + "\"name\":\"sunroof_set\",\"args\":{\"action\":\"open\"}}}],"
                + "\"role\":\"model\"},\"finishReason\":\"STOP\"}]}";
        GeminiTurn turn = GeminiResponseParser.parse(json);
        assertEquals(1, turn.calls.size());
        assertEquals("sunroof_set", turn.calls.get(0).name);
        assertEquals("open", turn.calls.get(0).args.get("action"));
        assertFalse(turn.hasText());
    }

    @Test
    public void preservesThoughtSignatureAndCallId() {
        String json = "{\"candidates\":[{\"content\":{\"parts\":[{\"functionCall\":{"
                + "\"name\":\"report_transcript\",\"args\":{\"text\":\"aç\"},"
                + "\"id\":\"call_99\"},\"thoughtSignature\":\"sig-abc\"}]}}]}";
        GeminiTurn turn = GeminiResponseParser.parse(json);
        assertEquals(1, turn.calls.size());
        assertEquals("sig-abc", turn.calls.get(0).thoughtSignature);
        assertEquals("call_99", turn.calls.get(0).id);
    }

    @Test
    public void parsesTextAndFunctionCallInSameTurn() {
        String json = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Tamam\"},"
                + "{\"functionCall\":{\"name\":\"volume_set\",\"args\":{\"action\":\"up\"}}}]}}]}";
        GeminiTurn turn = GeminiResponseParser.parse(json);
        assertTrue(turn.hasText());
        assertEquals("Tamam", turn.text);
        assertEquals(1, turn.calls.size());
    }

    @Test
    public void parsesBlockReason() {
        String json = "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}";
        GeminiTurn turn = GeminiResponseParser.parse(json);
        assertTrue(turn.isBlocked());
        assertEquals("SAFETY", turn.blockReason);
    }

    @Test
    public void parsesApiErrorMessage() {
        String json = "{\"error\":{\"code\":400,\"message\":\"API key not valid. "
                + "Please pass a valid API key.\",\"status\":\"INVALID_ARGUMENT\"}}";
        assertEquals("API key not valid. Please pass a valid API key.",
                GeminiResponseParser.errorMessage(json));
    }

    @Test
    public void handlesEmptyAndGarbage() {
        assertFalse(GeminiResponseParser.parse("").hasText());
        assertFalse(GeminiResponseParser.parse("not json").hasCalls());
        assertEquals("", GeminiResponseParser.errorMessage("<html>500</html>"));
    }

    @Test
    public void joinsMultipleTextParts() {
        String json = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Merhaba\"},"
                + "{\"text\":\"efendim\"}]}}]}";
        assertEquals("Merhaba efendim", GeminiResponseParser.parse(json).text);
    }
}
