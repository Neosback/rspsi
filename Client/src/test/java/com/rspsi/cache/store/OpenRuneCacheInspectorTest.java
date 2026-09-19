package com.rspsi.cache.store;

import com.rspsi.cache.workspace.CacheDecoderSummary;
import dev.openrune.filesystem.Cache;
import dev.openrune.filesystem.Compression;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static dev.openrune.cache.ArchiveIndexKt.*;
import static org.junit.jupiter.api.Assertions.*;

class OpenRuneCacheInspectorTest {

    @Test
    void inspectsIndicesAndCalculatesSummary() {
        MockCache cache = new MockCache();
        cache.indices = new int[]{ANIMATIONS, SOUNDEFFECTS, MAPS, MUSIC_TRACKS, MODELS, SPRITES};
        cache.archivesPerIndex.put(ANIMATIONS, new int[]{1, 2, 3});
        cache.archivesPerIndex.put(SOUNDEFFECTS, new int[]{10, 20});
        cache.archivesPerIndex.put(MAPS, new int[]{100, 101, 102, 103});
        cache.archivesPerIndex.put(MUSIC_TRACKS, new int[]{5});
        cache.archivesPerIndex.put(MODELS, new int[]{1000, 1001, 1002});
        cache.archivesPerIndex.put(SPRITES, new int[]{50});

        CacheDecoderSummary summary = OpenRuneCacheInspector.inspect(
                cache, 240, "Test FileStore", null);

        assertNotNull(summary);
        assertEquals(240, summary.revision());
        assertEquals("Test FileStore", summary.backendName());
        assertEquals(6, summary.totalIndices());
        assertEquals(14, summary.totalArchives()); // 3 + 2 + 4 + 1 + 3 + 1 = 14
        assertEquals(2, summary.soundEffects());
        assertEquals(4, summary.maps());
        assertEquals(1, summary.musicTracks());
        assertEquals(3, summary.models());
        assertTrue(summary.allDecodersPassed());
        assertEquals(0, summary.failures().size());
        assertEquals(6, summary.indices().size());
    }

    private static final class MockCache implements Cache {
        int[] indices = new int[0];
        final Map<Integer, int[]> archivesPerIndex = new HashMap<>();

        @Override public byte[] getVersionTable() { return new byte[0]; }
        @Override public int indexCount() { return indices.length; }
        @Override public boolean exists(int id) { return false; }
        @Override public void createIndex(Compression c, int v, int r, boolean n, boolean w, boolean l, boolean ch, boolean wr, int id) { }
        @Override public int[] indices() { return indices; }
        @Override public byte[] sector(int i, int a) { return null; }
        @Override public int[] archives(int i) { return archivesPerIndex.getOrDefault(i, new int[0]); }
        @Override public int archiveCount(int i) { return archives(i).length; }
        @Override public int lastArchiveId(int i) { return -1; }
        @Override public int archiveId(int i, int h) { return -1; }
        @Override public int archiveId(int i, String n) { return -1; }
        @Override public int[] files(int i, int a) { return new int[0]; }
        @Override public int fileCount(int i, int a) { return 0; }
        @Override public int lastFileId(int i, int a) { return -1; }
        @Override public byte[][] fileData(int i, int a) { return null; }
        @Override public byte[][] fileData(int i, int a, int[] x) { return null; }
        @Override public byte[] data(int i, int a, int f, int[] x) { return null; }
        @Override public byte[] data(int i, String a, int[] x) { return null; }
        @Override public int crc(int i) { return 0; }
        @Override public int crc(int i, int a) { return 0; }
        @Override public void write(int i, int a, int f, byte[] d, int[] x) { }
        @Override public void write(int i, int a, byte[] d, int[] x) { }
        @Override public void write(int i, String a, byte[] d, int[] x) { }
        @Override public void remove(int i, int a, int f) { }
        @Override public void remove(int i, int a) { }
        @Override public boolean update() { return false; }
        @Override public void close() { }
    }
}
