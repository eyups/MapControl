package com.eyups.roaddeck.assistant;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ConfirmationFlowTest {

    @Test
    public void recognisesTurkishYes() {
        assertEquals(ConfirmationFlow.Answer.YES, ConfirmationFlow.parseAnswer("evet"));
        assertEquals(ConfirmationFlow.Answer.YES, ConfirmationFlow.parseAnswer("Tamam, aç."));
        assertEquals(ConfirmationFlow.Answer.YES, ConfirmationFlow.parseAnswer("onaylıyorum"));
        assertEquals(ConfirmationFlow.Answer.YES, ConfirmationFlow.parseAnswer("Olur"));
    }

    @Test
    public void recognisesTurkishNo() {
        assertEquals(ConfirmationFlow.Answer.NO, ConfirmationFlow.parseAnswer("hayır"));
        assertEquals(ConfirmationFlow.Answer.NO, ConfirmationFlow.parseAnswer("vazgeç"));
        assertEquals(ConfirmationFlow.Answer.NO, ConfirmationFlow.parseAnswer("iptal lütfen"));
        assertEquals(ConfirmationFlow.Answer.NO, ConfirmationFlow.parseAnswer("yok"));
    }

    @Test
    public void unknownForOtherText() {
        assertEquals(ConfirmationFlow.Answer.UNKNOWN, ConfirmationFlow.parseAnswer("belki"));
        assertEquals(ConfirmationFlow.Answer.UNKNOWN, ConfirmationFlow.parseAnswer(""));
        assertEquals(ConfirmationFlow.Answer.UNKNOWN, ConfirmationFlow.parseAnswer(null));
    }

    @Test
    public void noWinsWhenBothWordsPresent() {
        assertEquals(ConfirmationFlow.Answer.NO, ConfirmationFlow.parseAnswer("evet hayır"));
    }

    @Test
    public void pendingIsTakenOnce() {
        ConfirmationFlow flow = new ConfirmationFlow();
        assertFalse(flow.hasPending());
        Map<String, String> args = new LinkedHashMap<>();
        args.put("action", "unlock");
        flow.setPending("door_lock_set", args, "Kilidi açmamı onaylıyor musun?");
        assertTrue(flow.hasPending());
        ConfirmationFlow.Pending pending = flow.take();
        assertNotNull(pending);
        assertEquals("door_lock_set", pending.tool);
        assertEquals("unlock", pending.args.get("action"));
        assertNull(flow.take());
        assertFalse(flow.hasPending());
    }
}
