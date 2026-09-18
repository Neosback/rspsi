package com.rspsi.compat.osrs;

import com.jagex.cache.loader.map.MapIndexLoader;
import com.jagex.cache.loader.map.MapType;
import com.rspsi.cache.CacheStoreCapabilities;
import com.rspsi.cache.store.CacheStore;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OsrsMapIndexLoaderTest {

    @Test
    void loadsNamedArchivesAndExposesLegacyCompatibilityLookups() {
        FakeStore store = new FakeStore();
        store.archiveIds.put("5:m128_50", 9001);
        store.archiveIds.put("5:l128_50", 9002);
        OsrsMapIndexLoader loader = new OsrsMapIndexLoader();

        loader.load(store, 5);

        assertEquals(9001, loader.getFileId((128 << 8) | 50, MapType.LANDSCAPE));
        assertEquals(9002, loader.getFileId((128 << 8) | 50, MapType.OBJECT));
        assertEquals("m128_50", loader.getFileName((128 << 8) | 50, MapType.LANDSCAPE));
        assertTrue(loader.landscapePresent(9001));
        assertTrue(loader.objectPresent(9002));
    }

    @Test
    void compatibilityEncodingCanBeDecodedWithoutCacheTypes() {
        OsrsMapIndexLoader original = new OsrsMapIndexLoader();
        original.set(10, 20, 300, 301);
        original.set(10, 21, 302, 303);

        OsrsMapIndexLoader decoded = new OsrsMapIndexLoader();
        decoded.init(new com.jagex.io.Buffer(original.encode()));

        assertEquals(300, decoded.getFileId((10 << 8) | 20, MapType.LANDSCAPE));
        assertEquals(303, decoded.getFileId((10 << 8) | 21, MapType.OBJECT));
        assertEquals(14, decoded.encode().length); // count (2) + two 6-byte entries
    }

    private static final class FakeStore implements CacheStore {
        private final Map<String, Integer> archiveIds = new HashMap<>();

        @Override public byte[] read(int index, int archive, int file) { return null; }
        @Override public int archiveId(int index, String archiveName) {
            return archiveIds.getOrDefault(index + ":" + archiveName, -1);
        }
        @Override public void write(int index, int archive, int file, byte[] data) { }
        @Override public void flush() { }
        @Override public CacheStoreCapabilities capabilities() {
            return new CacheStoreCapabilities(false, true, false);
        }
    }
}
