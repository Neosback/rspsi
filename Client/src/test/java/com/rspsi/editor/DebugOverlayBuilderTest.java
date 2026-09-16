package com.rspsi.editor;

import com.rspsi.editor.collision.CollisionFlag;
import com.rspsi.editor.collision.CollisionMap;
import com.rspsi.editor.debug.DebugGridLevel;
import com.rspsi.editor.debug.DebugOverlayBuilder;
import com.rspsi.editor.debug.DebugOverlayMode;
import com.rspsi.editor.debug.DebugOverlaySettings;
import com.rspsi.editor.debug.DebugOverlaySnapshot;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldWindow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DebugOverlayBuilderTest {
    @Test
    void buildsTileCoordinatesFlagsBridgeAndCollisionForFrontend() {
        WorldDocument document = new WorldDocument(8, 8, 2);
        TileCoordinate tile = new TileCoordinate(1, 7, 0);
        document.tile(tile).restore(new TileSnapshot(10, 11, 12, 13,
                4, 5, 2, 3, 0x6, List.of()));
        CollisionMap collision = new CollisionMap(8, 8, 2);
        collision.add(tile, CollisionFlag.BLOCK_NORTH | CollisionFlag.LOC_PROJECTILE);

        DebugOverlaySnapshot snapshot = new DebugOverlayBuilder().build(document,
                new WorldWindow(3200, 3200, 8, 8), collision,
                DebugOverlaySettings.of(DebugOverlayMode.COLLISION,
                        DebugOverlayMode.TILE_FLAGS, DebugOverlayMode.LOADED_WORLD_WINDOW));

        var debugTile = snapshot.tiles(1).stream()
                .filter(value -> value.coordinate().equals(tile)).findFirst().orElseThrow();
        assertEquals(3207, debugTile.address().worldX());
        assertEquals(3200, debugTile.address().worldY());
        assertTrue(debugTile.terrain().bridge());
        assertTrue(debugTile.terrain().roofRelated());
        assertTrue(debugTile.collision().orElseThrow().movementBlocked()
                .contains(com.rspsi.editor.collision.CollisionDirection.NORTH));
        assertEquals(0, debugTile.effectivePlane());
    }

    @Test
    void emitsSemanticChunkAndRegionBoundariesAtWorldOffsets() {
        WorldDocument document = new WorldDocument(64, 64, 1);
        DebugOverlaySnapshot snapshot = new DebugOverlayBuilder().build(document,
                new WorldWindow(3203, 3211, 64, 64), null,
                DebugOverlaySettings.of(DebugOverlayMode.CHUNK_GRID,
                        DebugOverlayMode.REGION_GRID));

        assertTrue(snapshot.gridLines().stream().anyMatch(line ->
                line.level() == DebugGridLevel.CHUNK && line.startX() == 3208));
        assertTrue(snapshot.gridLines().stream().anyMatch(line ->
                line.level() == DebugGridLevel.REGION && line.startX() == 3264));
        assertTrue(snapshot.tiles(0).size() == 64 * 64);
    }

    @Test
    void rejectsWindowThatCannotAddressTheDocument() {
        assertThrows(IllegalArgumentException.class, () -> new DebugOverlayBuilder().build(
                new WorldDocument(8, 8), new WorldWindow(0, 0, 64, 64), null,
                DebugOverlaySettings.none()));
    }
}
