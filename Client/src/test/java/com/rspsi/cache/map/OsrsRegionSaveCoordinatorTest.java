package com.rspsi.cache.map;

import com.rspsi.cache.CacheStoreCapabilities;
import com.rspsi.cache.store.CacheStore;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.SetTileCommand;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OsrsRegionSaveCoordinatorTest {
    @Test
    void writesBothPayloadsAndMarksSessionSavedAfterFlush() {
        RecordingStore store = new RecordingStore();
        OsrsMapService maps = new OsrsMapService(store, 5, MapIndexTable.of(List.of(
                new MapIndexEntry(50, 50, 100, 101, "m50_50", "l50_50"))));
        WorldDocument document = new WorldDocument(64, 64, 4);
        EditorSession session = new EditorSession(document);
        session.execute(new SetTileCommand(new TileCoordinate(0, 1, 1),
                document.tile(0, 1, 1).snapshot(),
                new TileSnapshot(0, 0, 0, 0, 7, 0, 0, 0, 0, List.of()), "paint"));
        assertTrue(session.isDirty());

        OsrsRegionSaveCoordinator.SaveResult result =
                new OsrsRegionSaveCoordinator(maps).save(session, 50, 50);

        assertFalse(session.isDirty());
        assertEquals(1, store.flushes);
        assertTrue(result.landscapeBytes() > 0);
        assertTrue(result.locationBytes() > 0);
        assertNotNull(store.values.get("5:100:0"));
        assertNotNull(store.values.get("5:101:0"));
    }

    @Test
    void encodesAndFlushesMultipleRegionsBeforeMarkingEitherSaved() {
        RecordingStore store = new RecordingStore();
        store.archiveIds.put("5:m50_51", 102);
        store.archiveIds.put("5:l50_51", 103);
        OsrsMapService maps = new OsrsMapService(store, 5, MapIndexTable.of(List.of(
                new MapIndexEntry(50, 50, 100, 101, "m50_50", "l50_50"),
                new MapIndexEntry(50, 51, 102, 103, "m50_51", "l50_51"))));
        EditorSession first = dirtySession();
        EditorSession second = dirtySession();

        OsrsRegionSaveCoordinator.BatchSaveResult result =
                new OsrsRegionSaveCoordinator(maps).saveAll(List.of(
                        new OsrsRegionSaveCoordinator.RegionSaveRequest(first, 50, 50),
                        new OsrsRegionSaveCoordinator.RegionSaveRequest(second, 50, 51)));

        assertEquals(2, result.regions().size());
        assertFalse(first.isDirty());
        assertFalse(second.isDirty());
        assertEquals(1, store.flushes);
        assertNotNull(store.values.get("5:102:0"));
        assertNotNull(store.values.get("5:103:0"));
    }

    private static EditorSession dirtySession() {
        WorldDocument document = new WorldDocument(64, 64, 4);
        EditorSession session = new EditorSession(document);
        session.execute(new SetTileCommand(new TileCoordinate(0, 1, 1),
                document.tile(0, 1, 1).snapshot(),
                new TileSnapshot(0, 0, 0, 0, 7, 0, 0, 0, 0, List.of()), "paint"));
        return session;
    }

    @Test
    void failedWriteDoesNotMarkSessionSaved() {
        FailingMapService maps = new FailingMapService();
        WorldDocument document = new WorldDocument(64, 64, 4);
        EditorSession session = new EditorSession(document);
        session.execute(new SetTileCommand(new TileCoordinate(0, 1, 1),
                document.tile(0, 1, 1).snapshot(),
                new TileSnapshot(0, 0, 0, 0, 7, 0, 0, 0, 0, List.of()), "paint"));

        assertThrows(UnsupportedOperationException.class,
                () -> new OsrsRegionSaveCoordinator(maps).save(session, 50, 50));
        assertTrue(session.isDirty());
    }

    @Test
    void failedFlushDoesNotMarkSessionSaved() {
        FlushingFailureStore store = new FlushingFailureStore();
        OsrsMapService maps = new OsrsMapService(store, 5, MapIndexTable.of(List.of(
                new MapIndexEntry(50, 50, 100, 101, "m50_50", "l50_50"))));
        WorldDocument document = new WorldDocument(64, 64, 4);
        EditorSession session = new EditorSession(document);
        session.execute(new SetTileCommand(new TileCoordinate(0, 1, 1),
                document.tile(0, 1, 1).snapshot(),
                new TileSnapshot(0, 0, 0, 0, 7, 0, 0, 0, 0, List.of()), "paint"));

        assertThrows(IllegalStateException.class,
                () -> new OsrsRegionSaveCoordinator(maps).save(session, 50, 50));
        assertTrue(session.isDirty());
        assertEquals(0, session.savedHistoryPosition());
    }

    @Test
    void readOnlySessionCannotWriteARegion() {
        RecordingStore store = new RecordingStore();
        OsrsMapService maps = new OsrsMapService(store, 5, MapIndexTable.of(List.of(
                new MapIndexEntry(50, 50, 100, 101, "m50_50", "l50_50"))));
        EditorSession session = EditorSession.readOnly(new WorldDocument(64, 64, 4));

        assertThrows(IllegalStateException.class,
                () -> new OsrsRegionSaveCoordinator(maps).save(session, 50, 50));
        assertTrue(store.values.isEmpty());
        assertEquals(0, store.flushes);
    }

    private static class RecordingStore implements CacheStore {
        private final Map<String, Integer> archiveIds = new HashMap<>();
        private final Map<String, byte[]> values = new HashMap<>();
        private int flushes;

        private RecordingStore() {
            archiveIds.put("5:m50_50", 100);
            archiveIds.put("5:l50_50", 101);
        }

        @Override public byte[] read(int index, int archive, int file) {
            return values.get(index + ":" + archive + ":" + file);
        }
        @Override public int archiveId(int index, String name) {
            return archiveIds.getOrDefault(index + ":" + name, -1);
        }
        @Override public void write(int index, int archive, int file, byte[] data) {
            values.put(index + ":" + archive + ":" + file, data.clone());
        }
        @Override public void flush() { flushes++; }
        @Override public CacheStoreCapabilities capabilities() {
            return new CacheStoreCapabilities(true, true, true);
        }
    }

    private static final class FailingMapService implements MapService {
        @Override public MapIndexTable index() { return MapIndexTable.of(List.of()); }
        @Override public byte[] readLandscape(int regionX, int regionY) { return null; }
        @Override public byte[] readLocations(int regionX, int regionY) { return null; }
        @Override public void writeLandscape(int regionX, int regionY, byte[] data) {
            throw new UnsupportedOperationException("read-only");
        }
        @Override public void writeLocations(int regionX, int regionY, byte[] data) { }
    }

    private static final class FlushingFailureStore extends RecordingStore {
        @Override public void flush() {
            throw new IllegalStateException("flush failed");
        }
    }
}
