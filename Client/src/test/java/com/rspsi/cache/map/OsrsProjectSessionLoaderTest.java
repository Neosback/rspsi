package com.rspsi.cache.map;

import com.rspsi.cache.CacheStoreCapabilities;
import com.rspsi.cache.CacheWriteMode;
import com.rspsi.cache.OsrsCacheMetadata;
import com.rspsi.cache.store.CacheStore;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.project.ProjectMetadata;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsrsProjectSessionLoaderTest {
    @Test
    void matchingIdentityProducesSaveCapableSession() {
        OsrsCacheMetadata identity = new OsrsCacheMetadata(240, 2, "cache-a");
        RecordingStore store = new RecordingStore(identity);
        OsrsMapService maps = maps(store);
        ProjectMetadata project = ProjectMetadata.forCache(identity);

        OsrsProjectSessionLoader.OpenedProject opened =
                new OsrsProjectSessionLoader(store, maps, project).load(50, 50);

        assertFalse(opened.readOnly());
        assertTrue(opened.region().session().canSave());
        assertEquals(CacheWriteMode.DIRECT, opened.writeMode());
    }

    @Test
    void mismatchedIdentityProducesInspectableReadOnlySession() {
        OsrsCacheMetadata projectIdentity = new OsrsCacheMetadata(240, 2, "cache-a");
        RecordingStore store = new RecordingStore(new OsrsCacheMetadata(240, 2, "cache-b"));
        OsrsMapService maps = maps(store);

        OsrsProjectSessionLoader.OpenedProject opened =
                new OsrsProjectSessionLoader(store, maps, ProjectMetadata.forCache(projectIdentity))
                        .load(50, 50);

        assertTrue(opened.readOnly());
        assertFalse(opened.region().session().canSave());
        assertFalse(opened.region().session().canEdit());
        assertEquals(CacheWriteMode.READ_ONLY, opened.writeMode());
        assertTrue(opened.compatibility().issues().contains("cache fingerprint differs"));
    }

    @Test
    void unavailableIdentityFailsClosedToReadOnly() {
        RecordingStore store = new RecordingStore(null);
        OsrsMapService maps = maps(store);

        OsrsProjectSessionLoader.OpenedProject opened =
                new OsrsProjectSessionLoader(store, maps,
                        ProjectMetadata.forCache(new OsrsCacheMetadata(240, 2, "cache-a")))
                        .load(50, 50);

        assertTrue(opened.readOnly());
        assertFalse(opened.region().session().canSave());
        assertFalse(opened.region().session().canEdit());
        assertThrows(UnsupportedOperationException.class,
                () -> opened.region().session().execute(
                        new com.rspsi.editor.SetTileCommand(
                                new com.rspsi.editor.model.TileCoordinate(0, 0, 0),
                                opened.region().session().world().tile(0, 0, 0).snapshot(),
                                opened.region().session().world().tile(0, 0, 0).snapshot(),
                "read-only edit")));
    }

    @Test
    void matchingProjectOnReadOnlyBackendIsInspectableOnly() {
        OsrsCacheMetadata identity = new OsrsCacheMetadata(240, 2, "cache-a");
        RecordingStore store = new RecordingStore(identity, false);
        OsrsMapService maps = maps(store);

        OsrsProjectSessionLoader.OpenedProject opened =
                new OsrsProjectSessionLoader(store, maps, ProjectMetadata.forCache(identity))
                        .load(50, 50);

        assertTrue(opened.readOnly());
        assertFalse(opened.region().session().canSave());
        assertFalse(opened.region().session().canEdit());
        assertTrue(opened.compatibility().issues().contains("cache backend is read-only"));
    }

    @Test
    void matchingIdentityProducesEditableMultiRegionWindow() {
        OsrsCacheMetadata identity = new OsrsCacheMetadata(240, 2, "cache-a");
        RecordingStore store = new RecordingStore(identity);
        OsrsMapService maps = maps(store);

        OsrsProjectSessionLoader.OpenedWorldWindow opened =
                new OsrsProjectSessionLoader(store, maps, ProjectMetadata.forCache(identity))
                        .loadWindow(50, 50, 2, 1);

        assertFalse(opened.readOnly());
        assertTrue(opened.window().sessions().canSave());
        assertEquals(2, opened.window().sessions().sessions().size());
        assertTrue(opened.window().window().complete());
    }

    @Test
    void mismatchedIdentityMakesEntireMultiRegionWindowReadOnly() {
        OsrsCacheMetadata projectIdentity = new OsrsCacheMetadata(240, 2, "cache-a");
        RecordingStore store = new RecordingStore(
                new OsrsCacheMetadata(240, 2, "cache-b"));
        OsrsMapService maps = maps(store);

        OsrsProjectSessionLoader.OpenedWorldWindow opened =
                new OsrsProjectSessionLoader(store, maps,
                        ProjectMetadata.forCache(projectIdentity))
                        .loadWindow(50, 50, 2, 1);

        assertTrue(opened.readOnly());
        assertFalse(opened.window().sessions().canSave());
        assertTrue(opened.window().sessions().sessions().values().stream()
                .noneMatch(com.rspsi.editor.EditorSession::canEdit));
    }

    private static OsrsMapService maps(RecordingStore store) {
        return new OsrsMapService(store, 5, MapIndexTable.of(List.of(
                new MapIndexEntry(50, 50, 100, 100, "m50_50", "l50_50"),
                new MapIndexEntry(51, 50, 101, 101, "m51_50", "l51_50"))));
    }

    private static final class RecordingStore implements CacheStore {
        private final Map<String, byte[]> values = new HashMap<>();
        private final OsrsCacheMetadata identity;
        private final boolean writable;

        private RecordingStore(OsrsCacheMetadata identity) {
            this(identity, true);
        }

        private RecordingStore(OsrsCacheMetadata identity, boolean writable) {
            this.identity = identity;
            this.writable = writable;
            WorldDocument source = new WorldDocument(64, 64, 4);
            WorldDocument neighbor = new WorldDocument(64, 64, 4);
            values.put("5:100:0", OsrsRegionEncoder.encodeTerrain(source, true));
            values.put("5:100:1", OsrsRegionEncoder.encodeLocations(source));
            values.put("5:101:0", OsrsRegionEncoder.encodeTerrain(neighbor, true));
            values.put("5:101:1", OsrsRegionEncoder.encodeLocations(neighbor));
        }

        @Override public byte[] read(int index, int archive, int file) {
            return values.get(index + ":" + archive + ":" + file);
        }

        @Override public void write(int index, int archive, int file, byte[] data) {
            values.put(index + ":" + archive + ":" + file, data.clone());
        }

        @Override public void flush() { }

        @Override public CacheStoreCapabilities capabilities() {
            return new CacheStoreCapabilities(writable, true, true);
        }

        @Override public Optional<OsrsCacheMetadata> metadata(int revision) {
            return Optional.ofNullable(identity);
        }
    }
}
