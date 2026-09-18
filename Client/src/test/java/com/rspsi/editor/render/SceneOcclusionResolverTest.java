package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static GpuSceneVertex vertex(float x, float y, float z) {
        return new GpuSceneVertex(x, y, z, 0, 0, 0,
                GpuColorEncoding.PACKED_JAGEX_HSL, 0,
                0, 0, 0, 0, -1, 255, 0);
    }
}
