package com.rspsi.cache.map;

import com.rspsi.cache.store.CacheStore;
import com.rspsi.cache.CacheStoreCapabilities;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MapIndexTableTest {

    @Test
    void discoversNamedOsrsMapArchivesThroughNeutralStore() {
        FakeStore store = new FakeStore();
        store.archiveIds.put("5:m50_75", 1234);
        store.archiveIds.put("5:l50_75", 5678);

        MapIndexTable table = MapIndexTable.discover(store, 5);

        assertEquals(1, table.size());
        assertEquals(1234, table.archiveId(50, 75, MapArchiveType.LANDSCAPE));
        assertEquals(5678, table.archiveId(50, 75, MapArchiveType.OBJECT));
        assertTrue(table.present(1234, MapArchiveType.LANDSCAPE));
        assertTrue(table.present(5678, MapArchiveType.OBJECT));
    }

    @Test
    void discoversNumericModernMapGroupsWhenNamesAreUnavailable() {
        FakeStore store = new FakeStore();
        store.numericArchiveIds = new int[]{(50 << 8) | 75};

        MapIndexTable table = MapIndexTable.discover(store, 5);

        assertEquals(1, table.size());
        assertEquals((50 << 8) | 75, table.archiveId(50, 75, MapArchiveType.LANDSCAPE));
        assertEquals((50 << 8) | 75, table.archiveId(50, 75, MapArchiveType.OBJECT));
    }

    @Test
    void knownModernRevisionUsesNumericGroups() {
        FakeStore store = new FakeStore();
        store.numericArchiveIds = new int[]{(50 << 8) | 75};

        MapIndexTable table = MapIndexTable.discover(store, 5, OsrsRevisionProfile.forRevision(240));

        assertEquals(1, table.size());
        assertEquals((50 << 8) | 75, table.archiveId(50, 75, MapArchiveType.LANDSCAPE));
    }

    @Test
    void knownLegacyRevisionUsesNamedGroups() {
        FakeStore store = new FakeStore();
        store.archiveIds.put("5:m50_75", 1234);

        MapIndexTable table = MapIndexTable.discover(store, 5, OsrsRevisionProfile.forRevision(236));

        assertEquals(1, table.size());
        assertEquals(1234, table.archiveId(50, 75, MapArchiveType.LANDSCAPE));
    }

    @Test
    void entriesAreReturnedInStableWorldOrder() {
        MapIndexTable table = MapIndexTable.of(java.util.List.of(
                entry(12, 2), entry(1, 200), entry(12, 1)));

        assertEquals(java.util.List.of(1, 12, 12),
                table.entries().stream().map(MapIndexEntry::regionX).toList());
        assertEquals(java.util.List.of(200, 1, 2),
                table.entries().stream().map(MapIndexEntry::regionY).toList());
    }

    @Test
    void osrsMapServiceReadsBothMapFamilies() {
        FakeStore store = new FakeStore();
        store.values.put("5:1234:0", new byte[]{1});
        store.values.put("5:5678:1", new byte[]{2});
        MapIndexEntry entry = new MapIndexEntry(50, 75, 1234, 5678, "m50_75", "l50_75");

        OsrsMapService service = new OsrsMapService(store, 5, MapIndexTable.of(java.util.List.of(entry)));

        assertArrayEquals(new byte[]{1}, service.readLandscape(50, 75));
        assertArrayEquals(new byte[]{2}, service.readLocations(50, 75));
        assertArrayEquals(new byte[]{2}, service.readObjects(50, 75));
        assertNull(service.readLandscape(51, 75));
    }

    @Test
    void writesExistingRegionsThroughWritableNeutralStore() {
        FakeStore store = new FakeStore(true);
        MapIndexEntry entry = new MapIndexEntry(50, 75, 1234, 5678, "m50_75", "l50_75");
        OsrsMapService service = new OsrsMapService(store, 5, MapIndexTable.of(java.util.List.of(entry)));

        service.writeLandscape(50, 75, new byte[]{3});
        service.writeLocations(50, 75, new byte[]{4});

        assertArrayEquals(new byte[]{3}, store.values.get("5:1234:0"));
        assertArrayEquals(new byte[]{4}, store.values.get("5:5678:1"));
    }

    private static MapIndexEntry entry(int x, int y) {
        return new MapIndexEntry(x, y, x * 100 + y, x * 100 + y + 1,
                "m" + x + "_" + y, "l" + x + "_" + y);
    }

    private static final class FakeStore implements CacheStore {
        private final Map<String, Integer> archiveIds = new HashMap<>();
        private final Map<String, byte[]> values = new HashMap<>();
        private final boolean writable;
        private int[] numericArchiveIds = new int[0];

        private FakeStore() {
            this(false);
        }

        private FakeStore(boolean writable) {
            this.writable = writable;
        }

        @Override public byte[] read(int index, int archive, int file) {
            return values.get(index + ":" + archive + ":" + file);
        }

        @Override public int archiveId(int index, String archiveName) {
            return archiveIds.getOrDefault(index + ":" + archiveName, -1);
        }
        @Override public int[] archiveIds(int index) { return numericArchiveIds.clone(); }

        @Override public void write(int index, int archive, int file, byte[] data) {
            values.put(index + ":" + archive + ":" + file, data.clone());
        }
        @Override public void flush() { }
        @Override public CacheStoreCapabilities capabilities() {
            return new CacheStoreCapabilities(writable, true, false);
        }
    }
}
