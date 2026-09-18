package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;
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
        GpuUploadPlan plan = new GpuUploadPlan(
                List.of(nearA, nearB, nearC, farA, farB, farC),
                List.of(0, 1, 2, 3, 4, 5),
                List.of(
                        new GpuDrawCommand(tile, SceneLayer.Kind.GROUND_OBJECT,
                                GpuDrawCommand.SubmissionPass.OPAQUE, 0, 3, -1, 0, 11),
                        new GpuDrawCommand(tile, SceneLayer.Kind.GROUND_OBJECT,
                                GpuDrawCommand.SubmissionPass.OPAQUE, 3, 3, -1, 0, 22)),
                List.of(), Map.of(), "picker-test");

        var result = new GpuPlanPicker().pick(plan, new CameraState(0, 0, 0, 0, 0),
                100, 100, 50, 50).orElseThrow();

        assertTrue(result.objectHit());
        assertEquals(11, result.objectId());
        assertEquals(tile.worldX(), result.tile().x());
        assertEquals(tile.worldY(), result.tile().y());
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
        CameraState camera = new CameraState(originX + 30, 100, originZ + 30, (float) -Math.PI / 2, 0);
        var result = new GpuPlanPicker().pick(plan, camera, 100, 100, 50, 50).orElseThrow();

        assertEquals(3201, result.tile().x());
        assertEquals(3200, result.tile().y());
    }

    private static GpuSceneVertex vertex(float x, float y, float z, int hsl) {
        return new GpuSceneVertex(x, y, z, 0, 0, hsl,
                GpuColorEncoding.PACKED_JAGEX_HSL, 0,
                0, 0, 0, 0, -1, 0, 0);
    }
}
