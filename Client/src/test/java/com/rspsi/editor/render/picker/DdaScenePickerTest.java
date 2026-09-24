package com.rspsi.editor.render.picker;

import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.render.CameraState;
import com.rspsi.editor.render.ClientModelBounds;
import com.rspsi.editor.render.GameObjectSceneMetadata;
import com.rspsi.editor.render.GpuColorEncoding;
import com.rspsi.editor.render.GpuDrawCommand;
import com.rspsi.editor.render.GpuSceneVertex;
import com.rspsi.editor.render.GpuUploadPlan;
import com.rspsi.editor.render.PickerId;
import com.rspsi.editor.render.SceneLayer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DdaScenePickerTest {
    @Test
    void picksNearestObjectAndStopsBeforeScanningTheWholePlan() {
        List<GpuSceneVertex> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        List<GpuDrawCommand> commands = new ArrayList<>();

        for (int tile = 0; tile < 64; tile++) {
            int base = vertices.size();
            float z = tile * 128.0f + 64.0f;
            vertices.add(vertex(-24.0f, -24.0f, z));
            vertices.add(vertex(24.0f, -24.0f, z));
            vertices.add(vertex(0.0f, 24.0f, z));
            indices.add(base);
            indices.add(base + 1);
            indices.add(base + 2);
            commands.add(new GpuDrawCommand(WorldTileAddress.of(0, tile, 0),
                    SceneLayer.Kind.GROUND_OBJECT, GpuDrawCommand.SubmissionPass.OPAQUE,
                    tile * 3, 3, -1, 0, tile));
        }

        GpuUploadPlan plan = new GpuUploadPlan(vertices, indices, commands, List.of(), Map.of(),
                "dda-nearest");
        DdaScenePicker picker = new DdaScenePicker();

        var hit = picker.pick(plan, new CameraState(0, 0, 0, 0, 0),
                100, 100, 50, 50).orElseThrow();

        assertEquals(0, hit.objectId());
        assertTrue(picker.lastMetrics().visitedCells() <= 2);
        assertTrue(picker.lastMetrics().triangleTests() < 64);
    }

    @Test
    void resolvesActualTerrainTileInsideMergedTerrainCommand() {
        float originX = 3201 * 128.0f;
        float originZ = 3200 * 128.0f;
        GpuUploadPlan plan = new GpuUploadPlan(
                List.of(
                        vertex(originX + 10, 0, originZ + 10),
                        vertex(originX + 100, 0, originZ + 10),
                        vertex(originX + 10, 0, originZ + 100)),
                List.of(0, 1, 2),
                List.of(new GpuDrawCommand(WorldTileAddress.of(3200, 3200, 0),
                        SceneLayer.Kind.TERRAIN, GpuDrawCommand.SubmissionPass.OPAQUE,
                        0, 3, -1, 0, -1)),
                List.of(), Map.of(), "dda-terrain");

        DdaScenePicker picker = new DdaScenePicker();
        var hit = picker.pick(plan,
                new CameraState(originX + 30, -100, originZ + 30,
                        (float) -Math.PI / 2, 0),
                100, 100, 50, 50).orElseThrow();

        assertEquals(3201, hit.tile().x());
        assertEquals(3200, hit.tile().y());
    }

    @Test
    void planeRestrictionSkipsOtherPlaneBuckets() {
        GpuUploadPlan plan = new GpuUploadPlan(
                List.of(
                        vertex(-20, -20, 100), vertex(20, -20, 100), vertex(0, 20, 100),
                        vertex(-20, -20, 200), vertex(20, -20, 200), vertex(0, 20, 200)),
                List.of(0, 1, 2, 3, 4, 5),
                List.of(
                        new GpuDrawCommand(WorldTileAddress.of(0, 0, 1),
                                SceneLayer.Kind.GROUND_OBJECT, GpuDrawCommand.SubmissionPass.OPAQUE,
                                0, 3, -1, 0, 11),
                        new GpuDrawCommand(WorldTileAddress.of(0, 1, 0),
                                SceneLayer.Kind.GROUND_OBJECT, GpuDrawCommand.SubmissionPass.OPAQUE,
                                3, 3, -1, 0, 22)),
                List.of(), Map.of(), "dda-plane");

        DdaScenePicker picker = new DdaScenePicker();
        var hit = picker.pick(plan, new CameraState(0, 0, 0, 0, 0),
                100, 100, 50, 50,
                com.rspsi.editor.render.SceneCameraProjection.editorDefault(), 0).orElseThrow();

        assertEquals(22, hit.objectId());
        assertEquals(0, hit.plane());
    }

    @Test
    void resolvesObjectAnchorTileForMultiTileModel() {
        // Object is anchored at tile (3200, 3200), but has geometry extending into tile (3201, 3200)
        float originX = 3201 * 128.0f;
        float originZ = 3200 * 128.0f;
        GpuUploadPlan plan = new GpuUploadPlan(
                List.of(
                        vertex(originX + 10, 0, originZ + 10),
                        vertex(originX + 100, 0, originZ + 10),
                        vertex(originX + 10, 0, originZ + 100)),
                List.of(0, 1, 2),
                List.of(new GpuDrawCommand(WorldTileAddress.of(3200, 3200, 0),
                        SceneLayer.Kind.GROUND_OBJECT, GpuDrawCommand.SubmissionPass.OPAQUE,
                        0, 3, -1, 0, 500)),
                List.of(), Map.of(), "dda-object-anchor");

        DdaScenePicker picker = new DdaScenePicker();
        var hit = picker.pick(plan,
                new CameraState(originX + 30, -100, originZ + 30,
                        (float) -Math.PI / 2, 0),
                100, 100, 50, 50).orElseThrow();

        assertTrue(hit.objectHit());
        assertEquals(500, hit.objectId());
        // Exact triangle hit tile is (3201, 3200)
        assertEquals(3201, hit.tile().x());
        assertEquals(3200, hit.tile().y());
        // Object root anchor tile is (3200, 3200)
        assertEquals(3200, hit.objectTile().x());
        assertEquals(3200, hit.objectTile().y());
    }


    @Test
    void gpuPickerIdNarrowsCandidatesWithoutLosingSameTileObjectIdentity() {
        int slot = PickerId.slotFor(SceneLayer.Kind.GROUND_OBJECT);
        GpuUploadPlan plan = new GpuUploadPlan(
                List.of(
                        pickerVertex(-20, -20, 100, 0, 0, 0, slot),
                        pickerVertex(20, -20, 100, 0, 0, 0, slot),
                        pickerVertex(0, 20, 100, 0, 0, 0, slot),
                        pickerVertex(-20, -20, 200, 0, 0, 0, slot),
                        pickerVertex(20, -20, 200, 0, 0, 0, slot),
                        pickerVertex(0, 20, 200, 0, 0, 0, slot)),
                List.of(0, 1, 2, 3, 4, 5),
                List.of(
                        new GpuDrawCommand(WorldTileAddress.of(0, 0, 0),
                                SceneLayer.Kind.GROUND_OBJECT, GpuDrawCommand.SubmissionPass.OPAQUE,
                                0, 3, -1, 0, 101),
                        new GpuDrawCommand(WorldTileAddress.of(0, 0, 0),
                                SceneLayer.Kind.GROUND_OBJECT, GpuDrawCommand.SubmissionPass.OPAQUE,
                                3, 3, -1, 0, 202)),
                List.of(), Map.of(), "dda-gpu-id-collision");

        DdaScenePicker picker = new DdaScenePicker();
        int packed = PickerId.encode(0, 0, 0, slot);
        var hit = picker.pickMatchingId(
                plan, null, new CameraState(0, 0, 0, 0, 0),
                100, 100, 50, 50,
                com.rspsi.editor.render.SceneCameraProjection.editorDefault(), null, packed)
                .orElseThrow();

        assertEquals(101, hit.objectId(),
                "same tile/layer IDs must still resolve the exact nearest object");
        assertTrue(picker.pickMatchingId(
                plan, null, new CameraState(0, 0, 0, 0, 0),
                100, 100, 50, 50,
                com.rspsi.editor.render.SceneCameraProjection.editorDefault(), null,
                PickerId.encode(0, 1, 0, slot)).isEmpty());
    }

    @Test
    void clientAabbBroadPhaseRejectsUnrelatedCommandsBeforeTriangleTests() {
        List<GpuSceneVertex> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        List<GpuDrawCommand> commands = new ArrayList<>();
        int[] centers = {8, 24, 40, 64, 88, 104, 120};

        for (int commandIndex = 0; commandIndex < centers.length; commandIndex++) {
            int centerX = centers[commandIndex];
            int base = vertices.size();
            vertices.add(vertex(centerX - 4, -4, 100));
            vertices.add(vertex(centerX + 4, -4, 100));
            vertices.add(vertex(centerX, 4, 100));
            indices.add(base);
            indices.add(base + 1);
            indices.add(base + 2);

            ClientModelBounds bounds = new ClientModelBounds(true, 8, 8, 8, 12, 24, false,
                    new ClientModelBounds.Aabb(true, 0,
                            centerX - 64, 0, 36, 8, 8, 8));
            commands.add(new GpuDrawCommand(WorldTileAddress.of(0, 0, 0), 0, 0,
                    SceneLayer.Kind.GROUND_OBJECT, GpuDrawCommand.SubmissionPass.OPAQUE,
                    commandIndex * 3, 3, -1, 0, 0, commandIndex,
                    GpuDrawCommand.RenderMode.DEFAULT,
                    com.rspsi.editor.render.WallDecorationPresentation.none(),
                    GameObjectSceneMetadata.none(), List.of(bounds)));
        }

        GpuUploadPlan plan = new GpuUploadPlan(vertices, indices, commands,
                List.of(), Map.of(), "aabb-broad-phase");
        DdaScenePicker picker = new DdaScenePicker();

        var hit = picker.pick(plan, new CameraState(64, 0, 0, 0, 0),
                100, 100, 50, 50).orElseThrow();

        assertEquals(3, hit.objectId());
        assertEquals(7, picker.lastMetrics().broadPhaseTests());
        assertEquals(6, picker.lastMetrics().broadPhaseRejects());
        assertEquals(6, picker.lastMetrics().broadPhaseTriangleSkips());
        assertEquals(1, picker.lastMetrics().triangleTests(),
                "only the AABB-hit command should reach exact triangle intersection");
    }

    @Test
    void gameObjectAabbUsesRotatedSceneFootprintCenter() {
        ClientModelBounds bounds = new ClientModelBounds(true, 16, 16, 32, 36, 72, false,
                new ClientModelBounds.Aabb(true, 0, 20, 0, 20, 24, 24, 24));
        GameObjectSceneMetadata scene = GameObjectSceneMetadata.of(0, 0, 2, 3, 0, 0);
        GpuDrawCommand command = new GpuDrawCommand(WorldTileAddress.of(0, 0, 0), 0, 0,
                SceneLayer.Kind.GROUND_OBJECT, GpuDrawCommand.SubmissionPass.OPAQUE,
                0, 3, -1, 0, 0, 44,
                GpuDrawCommand.RenderMode.DEFAULT,
                com.rspsi.editor.render.WallDecorationPresentation.none(),
                scene, List.of(bounds));

        GpuUploadPlan plan = new GpuUploadPlan(
                List.of(
                        vertex(140, -8, 212),
                        vertex(156, -8, 212),
                        vertex(148, 8, 212)),
                List.of(0, 1, 2), List.of(command), List.of(), Map.of(),
                "multi-tile-aabb-center");
        DdaScenePicker picker = new DdaScenePicker();

        var hit = picker.pick(plan, new CameraState(148, 0, 0, 0, 0),
                100, 100, 50, 50).orElseThrow();

        assertEquals(44, hit.objectId());
        assertEquals(1, picker.lastMetrics().broadPhaseTests());
        assertEquals(0, picker.lastMetrics().broadPhaseRejects());
        assertEquals(1, picker.lastMetrics().triangleTests());
    }

    private static GpuSceneVertex vertex(float x, float y, float z) {
        return new GpuSceneVertex(x, y, z, 0, 0, 0x1200,
                GpuColorEncoding.PACKED_JAGEX_HSL, 0,
                0, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0);
    }

    private static GpuSceneVertex pickerVertex(
            float x, float y, float z,
            int plane, int tileX, int tileY, int slot) {
        return new GpuSceneVertex(x, y, z, 0, 0, 0x1200,
                GpuColorEncoding.PACKED_JAGEX_HSL, 0,
                0, 0, 0, 0, -1, 0, 0,
                plane, tileX, tileY, slot);
    }
}
