package com.rspsi.editor.render.picker;

import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.render.GpuDrawCommand;
import com.rspsi.editor.render.GpuSceneVertex;
import com.rspsi.editor.render.GpuUploadPlan;
import com.rspsi.editor.render.GpuZonedUploadPlan;
import com.rspsi.editor.render.IncrementalGpuZonedUploadPlanBuilder;
import com.rspsi.editor.render.SceneLayer;
import com.rspsi.editor.render.WorldZoneCoordinate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PickingSpatialIndexTest {
    @Test
    void rebuildsOnlyDirtySourceAndSpatialZone() {
        GpuUploadPlan first = twoZonePlan(0.0f);
        GpuUploadPlan second = twoZonePlan(8.0f);
        WorldZoneCoordinate zone0 = new WorldZoneCoordinate(0, 0, 0);
        WorldZoneCoordinate zone1 = new WorldZoneCoordinate(0, 1, 0);

        IncrementalGpuZonedUploadPlanBuilder builder = new IncrementalGpuZonedUploadPlanBuilder();
        GpuZonedUploadPlan zonedFirst = builder.buildInitial(first);
        GpuZonedUploadPlan zonedSecond = builder.build(second, Set.of(zone1));

        assertSame(zonedFirst.zones().get(zone0), zonedSecond.zones().get(zone0));

        PickingSpatialIndex index = new PickingSpatialIndex();
        index.indexFor(first, zonedFirst);
        PickingSpatialIndex.Metrics initial = index.lastMetrics();
        assertEquals(2, initial.rebuiltSourceZones());
        assertEquals(0, initial.reusedSourceZones());
        assertEquals(2, initial.rebuiltZones());

        index.indexFor(second, zonedSecond);
        PickingSpatialIndex.Metrics incremental = index.lastMetrics();
        assertEquals(1, incremental.rebuiltSourceZones());
        assertEquals(1, incremental.reusedSourceZones());
        assertEquals(1, incremental.rebuiltZones());
        assertEquals(1, incremental.reusedZones());
        assertEquals(2, incremental.totalZones());
    }

    @Test
    void indexesTriangleIntoNeighborZoneWhenGeometryCrossesAnchorBoundary() {
        float boundary = 8 * 128.0f;
        GpuUploadPlan plan = new GpuUploadPlan(
                List.of(
                        vertex(boundary - 16, -20, 32),
                        vertex(boundary + 48, -20, 32),
                        vertex(boundary + 16, 20, 96)),
                List.of(0, 1, 2),
                List.of(command(7, 0, 0, 3, 77)),
                List.of(), Map.of(), "cross-zone");

        GpuZonedUploadPlan zoned = new IncrementalGpuZonedUploadPlanBuilder().buildInitial(plan);
        PickingSpatialIndex index = new PickingSpatialIndex();
        PickingSpatialIndex.Snapshot snapshot = index.indexFor(plan, zoned);

        assertEquals(1, snapshot.bucket(0, 7, 0).length);
        assertEquals(1, snapshot.bucket(0, 8, 0).length);
    }

    @Test
    void generationStampSuppressesDuplicateTriangleWithoutClearingPerPick() {
        GpuUploadPlan plan = new GpuUploadPlan(
                List.of(
                        vertex(120, -20, 32),
                        vertex(180, -20, 32),
                        vertex(150, 20, 96)),
                List.of(0, 1, 2),
                List.of(command(0, 0, 0, 3, 91)),
                List.of(), Map.of(), "generation-stamp");

        GpuZonedUploadPlan zoned = new IncrementalGpuZonedUploadPlanBuilder().buildInitial(plan);
        PickingSpatialIndex index = new PickingSpatialIndex();
        PickingSpatialIndex.Snapshot snapshot = index.indexFor(plan, zoned);
        PickingSpatialIndex.TriangleRef triangle = snapshot.bucket(0, 0, 0)[0];

        int firstGeneration = index.beginPick();
        assertTrue(triangle.markTested(firstGeneration));
        assertFalse(triangle.markTested(firstGeneration));

        int secondGeneration = index.beginPick();
        assertTrue(triangle.markTested(secondGeneration));
    }

    private static GpuUploadPlan twoZonePlan(float secondOffsetX) {
        float firstX = 1 * 128.0f + 64.0f;
        float secondX = 9 * 128.0f + 64.0f + secondOffsetX;
        return new GpuUploadPlan(
                List.of(
                        vertex(firstX - 20, -20, 32),
                        vertex(firstX + 20, -20, 32),
                        vertex(firstX, 20, 96),
                        vertex(secondX - 20, -20, 32),
                        vertex(secondX + 20, -20, 32),
                        vertex(secondX, 20, 96)),
                List.of(0, 1, 2, 3, 4, 5),
                List.of(
                        command(1, 0, 0, 3, 10),
                        command(9, 0, 3, 3, 20)),
                List.of(), Map.of(), "two-zone-" + secondOffsetX);
    }

    private static GpuDrawCommand command(int tileX, int tileY,
                                          int firstIndex, int indexCount, int objectId) {
        return new GpuDrawCommand(WorldTileAddress.of(tileX, tileY, 0),
                SceneLayer.Kind.GROUND_OBJECT, GpuDrawCommand.SubmissionPass.OPAQUE,
                firstIndex, indexCount, -1, 0, objectId);
    }

    private static GpuSceneVertex vertex(float x, float y, float z) {
        return new GpuSceneVertex(x, y, z, 0, 0, 0x1200,
                com.rspsi.editor.render.GpuColorEncoding.PACKED_JAGEX_HSL, 0,
                0, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0);
    }
}
