package com.eyups.roaddeck.cloud;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.eyups.roaddeck.dvr.DvrClipStore;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DvrIndexCacheTest {

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void persistsSignatureAndThumbnailsAcrossReboots() throws Exception {
        File cacheDir = tempFolder.newFolder("dvr_index_cache");
        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, 1, 2, 3, (byte) 0xFF, (byte) 0xD9};
        String key = "2026-11-09_12-00-00_0001:102400:1700000000000";

        DvrIndexCache firstBoot = new DvrIndexCache(cacheDir);
        firstBoot.put(key, 60_000L, jpeg, false);
        firstBoot.markUploaded(Collections.singletonList(key));
        firstBoot.setLastSignature("sig-v1");
        firstBoot.markFullUpload(1_000_000L);
        firstBoot.save();

        DvrIndexCache secondBoot = new DvrIndexCache(cacheDir);
        assertEquals("sig-v1", secondBoot.getLastSignature());
        assertFalse(secondBoot.isFullUploadDue(1_000_000L + 60_000L));
        assertTrue(secondBoot.isFullUploadDue(
                1_000_000L + DvrIndexCache.FULL_REFRESH_INTERVAL_MS));

        DvrIndexCache.Entry restored = secondBoot.get(key);
        assertNotNull(restored);
        assertEquals(60_000L, restored.durationMs);
        assertTrue(restored.uploaded);
        assertArrayEquals(jpeg, restored.jpeg);
    }

    @Test
    public void retainOnlyRemovesPrunedEntriesAndJpegFiles() throws Exception {
        File cacheDir = tempFolder.newFolder("dvr_index_cache");
        DvrIndexCache cache = new DvrIndexCache(cacheDir);
        String keepKey = "clip_keep:100:1";
        String dropKey = "clip_drop:200:2";
        cache.put(keepKey, 10_000L, new byte[] {1, 2, 3}, true);
        cache.put(dropKey, 20_000L, new byte[] {4, 5, 6}, true);
        cache.save();

        File droppedJpeg = new File(cacheDir, DvrIndexCache.sha256Hex(dropKey) + ".jpg");
        assertTrue(droppedJpeg.isFile());

        Set<String> active = new HashSet<>(Collections.singletonList(keepKey));
        cache.retainOnly(active);
        cache.save();

        assertFalse(droppedJpeg.exists());
        assertNull(cache.get(dropKey));
        assertNotNull(cache.get(keepKey));
    }

    @Test
    public void filterCompletedClipsExcludesActiveRecordingBases() throws Exception {
        File clipDir = tempFolder.newFolder("clips");
        assertTrue(new File(clipDir, "s_2026-11-09_12-00-00.mp4").createNewFile());
        assertTrue(new File(clipDir, "s_2026-11-09_12-01-00.mp4").createNewFile());

        List<DvrClipStore.Clip> all = DvrClipStore.list(clipDir, Collections.emptySet());
        assertEquals(2, all.size());

        List<DvrClipStore.Clip> filtered = DvrClipIndexer.filterCompletedClips(
                all,
                Collections.singleton("2026-11-09_12-01-00"));

        assertEquals(1, filtered.size());
        assertEquals("2026-11-09_12-00-00", filtered.get(0).base);
    }
}
