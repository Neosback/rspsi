package com.rspsi.editor.render;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.DirtyRegion;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.ObjectCollisionView;
import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.SetTileCommand;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import java.util.Optional;

class RenderSceneBuilderTest {
    @Test
    void buildsTerrainForEveryPlaneAndExposesCanonicalObjects() {
        WorldDocument document = new WorldDocument(2, 3, 2);
        WorldObject object = new WorldObject(100, 10, 2, 1, 1, 2);
        document.tile(1, 1, 2).restore(new TileSnapshot(
                10, 20, 30, 40, 2, 3, 6, 1, 0, List.of(object)));

        RenderScene scene = new RenderSceneBuilder().build(document);

        assertSame(document, scene.document());
        assertEquals(12, scene.terrainMeshes().size());
        assertEquals(1, scene.objects().size());
        assertEquals(1, scene.renderObjects().size());
        assertEquals(object, scene.objects().get(0));
        assertEquals(6, scene.terrainMeshes()
                .get(new TileCoordinate(1, 1, 2)).vertices().size());
        assertEquals(12, scene.terrainLighting().size());
        assertEquals(12, scene.collision().size());
    }

    @Test
    void compatibilityConstructorDoesNotInventDerivedGeometry() {
        WorldDocument document = new WorldDocument(1, 1, 1);

        RenderScene scene = new RenderScene(document);

        assertSame(document, scene.document());
        assertEquals(0, scene.terrainMeshes().size());
        assertEquals(0, scene.objects().size());
        assertEquals(0, scene.bridges().size());
        assertEquals(0, scene.terrainMaterials().size());
        assertEquals(0, scene.terrainLighting().size());
        assertEquals(0, scene.collision().size());
    }

    @Test
    void updateRebuildsOnlyDirtyTerrainAndRefreshesObjects() {
        WorldDocument document = new WorldDocument(2, 1, 1);
        RenderSceneBuilder builder = new RenderSceneBuilder();
        RenderScene initial = builder.build(document);
        var untouched = initial.terrainMeshes().get(new TileCoordinate(0, 1, 0));

        WorldObject object = new WorldObject(200, 10, 0, 0, 0, 0);
        document.tile(0, 0, 0).restore(new TileSnapshot(
                20, 20, 20, 20, 0, 0, 0, 0, 0, List.of(object)));
        RenderScene updated = builder.update(initial,
                new RenderChanges(Set.of(new TileCoordinate(0, 0, 0))));

        assertSame(untouched, updated.terrainMeshes().get(new TileCoordinate(0, 1, 0)));
        org.junit.jupiter.api.Assertions.assertNotSame(
                initial.terrainMeshes().get(new TileCoordinate(0, 0, 0)),
                updated.terrainMeshes().get(new TileCoordinate(0, 0, 0)));
        assertEquals(List.of(object), updated.objects());
    }

    @Test
    void exposesBridgeLinksDerivedFromTheCanonicalFlag() {
        WorldDocument document = new WorldDocument(1, 1, 4);
        document.tile(1, 0, 0).restore(new TileSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0, com.rspsi.editor.model.OsrsTileFlags.BRIDGE, List.of()));

        RenderScene scene = new RenderSceneBuilder().build(document);

