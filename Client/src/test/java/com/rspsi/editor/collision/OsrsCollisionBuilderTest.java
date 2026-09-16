package com.rspsi.editor.collision;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OsrsCollisionBuilderTest {
    @Test
    void appliesTerrainFlagsOnTheResolvedBridgePlane() {
        WorldDocument document = new WorldDocument(4, 4, 4);
        TileSnapshot bridge = new TileSnapshot(0, 0, 0, 0, 0, 0, 0, 0,
                OsrsCollisionBuilder.LINK_BELOW | OsrsCollisionBuilder.BLOCK_MAP_SQUARE
                        | OsrsCollisionBuilder.REMOVE_ROOFS, java.util.List.of());
        document.tile(1, 2, 2).restore(bridge);

        CollisionMap collision = OsrsCollisionBuilder.fromTerrain(document);

        assertEquals(CollisionFlag.BLOCK_WALK | CollisionFlag.ROOF,
                collision.flags(new TileCoordinate(0, 2, 2)));
        assertEquals(0, collision.flags(new TileCoordinate(1, 2, 2)));
        assertEquals(0, collision.flags(new TileCoordinate(2, 2, 2)));
        assertEquals(0, collision.flags(new TileCoordinate(3, 2, 2)));
        assertEquals(0, collision.flags(new TileCoordinate(0, 1, 1)));
        assertEquals(-1, OsrsCollisionBuilder.resolvedPlane(document, 0, 2, 2));
        assertEquals(2, OsrsCollisionBuilder.resolvedPlane(document, 3, 2, 2));
    }
}
