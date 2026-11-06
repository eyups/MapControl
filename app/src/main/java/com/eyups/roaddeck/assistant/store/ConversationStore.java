package com.eyups.roaddeck.assistant.store;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Konuşma geçmişi: {@code files/assistant/history.jsonl}, satır başına bir kayıt.
 *
 * <p>Kurallar: en fazla {@link #MAX_MESSAGES} mesaj ve {@link #MAX_BYTES} bayt;
 * aşınca en eskiler silinir. Yazım yalnızca konuşma olduğunda, tek arka plan
 * executor'ında ve 1 sn debounce ile yapılır. Açılışta okuma yapılmaz; sekme
 * açılınca {@link #load()} çağrılır. Ses ve API anahtarı saklanmaz.</p>
 */
public final class ConversationStore {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_TOOL = "tool";
    public static final String ROLE_ERROR = "error";
    /** Uygulamanın sorduğu "Başka bir isteğin var mı?" istemi; model bağlamına girmez. */
    public static final String ROLE_FOLLOW_UP = "followup";

    public static final int MAX_MESSAGES = 500;
    public static final long MAX_BYTES = 1024L * 1024L;
    private static final long FLUSH_DELAY_MS = 1000L;

    /** Tek mesaj. Alanlar JSONL'e düz metin olarak yazılır. */
    public static final class Entry {
        public long id;
        public long timeMs;
        public String role = ROLE_ASSISTANT;
        public String text = "";
        public String tool = "";
        public String args = "";
        public String result = "";
        public String sessionId = "";

        public Entry() {
        }

        public Entry(long id, long timeMs, String role, String text, String sessionId) {
            this.id = id;
            this.timeMs = timeMs;
            this.role = role;
            this.text = text == null ? "" : text;
            this.sessionId = sessionId == null ? "" : sessionId;
        }

        public boolean isUser() {
            return ROLE_USER.equals(role);
        }

        public String toJsonLine() {
            StringBuilder b = new StringBuilder(160);
            b.append('{');
            appendField(b, "id", String.valueOf(id)).append(',');
            appendField(b, "ts", String.valueOf(timeMs)).append(',');
            appendField(b, "role", role).append(',');
            appendField(b, "text", text).append(',');
            appendField(b, "tool", tool).append(',');
            appendField(b, "args", args).append(',');
            appendField(b, "res", result).append(',');
            appendField(b, "session", sessionId);
            b.append('}');
            return b.toString();
        }

        private static StringBuilder appendField(StringBuilder b, String key, String value) {
            b.append('"').append(key).append("\":\"").append(escape(value)).append('"');
            return b;
        }

        /** Satırı elle ayrıştırır (org.json'a bağımlı değil, JVM testinde çalışır). */
        public static Entry fromJsonLine(String line) {
            if (line == null || line.trim().isEmpty()) {
                return null;
            }
            Entry entry = new Entry();
            boolean found = false;
            int index = 0;
            int length = line.length();
            while (index < length) {
                int keyStart = line.indexOf('"', index);
                if (keyStart < 0) {
                    break;
                }
                int keyEnd = line.indexOf('"', keyStart + 1);
                if (keyEnd < 0) {
                    break;
                }
                String key = line.substring(keyStart + 1, keyEnd);
                int colon = line.indexOf(':', keyEnd + 1);
                if (colon < 0) {
                    break;
                }
                int cursor = colon + 1;
                while (cursor < length && Character.isWhitespace(line.charAt(cursor))) {
                    cursor++;
                }
                StringBuilder value = new StringBuilder();
                if (cursor < length && line.charAt(cursor) == '"') {
                    cursor++;
                    while (cursor < length) {
                        char c = line.charAt(cursor);
                        if (c == '\\' && cursor + 1 < length) {
                            char next = line.charAt(cursor + 1);
                            if (next == 'n') {
                                value.append('\n');
                            } else if (next == 'r') {
                                value.append('\r');
                            } else if (next == 't') {
                                value.append('\t');
                            } else if (next == 'u' && cursor + 5 < length) {
                                try {
                                    value.append((char) Integer.parseInt(
                                            line.substring(cursor + 2, cursor + 6), 16));
                                    cursor += 4;
                                } catch (NumberFormatException ignored) {
                                    value.append(next);
                                }
                            } else {
                                value.append(next);
                            }
                            cursor += 2;
                            continue;
                        }
                        if (c == '"') {
                            cursor++;
                            break;
                        }
                        value.append(c);
                        cursor++;
                    }
                } else {
                    int startValue = cursor;
                    while (cursor < length) {
                        char c = line.charAt(cursor);
                        if (c == ',' || c == '}') {
                            break;
                        }
                        cursor++;
                    }
                    value.append(line.substring(startValue, cursor).trim());
                }
                index = cursor;
                found = true;
                String text = value.toString();
                if ("id".equals(key)) {
                    entry.id = parseLong(text, 0L);
                } else if ("ts".equals(key)) {
                    entry.timeMs = parseLong(text, 0L);
                } else if ("role".equals(key)) {
                    entry.role = text;
                } else if ("text".equals(key)) {
                    entry.text = text;
                } else if ("tool".equals(key)) {
                    entry.tool = text;
                } else if ("args".equals(key)) {
                    entry.args = text;
                } else if ("res".equals(key)) {
                    entry.result = text;
                } else if ("session".equals(key)) {
                    entry.sessionId = text;
                }
            }
            return found ? entry : null;
        }

        private static long parseLong(String value, long fallback) {
            try {
                return Long.parseLong(value);
            } catch (Throwable t) {
                return fallback;
            }
        }

        private static String escape(String value) {
            if (value == null) {
                return "";
            }
            StringBuilder b = new StringBuilder(value.length() + 8);
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"':
                        b.append("\\\"");
                        break;
                    case '\\':
                        b.append("\\\\");
                        break;
                    case '\n':
                        b.append("\\n");
                        break;
                    case '\r':
                        b.append("\\r");
                        break;
                    case '\t':
                        b.append("\\t");
                        break;
                    default:
                        if (c < 0x20) {
                            String hex = Integer.toHexString(c);
                            b.append("\\u");
                            for (int pad = hex.length(); pad < 4; pad++) {
                                b.append('0');
                            }
                            b.append(hex);
                        } else {
                            b.append(c);
                        }
                }
            }
            return b.toString();
        }
    }

    public interface Listener {
        void onConversationChanged();
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final File dir;
    private final File file;
    private final boolean asyncWrites;
    private final List<Entry> entries = new ArrayList<>();
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private Handler main;
    private final Object lock = new Object();

    private ScheduledExecutorService executor;
    private boolean loaded;
    private boolean dirty;
    private long nextId = 1L;

    public ConversationStore(File dir) {
        this(dir, true);
    }

    public ConversationStore(File dir, boolean asyncWrites) {
        this.dir = dir;
        this.file = new File(dir, "history.jsonl");
        this.asyncWrites = asyncWrites;
    }

    public File file() {
        return file;
    }

    public void addListener(Listener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    /** Tembel yükleme; hiçbir zaman boot sırasında çağrılmamalı. */
    public List<Entry> load() {
        synchronized (lock) {
            if (!loaded) {
                entries.clear();
                readFile();
                loaded = true;
                for (Entry entry : entries) {
                    nextId = Math.max(nextId, entry.id + 1);
                }
            }
            return new ArrayList<>(entries);
        }
    }

    public Entry append(String role, String text, String sessionId) {
        Entry entry = new Entry(nextId, System.currentTimeMillis(), role, text, sessionId);
        append(entry);
        return entry;
    }

    public Entry appendTool(String tool, String args, String result, String sessionId) {
        Entry entry = new Entry(nextId, System.currentTimeMillis(), ROLE_TOOL, "", sessionId);
        entry.tool = tool == null ? "" : tool;
        entry.args = args == null ? "" : args;
        entry.result = result == null ? "" : result;
        append(entry);
        return entry;
    }

    public void append(Entry entry) {
        if (entry == null) {
            return;
        }
        synchronized (lock) {
            if (!loaded) {
                entries.clear();
                readFile();
                loaded = true;
            }
            if (entry.id <= 0L) {
                entry.id = nextId;
            }
            nextId = Math.max(nextId, entry.id + 1);
            entry.timeMs = entry.timeMs > 0L ? entry.timeMs : System.currentTimeMillis();
            entries.add(entry);
            trim();
            dirty = true;
            scheduleFlush();
        }
        notifyListeners();
    }

    public List<Entry> snapshot() {
        synchronized (lock) {
            if (!loaded) {
                return new ArrayList<>();
            }
            return new ArrayList<>(entries);
        }
    }

    /** report_transcript sonucu kullanıcı balonunu günceller. */
    public void updateText(Entry entry, String text) {
        if (entry == null) {
            return;
        }
        synchronized (lock) {
            entry.text = text == null ? "" : text;
            dirty = true;
            scheduleFlush();
        }
        notifyListeners();
    }

    public void clear() {
        synchronized (lock) {
            entries.clear();
            loaded = true;
            dirty = true;
            scheduleFlush();
        }
        notifyListeners();
    }

    /** Test ve kapanış için: bekleyen yazımı hemen tamamlar. */
    public void flushNow() {
        synchronized (lock) {
            if (!dirty) {
                return;
            }
            dirty = false;
            writeFile();
        }
    }

    public void release() {
        flushNow();
        synchronized (lock) {
            if (executor != null) {
                executor.shutdownNow();
                executor = null;
            }
        }
    }

    private void scheduleFlush() {
        if (!asyncWrites) {
            return;
        }
        ScheduledExecutorService service;
        synchronized (lock) {
            if (executor == null) {
                executor = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread thread = new Thread(r, "assistant-store");
                    thread.setDaemon(true);
                    return thread;
                });
            }
            service = executor;
        }
        service.schedule(this::flushNow, FLUSH_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void trim() {
        while (entries.size() > MAX_MESSAGES) {
            entries.remove(0);
        }
    }

    private void readFile() {
        if (!file.isFile()) {
            return;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new java.io.FileInputStream(file), UTF8));
            String line;
            while ((line = reader.readLine()) != null) {
                Entry entry = Entry.fromJsonLine(line);
                if (entry != null && entry.role != null) {
                    entries.add(entry);
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }
        trim();
    }

    private void writeFile() {
        FileOutputStream out = null;
        File temp = new File(dir, "history.jsonl.tmp");
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return;
            }
            out = new FileOutputStream(temp);
            long bytes = 0L;
            List<Entry> copy;
            synchronized (lock) {
                copy = new ArrayList<>(entries);
            }
            // 1 MB sınırı: en yeniden geriye doğru yaz, sığanları kronolojik sıraya çevir.
            List<String> lines = new ArrayList<>(copy.size());
            for (Entry entry : copy) {
                lines.add(entry.toJsonLine());
            }
            int start = 0;
            long total = 0L;
            for (int i = lines.size() - 1; i >= 0; i--) {
                long size = lines.get(i).getBytes(UTF8).length + 1L;
                if (total + size > MAX_BYTES) {
                    start = i + 1;
                    break;
                }
                total += size;
            }
            for (int i = start; i < lines.size(); i++) {
                byte[] data = (lines.get(i) + "\n").getBytes(UTF8);
                out.write(data);
                bytes += data.length;
            }
            out.flush();
            if (out != null) {
                out.close();
                out = null;
            }
            if (temp.renameTo(file)) {
                return;
            }
            // rename başarısızsa doğrudan yaz.
            out = new FileOutputStream(file);
            for (int i = start; i < lines.size(); i++) {
                out.write((lines.get(i) + "\n").getBytes(UTF8));
            }
            out.flush();
        } catch (Throwable ignored) {
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
            }
            if (temp.isFile() && temp.length() > 0L && !temp.equals(file)) {
                // rename başarılıysa temp kalmaz; kaldıysa temizle.
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
            }
        }
    }

    private void notifyListeners() {
        if (listeners.isEmpty()) {
            return;
        }
        Handler handler = mainHandler();
        Runnable dispatch = () -> {
            for (Listener listener : listeners) {
                listener.onConversationChanged();
            }
        };
        if (handler != null) {
            handler.post(dispatch);
        } else {
            dispatch.run();
        }
    }

    /** JVM unit testlerinde Looper yoktur; handler tembel ve güvenli oluşturulur. */
    private Handler mainHandler() {
        if (main == null) {
            try {
                main = new Handler(Looper.getMainLooper());
            } catch (Throwable ignored) {
                main = null;
            }
        }
        return main;
    }
}
