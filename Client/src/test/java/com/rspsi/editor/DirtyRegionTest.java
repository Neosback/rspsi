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

        assertEquals(3, region.plane());
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
        session.execute(new SetTileCommand(new TileCoordinate(0, 6, 6),
                document.tile(0, 6, 6).snapshot(),
                new com.rspsi.editor.model.TileSnapshot(0, 0, 0, 0, 8, 0, 0, 0, 0, java.util.List.of()),
                "same chunk"));

        assertEquals(1, session.dirtyRegions().size());
        assertEquals(1, session.drainDirtyRegions().size());
        assertTrue(session.dirtyRegions().isEmpty());
    }

    @Test
    void edgeEditsInvalidateAdjacentChunksForSharedGeometry() {
        WorldDocument document = new WorldDocument(16, 16);
        EditorSession session = new EditorSession(document);
        TileCoordinate edge = new TileCoordinate(0, 7, 7);
        session.execute(new SetTileCommand(edge, document.tile(edge).snapshot(),
                new com.rspsi.editor.model.TileSnapshot(0, 0, 0, 0, 7, 0, 0, 0, 0, java.util.List.of()),
                "edge edit"));

        assertEquals(3, session.dirtyRegions().size());
        assertTrue(session.dirtyRegions().stream().anyMatch(region ->
                region.chunkX() == 0 && region.chunkY() == 0));
        assertTrue(session.dirtyRegions().stream().anyMatch(region ->
                region.chunkX() == 1 && region.chunkY() == 0));
        assertTrue(session.dirtyRegions().stream().anyMatch(region ->
                region.chunkX() == 0 && region.chunkY() == 1));
    }

    @Test
    void editsOnDifferentPlanesRemainSeparateInvalidationEntries() {
        WorldDocument document = new WorldDocument(8, 8, 2);
        EditorSession session = new EditorSession(document);
        session.execute(new SetTileCommand(new TileCoordinate(0, 2, 2),
                document.tile(0, 2, 2).snapshot(),
                new com.rspsi.editor.model.TileSnapshot(0, 0, 0, 0, 7, 0, 0, 0, 0, java.util.List.of()),
                "plane 0"));
        session.execute(new SetTileCommand(new TileCoordinate(1, 2, 2),
                document.tile(1, 2, 2).snapshot(),
                new com.rspsi.editor.model.TileSnapshot(0, 0, 0, 0, 8, 0, 0, 0, 0, java.util.List.of()),
                "plane 1"));

        assertEquals(2, session.dirtyRegions().size());
        assertTrue(session.dirtyRegions().stream().anyMatch(region -> region.plane() == 0));
        assertTrue(session.dirtyRegions().stream().anyMatch(region -> region.plane() == 1));
    }

    @Test
    void rendererExpansionStaysOnTheChangedPlane() {
        WorldDocument document = new WorldDocument(10, 9, 2);
        var changes = com.rspsi.editor.render.RenderChanges.fromDirtyRegions(
                java.util.Set.of(new DirtyRegion(1, 1, 1, false, false, false, false, true)),
                document);

        assertEquals(2, changes.dirtyTiles().size());
        assertTrue(changes.dirtyTiles().stream().allMatch(tile -> tile.plane() == 1));
    }
}