        assertEquals(3, scene.bridges().size());
        assertEquals(new com.rspsi.editor.model.BridgeLink(
                new TileCoordinate(1, 0, 0), new TileCoordinate(0, 0, 0)), scene.bridges().get(0));
        assertEquals(scene.bridges(), document.bridgeLinks());
    }

    @Test
    void expandsDirtyChunksToClampedCoordinatesAcrossAllPlanes() {
        WorldDocument document = new WorldDocument(10, 9, 2);

        RenderChanges changes = RenderChanges.fromDirtyRegions(Set.of(
                new DirtyRegion(1, 1, false, false, false, false, true)), document);

        assertEquals(4, changes.dirtyTiles().size());
        assertEquals(Set.of(
                new TileCoordinate(0, 8, 8), new TileCoordinate(0, 9, 8),
                new TileCoordinate(1, 8, 8), new TileCoordinate(1, 9, 8)),
                changes.dirtyTiles());
    }

    @Test
    void acceptsSessionChunkInvalidationBatchesDirectly() {
        WorldDocument document = new WorldDocument(8, 8, 1);
        RenderSceneBuilder builder = new RenderSceneBuilder();
        RenderScene initial = builder.build(document);
        document.tile(0, 4, 4).restore(new TileSnapshot(
                20, 20, 20, 20, 0, 4, 0, 0, 0, List.of()));

        RenderScene updated = builder.update(initial, Set.of(
                new DirtyRegion(0, 0, false, false, false, false, true)));

        assertEquals(20, updated.terrainMeshes()
                .get(new TileCoordinate(0, 4, 4)).vertices().get(0).height());
        org.junit.jupiter.api.Assertions.assertNotNull(updated.terrainLighting()
                .get(new TileCoordinate(0, 4, 4)));
    }

    @Test
    void definitionAwareBuilderCarriesNeutralTerrainMaterialInputs() {
        WorldDocument document = new WorldDocument(1, 1, 1);
        WorldObject object = new WorldObject(100, 10, 1, 0, 0, 0);
        document.tile(0, 0, 0).restore(new TileSnapshot(
                0, 0, 0, 0, 2, 3, 0, 0, 0, List.of(object)));

        RenderScene scene = new RenderSceneBuilder(definitions()).build(document);

        assertEquals(new TerrainMaterial(2, 3, 17, 0x102030, 0xA0B0C0),
                scene.terrainMaterials().get(new TileCoordinate(0, 0, 0)));
        assertEquals(1, scene.terrainPackets().size());
        assertEquals(2, scene.terrainPackets().get(new TileCoordinate(0, 0, 0)).faces().size());
        assertEquals(com.rspsi.editor.collision.CollisionFlag.LOC
                        | com.rspsi.editor.collision.CollisionFlag.LOC_PROJECTILE,
                scene.collision().get(new TileCoordinate(0, 0, 0)).rawFlags());
        RenderObject renderObject = scene.renderObjects().get(0);
        assertEquals(object, renderObject.object());
        assertEquals(com.rspsi.editor.model.ObjectCategory.GROUND, renderObject.category());
        assertEquals(Optional.of(com.rspsi.editor.model.OsrsLocShape.CENTREPIECE_STRAIGHT), renderObject.shape());
        assertEquals(2, renderObject.footprintWidth());
        assertEquals(1, renderObject.footprintLength());
        assertArrayEquals(new int[]{501, 502}, renderObject.modelIds());
        assertTrue(renderObject.blocksMovement());
        assertTrue(renderObject.blocksProjectile());
        assertEquals(700, renderObject.appearance().animationId());
        assertTrue(renderObject.appearance().contouredGround());
        assertEquals(150, renderObject.appearance().scaleX());
        assertEquals(java.util.Map.of(10, 20), renderObject.appearance().recolors());
    }

    private static DefinitionProvider definitions() {
        return new DefinitionProvider() {
            @Override public Optional<FloorDefinitionView> underlay(int id) {
                return id == 1 ? Optional.of(new FloorDefinitionView(id, -1, 0x102030,
                        0, 0, 0, 0, 0)) : Optional.empty();
            }
            @Override public Optional<FloorDefinitionView> overlay(int id) {
                return id == 2 ? Optional.of(new FloorDefinitionView(id, 17, 0xA0B0C0,
                        0, 0, 0, 0, 0)) : Optional.empty();
            }

            @Override public Optional<ObjectDefinitionView> object(int id) {
                return id == 100 ? Optional.of(new ObjectDefinitionView(id, "Test object", 1, 2,
                        List.of("Use"), new int[]{501, 502})) : Optional.empty();
            }

            @Override public Optional<ObjectCollisionView> objectCollision(int id) {
                return id == 100 ? Optional.of(new ObjectCollisionView(
                        id, 1, 2, 1, true, false)) : Optional.empty();
            }

            @Override public Optional<ObjectAppearanceView> objectAppearance(int id) {
                return id == 100 ? Optional.of(new ObjectAppearanceView(700, true,
                        150, 128, 128, 2, 3, 4,
                        java.util.Map.of(10, 20), java.util.Map.of(30, 40)))
                        : Optional.empty();
            }
        };
    }

    @Test
    void compatibilityAnimationRefreshReusesStaticModelPackets() {
        WorldDocument document = new WorldDocument(3, 1, 1);
        WorldObject animated = new WorldObject(42, 10, 0, 0, 0, 0);
        WorldObject stationary = new WorldObject(43, 10, 0, 0, 2, 0);
        document.tile(0, 0, 0).restore(new TileSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(animated)));
        document.tile(0, 2, 0).restore(new TileSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(stationary)));

        RenderSceneBuilder builder = new RenderSceneBuilder(animationDefinitions());
        RenderScene initial = builder.build(document, 0);
        ModelRenderPacket staticPacket = initial.modelPackets().stream()
                .filter(packet -> packet.objectId() == 43)
                .findFirst().orElseThrow();
        ModelRenderPacket firstAnimated = initial.modelPackets().stream()
                .filter(packet -> packet.objectId() == 42)
                .findFirst().orElseThrow();

        RenderScene refreshed = builder.refreshAnimations(initial, 2);

        ModelRenderPacket nextStatic = refreshed.modelPackets().stream()
                .filter(packet -> packet.objectId() == 43)
                .findFirst().orElseThrow();
        ModelRenderPacket nextAnimated = refreshed.modelPackets().stream()
                .filter(packet -> packet.objectId() == 42)
                .findFirst().orElseThrow();
        assertSame(staticPacket, nextStatic,
                "compatibility scene refresh must retain stationary packets by identity");
        org.junit.jupiter.api.Assertions.assertNotEquals(
                firstAnimated.animationState().frameIndex(),
                nextAnimated.animationState().frameIndex());
    }

    private static DefinitionProvider animationDefinitions() {
        ObjectAppearanceView animatedAppearance = new ObjectAppearanceView(
                77, false, 128, 128, 128,
                0, 0, 0, java.util.Map.of(), java.util.Map.of(),
                true, false, false, false,
                0, 0, 16, -1, 0,
                false, false, false, 0);
        ObjectAppearanceView staticAppearance = new ObjectAppearanceView(
                -1, false, 128, 128, 128,
                0, 0, 0, java.util.Map.of(), java.util.Map.of(),
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
            @Override public Optional<ObjectDefinitionView> object(int id) {
                return switch (id) {
                    case 42 -> Optional.of(new ObjectDefinitionView(
                            42, "animated", 1, 1, List.of(),
                            new int[]{7}, new int[]{10}, -1, false));
                    case 43 -> Optional.of(new ObjectDefinitionView(
                            43, "static", 1, 1, List.of(),
                            new int[]{7}, new int[]{10}, -1, false));
                    default -> Optional.empty();
                };
            }

            @Override public Optional<ObjectAppearanceView> objectAppearance(int id) {
                return switch (id) {
                    case 42 -> Optional.of(animatedAppearance);
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

            @Override public Optional<FloorDefinitionView> underlay(int id) {
                return Optional.empty();
            }

            @Override public Optional<FloorDefinitionView> overlay(int id) {
                return Optional.empty();
            }
        };
    }

    @Test
    void sessionSceneControllerPublishesInitialAndChunkUpdates() {
        WorldDocument document = new WorldDocument(8, 8, 1);
        EditorSession session = new EditorSession(document);
        RecordingRenderer renderer = new RecordingRenderer();

        try (SessionSceneController controller = new SessionSceneController(session, renderer)) {
            assertEquals(1, renderer.loadCount);
            session.execute(new SetTileCommand(new TileCoordinate(0, 4, 4),
                    document.tile(0, 4, 4).snapshot(),
                    new TileSnapshot(20, 20, 20, 20, 0, 0, 0, 0, 0, List.of()),
                    "height edit"));

            assertEquals(1, renderer.updateCount);
            assertEquals(20, renderer.lastScene.terrainMeshes()
                    .get(new TileCoordinate(0, 4, 4)).vertices().get(0).height());
            assertEquals(20, controller.scene().terrainMeshes()
                    .get(new TileCoordinate(0, 4, 4)).vertices().get(0).height());
            assertTrue(session.dirtyRegions().isEmpty());
        }

        session.execute(new SetTileCommand(new TileCoordinate(0, 4, 4),
                document.tile(0, 4, 4).snapshot(),
                new TileSnapshot(24, 24, 24, 24, 0, 0, 0, 0, 0, List.of()),
                "second height edit"));
        assertEquals(1, renderer.updateCount);
    }

    private static final class RecordingRenderer implements SceneRenderer {
        private int loadCount;
        private int updateCount;
        private RenderScene lastScene;

        @Override public void load(RenderScene scene) { loadCount++; lastScene = scene; }
        @Override public void update(RenderChanges changes) { updateCount++; }
        @Override public void update(RenderScene scene, RenderChanges changes) {
            updateCount++;
            lastScene = scene;
        }
        @Override public void render(CameraState camera) { }
        @Override public java.util.Optional<PickResult> pick(float x, float y) {
            return java.util.Optional.empty();
        }
    }
}
