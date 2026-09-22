package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class IncrementalGpuZonedUploadPlanBuilderTest {
    @Test
    void reusesUnchangedZoneObjectsAndRebuildsDirtyZoneOnly() {
        GpuUploadPlan initial = plan(100, 200, "initial");
        IncrementalGpuZonedUploadPlanBuilder builder =
                new IncrementalGpuZonedUploadPlanBuilder();
        GpuZonedUploadPlan first = builder.buildInitial(initial);

        WorldZoneCoordinate zoneA = WorldZoneCoordinate.from(WorldTileAddress.of(1, 1, 0));
        WorldZoneCoordinate zoneB = WorldZoneCoordinate.from(WorldTileAddress.of(16, 1, 0));
        GpuZoneUpload oldA = first.zones().get(zoneA);
        GpuZoneUpload oldB = first.zones().get(zoneB);

        GpuUploadPlan changed = plan(100, 300, "changed");
        GpuZonedUploadPlan second = builder.build(changed, Set.of(zoneB));

        assertSame(oldA, second.zones().get(zoneA));
        assertNotSame(oldB, second.zones().get(zoneB));
        assertEquals(changed.commands(),
                second.commandRefs().stream().map(GpuZonedDrawCommand::command).toList());
        assertEquals(changed.fingerprint(), second.sourceFingerprint());
    }

    @Test
    void commandShapeMismatchRebuildsZoneEvenWithoutDirtyHint() {
        GpuUploadPlan initial = plan(100, 200, "initial");
        IncrementalGpuZonedUploadPlanBuilder builder =
                new IncrementalGpuZonedUploadPlanBuilder();
        GpuZonedUploadPlan first = builder.buildInitial(initial);
        WorldZoneCoordinate zoneA = WorldZoneCoordinate.from(WorldTileAddress.of(1, 1, 0));

        GpuUploadPlan changed = new GpuUploadPlan(
                initial.vertices(), initial.indices(),
                List.of(initial.commands().get(0)),
                List.of(), Map.of(), List.of(), "filtered");
        GpuZonedUploadPlan second = builder.build(changed, Set.of());

        assertEquals(1, second.zones().size());
        assertSame(first.zones().get(zoneA), second.zones().get(zoneA));
    }

    private static GpuUploadPlan plan(int firstColor, int secondColor, String fingerprint) {
        List<GpuSceneVertex> vertices = List.of(
                vertex(1, 1, 0, firstColor), vertex(1, 1, 1, firstColor),
                vertex(1, 1, 2, firstColor),
                vertex(16, 1, 0, secondColor), vertex(16, 1, 1, secondColor),
                vertex(16, 1, 2, secondColor));
        List<Integer> indices = List.of(0, 1, 2, 3, 4, 5);
        List<GpuDrawCommand> commands = List.of(
                command(WorldTileAddress.of(1, 1, 0), 0),
                command(WorldTileAddress.of(16, 1, 0), 3));
        return new GpuUploadPlan(vertices, indices, commands,
                List.of(), Map.of(), List.of(), fingerprint);
    }

    private static GpuDrawCommand command(WorldTileAddress tile, int first) {
        return new GpuDrawCommand(tile, SceneLayer.Kind.TERRAIN,
                GpuDrawCommand.SubmissionPass.OPAQUE,
                first, 3, -1, 0, 0, -1, GpuDrawCommand.RenderMode.DEFAULT);
    }

    private static GpuSceneVertex vertex(int worldX, int worldY, int offset, int color) {
        return new GpuSceneVertex(worldX * 128.0f + offset, 0, worldY * 128.0f,
                0, 0, color, GpuColorEncoding.PACKED_JAGEX_HSL,
                0, 0, 0, 0, 0, -1, 255, 0,
                0, worldX, worldY, PickerId.terrainSlot());
    }
}
