package com.rspsi.editor.render;

import com.rspsi.editor.model.ObjectCategory;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldTileAddress;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuHighlightIndexTest {
    @Test
    void findsTerrainAndLocationDrawsOnTheirTile() {
        GpuUploadPlan plan = new GpuUploadPlanBuilder().build(packet());
        GpuHighlightIndex index = new GpuHighlightIndex(plan);
        WorldTileAddress tile = WorldTileAddress.of(3200, 3200, 0);

        int[] terrain = index.terrain(tile);
        int[] location = index.location(tile, 42, 10, 0);

        assertTrue(terrain.length > 0, "terrain draws");
        assertTrue(location.length > 0, "object draws");
        for (int command : terrain) {
            assertEquals(SceneLayer.Kind.TERRAIN, plan.commands().get(command).layer());
        }
        for (int command : location) {
            assertEquals(42, plan.commands().get(command).objectId());
        }
        assertEquals(0, index.location(tile, 43, 10, 0).length, "other ids do not match");
        assertEquals(0, index.terrain(WorldTileAddress.of(3201, 3200, 0)).length);
    }

    @Test
    void highlightsCompareByContent() {
        assertEquals(new SceneHighlight(new int[]{1, 2}, new int[]{3}),
                new SceneHighlight(new int[]{1, 2}, new int[]{3}));
        assertNotEquals(new SceneHighlight(new int[]{1}, new int[0]), SceneHighlight.NONE);
        assertArrayEquals(new int[0], SceneHighlight.NONE.getHovered());
    }

    private static GpuScenePacket packet() {
        TileCoordinate coordinate = new TileCoordinate(0, 3200, 3200);
        WorldTileAddress address = WorldTileAddress.of(3200, 3200, 0);
        TerrainRenderPacket terrain = new TerrainRenderPacket(
                coordinate,
                List.of(new TerrainRenderVertex(0, 0, 0, 100, 0, 0),
                        new TerrainRenderVertex(128, 0, 0, 100, 128, 0),
                        new TerrainRenderVertex(0, 128, 0, 100, 0, 128)),
                List.of(new TerrainRenderFace(0, 1, 2, 0, -1, 255, 0)),
                0, 0, -1, 100, -1, false, false, -1);
        ModelRenderPacket model = new ModelRenderPacket(
                coordinate, 42, ObjectCategory.GROUND,
                List.of(new ModelVertex(0, 0, 0, 1, 0, 0, 1, 0, 0),
                        new ModelVertex(64, 0, 0, 1, 0, 0, 1, 1, 0),
                        new ModelVertex(0, 64, 0, 1, 0, 0, 1, 0, 1)),
                List.of(new ModelTriangle(0, 1, 2, 100, 100, 100, -1, 0, 0, 0,
                        0, 0, 1, 0, 0, 1, 100, 0)),
                List.of(), -1, 0, 0, 0, 64, 64, 0, false, false);
        SceneTileSnapshot tile = new SceneTileSnapshot(
                coordinate, address, 0, 0, Optional.empty(), Optional.of(terrain),
                List.of(model),
                List.of(new SceneLayer(SceneLayer.Kind.TERRAIN, List.of()),
                        new SceneLayer(SceneLayer.Kind.GROUND_OBJECT, List.of(0))),
                List.of(), false, false);
        return new GpuScenePacket(
                new SceneWindow(new com.rspsi.editor.model.WorldRegionWindow(
                        50, 50, 1, 1, Map.of()), 3200, 3200, 1, 0,
                        java.util.Set.of(), List.of()),
                List.of(tile), LightingProfile.osrs(), "highlight-index", Map.of());
    }
}
