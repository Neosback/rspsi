package com.rspsi.editor;

import com.rspsi.editor.model.DirtyRegion;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DirtyRegionTest {

    @Test
    void coordinatesAreGroupedByEightByEightChunk() {
        DirtyRegion region = DirtyRegion.forTile(new TileCoordinate(3, 15, 16));

        assertEquals(1, region.chunkX());
        assertEquals(2, region.chunkY());
        assertTrue(region.terrain());
        assertTrue(region.collision());
    }

    @Test
    void sessionMergesAndDrainsAffectedChunks() {
        WorldDocument document = new WorldDocument(16, 16);
        EditorSession session = new EditorSession(document);
        session.execute(new SetTileCommand(new TileCoordinate(0, 1, 1),
                document.tile(0, 1, 1).snapshot(),
                new com.rspsi.editor.model.TileSnapshot(0, 0, 0, 0, 7, 0, 0, 0, 0, java.util.List.of()),
                "test edit"));
        session.execute(new SetTileCommand(new TileCoordinate(0, 7, 7),
                document.tile(0, 7, 7).snapshot(),
                new com.rspsi.editor.model.TileSnapshot(0, 0, 0, 0, 8, 0, 0, 0, 0, java.util.List.of()),
                "same chunk"));

        assertEquals(1, session.dirtyRegions().size());
        assertEquals(1, session.drainDirtyRegions().size());
        assertTrue(session.dirtyRegions().isEmpty());
    }
}
