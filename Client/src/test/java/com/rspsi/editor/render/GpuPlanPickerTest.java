package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.model.WorldObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuPlanPickerTest {
    @Test
    void picksNearestObjectFromTheSameWorldTrianglesUsedForRendering() {
        GpuSceneVertex nearA = vertex(-20, -20, 100, 0x1200);
        GpuSceneVertex nearB = vertex(20, -20, 100, 0x1200);
        GpuSceneVertex nearC = vertex(0, 20, 100, 0x1200);
        GpuSceneVertex farA = vertex(-20, -20, 200, 0x4A38);
        GpuSceneVertex farB = vertex(20, -20, 200, 0x4A38);
        GpuSceneVertex farC = vertex(0, 20, 200, 0x4A38);
        WorldTileAddress tile = WorldTileAddress.of(3200, 3200, 0);
        ClientModelBounds clientBounds = ClientModelBounds.calculate(
                List.of(new ModelVertex(-10, -20, -30, 0, 0, 0, 0, 0, 0),
                        new ModelVertex(50, 40, 70, 0, 0, 0, 0, 0, 0),
                        new ModelVertex(20, 10, -5, 0, 0, 0, 0, 0, 0)),
                0, false);
        GpuUploadPlan plan = new GpuUploadPlan(
                List.of(nearA, nearB, nearC, farA, farB, farC),
                List.of(0, 1, 2, 3, 4, 5),
                List.of(
                        new GpuDrawCommand(tile, 0, 0, SceneLayer.Kind.GROUND_OBJECT,
                                GpuDrawCommand.SubmissionPass.OPAQUE, 0, 3, -1, 0, 0, 11,
                                GpuDrawCommand.RenderMode.DEFAULT,
                                WallDecorationPresentation.none(),
                                GameObjectSceneMetadata.of(3200, 3200, 3, 2, 1, 0),
                                List.of(clientBounds)),
                        new GpuDrawCommand(tile, SceneLayer.Kind.GROUND_OBJECT,
                                GpuDrawCommand.SubmissionPass.OPAQUE, 3, 3, -1, 0, 22)),
                List.of(), Map.of(), "picker-test");

        var result = new GpuPlanPicker().pick(plan, new CameraState(0, 0, 0, 0, 0),
                100, 100, 50, 50).orElseThrow();

        assertTrue(result.objectHit());
        assertEquals(11, result.objectId());
        assertEquals(tile.worldX(), result.objectTile().x());
        assertEquals(tile.worldY(), result.objectTile().y());
        assertEquals(0, result.tile().x());
        assertEquals(0, result.tile().y());
        assertTrue(result.hasGameObjectSceneMetadata());
        assertEquals(3200, result.objectSceneMinTile().x());
        assertEquals(3200, result.objectSceneMinTile().y());
        assertEquals(3202, result.objectSceneMaxTile().x());
        assertEquals(3201, result.objectSceneMaxTile().y());
        assertEquals(1, result.gameObjectSceneMetadata().rotation());
        assertEquals(512, result.gameObjectSceneMetadata().orientation());
        assertTrue(result.hasClientModelBounds());
        assertEquals(List.of(clientBounds), result.clientRenderableBounds());
        assertEquals(90, result.clientRenderableBounds().get(0).radius());
        assertEquals(186, result.clientRenderableBounds().get(0).diameter());
    }

    @Test
    void returnsStableSceneIdentityForSameDefinitionAtDifferentPlacements() {
        ClientModelBounds bounds = boundsForTriangle(-20, -20, 36, 20, -20, 36, 0, 20, 36);
        SceneObjectIdentity firstIdentity = SceneObjectIdentity.of(
                new WorldObject(11, 10, 0, 0, 0, 0), 1, 1);
        SceneObjectIdentity secondIdentity = SceneObjectIdentity.of(
                new WorldObject(11, 10, 0, 0, 1, 0), 1, 1);
        GpuUploadPlan plan = new GpuUploadPlan(
                List.of(
                        vertex(44, -20, 100, 0x1200),
                        vertex(84, -20, 100, 0x1200),
                        vertex(64, 20, 100, 0x1200),
                        vertex(172, -20, 100, 0x4A38),
                        vertex(212, -20, 100, 0x4A38),
                        vertex(192, 20, 100, 0x4A38)),
                List.of(0, 1, 2, 3, 4, 5),
                List.of(
                        objectCommand(WorldTileAddress.of(0, 0, 0), 0, 11,
                                firstIdentity, bounds),
                        objectCommand(WorldTileAddress.of(1, 0, 0), 3, 11,
                                secondIdentity, bounds)),
                List.of(), Map.of(), "identity-placement");

        PickResult result = new GpuPlanPicker().pick(plan,
                new CameraState(64, 0, 0, 0, 0),
                100, 100, 50, 50).orElseThrow();

        assertTrue(result.hasSceneObjectIdentity());
        assertEquals(firstIdentity, result.sceneObjectIdentity());
        assertEquals(firstIdentity.stableId(), result.sceneInstanceId());
        assertTrue(!firstIdentity.stableId().equals(secondIdentity.stableId()));
    }

    @Test
    void clientBoundsRejectTriangleFromWrongScenePlacement() {
        ClientModelBounds bounds = boundsForTriangle(-20, -20, 36, 20, -20, 36, 0, 20, 36);
        SceneObjectIdentity wrongPlacement = SceneObjectIdentity.of(
                new WorldObject(11, 10, 0, 0, 1, 0), 1, 1);
        GpuUploadPlan plan = new GpuUploadPlan(
                List.of(
                        vertex(44, -20, 100, 0x1200),
                        vertex(84, -20, 100, 0x1200),
                        vertex(64, 20, 100, 0x1200)),
                List.of(0, 1, 2),
                List.of(objectCommand(WorldTileAddress.of(1, 0, 0), 0, 11,
                        wrongPlacement, bounds)),
                List.of(), Map.of(), "bounds-reject");

        assertTrue(new GpuPlanPicker().pick(plan,
                new CameraState(64, 0, 0, 0, 0),
                100, 100, 50, 50).isEmpty());
    }

    @Test
    void overlappingClientBoundsStillUseExactTriangleAsFinalAnswer() {
        ClientModelBounds broadBounds = boundsForTriangle(
                -96, -20, 36, 96, -20, 36, 0, 20, 36);
        SceneObjectIdentity hitIdentity = SceneObjectIdentity.of(
                new WorldObject(11, 10, 0, 0, 0, 0), 1, 1);
        SceneObjectIdentity missIdentity = SceneObjectIdentity.of(
                new WorldObject(12, 10, 0, 0, 0, 0), 1, 1);
        GpuUploadPlan plan = new GpuUploadPlan(
                List.of(
                        vertex(44, -20, 100, 0x1200),
                        vertex(84, -20, 100, 0x1200),
                        vertex(64, 20, 100, 0x1200),
                        vertex(100, -20, 90, 0x4A38),
                        vertex(124, -20, 90, 0x4A38),
                        vertex(112, 20, 90, 0x4A38)),
                List.of(0, 1, 2, 3, 4, 5),
                List.of(
                        objectCommand(WorldTileAddress.of(0, 0, 0), 0, 11,
                                hitIdentity, broadBounds),
                        objectCommand(WorldTileAddress.of(0, 0, 0), 3, 12,
                                missIdentity, broadBounds)),
                List.of(), Map.of(), "bounds-exact");

        PickResult result = new GpuPlanPicker().pick(plan,
                new CameraState(64, 0, 0, 0, 0),
                100, 100, 50, 50).orElseThrow();

        assertEquals(11, result.objectId());
        assertEquals(hitIdentity, result.sceneObjectIdentity());
    }

    @Test
    void displacedWallDecorationUsesPerRenderablePlacementForBroadPhase() {
        ClientModelBounds bounds = boundsForTriangle(
                -10, -20, 36, 10, -20, 36, 0, 20, 36);
        SceneObjectIdentity identity = SceneObjectIdentity.of(
                new WorldObject(11, 5, 0, 0, 0, 0), 1, 1);
        GpuDrawCommand command = new GpuDrawCommand(
                WorldTileAddress.of(0, 0, 0), 0, 0,
                SceneLayer.Kind.WALL_DECORATION,
                GpuDrawCommand.SubmissionPass.OPAQUE,
                0, 3, -1, 0, 0, 11,
                GpuDrawCommand.RenderMode.DEFAULT,
                WallDecorationPresentation.none(),
                GameObjectSceneMetadata.none(),
                List.of(bounds),
                List.of(new ClientRenderablePlacement(64, 0)),
                identity, 0, 0, 0);
        GpuUploadPlan plan = new GpuUploadPlan(
                List.of(
                        vertex(118, -20, 100, 0x1200),
                        vertex(138, -20, 100, 0x1200),
                        vertex(128, 20, 100, 0x1200)),
                List.of(0, 1, 2),
                List.of(command),
                List.of(), Map.of(), "decor-placement");

        PickResult result = new GpuPlanPicker().pick(plan,
                new CameraState(128, 0, 0, 0, 0),
                100, 100, 50, 50).orElseThrow();

        assertEquals(11, result.objectId());
        assertEquals(identity, result.sceneObjectIdentity());
    }

    @Test
    void picksCorrectTileInMergedTerrainCommand() {
        // A single merged draw command covering tile (3200, 3200) and (3201, 3200)
        // Tile 3200, 3200: X in [3200*128, 3201*128]
        // Tile 3201, 3200: X in [3201*128, 3202*128]
        float originX = 3201 * 128.0f;
        float originZ = 3200 * 128.0f;
        GpuSceneVertex tA = vertex(originX + 10, 0, originZ + 10, 0x1200);
        GpuSceneVertex tB = vertex(originX + 100, 0, originZ + 10, 0x1200);
        GpuSceneVertex tC = vertex(originX + 10, 0, originZ + 100, 0x1200);
        WorldTileAddress tile = WorldTileAddress.of(3200, 3200, 0); // Merged command base address
        GpuUploadPlan plan = new GpuUploadPlan(
                List.of(tA, tB, tC),
                List.of(0, 1, 2),
                List.of(new GpuDrawCommand(tile, SceneLayer.Kind.TERRAIN,
                        GpuDrawCommand.SubmissionPass.OPAQUE, 0, 3, -1, 0, -1)),
                List.of(), Map.of(), "picker-terrain-test");

        // Camera directly above tile 3201, 3200 looking down (-pitch = 90 deg = -PI/2)
        CameraState camera = new CameraState(originX + 30, -100, originZ + 30, (float) -Math.PI / 2, 0);
        var result = new GpuPlanPicker().pick(plan, camera, 100, 100, 50, 50).orElseThrow();

        assertEquals(3201, result.tile().x());
        assertEquals(3200, result.tile().y());
    }

    private static GpuDrawCommand objectCommand(WorldTileAddress tile, int firstIndex,
                                                int objectId, SceneObjectIdentity identity,
                                                ClientModelBounds bounds) {
        return new GpuDrawCommand(tile, 0, 0, SceneLayer.Kind.GROUND_OBJECT,
                GpuDrawCommand.SubmissionPass.OPAQUE, firstIndex, 3, -1, 0, 0, objectId,
                GpuDrawCommand.RenderMode.DEFAULT, WallDecorationPresentation.none(),
                GameObjectSceneMetadata.none(), List.of(bounds), identity, 0);
    }

    private static ClientModelBounds boundsForTriangle(
            int ax, int ay, int az,
            int bx, int by, int bz,
            int cx, int cy, int cz) {
        return ClientModelBounds.calculate(List.of(
                new ModelVertex(ax, ay, az, 0, 0, 0, 0, 0, 0),
                new ModelVertex(bx, by, bz, 0, 0, 0, 0, 0, 0),
                new ModelVertex(cx, cy, cz, 0, 0, 0, 0, 0, 0)),
                0, false);
    }

    private static GpuSceneVertex vertex(float x, float y, float z, int hsl) {
        return new GpuSceneVertex(x, y, z, 0, 0, hsl,
                GpuColorEncoding.PACKED_JAGEX_HSL, 0,
                0, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0);
    }
}
