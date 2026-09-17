package com.rspsi.cache.store;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheIndexViewTest {
    @Test
    void exposesArchiveAndFileIdsWithoutCacheLibraryTypes() {
        CacheStore store = new CacheStore() {
            @Override public byte[] read(int index, int archive, int file) {
                return index == 2 && archive == 6 && file == 42 ? new byte[]{4, 5} : null;
            }
            @Override public int[] archiveIds(int index) { return new int[]{1, 6}; }
            @Override public int[] fileIds(int index, int archive) {
                return Map.of(6, new int[]{42, 43}).getOrDefault(archive, new int[0]);
            }
            @Override public void write(int index, int archive, int file, byte[] data) { }
            @Override public void flush() { }
        };

        CacheIndexView index = new CacheIndexView(store, 2);
        CacheArchiveView archive = index.archive(6);

        assertArrayEquals(new int[]{1, 6}, index.archiveIds());
        assertArrayEquals(new int[]{42, 43}, archive.fileIds());
        assertArrayEquals(new byte[]{4, 5}, archive.file(42));
        assertTrue(archive.containsData());
    }
}
