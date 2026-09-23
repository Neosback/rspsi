package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ObjectCollisionView;
import com.rspsi.editor.collision.CollisionFlag;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldRegion;
import com.rspsi.editor.model.WorldRegionWindow;
import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.OsrsTileFlags;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderWindowSceneBuilderTest {
    @Test
    void buildsLoadedRegionsAtWorldAddressesWithoutFabricatingHoles() {
        WorldDocument document = new WorldDocument(64, 64, 4);
        WorldObject object = new WorldObject(100, 10, 0, 0, 7, 9);
        document.tile(0, 7, 9).restore(new TileSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(object)));
        WorldRegion loaded = new WorldRegion(10, 20, document);
        WorldRegionWindow window = new WorldRegionWindow(10, 20, 2, 1,
                Map.of(loaded.regionId(), loaded));

        RenderWindowScene scene = new RenderWindowSceneBuilder().build(window);

        assertEquals(1, window.loadedRegionCount());
        assertEquals(64 * 64 * 4, scene.terrainMeshes().size());
        WorldTileAddress objectAddress = WorldTileAddress.of(10 * 64 + 7, 20 * 64 + 9, 0);
        assertTrue(scene.hasTile(objectAddress));
        assertEquals(1, scene.objects().size());
        assertEquals(objectAddress, scene.objects().get(0).address());
        assertEquals(64 * 64 * 4, scene.collision().size());
        assertEquals(0, scene.terrainPackets().size());
        assertFalse(scene.hasTile(WorldTileAddress.of(11 * 64, 20 * 64, 0)));
    }

    @Test
    void publishesWorldAddressedTerrainCollisionForOverlayConsumers() {
        WorldDocument document = new WorldDocument(64, 64, 4);
        document.tile(1, 12, 13).restore(new TileSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0,
                OsrsTileFlags.BLOCK_MAP_SQUARE | OsrsTileFlags.REMOVE_ROOFS,
                List.of()));
        WorldRegion loaded = new WorldRegion(10, 20, document);
        WorldRegionWindow window = new WorldRegionWindow(10, 20, 1, 1,
                Map.of(loaded.regionId(), loaded));

        RenderWindowScene scene = new RenderWindowSceneBuilder().build(window);

        var address = WorldTileAddress.of(10 * 64 + 12, 20 * 64 + 13, 1);
        var snapshot = scene.collision().get(address);
        assertEquals(CollisionFlag.BLOCK_WALK | CollisionFlag.ROOF, snapshot.rawFlags());
        assertTrue(snapshot.floorBlocked());
        assertTrue(snapshot.roof());
        assertEquals(address, WorldTileAddress.of(
                10 * 64 + snapshot.coordinate().x(),
                20 * 64 + snapshot.coordinate().y(),
                snapshot.coordinate().plane()));
    }

    @Test
    void includesDefinitionBackedObjectCollisionAtWorldAddresses() {
        WorldDocument document = new WorldDocument(64, 64, 4);
        document.tile(0, 12, 13).restore(new TileSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of(new WorldObject(42, 10, 0, 0, 12, 13))));
        WorldRegion loaded = new WorldRegion(10, 20, document);
        WorldRegionWindow window = new WorldRegionWindow(10, 20, 1, 1,
                Map.of(loaded.regionId(), loaded));
        DefinitionProvider definitions = new DefinitionProvider() {
            @Override public Optional<com.rspsi.cache.definition.ObjectDefinitionView> object(int id) {
                return Optional.empty();
            }

            @Override public Optional<com.rspsi.cache.definition.FloorDefinitionView> underlay(int id) {
                return Optional.empty();
            }

            @Override public Optional<com.rspsi.cache.definition.FloorDefinitionView> overlay(int id) {
                return Optional.empty();
            }

            @Override public Optional<ObjectCollisionView> objectCollision(int id) {
                return Optional.of(new ObjectCollisionView(id, 2, 1, 2, true, false));
            }
        };

        RenderWindowScene scene = new RenderWindowSceneBuilder(definitions).build(window);

        assertEquals(CollisionFlag.LOC | CollisionFlag.LOC_PROJECTILE,
                scene.collision().get(WorldTileAddress.of(10 * 64 + 12, 20 * 64 + 13, 0)).rawFlags());
        assertEquals(CollisionFlag.LOC | CollisionFlag.LOC_PROJECTILE,
                scene.collision().get(WorldTileAddress.of(10 * 64 + 13, 20 * 64 + 13, 0)).rawFlags());
    }

    @Test
    void animationRefreshKeepsTerrainResidentAndDirtiesOnlyChangedFrames() {
        WorldDocument document = new WorldDocument(64, 64, 1);
        WorldObject object = new WorldObject(42, 10, 0, 0, 8, 8);
        document.tile(0, 8, 8).restore(new TileSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(object)));
        WorldRegion loaded = new WorldRegion(10, 20, document);
        WorldRegionWindow window = new WorldRegionWindow(
                10, 20, 1, 1, Map.of(loaded.regionId(), loaded));
        RenderWindowSceneBuilder builder =
                new RenderWindowSceneBuilder(animatedDefinitions());

        RenderWindowScene initial = builder.build(window, 0);
        WorldTileAddress address = WorldTileAddress.of(10 * 64 + 8, 20 * 64 + 8, 0);
        ModelRenderPacket frameZero = initial.modelPackets().get(address).get(0);

        RenderWindowSceneBuilder.AnimationRefreshResult sameFrame =
                builder.refreshAnimations(initial, 1);
        ModelRenderPacket cycleOne = sameFrame.scene().modelPackets().get(address).get(0);

        assertTrue(sameFrame.dirtyZones().isEmpty(),
                "advancing inside one frame must not rebuild a GPU zone");
        assertEquals(0, sameFrame.changedTiles());
        assertEquals(1, cycleOne.animationState().clientCycle(),
                "diagnostic timing state should still advance");
        assertEquals(frameZero.vertices(), cycleOne.vertices());
        assertSame(initial.terrainMeshes().get(address),
                sameFrame.scene().terrainMeshes().get(address),
                "animation refresh must leave resident terrain geometry untouched");

        RenderWindowSceneBuilder.AnimationRefreshResult nextFrame =
                builder.refreshAnimations(sameFrame.scene(), 2);
        ModelRenderPacket frameOne = nextFrame.scene().modelPackets().get(address).get(0);

        assertEquals(java.util.Set.of(WorldZoneCoordinate.from(address)),
                nextFrame.dirtyZones());
        assertEquals(1, nextFrame.changedTiles());
        assertEquals(1, frameOne.animationState().frameIndex());
        assertEquals(101, frameOne.animationState().frameId());
        assertNotEquals(frameZero.vertices(), frameOne.vertices());
        assertSame(initial.terrainMeshes().get(address),
                nextFrame.scene().terrainMeshes().get(address));
    }

    @Test
    void animationRefreshRebuildsOnlyActiveModelTilesAndReusesStaticPackets() {
        WorldDocument document = new WorldDocument(64, 64, 1);
        WorldObject animated = new WorldObject(42, 10, 0, 0, 8, 8);
        WorldObject stationary = new WorldObject(43, 10, 0, 0, 24, 24);
        document.tile(0, 8, 8).restore(new TileSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(animated)));
        document.tile(0, 24, 24).restore(new TileSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(stationary)));
        WorldRegion loaded = new WorldRegion(10, 20, document);
        WorldRegionWindow window = new WorldRegionWindow(
                10, 20, 1, 1, Map.of(loaded.regionId(), loaded));
        RenderWindowSceneBuilder builder =
                new RenderWindowSceneBuilder(animatedDefinitions());

        RenderWindowScene initial = builder.build(window, 0);
        WorldTileAddress animatedAddress =
                WorldTileAddress.of(10 * 64 + 8, 20 * 64 + 8, 0);
        WorldTileAddress staticAddress =
                WorldTileAddress.of(10 * 64 + 24, 20 * 64 + 24, 0);
        ModelRenderPacket staticPacket =
                initial.modelPackets().get(staticAddress).get(0);

        RenderWindowSceneBuilder.AnimationRefreshResult refresh =
                builder.refreshAnimations(initial, 2);

        assertEquals(1, refresh.rebuiltModelTiles());
        assertFalse(refresh.fullModelRebuild());
        assertEquals(1, refresh.changedTiles());
        assertEquals(java.util.Set.of(WorldZoneCoordinate.from(animatedAddress)),
                refresh.dirtyZones());
        assertSame(staticPacket,
                refresh.scene().modelPackets().get(staticAddress).get(0),
                "stationary model packets must remain resident by identity");
    }

    @Test
    void animationRefreshFallsBackWhenActiveTileRequiresSceneWideNormalMerge() {
        WorldDocument document = new WorldDocument(64, 64, 1);
        WorldObject animated = new WorldObject(42, 10, 0, 0, 8, 8);
        document.tile(0, 8, 8).restore(new TileSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(animated)));
        WorldRegion loaded = new WorldRegion(10, 20, document);
        WorldRegionWindow window = new WorldRegionWindow(
                10, 20, 1, 1, Map.of(loaded.regionId(), loaded));
        RenderWindowSceneBuilder builder =
                new RenderWindowSceneBuilder(animatedDefinitions(true));

        RenderWindowScene initial = builder.build(window, 0);
        RenderWindowSceneBuilder.AnimationRefreshResult refresh =
                builder.refreshAnimations(initial, 2);

        assertTrue(refresh.fullModelRebuild(),
                "mergeNormals animation tiles must preserve the scene-wide normal merge pass");
        assertEquals(1, refresh.changedTiles());
        assertTrue(refresh.rebuiltModelTiles() >= 1);
    }

    @Test
    void stitchesEastNeighborBeforeBuildingSharedGeometry() {
        WorldDocument westDocument = new WorldDocument(64, 64, 4);
        WorldDocument eastDocument = new WorldDocument(64, 64, 4);
        westDocument.tile(0, 63, 4).restore(new TileSnapshot(
                10, 20, 30, 40, 0, 0, 0, 0, 0, List.of()));
        eastDocument.tile(0, 0, 4).restore(new TileSnapshot(
                200, 201, 202, 203, 0, 0, 0, 0, 0, List.of()));
        eastDocument.tile(0, 0, 5).restore(new TileSnapshot(
                203, 204, 205, 206, 0, 0, 0, 0, 0, List.of()));
        WorldRegion west = new WorldRegion(10, 20, westDocument);
        WorldRegion east = new WorldRegion(11, 20, eastDocument);
        WorldRegionWindow window = new WorldRegionWindow(10, 20, 2, 1,
                Map.of(west.regionId(), west, east.regionId(), east));

        RenderWindowScene scene = new RenderWindowSceneBuilder().build(window);

        assertEquals(20, westDocument.tile(0, 63, 4).snapshot().southEastHeight());
        assertEquals(30, westDocument.tile(0, 63, 4).snapshot().northEastHeight());
        assertEquals(scene.window().region(10, 20).orElseThrow().document()
                        .tile(0, 63, 4).snapshot().southEastHeight(),
                eastDocument.tile(0, 0, 4).snapshot().southWestHeight());
        assertEquals(scene.window().region(10, 20).orElseThrow().document()
                        .tile(0, 63, 4).snapshot().northEastHeight(),
                eastDocument.tile(0, 0, 5).snapshot().southWestHeight());
    }

    @Test
    void derivesAppearanceAcrossRegionBoundariesUsingWorldWindowContext() {
        WorldDocument westDocument = filledRegion(1);
        WorldDocument eastDocument = filledRegion(2);
        WorldRegion west = new WorldRegion(10, 20, westDocument);
        WorldRegion east = new WorldRegion(11, 20, eastDocument);
        WorldRegionWindow window = new WorldRegionWindow(10, 20, 2, 1,
                Map.of(west.regionId(), west, east.regionId(), east));

        RenderWindowScene scene = new RenderWindowSceneBuilder(boundaryDefinitions()).build(window);

        WorldTileAddress boundary = WorldTileAddress.of(10 * 64 + 63, 20 * 64 + 10, 0);
        assertEquals(OsrsTerrainColorMath.packHsl(64, 128, 96),
                scene.terrainAppearances().get(boundary).underlayHsl());
        assertTrue(scene.terrainPackets().containsKey(boundary));
    }


    @Test
    void mergesWallNormalsAcrossLoadedRegionBoundary() {
        WorldDocument westDocument = new WorldDocument(64, 64, 4);
        WorldDocument eastDocument = new WorldDocument(64, 64, 4);
        int y = 10;
        westDocument.tile(0, 63, y).restore(new TileSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of(new WorldObject(42, 0, 0, 0, 63, y))));
        eastDocument.tile(0, 0, y).restore(new TileSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of(new WorldObject(42, 0, 2, 0, 0, y))));

        WorldRegion west = new WorldRegion(10, 20, westDocument);
        WorldRegion east = new WorldRegion(11, 20, eastDocument);
        WorldRegionWindow window = new WorldRegionWindow(10, 20, 2, 1,
                Map.of(west.regionId(), west, east.regionId(), east));

        RenderWindowScene scene = new RenderWindowSceneBuilder(mergingWallDefinitions()).build(window);

        ModelRenderPacket westPacket = scene.modelPackets()
                .get(WorldTileAddress.of(10 * 64 + 63, 20 * 64 + y, 0)).get(0);
        ModelRenderPacket eastPacket = scene.modelPackets()
                .get(WorldTileAddress.of(11 * 64, 20 * 64 + y, 0)).get(0);

        assertTrue(westPacket.vertices().stream().anyMatch(vertex -> vertex.normalMagnitude() > 1),
                "west boundary wall should receive its east neighbor's normal");
        assertTrue(eastPacket.vertices().stream().anyMatch(vertex -> vertex.normalMagnitude() > 1),
                "east boundary wall should receive its west neighbor's normal");
        assertTrue(westPacket.triangles().stream().anyMatch(face -> face.renderType() == 2));
        assertTrue(eastPacket.triangles().stream().anyMatch(face -> face.renderType() == 2));
    }

    private static DefinitionProvider animatedDefinitions() {
        return animatedDefinitions(false);
    }

    private static DefinitionProvider animatedDefinitions(boolean mergeNormals) {
        com.rspsi.cache.definition.ObjectAppearanceView appearance =
                new com.rspsi.cache.definition.ObjectAppearanceView(
                        77, false, 128, 128, 128,
                        0, 0, 0, Map.of(), Map.of(),
                        true, false, mergeNormals, false,
                        0, 0, 16, -1, 0,
                        false, false, false, 0);
        com.rspsi.cache.definition.ObjectAppearanceView staticAppearance =
                new com.rspsi.cache.definition.ObjectAppearanceView(
                        -1, false, 128, 128, 128,
                        0, 0, 0, Map.of(), Map.of(),
                        true, false, false, false,
                        0, 0, 16, -1, 0,
                        false, false, false, 0);
        com.rspsi.cache.definition.ModelGeometryView geometry =
                new com.rspsi.cache.definition.ModelGeometryView(
                        7,
                        new int[]{0, 0, 0, 64, 0, 0, 0, 0, 64},
                        new int[]{0, 1, 2}, new short[]{100},
                        new int[]{0}, new int[]{-1})
                        .withVertexSkins(new int[]{1, 1, 1});
        com.rspsi.cache.definition.SequenceDefinitionView sequence =
                new com.rspsi.cache.definition.SequenceDefinitionView(
                        77, new int[]{100, 101}, new int[]{1, 1}, 2, false,
                        -1, -1, 99, 0, 0, 2, -1, 0);
        com.rspsi.cache.definition.SkeletonDefinitionView skeleton =
                new com.rspsi.cache.definition.SkeletonDefinitionView(
                        5, new int[]{1}, new int[][]{{1}});
        com.rspsi.cache.definition.AnimationFrameView first =
                new com.rspsi.cache.definition.AnimationFrameView(
                        100, 5, new int[]{0}, new int[]{0},
                        new int[]{0}, new int[]{0}, false);
        com.rspsi.cache.definition.AnimationFrameView second =
                new com.rspsi.cache.definition.AnimationFrameView(
                        101, 5, new int[]{0}, new int[]{10},
                        new int[]{0}, new int[]{0}, false);

        return new DefinitionProvider() {
            @Override public Optional<com.rspsi.cache.definition.ObjectDefinitionView> object(int id) {
                return switch (id) {
                    case 42 -> Optional.of(new com.rspsi.cache.definition.ObjectDefinitionView(
                            42, "animated", 1, 1, List.of(),
                            new int[]{7}, new int[]{10}, -1, false));
                    case 43 -> Optional.of(new com.rspsi.cache.definition.ObjectDefinitionView(
                            43, "stationary", 1, 1, List.of(),
                            new int[]{7}, new int[]{10}, -1, false));
                    default -> Optional.empty();
                };
            }

            @Override public Optional<com.rspsi.cache.definition.ObjectAppearanceView> objectAppearance(int id) {
                return switch (id) {
                    case 42 -> Optional.of(appearance);
                    case 43 -> Optional.of(staticAppearance);
                    default -> Optional.empty();
                };
            }

            @Override public Optional<com.rspsi.cache.definition.ModelGeometryView> modelGeometry(int id) {
                return id == 7 ? Optional.of(geometry) : Optional.empty();
            }

            @Override public Optional<com.rspsi.cache.definition.SequenceDefinitionView> sequence(int id) {
                return id == 77 ? Optional.of(sequence) : Optional.empty();
            }

            @Override public Optional<com.rspsi.cache.definition.AnimationFrameView> animationFrame(int id) {
                return switch (id) {
                    case 100 -> Optional.of(first);
                    case 101 -> Optional.of(second);
                    default -> Optional.empty();
                };
            }

            @Override public Optional<com.rspsi.cache.definition.SkeletonDefinitionView> skeleton(int id) {
                return id == 5 ? Optional.of(skeleton) : Optional.empty();
            }

            @Override public Optional<com.rspsi.cache.definition.FloorDefinitionView> underlay(int id) {
                return Optional.empty();
            }

            @Override public Optional<com.rspsi.cache.definition.FloorDefinitionView> overlay(int id) {
                return Optional.empty();
            }
        };
    }

    private static DefinitionProvider mergingWallDefinitions() {
        com.rspsi.cache.definition.ModelGeometryView wallGeometry =
                new com.rspsi.cache.definition.ModelGeometryView(
                        7,
                        new int[]{
                                64, 0, -32,
                                64, 64, 0,
                                64, 0, 32
                        },
                        new int[]{0, 1, 2},
                        new short[]{100},
                        new int[]{0},
                        new int[]{-1});
        com.rspsi.cache.definition.ObjectAppearanceView merging =
                new com.rspsi.cache.definition.ObjectAppearanceView(
                        -1, false, 128, 128, 128,
                        0, 0, 0, Map.of(), Map.of(),
                        true, false, true, false,
                        0, 0, 16, -1, 0,
                        false, false, false, 0);
        return new DefinitionProvider() {
            @Override public Optional<com.rspsi.cache.definition.ObjectDefinitionView> object(int id) {
                return id == 42
                        ? Optional.of(new com.rspsi.cache.definition.ObjectDefinitionView(
                                42, "boundary wall", 1, 1, List.of(),
                                new int[]{7}, new int[]{0}, -1, false))
                        : Optional.empty();
            }

            @Override public Optional<com.rspsi.cache.definition.ObjectAppearanceView> objectAppearance(int id) {
                return id == 42 ? Optional.of(merging) : Optional.empty();
            }

            @Override public Optional<com.rspsi.cache.definition.ModelGeometryView> modelGeometry(int id) {
                return id == 7 ? Optional.of(wallGeometry) : Optional.empty();
            }

            @Override public Optional<com.rspsi.cache.definition.FloorDefinitionView> underlay(int id) {
                return Optional.empty();
            }

            @Override public Optional<com.rspsi.cache.definition.FloorDefinitionView> overlay(int id) {
                return Optional.empty();
            }
        };
    }

    private static WorldDocument filledRegion(int underlayId) {
        WorldDocument document = new WorldDocument(64, 64, 4);
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                document.tile(0, x, y).restore(new TileSnapshot(
                        0, 0, 0, 0, underlayId, 0, 0, 0, 0, List.of()));
            }
        }
        return document;
    }

    private static DefinitionProvider boundaryDefinitions() {
        return new DefinitionProvider() {
            @Override public Optional<com.rspsi.cache.definition.ObjectDefinitionView> object(int id) {
                return Optional.empty();
            }

            @Override public Optional<com.rspsi.cache.definition.FloorDefinitionView> underlay(int id) {
                if (id == 0) return Optional.of(new com.rspsi.cache.definition.FloorDefinitionView(
                        id, -1, 0, 0, 128, 96, 0, 256));
                if (id == 1) return Optional.of(new com.rspsi.cache.definition.FloorDefinitionView(
                        id, -1, 0, 128, 128, 96, 128, 256));
                return Optional.empty();
            }

            @Override public Optional<com.rspsi.cache.definition.FloorDefinitionView> overlay(int id) {
                return Optional.empty();
            }
        };
    }
}
