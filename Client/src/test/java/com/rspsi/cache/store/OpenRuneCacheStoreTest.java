package com.rspsi.cache.store;

import dev.openrune.filesystem.Cache;
import dev.openrune.filesystem.Compression;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OpenRuneCacheStoreTest {

    @Test
    void readsThroughNeutralByteBoundary() {
        FakeCache cache = new FakeCache();
        cache.values.put("5:123:0", new byte[]{1, 2, 3});

        try (CacheStore store = new OpenRuneCacheStore(cache)) {
            assertArrayEquals(new byte[]{1, 2, 3}, store.read(5, 123, 0));
            assertNull(store.read(5, 999, 0));
        }
        assertTrue(cache.closed);
    }

    @Test
    void refusesWritesUntilWritablePackingIsValidated() {
        CacheStore store = new OpenRuneCacheStore(new FakeCache());
        UnsupportedOperationException error = assertThrows(UnsupportedOperationException.class,
                () -> store.write(5, 1, 0, new byte[]{7}));
        assertTrue(error.getMessage().contains("read-only"));
    }

    @Test
    void reportsReadOnlyCapabilitiesWithoutExposingOpenRuneTypes() {
        try (CacheStore store = new OpenRuneCacheStore(new FakeCache())) {
            assertFalse(store.capabilities().writable());
            assertTrue(store.capabilities().namedArchives());
            assertFalse(store.capabilities().mapPacking());
        }
    }

    private static final class FakeCache implements Cache {
        private final Map<String, byte[]> values = new HashMap<>();
        private boolean closed;

        @Override public byte[] getVersionTable() { return new byte[0]; }
        @Override public int indexCount() { return 0; }
        @Override public boolean exists(int id) { return false; }
        @Override public void createIndex(Compression c, int v, int r, boolean n, boolean w, boolean l, boolean ch, boolean wr, int id) { }
        @Override public int[] indices() { return new int[0]; }
        @Override public byte[] sector(int i, int a) { return null; }
        @Override public int[] archives(int i) { return new int[0]; }
        @Override public int archiveCount(int i) { return 0; }
        @Override public int lastArchiveId(int i) { return -1; }
        @Override public int archiveId(int i, int h) { return -1; }
        @Override public int archiveId(int i, String n) { return -1; }
        @Override public int[] files(int i, int a) { return new int[0]; }
        @Override public int fileCount(int i, int a) { return 0; }
        @Override public int lastFileId(int i, int a) { return -1; }
        @Override public byte[][] fileData(int i, int a) { return null; }
        @Override public byte[][] fileData(int i, int a, int[] x) { return null; }
        @Override public byte[] data(int i, int a, int f, int[] x) { return values.get(i + ":" + a + ":" + f); }
        @Override public byte[] data(int i, String a, int[] x) { return null; }
        @Override public int crc(int i) { return 0; }
        @Override public int crc(int i, int a) { return 0; }
        @Override public void write(int i, int a, int f, byte[] d, int[] x) { }
        @Override public void write(int i, int a, byte[] d, int[] x) { }
        @Override public void write(int i, String a, byte[] d, int[] x) { }
        @Override public void remove(int i, int a, int f) { }
        @Override public void remove(int i, int a) { }
        @Override public boolean update() { return false; }
        @Override public void close() { closed = true; }
    }
}
