package com.mapcontrol.assistant.store;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConversationStoreTest {

    private File dir;
    private ConversationStore store;

    @Before
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("assistant-store").toFile();
        store = new ConversationStore(dir, false);
    }

    @After
    public void tearDown() {
        store.release();
        delete(dir);
    }

    private static void delete(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                delete(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    @Test
    public void appendsAndPersists() {
        store.append(ConversationStore.ROLE_USER, "cam tavanı aç", "s1");
        store.append(ConversationStore.ROLE_ASSISTANT, "Cam tavanı açıyorum.", "s1");
        store.flushNow();

        ConversationStore reopened = new ConversationStore(dir, false);
        List<ConversationStore.Entry> entries = reopened.load();
        assertEquals(2, entries.size());
        assertEquals("cam tavanı aç", entries.get(0).text);
        assertEquals(ConversationStore.ROLE_ASSISTANT, entries.get(1).role);
        assertEquals("s1", entries.get(1).sessionId);
        reopened.release();
    }

    @Test
    public void capsAtMaxMessagesAndDropsOldest() {
        for (int i = 0; i < ConversationStore.MAX_MESSAGES + 12; i++) {
            store.append(ConversationStore.ROLE_USER, "mesaj " + i, "s");
        }
        List<ConversationStore.Entry> entries = store.snapshot();
        assertEquals(ConversationStore.MAX_MESSAGES, entries.size());
        assertEquals("mesaj 12", entries.get(0).text);
        assertEquals("mesaj 511", entries.get(entries.size() - 1).text);
    }

    @Test
    public void escapesAndRoundTripsSpecialCharacters() {
        String text = "Satır\n\"alıntı\" \\ ters\tve türkçe: ıİğüşöç 🤖";
        store.append(ConversationStore.ROLE_USER, text, "s");
        store.flushNow();

        ConversationStore reopened = new ConversationStore(dir, false);
        assertEquals(text, reopened.load().get(0).text);
        reopened.release();
    }

    @Test
    public void toolEntriesCarryNameAndResult() {
        ConversationStore.Entry entry = store.appendTool("sunroof_set", "{\"action\":\"open\"}",
                "Cam tavanı açıyorum.", "s");
        assertEquals(ConversationStore.ROLE_TOOL, entry.role);
        store.flushNow();
        ConversationStore reopened = new ConversationStore(dir, false);
        ConversationStore.Entry loaded = reopened.load().get(0);
        assertEquals("sunroof_set", loaded.tool);
        assertEquals("Cam tavanı açıyorum.", loaded.result);
        reopened.release();
    }

    @Test
    public void updateTextReplacesPlaceholder() {
        ConversationStore.Entry entry = store.append(ConversationStore.ROLE_USER, "", "s");
        store.updateText(entry, "pencereleri aç");
        store.flushNow();
        ConversationStore reopened = new ConversationStore(dir, false);
        assertEquals("pencereleri aç", reopened.load().get(0).text);
        reopened.release();
    }

    @Test
    public void clearRemovesEverything() {
        store.append(ConversationStore.ROLE_USER, "a", "s");
        store.append(ConversationStore.ROLE_USER, "b", "s");
        store.clear();
        store.flushNow();
        assertTrue(store.snapshot().isEmpty());
        ConversationStore reopened = new ConversationStore(dir, false);
        assertTrue(reopened.load().isEmpty());
        reopened.release();
    }

    @Test
    public void keepsHistoryFileUnderByteCap() {
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 2500; i++) {
            big.append('a');
        }
        for (int i = 0; i < ConversationStore.MAX_MESSAGES; i++) {
            store.append(ConversationStore.ROLE_USER, i + ":" + big, "s");
        }
        store.flushNow();
        assertTrue("file should be capped: " + store.file().length(),
                store.file().length() <= ConversationStore.MAX_BYTES + 4096L);
        ConversationStore reopened = new ConversationStore(dir, false);
        List<ConversationStore.Entry> loaded = reopened.load();
        assertTrue(loaded.size() > 0);
        assertTrue(loaded.size() < ConversationStore.MAX_MESSAGES);
        assertTrue(loaded.get(loaded.size() - 1).text.startsWith(
                (ConversationStore.MAX_MESSAGES - 1) + ":"));
        reopened.release();
    }

    @Test
    public void survivesCorruptLines() {
        store.flushNow();
        try {
            java.nio.file.Files.write(new File(dir, "history.jsonl").toPath(),
                    "garbage line\n{\"id\":1,\"role\":\"user\",\"text\":\"ok\"}\n"
                            .getBytes("UTF-8"));
        } catch (IOException ignored) {
        }
        ConversationStore reopened = new ConversationStore(dir, false);
        List<ConversationStore.Entry> entries = reopened.load();
        assertEquals(1, entries.size());
        assertEquals("ok", entries.get(0).text);
        reopened.release();
    }
}
