package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SceneOcclusionResolverTest {
    @Test
    void typeOneWallOccludesCompleteGeometryBehindItsXPlane() {
        WorldTileAddress tile = WorldTileAddress.of(2, 0, 0);
        GpuDrawCommand command = new GpuDrawCommand(tile, SceneLayer.Kind.GROUND_OBJECT,
                GpuDrawCommand.SubmissionPass.OPAQUE, 0, 3, -1, 0, 1);
        SceneOccluder wall = new SceneOccluder(1, 1, 1, 0, 0, 0, 0,
                128, 128, 0, 128, 0, 128);
        CameraState camera = new CameraState(0, 64, 0, 0, 0);

        assertTrue(SceneOcclusionResolver.occludesTriangle(command,
                vertex(256, 32, 32), vertex(256, 32, 96), vertex(256, 96, 64),
                camera, List.of(wall)));
        assertFalse(SceneOcclusionResolver.occludesTriangle(command,
                vertex(64, 32, 32), vertex(64, 32, 96), vertex(64, 96, 64),
                camera, List.of(wall)));
    }

    @Test
    void occludesCommandOnlyWhenEveryTriangleInTheCommandIsOccluded() {
        // Command-granularity counterpart of the test above: two already
        // merged commands share one plan, one fully behind the wall and one
        // fully in front of it. occludesCommand must never rebuild indices -
        // it only answers a per-command skip/draw question over the plan
        // that is already resident on the GPU.
        WorldTileAddress tile = WorldTileAddress.of(2, 0, 0);
        SceneOccluder wall = new SceneOccluder(1, 1, 1, 0, 0, 0, 0,
                128, 128, 0, 128, 0, 128);
        CameraState camera = new CameraState(0, 64, 0, 0, 0);

        List<GpuSceneVertex> vertices = List.of(
                vertex(256, 32, 32), vertex(256, 32, 96), vertex(256, 96, 64),
                vertex(64, 32, 32), vertex(64, 32, 96), vertex(64, 96, 64));
        List<Integer> indices = List.of(0, 1, 2, 3, 4, 5);
        GpuDrawCommand occludedCommand = new GpuDrawCommand(tile, SceneLayer.Kind.GROUND_OBJECT,
                GpuDrawCommand.SubmissionPass.OPAQUE, 0, 3, -1, 0, 1);
        GpuDrawCommand visibleCommand = new GpuDrawCommand(tile, SceneLayer.Kind.GROUND_OBJECT,
                GpuDrawCommand.SubmissionPass.OPAQUE, 3, 3, -1, 0, 1);
        GpuUploadPlan plan = new GpuUploadPlan(vertices, indices,
                List.of(occludedCommand, visibleCommand), List.of(), Map.of(), List.of(wall),
                "fingerprint");

        assertTrue(SceneOcclusionResolver.occludesCommand(occludedCommand, plan, camera, List.of(wall)));
        assertFalse(SceneOcclusionResolver.occludesCommand(visibleCommand, plan, camera, List.of(wall)));
    }

    @Test
    void occludesCommandIsFalseWithNoOccluders() {
        WorldTileAddress tile = WorldTileAddress.of(2, 0, 0);
        List<GpuSceneVertex> vertices = List.of(
                vertex(256, 32, 32), vertex(256, 32, 96), vertex(256, 96, 64));
        List<Integer> indices = List.of(0, 1, 2);
        GpuDrawCommand command = new GpuDrawCommand(tile, SceneLayer.Kind.GROUND_OBJECT,
                GpuDrawCommand.SubmissionPass.OPAQUE, 0, 3, -1, 0, 1);
        GpuUploadPlan plan = new GpuUploadPlan(vertices, indices, List.of(command),
                List.of(), Map.of(), List.of(), "fingerprint");
        CameraState camera = new CameraState(0, 64, 0, 0, 0);

        assertFalse(SceneOcclusionResolver.occludesCommand(command, plan, camera, List.of()));
    }

    @Test
    void rejectsNonPlanarVerticalOccluderBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> new SceneOccluder(1, 0, 1, 0, 1, 0, 0,
                        128, 256, 0, 128, 0, 128));
        assertThrows(IllegalArgumentException.class,
                () -> new SceneOccluder(2, 0, 1, 0, 1, 0, 0,
                        0, 128, 128, 256, 0, 128));
    }

    private static GpuSceneVertex vertex(float x, float y, float z) {
        return new GpuSceneVertex(x, y, z, 0, 0, 0,
                GpuColorEncoding.PACKED_JAGEX_HSL, 0,
                0, 0, 0, 0, -1, 255, 0, 0, 0, 0, 0);
    }
}
