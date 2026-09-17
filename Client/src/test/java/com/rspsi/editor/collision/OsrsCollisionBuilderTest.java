package com.rspsi.editor.collision;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ObjectCollisionView;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.OsrsTileFlags;
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

    @Test
    void followsOpenRuneLayerSemanticsForDiagonalGroundObjectsAndWallDecor() {
        WorldDocument document = new WorldDocument(5, 5, 1);
        WorldObject diagonalObject = new WorldObject(42, 9, 0, 0, 1, 1);
        WorldObject wallDecor = new WorldObject(43, 4, 0, 0, 3, 3);
        document.tile(0, 1, 1).restore(new TileSnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0,
                java.util.List.of(diagonalObject)));
        document.tile(0, 3, 3).restore(new TileSnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0,
                java.util.List.of(wallDecor)));
        DefinitionProvider definitions = new DefinitionProvider() {
            @Override public Optional<com.rspsi.cache.definition.ObjectDefinitionView> object(int id) { return Optional.empty(); }
            @Override public Optional<com.rspsi.cache.definition.FloorDefinitionView> underlay(int id) { return Optional.empty(); }
            @Override public Optional<com.rspsi.cache.definition.FloorDefinitionView> overlay(int id) { return Optional.empty(); }
            @Override public Optional<ObjectCollisionView> objectCollision(int id) {
                return Optional.of(new ObjectCollisionView(id, 1, 1, 2, true, false));
            }
        };

        CollisionMap collision = OsrsCollisionBuilder.fromTerrainAndObjects(document, definitions);

        assertEquals(CollisionFlag.LOC | CollisionFlag.LOC_PROJECTILE,
                collision.flags(0, 1, 1));
        assertEquals(0, collision.flags(0, 3, 3));
    }

    @Test
    void keepsOpenRuneCollisionWhenClientClipTypeDiffers() {
        WorldDocument document = new WorldDocument(4, 4, 1);
        WorldObject object = new WorldObject(44, 10, 0, 0, 1, 1);
        document.tile(0, 1, 1).restore(new TileSnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0,
                java.util.List.of(object)));
        DefinitionProvider definitions = new DefinitionProvider() {
            @Override public Optional<com.rspsi.cache.definition.ObjectDefinitionView> object(int id) { return Optional.empty(); }
            @Override public Optional<com.rspsi.cache.definition.FloorDefinitionView> underlay(int id) { return Optional.empty(); }
            @Override public Optional<com.rspsi.cache.definition.FloorDefinitionView> overlay(int id) { return Optional.empty(); }
            @Override public Optional<ObjectCollisionView> objectCollision(int id) {
                return Optional.of(new ObjectCollisionView(id, 1, 1, 2, true, false, 0));
            }
        };

        CollisionMap collision = OsrsCollisionBuilder.fromTerrainAndObjects(document, definitions);

        assertEquals(CollisionFlag.LOC | CollisionFlag.LOC_PROJECTILE,
                collision.flags(0, 1, 1));
    }

    @Test
    void preservesOpenRuneRouteBlockerForGroundLocations() {
        WorldDocument document = new WorldDocument(3, 1, 1);
        WorldObject object = new WorldObject(42, 10, 0, 0, 1, 0);
        document.tile(0, 1, 0).restore(new TileSnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0,
                java.util.List.of(object)));
        DefinitionProvider definitions = new DefinitionProvider() {
            @Override public Optional<com.rspsi.cache.definition.ObjectDefinitionView> object(int id) { return Optional.empty(); }
            @Override public Optional<com.rspsi.cache.definition.FloorDefinitionView> underlay(int id) { return Optional.empty(); }
            @Override public Optional<com.rspsi.cache.definition.FloorDefinitionView> overlay(int id) { return Optional.empty(); }
            @Override public Optional<ObjectCollisionView> objectCollision(int id) {
                return Optional.of(new ObjectCollisionView(id, 1, 1, 2, false, true));
            }
        };

        CollisionMap collision = OsrsCollisionBuilder.fromTerrainAndObjects(document, definitions);

        assertEquals(CollisionFlag.LOC | CollisionFlag.LOC_ROUTE_BLOCKER,
                collision.flags(0, 1, 0));
    }

    @Test
    void relinksAdjacentNonBridgeWallFlagsToTheEffectiveBridgePlane() {
        WorldDocument document = new WorldDocument(4, 4, 2);
        document.tile(1, 2, 2).restore(new TileSnapshot(0, 0, 0, 0, 0, 0, 0, 0,
                OsrsTileFlags.BRIDGE, java.util.List.of()));
        WorldObject wall = new WorldObject(43, 0, 2, 1, 1, 2);
        document.tile(1, 1, 2).restore(new TileSnapshot(0, 0, 0, 0, 0, 0, 0, 0,
                0, java.util.List.of(wall)));
        DefinitionProvider definitions = new DefinitionProvider() {
            @Override public Optional<com.rspsi.cache.definition.ObjectDefinitionView> object(int id) { return Optional.empty(); }
            @Override public Optional<com.rspsi.cache.definition.FloorDefinitionView> underlay(int id) { return Optional.empty(); }
            @Override public Optional<com.rspsi.cache.definition.FloorDefinitionView> overlay(int id) { return Optional.empty(); }
            @Override public Optional<ObjectCollisionView> objectCollision(int id) {
                return Optional.of(new ObjectCollisionView(id, 1, 1, 1, true, true));
            }
        };

        CollisionMap collision = OsrsCollisionBuilder.fromTerrainAndObjects(document, definitions);

        int boundaryFlags = CollisionFlag.WALL_EAST
                | CollisionFlag.WALL_EAST_PROJECTILE
                | CollisionFlag.WALL_EAST_ROUTE_BLOCKER;
        assertEquals(boundaryFlags, collision.flags(0, 1, 2) & boundaryFlags);
        assertEquals(0, collision.flags(0, 1, 2) & CollisionFlag.WALL_WEST);
        assertEquals(CollisionFlag.WALL_EAST, collision.flags(1, 1, 2) & CollisionFlag.WALL_EAST);
    }
}
