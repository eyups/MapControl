package com.mapcontrol.assistant.core;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConversationPolicyTest {

    @Test
    public void detectsShortDeclines() {
        assertTrue(ConversationPolicy.isDecline("hayır"));
        assertTrue(ConversationPolicy.isDecline("Hayır, teşekkürler."));
        assertTrue(ConversationPolicy.isDecline("yok"));
        assertTrue(ConversationPolicy.isDecline("başka yok"));
        assertTrue(ConversationPolicy.isDecline("tamam"));
    }

    @Test
    public void ignoresCommands() {
        assertFalse(ConversationPolicy.isDecline("cam tavanı aç"));
        assertFalse(ConversationPolicy.isDecline("klimayı kapat"));
        assertFalse(ConversationPolicy.isDecline(""));
    }
}
