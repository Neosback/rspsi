package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    @Test
    void cacheReusesEntireSnapshotWhenInputsAreStable() {
        WorldTileAddress tile = WorldTileAddress.of(2, 0, 0);
        GpuDrawCommand command = new GpuDrawCommand(
                tile, SceneLayer.Kind.GROUND_OBJECT,
                GpuDrawCommand.SubmissionPass.OPAQUE, 0, 3, -1, 0, 1);
        GpuUploadPlan plan = new GpuUploadPlan(
                List.of(vertex(0, 0, 0), vertex(64, 0, 0), vertex(0, 64, 0)),
                List.of(0, 1, 2), List.of(command), List.of(), Map.of(),
                List.of(), "cache-stable");
        GpuCommandVisibility.Cache cache = new GpuCommandVisibility.Cache();
        CameraState camera = new CameraState(0, 64, 0, 0, 0);

        GpuCommandVisibility first = cache.resolve(plan, camera);
        GpuCommandVisibility second = cache.resolve(plan, camera);
        GpuCommandVisibility moved = cache.resolve(
                plan, new CameraState(128, 64, 0, 0, 0));

        assertSame(first, second);
        assertSame(first, moved,
                "camera movement is irrelevant when there are no camera-dependent filters");
    }

    @Test
    void cacheInvalidatesCameraDependentOcclusionOnCameraMovement() {
        WorldTileAddress tile = WorldTileAddress.of(2, 0, 0);
        SceneOccluder wall = new SceneOccluder(
                1, 1, 1, 0, 0, 0, 0,
                128, 128, 0, 128, 0, 128);
        GpuDrawCommand command = new GpuDrawCommand(
                tile, SceneLayer.Kind.GROUND_OBJECT,
                GpuDrawCommand.SubmissionPass.OPAQUE, 0, 3, -1, 0, 1);
        GpuUploadPlan plan = new GpuUploadPlan(
                List.of(vertex(256, 32, 32), vertex(256, 32, 96), vertex(256, 96, 64)),
                List.of(0, 1, 2), List.of(command), List.of(), Map.of(),
                List.of(wall), "cache-camera");
        GpuCommandVisibility.Cache cache = new GpuCommandVisibility.Cache();

        GpuCommandVisibility first = cache.resolve(
                plan, new CameraState(0, 64, 0, 0, 0));
        GpuCommandVisibility second = cache.resolve(
                plan, new CameraState(64, 64, 0, 0, 0));

        assertNotSame(first, second);
    }

    @Test
    void extendedSceneMembershipMaskIsNotRescannedWhenOnlyCameraMoves() {
        com.rspsi.editor.model.WorldRegionWindow source =
                new com.rspsi.editor.model.WorldRegionWindow(50, 50, 1, 1, Map.of());
        SceneWindow window = new SceneWindow(
                source, 3200, 3200, 1, 0, 0, -1, java.util.Set.of(), List.of());
        GpuDrawCommand inside = new GpuDrawCommand(
                WorldTileAddress.of(3200, 3200, 0), SceneLayer.Kind.TERRAIN,
                GpuDrawCommand.SubmissionPass.OPAQUE, 0, 3, -1, 0, -1);
        GpuDrawCommand outside = new GpuDrawCommand(
                WorldTileAddress.of(3152, 3200, 0), SceneLayer.Kind.TERRAIN,
                GpuDrawCommand.SubmissionPass.OPAQUE, 3, 3, -1, 0, -1);
        CountingGeometry geometry = new CountingGeometry(List.of(inside, outside));
        GpuCommandVisibility.Cache cache = new GpuCommandVisibility.Cache();
        Optional<SceneWindow> sceneWindow = Optional.of(window);

        GpuCommandVisibility first = cache.resolve(
                geometry, new CameraState(3200 * 128f, 0, 3200 * 128f, 0, 0),
                List.of(), sceneWindow);
        int firstCommandReads = geometry.commandReads;
        GpuCommandVisibility second = cache.resolve(
                geometry, new CameraState(3210 * 128f, 0, 3200 * 128f, 0, 0),
                List.of(), sceneWindow);

        assertTrue(first.visible(0));
        assertFalse(first.visible(1));
        assertTrue(second.visible(0));
        assertFalse(second.visible(1));
        assertEquals(firstCommandReads, geometry.commandReads,
                "camera movement must not rescan the static extended-scene membership mask");
    }

    private static final class CountingGeometry implements GpuCommandGeometry {
        private final List<GpuDrawCommand> commands;
        private int commandReads;

        private CountingGeometry(List<GpuDrawCommand> commands) {
            this.commands = commands;
        }

        @Override public int commandCount() {
            return commands.size();
        }

        @Override public GpuDrawCommand command(int commandIndex) {
            commandReads++;
            return commands.get(commandIndex);
        }

        @Override public GpuSceneVertex indexedVertex(int commandIndex, int indexOffset) {
            throw new UnsupportedOperationException();
        }

        @Override public int vertexCount() {
            return 0;
        }

        @Override public int indexCount() {
            return 0;
        }

        @Override public void forEachUniqueVertex(java.util.function.Consumer<GpuSceneVertex> consumer) {
        }
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
