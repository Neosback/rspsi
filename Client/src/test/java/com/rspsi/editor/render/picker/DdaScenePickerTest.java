package com.rspsi.editor.render.picker;

import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.render.CameraState;
import com.rspsi.editor.render.GpuColorEncoding;
import com.rspsi.editor.render.GpuDrawCommand;
import com.rspsi.editor.render.GpuSceneVertex;
import com.rspsi.editor.render.GpuUploadPlan;
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

    private static GpuSceneVertex vertex(float x, float y, float z) {
        return new GpuSceneVertex(x, y, z, 0, 0, 0x1200,
                GpuColorEncoding.PACKED_JAGEX_HSL, 0,
                0, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0);
    }
}
