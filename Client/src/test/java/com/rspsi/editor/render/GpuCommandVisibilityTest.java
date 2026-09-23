package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuCommandVisibilityTest {
    @Test
    void marksOnlyFullyOccludedCommandsHidden() {
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

        GpuCommandVisibility visibility = GpuCommandVisibility.of(plan, camera);

        assertFalse(visibility.visible(0));
        assertTrue(visibility.visible(1));
    }

    @Test
    void everyCommandIsVisibleWhenThePlanHasNoOccluders() {
        WorldTileAddress tile = WorldTileAddress.of(2, 0, 0);
        List<GpuSceneVertex> vertices = List.of(
                vertex(256, 32, 32), vertex(256, 32, 96), vertex(256, 96, 64));
        List<Integer> indices = List.of(0, 1, 2);
        GpuDrawCommand command = new GpuDrawCommand(tile, SceneLayer.Kind.GROUND_OBJECT,
                GpuDrawCommand.SubmissionPass.OPAQUE, 0, 3, -1, 0, 1);
        GpuUploadPlan plan = new GpuUploadPlan(vertices, indices, List.of(command),
                List.of(), Map.of(), List.of(), "fingerprint");
        CameraState camera = new CameraState(0, 64, 0, 0, 0);

        GpuCommandVisibility visibility = GpuCommandVisibility.of(plan, camera);

        assertTrue(visibility.visible(0));
    }

    @Test
    void rejectsOutOfRangeCommandIndex() {
        WorldTileAddress tile = WorldTileAddress.of(2, 0, 0);
        List<GpuSceneVertex> vertices = List.of(
                vertex(256, 32, 32), vertex(256, 32, 96), vertex(256, 96, 64));
        List<Integer> indices = List.of(0, 1, 2);
        GpuDrawCommand command = new GpuDrawCommand(tile, SceneLayer.Kind.GROUND_OBJECT,
                GpuDrawCommand.SubmissionPass.OPAQUE, 0, 3, -1, 0, 1);
        GpuUploadPlan plan = new GpuUploadPlan(vertices, indices, List.of(command),
                List.of(), Map.of(), List.of(), "fingerprint");
        CameraState camera = new CameraState(0, 64, 0, 0, 0);

        GpuCommandVisibility visibility = GpuCommandVisibility.of(plan, camera);

        assertThrows(IndexOutOfBoundsException.class, () -> visibility.visible(1));
    }

    private static GpuSceneVertex vertex(float x, float y, float z) {
        return new GpuSceneVertex(x, y, z, 0, 0, 0,
                GpuColorEncoding.PACKED_JAGEX_HSL, 0,
                0, 0, 0, 0, -1, 255, 0, 0, 0, 0, 0);
    }
    @Test
    void topLevelExtendedSceneGateKeepsBorderCommandsAndRejectsOutsideZones() {
        com.rspsi.editor.model.WorldRegionWindow source =
                new com.rspsi.editor.model.WorldRegionWindow(50, 50, 1, 1, Map.of());
        SceneWindow window = new SceneWindow(
                source, 3200, 3200, 1, 0, 0, -1, java.util.Set.of(), List.of());

        WorldTileAddress borderTile = WorldTileAddress.of(3160, 3200, 0); // extended zone (0,5)
        WorldTileAddress normalTile = WorldTileAddress.of(3200, 3200, 0); // extended zone (5,5)
        WorldTileAddress outsideTile = WorldTileAddress.of(3152, 3200, 0); // one zone west of extended scene

        List<GpuSceneVertex> vertices = List.of(
                vertex(3160 * 128.0f, 0, 3200 * 128.0f),
                vertex(3160 * 128.0f + 64, 0, 3200 * 128.0f),
                vertex(3160 * 128.0f, 64, 3200 * 128.0f),
                vertex(3200 * 128.0f, 0, 3200 * 128.0f),
                vertex(3200 * 128.0f + 64, 0, 3200 * 128.0f),
                vertex(3200 * 128.0f, 64, 3200 * 128.0f),
                vertex(3152 * 128.0f, 0, 3200 * 128.0f),
                vertex(3152 * 128.0f + 64, 0, 3200 * 128.0f),
                vertex(3152 * 128.0f, 64, 3200 * 128.0f));
        List<Integer> indices = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8);
        List<GpuDrawCommand> commands = List.of(
                new GpuDrawCommand(borderTile, SceneLayer.Kind.TERRAIN,
                        GpuDrawCommand.SubmissionPass.OPAQUE, 0, 3, -1, 0, -1),
                new GpuDrawCommand(normalTile, SceneLayer.Kind.TERRAIN,
                        GpuDrawCommand.SubmissionPass.OPAQUE, 3, 3, -1, 0, -1),
                new GpuDrawCommand(outsideTile, SceneLayer.Kind.TERRAIN,
                        GpuDrawCommand.SubmissionPass.OPAQUE, 6, 3, -1, 0, -1));
        GpuUploadPlan plan = new GpuUploadPlan(
                vertices, indices, commands, List.of(), Map.of(), List.of(),
                "extended-visibility", java.util.Optional.of(window));

        float cameraX = 3160 * 128.0f + 64;
        float cameraZ = 3200 * 128.0f + 64;
        GpuCommandVisibility visibility = GpuCommandVisibility.of(
                plan, new CameraState(cameraX, 0, cameraZ, 0, 0));

        assertTrue(visibility.extendedSceneApplied());
        assertTrue(visibility.cameraInExtendedBorder());
        assertTrue(visibility.visible(0));
        assertTrue(visibility.visible(1));
        assertFalse(visibility.visible(2));
    }

}
