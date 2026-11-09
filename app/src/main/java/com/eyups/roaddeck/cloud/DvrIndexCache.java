package com.eyups.roaddeck.cloud;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * DVR katalog imzasını, klip sürelerini ve küçük resimleri diskte kalıcı tutar.
 * Süreç yeniden başladığında 100 klibin tamamını tekrar çözmeyi ve yüklemeyi önler.
 */
final class DvrIndexCache {

    static final long FULL_REFRESH_INTERVAL_MS = 24 * 60 * 60_000L;
    private static final String INDEX_FILE = "index.properties";
    private static final String PROP_SIGNATURE = "last_signature";
    private static final String PROP_FULL_UPLOAD_MS = "last_full_upload_ms";
    private static final String PREFIX_ENTRY = "entry.";
    private static final String SUFFIX_KEY = ".key";
    private static final String SUFFIX_DURATION = ".duration_ms";
    private static final String SUFFIX_UPLOADED = ".uploaded";

    static final class Entry {
        final long durationMs;
        final byte[] jpeg;
        final boolean uploaded;

        Entry(long durationMs, byte[] jpeg, boolean uploaded) {
            this.durationMs = durationMs;
            this.jpeg = jpeg;
            this.uploaded = uploaded;
        }
    }

    private static final class Meta {
        final String thumbKey;
        final String hash;
        long durationMs;
        boolean uploaded;

        Meta(String thumbKey, String hash, long durationMs, boolean uploaded) {
            this.thumbKey = thumbKey;
            this.hash = hash;
            this.durationMs = durationMs;
            this.uploaded = uploaded;
        }
    }

    private final File dir;
    private final Map<String, Meta> entries = new HashMap<>();
    private String lastSignature = "";
    private long lastFullUploadMs;

    DvrIndexCache(File dir) {
        this.dir = dir;
        load();
    }

    synchronized String getLastSignature() {
        return lastSignature;
    }

    synchronized void setLastSignature(String signature) {
        this.lastSignature = signature == null ? "" : signature;
    }

    synchronized boolean isFullUploadDue(long nowMs) {
        return lastFullUploadMs <= 0L || (nowMs - lastFullUploadMs) >= FULL_REFRESH_INTERVAL_MS;
    }

    synchronized void markFullUpload(long nowMs) {
        this.lastFullUploadMs = Math.max(0L, nowMs);
    }

    synchronized Entry get(String thumbKey) {
        if (thumbKey == null || thumbKey.isEmpty()) {
            return null;
        }
        Meta meta = entries.get(thumbKey);
        if (meta == null) {
            return null;
        }
        byte[] jpeg = readJpeg(meta.hash);
        return new Entry(meta.durationMs, jpeg, meta.uploaded);
    }

    synchronized void put(String thumbKey, long durationMs, byte[] jpeg, boolean uploaded) {
        if (thumbKey == null || thumbKey.isEmpty()) {
            return;
        }
        String hash = sha256Hex(thumbKey);
        if (jpeg != null && jpeg.length > 0) {
            writeJpeg(hash, jpeg);
        }
        entries.put(thumbKey, new Meta(thumbKey, hash, Math.max(0L, durationMs), uploaded));
    }

    synchronized void markUploaded(Collection<String> thumbKeys) {
        if (thumbKeys == null) {
            return;
        }
        for (String key : thumbKeys) {
            Meta meta = entries.get(key);
            if (meta != null) {
                meta.uploaded = true;
            }
        }
    }

    synchronized void retainOnly(Set<String> activeThumbKeys) {
        Set<String> keepKeys = activeThumbKeys == null ? new HashSet<>() : activeThumbKeys;
        Set<String> keepHashes = new HashSet<>();
        Iterator<Map.Entry<String, Meta>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Meta> item = it.next();
            if (!keepKeys.contains(item.getKey())) {
                deleteJpeg(item.getValue().hash);
                it.remove();
            } else {
                keepHashes.add(item.getValue().hash);
            }
        }
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.endsWith(".jpg")) {
                    String hash = name.substring(0, name.length() - 4);
                    if (!keepHashes.contains(hash)) {
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                    }
                }
            }
        }
    }

    synchronized void save() {
        if (!dir.exists() && !dir.mkdirs()) {
            return;
        }
        Properties props = new Properties();
        props.setProperty(PROP_SIGNATURE, lastSignature);
        props.setProperty(PROP_FULL_UPLOAD_MS, Long.toString(lastFullUploadMs));
        for (Meta meta : entries.values()) {
            String base = PREFIX_ENTRY + meta.hash;
            props.setProperty(base + SUFFIX_KEY, meta.thumbKey);
            props.setProperty(base + SUFFIX_DURATION, Long.toString(meta.durationMs));
            props.setProperty(base + SUFFIX_UPLOADED, Boolean.toString(meta.uploaded));
        }
        File target = new File(dir, INDEX_FILE);
        File tmp = new File(dir, INDEX_FILE + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            props.store(out, null);
        } catch (IOException ignored) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(target);
        }
    }

    private void load() {
        File file = new File(dir, INDEX_FILE);
        if (!file.isFile()) {
            return;
        }
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            props.load(in);
        } catch (IOException ignored) {
            return;
        }
        lastSignature = props.getProperty(PROP_SIGNATURE, "");
        lastFullUploadMs = parseLong(props.getProperty(PROP_FULL_UPLOAD_MS), 0L);
        for (String propName : props.stringPropertyNames()) {
            if (!propName.startsWith(PREFIX_ENTRY) || !propName.endsWith(SUFFIX_KEY)) {
                continue;
            }
            String hash = propName.substring(
                    PREFIX_ENTRY.length(), propName.length() - SUFFIX_KEY.length());
            if (hash.isEmpty()) {
                continue;
            }
            String key = props.getProperty(propName, "");
            if (key.isEmpty()) {
                continue;
            }
            String base = PREFIX_ENTRY + hash;
            long durationMs = parseLong(props.getProperty(base + SUFFIX_DURATION), 0L);
            boolean uploaded = Boolean.parseBoolean(props.getProperty(base + SUFFIX_UPLOADED));
            entries.put(key, new Meta(key, hash, durationMs, uploaded));
        }
    }

    private byte[] readJpeg(String hash) {
        File file = new File(dir, hash + ".jpg");
        if (!file.isFile() || file.length() <= 0L || file.length() > 512 * 1024L) {
            return null;
        }
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream((int) file.length())) {
            byte[] buf = new byte[4096];
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
            return out.toByteArray();
        } catch (IOException ignored) {
            return null;
        }
    }

    private void writeJpeg(String hash, byte[] jpeg) {
        if (!dir.exists() && !dir.mkdirs()) {
            return;
        }
        File target = new File(dir, hash + ".jpg");
        File tmp = new File(dir, hash + ".jpg.tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(jpeg);
        } catch (IOException ignored) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(target);
        }
    }

    private void deleteJpeg(String hash) {
        File file = new File(dir, hash + ".jpg");
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static long parseLong(String raw, long fallback) {
        if (raw == null || raw.isEmpty()) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception error) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
