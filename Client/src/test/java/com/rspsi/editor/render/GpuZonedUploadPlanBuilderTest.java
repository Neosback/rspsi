package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldRegionWindow;
import com.rspsi.editor.model.WorldTileAddress;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class GpuZonedUploadPlanBuilderTest {
    @Test
    void partitionsGeometryWithoutChangingGlobalCommandOrder() {
        GpuUploadPlan flat = flatPlan();
        GpuZonedUploadPlan zoned = new GpuZonedUploadPlanBuilder().build(flat);

        assertEquals(2, zoned.zones().size());
        assertEquals(flat.commands().size(), zoned.commandRefs().size());
        assertEquals(flat.commands(),
                zoned.commandRefs().stream().map(GpuZonedDrawCommand::command).toList());
        assertEquals(flat.vertices().size(), zoned.vertexCount());
        assertEquals(flat.indices().size(), zoned.indexCount());
        assertEquals(flat.fingerprint(), zoned.sourceFingerprint());

        GpuZonedDrawCommand first = zoned.commandRefs().get(0);
        GpuZonedDrawCommand second = zoned.commandRefs().get(1);
        assertNotEquals(first.zone(), second.zone());
        assertEquals(0, first.localFirstIndex());
        assertEquals(0, second.localFirstIndex());
    }

    @Test
    void everyZonedCommandReferencesTheSameGeometryAsTheFlatCommand() {
        GpuUploadPlan flat = flatPlan();
        GpuZonedUploadPlan zoned = new GpuZonedUploadPlanBuilder().build(flat);

        for (int commandIndex = 0; commandIndex < flat.commands().size(); commandIndex++) {
            GpuDrawCommand flatCommand = flat.commands().get(commandIndex);
            GpuZonedDrawCommand ref = zoned.commandRefs().get(commandIndex);
            GpuZoneUpload zone = zoned.zones().get(ref.zone());

            List<GpuSceneVertex> flatVertices = new java.util.ArrayList<>();
            List<GpuSceneVertex> zonedVertices = new java.util.ArrayList<>();
            for (int offset = 0; offset < flatCommand.indexCount(); offset++) {
                int flatIndex = flat.indices().get(flatCommand.firstIndex() + offset);
                int localIndex = zone.indices().get(ref.localFirstIndex() + offset);
                flatVertices.add(flat.vertices().get(flatIndex));
                zonedVertices.add(zone.vertices().get(localIndex));
            }
            assertEquals(flatVertices, zonedVertices);
        }
    }

    private static GpuUploadPlan flatPlan() {
        GpuSceneVertex a = vertex(7, 4, 0);
        GpuSceneVertex b = vertex(7, 4, 1);
        GpuSceneVertex c = vertex(7, 4, 2);
        GpuSceneVertex d = vertex(8, 4, 0);
        GpuSceneVertex e = vertex(8, 4, 1);
        GpuSceneVertex f = vertex(8, 4, 2);
        List<GpuSceneVertex> vertices = List.of(a, b, c, d, e, f);
        List<Integer> indices = List.of(0, 1, 2, 3, 4, 5);
        GpuDrawCommand first = command(WorldTileAddress.of(7, 4, 0), 0);
        GpuDrawCommand second = command(WorldTileAddress.of(8, 4, 0), 3);
        return new GpuUploadPlan(vertices, indices, List.of(first, second),
                List.of(), Map.of(), List.of(), "zoned-fixture");
    }

    private static GpuDrawCommand command(WorldTileAddress tile, int first) {
        return new GpuDrawCommand(tile, SceneLayer.Kind.TERRAIN,
                GpuDrawCommand.SubmissionPass.OPAQUE,
                first, 3, -1, 0, 0, -1, GpuDrawCommand.RenderMode.DEFAULT);
    }

    private static GpuSceneVertex vertex(int worldX, int worldY, int offset) {
        return new GpuSceneVertex(
                worldX * 128.0f + offset, 0.0f, worldY * 128.0f,
                0.0f, 0.0f, 100, GpuColorEncoding.PACKED_JAGEX_HSL,
                0, 0, 0, 0, 0, -1, 255, 0,
                0, worldX, worldY, PickerId.terrainSlot());
    }
}
