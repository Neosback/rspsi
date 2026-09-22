package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GpuCommandGeometryTest {
    @Test
    void flatAndZonedGeometryExposeIdenticalCommandVerticesBoundsAndFog() {
        GpuUploadPlan flat = plan();
        GpuZonedUploadPlan zoned = new GpuZonedUploadPlanBuilder().build(flat);

        assertEquals(flat.commandCount(), zoned.commandCount());
        assertEquals(flat.vertexCount(), zoned.vertexCount());
        assertEquals(flat.indexCount(), zoned.indexCount());

        for (int commandIndex = 0; commandIndex < flat.commandCount(); commandIndex++) {
            GpuDrawCommand command = flat.command(commandIndex);
            assertEquals(command, zoned.command(commandIndex));
            for (int offset = 0; offset < command.indexCount(); offset++) {
                assertEquals(flat.indexedVertex(commandIndex, offset),
                        zoned.indexedVertex(commandIndex, offset));
            }
            assertEquals(
                    SceneOcclusionResolver.CommandBounds.of(commandIndex, command, flat),
                    SceneOcclusionResolver.CommandBounds.of(commandIndex, command, zoned));
        }
        assertEquals(SceneFog.bounds(flat), SceneFog.bounds(zoned));
    }

    private static GpuUploadPlan plan() {
        List<GpuSceneVertex> vertices = List.of(
                vertex(7, 4, 0), vertex(7, 4, 1), vertex(7, 4, 2),
                vertex(8, 4, 0), vertex(8, 4, 1), vertex(8, 4, 2));
        List<Integer> indices = List.of(0, 1, 2, 3, 4, 5);
        List<GpuDrawCommand> commands = List.of(
                command(WorldTileAddress.of(7, 4, 0), 0),
                command(WorldTileAddress.of(8, 4, 0), 3));
        return new GpuUploadPlan(vertices, indices, commands,
                List.of(), Map.of(), List.of(), "geometry-contract");
    }

    private static GpuDrawCommand command(WorldTileAddress tile, int firstIndex) {
        return new GpuDrawCommand(tile, SceneLayer.Kind.TERRAIN,
                GpuDrawCommand.SubmissionPass.OPAQUE,
                firstIndex, 3, -1, 0, 0, -1, GpuDrawCommand.RenderMode.DEFAULT);
    }

    private static GpuSceneVertex vertex(int worldX, int worldY, int offset) {
        return new GpuSceneVertex(worldX * 128.0f + offset, -offset, worldY * 128.0f,
                0, 0, 100, GpuColorEncoding.PACKED_JAGEX_HSL,
                0, 0, 0, 0, 0, -1, 255, 0,
                0, worldX, worldY, PickerId.terrainSlot());
    }
}
