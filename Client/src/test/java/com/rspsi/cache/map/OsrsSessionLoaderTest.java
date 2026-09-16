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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsrsSessionLoaderTest {
    @Test
    void createsCleanSaveCapableSessionAndPersistsThroughCoordinator() {
        WorldDocument source = new WorldDocument(64, 64, 4);
        byte[] terrain = OsrsRegionEncoder.encodeTerrain(source, true);
        byte[] locations = OsrsRegionEncoder.encodeLocations(source);
        RecordingStore store = new RecordingStore(terrain, locations);
        OsrsMapService maps = new OsrsMapService(store, 5, MapIndexTable.of(List.of(
                new MapIndexEntry(50, 50, 100, 100, "m50_50", "l50_50"))));

        OsrsSessionLoader.LoadedRegion loaded = new OsrsSessionLoader(maps).load(50, 50);
        EditorSession session = loaded.session();
        assertTrue(session.canSave());
        assertFalse(session.isDirty());

        WorldDocument document = session.world();
        TileSnapshot before = document.tile(0, 1, 1).snapshot();
        session.execute(new SetTileCommand(new TileCoordinate(0, 1, 1), before,
                new TileSnapshot(0, 0, 0, 0, 7, 0, 0, 0, 0, List.of()), "paint"));
        assertTrue(session.isDirty());

        session.save();

        assertFalse(session.isDirty());
        assertTrue(store.values.containsKey("5:100:0"));
        assertTrue(store.values.containsKey("5:100:1"));
        assertTrue(store.flushes > 0);
    }

    private static final class RecordingStore implements CacheStore {
        private final Map<String, byte[]> values = new HashMap<>();
        private int flushes;

        private RecordingStore(byte[] terrain, byte[] locations) {
            values.put("5:100:0", terrain);
            values.put("5:100:1", locations);
        }

        @Override public byte[] read(int index, int archive, int file) {
            return values.get(index + ":" + archive + ":" + file);
        }

        @Override public void write(int index, int archive, int file, byte[] data) {
            values.put(index + ":" + archive + ":" + file, data.clone());
        }

        @Override public void flush() {
            flushes++;
        }

        @Override public CacheStoreCapabilities capabilities() {
            return new CacheStoreCapabilities(true, true, true);
        }
    }
}
