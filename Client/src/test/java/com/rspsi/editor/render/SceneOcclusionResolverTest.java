package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SceneOcclusionResolverTest {
    @Test
    void boundedIdentityCacheKeepsCurrentGeometryAndBuildsOnlyRequestedCommands() {
        SceneOcclusionResolver.clearBoundsCacheForTests();
        List<CountingGeometry> geometries = new java.util.ArrayList<>();

        for (int index = 0; index < 5; index++) {
            CountingGeometry geometry = countingGeometry("bounds-" + index);
            geometries.add(geometry);
            SceneOcclusionResolver.boundsOf(0, geometry.command(0), geometry);
            assertEquals(3, geometry.indexedVertexReads,
                    "first lookup should inspect only the requested triangle");
        }

        CountingGeometry current = geometries.get(4);
        for (int repeat = 0; repeat < 20; repeat++) {
            SceneOcclusionResolver.boundsOf(0, current.command(0), current);
        }
        assertEquals(3, current.indexedVertexReads,
                "the current geometry must remain cached after capacity eviction");
        assertEquals(4, SceneOcclusionResolver.cachedGeometryCountForTests());

        SceneOcclusionResolver.boundsOf(1, current.command(1), current);
        assertEquals(6, current.indexedVertexReads,
                "a cache entry must populate command bounds lazily rather than scene-wide");

        CountingGeometry evicted = geometries.get(0);
        SceneOcclusionResolver.boundsOf(0, evicted.command(0), evicted);
        assertEquals(6, evicted.indexedVertexReads,
                "the least-recently-used geometry should be rebuilt after eviction");
    }

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
    void bridgeShiftedCommandUsesCurrentScenePlaneForOcclusion() {
        WorldTileAddress authoredTile = WorldTileAddress.of(2, 0, 1);
        GpuDrawCommand command = new GpuDrawCommand(
                authoredTile, 0, 0,
                SceneLayer.Kind.GROUND_OBJECT, GpuDrawCommand.SubmissionPass.OPAQUE,
                0, 3, -1, 0, 0, 1, GpuDrawCommand.RenderMode.DEFAULT,
                WallDecorationPresentation.none());
        SceneOccluder lowerPlaneWall = new SceneOccluder(1, 1, 1, 0, 0, 0, 0,
                128, 128, 0, 128, 0, 128);
        CameraState camera = new CameraState(0, 64, 0, 0, 0);

        assertTrue(SceneOcclusionResolver.occludesTriangle(command,
                vertex(256, 32, 32), vertex(256, 32, 96), vertex(256, 96, 64),
                camera, List.of(lowerPlaneWall)));
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

    private static CountingGeometry countingGeometry(String fingerprint) {
        WorldTileAddress firstTile = WorldTileAddress.of(0, 0, 0);
        WorldTileAddress secondTile = WorldTileAddress.of(1, 0, 0);
        List<GpuSceneVertex> vertices = List.of(
                vertex(0, 0, 0), vertex(64, 0, 0), vertex(0, 0, 64),
                vertex(128, 0, 0), vertex(192, 0, 0), vertex(128, 0, 64));
        List<Integer> indices = List.of(0, 1, 2, 3, 4, 5);
        List<GpuDrawCommand> commands = List.of(
                new GpuDrawCommand(firstTile, SceneLayer.Kind.GROUND_OBJECT,
                        GpuDrawCommand.SubmissionPass.ALPHA, 0, 3, -1, 0, 1),
                new GpuDrawCommand(secondTile, SceneLayer.Kind.GROUND_OBJECT,
                        GpuDrawCommand.SubmissionPass.ALPHA, 3, 3, -1, 0, 2));
        return new CountingGeometry(new GpuUploadPlan(
                vertices, indices, commands, List.of(), Map.of(), List.of(), fingerprint));
    }

    private static final class CountingGeometry implements GpuCommandGeometry {
        private final GpuUploadPlan delegate;
        private int indexedVertexReads;

        private CountingGeometry(GpuUploadPlan delegate) {
            this.delegate = delegate;
        }

        @Override
        public int commandCount() {
            return delegate.commandCount();
        }

        @Override
        public GpuDrawCommand command(int commandIndex) {
            return delegate.command(commandIndex);
        }

        @Override
        public GpuSceneVertex indexedVertex(int commandIndex, int indexOffset) {
            indexedVertexReads++;
            return delegate.indexedVertex(commandIndex, indexOffset);
        }

        @Override
        public int vertexCount() {
            return delegate.vertexCount();
        }

        @Override
        public int indexCount() {
            return delegate.indexCount();
        }

        @Override
        public void forEachUniqueVertex(java.util.function.Consumer<GpuSceneVertex> consumer) {
            delegate.forEachUniqueVertex(consumer);
        }
    }

    private static GpuSceneVertex vertex(float x, float y, float z) {
        return new GpuSceneVertex(x, y, z, 0, 0, 0,
                GpuColorEncoding.PACKED_JAGEX_HSL, 0,
                0, 0, 0, 0, -1, 255, 0, 0, 0, 0, 0);
    }
}
