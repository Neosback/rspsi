package com.rspsi.cache.map;

import com.openrune.studio.core.osrs.map.OsrsRegionData;
import com.rspsi.editor.model.TerrainHeightSource;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldRegion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreOsrsRegionAdapterTest {
    @Test
    void roundTripPreservesLegacyDocumentSemanticsAndCacheHeightProvenance() {
        WorldDocument source = new WorldDocument(64, 64, 4);
        source.tile(0, 4, 5).restore(new TileSnapshot(
                -40, -48, -56, -48,
                7, 23, 11, 1, 6,
                List.of(new WorldObject(100, 10, 2, 0, 4, 5)),
                TerrainHeightSource.explicitSource(5)),
                TerrainHeightSource.explicitSource(5));

        OsrsRegionData core = CoreOsrsRegionAdapter.toCore(source, 50, 51);
        WorldRegion restored = CoreOsrsRegionAdapter.toClient(core);

        assertEquals(50, restored.regionX());
        assertEquals(51, restored.regionY());
        assertEquals(source.tile(0, 4, 5).snapshot(), restored.document().tile(0, 4, 5).snapshot());
        assertTrue(restored.document().tile(0, 4, 5).heightSource().cacheEncoded());
        assertEquals(5, restored.document().tile(0, 4, 5).heightSource().explicitValue());
    }
}
