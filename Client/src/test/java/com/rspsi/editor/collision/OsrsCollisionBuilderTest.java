package com.rspsi.editor.collision;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ObjectCollisionView;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import org.junit.jupiter.api.Test;

import java.util.Optional;

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

    @Test
    void appliesRotatedDefinitionBackedObjectCollision() {
        WorldDocument document = new WorldDocument(5, 5, 1);
        WorldObject object = new WorldObject(42, 10, 1, 0, 1, 1);
        document.tile(0, 1, 1).restore(new TileSnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0,
                java.util.List.of(object)));
        DefinitionProvider definitions = new DefinitionProvider() {
            @Override public Optional<com.rspsi.cache.definition.ObjectDefinitionView> object(int id) { return Optional.empty(); }
            @Override public Optional<com.rspsi.cache.definition.FloorDefinitionView> underlay(int id) { return Optional.empty(); }
            @Override public Optional<com.rspsi.cache.definition.FloorDefinitionView> overlay(int id) { return Optional.empty(); }
            @Override public Optional<ObjectCollisionView> objectCollision(int id) {
                return Optional.of(new ObjectCollisionView(id, 2, 1, 2, true, false));
            }
        };

        CollisionMap collision = OsrsCollisionBuilder.fromTerrainAndObjects(document, definitions);

        assertEquals(CollisionFlag.LOC | CollisionFlag.LOC_PROJECTILE, collision.flags(0, 1, 1));
        assertEquals(CollisionFlag.LOC | CollisionFlag.LOC_PROJECTILE, collision.flags(0, 1, 2));
        assertEquals(0, collision.flags(0, 2, 1));
    }
}
