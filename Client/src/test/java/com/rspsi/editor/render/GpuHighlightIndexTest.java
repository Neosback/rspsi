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
        GpuUploadPlan plan = new GpuUploadPlanBuilder().build(packet(List.of(3200)));
        GpuHighlightIndex index = new GpuHighlightIndex(plan);
        WorldTileAddress tile = WorldTileAddress.of(3200, 3200, 0);

        int[] terrain = index.terrain(tile);
        int[] location = index.location(tile, 42, 10, 0);

        assertTrue(terrain.length > 0, "terrain draws");
        assertTrue(location.length > 0, "object draws");
        for (int i = 0; i < terrain.length; i += 3) {
            assertEquals(SceneLayer.Kind.TERRAIN, plan.commands().get(terrain[i]).layer());
        }
        for (int i = 0; i < location.length; i += 3) {
            assertEquals(42, plan.commands().get(location[i]).objectId());
            assertEquals(plan.commands().get(location[i]).indexCount(), location[i + 2]);
        }
        assertEquals(0, index.location(tile, 43, 10, 0).length, "other ids do not match");
        assertEquals(0, index.terrain(WorldTileAddress.of(3201, 3200, 0)).length);
    }

    @Test
    void highlightsCompareByContent() {
        assertEquals(new SceneHighlight(new int[]{1, 0, 3}, new int[]{3, 0, 6}),
                new SceneHighlight(new int[]{1, 0, 3}, new int[]{3, 0, 6}));
        assertNotEquals(new SceneHighlight(new int[]{1, 0, 3}, new int[0]), SceneHighlight.NONE);
        assertArrayEquals(new int[0], SceneHighlight.NONE.getHovered());
    }

    @Test
    void aTileInsideAZoneMergedTerrainDrawOutlinesOnlyItsOwnTriangles() {
        GpuUploadPlan plan = new GpuUploadPlanBuilder().build(packet(List.of(3200, 3201, 3202)));
        GpuHighlightIndex index = new GpuHighlightIndex(plan);

        int[] middle = index.terrain(WorldTileAddress.of(3201, 3200, 0));

        assertEquals(3, middle.length, "one contiguous range");
        assertEquals(3, middle[2], "one triangle, not the whole merged row");
        GpuSceneVertex vertex = plan.indexedVertex(middle[0], middle[1]);
        assertEquals(3201, vertex.pickerTileX());
    }

    private static GpuScenePacket packet(List<Integer> tileXs) {
        List<SceneTileSnapshot> tiles = new java.util.ArrayList<>();
        for (int x : tileXs) tiles.add(tile(x));
        return new GpuScenePacket(
                new SceneWindow(new com.rspsi.editor.model.WorldRegionWindow(
                        50, 50, 1, 1, Map.of()), 3200, 3200, 1, 0,
                        java.util.Set.of(), List.of()),
                tiles, LightingProfile.osrs(), "highlight-index", Map.of());
    }

    private static SceneTileSnapshot tile(int worldX) {
        TileCoordinate coordinate = new TileCoordinate(0, worldX, 3200);
        WorldTileAddress address = WorldTileAddress.of(worldX, 3200, 0);
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
        return new SceneTileSnapshot(
                coordinate, address, 0, 0, Optional.empty(), Optional.of(terrain),
                List.of(model),
                List.of(new SceneLayer(SceneLayer.Kind.TERRAIN, List.of()),
                        new SceneLayer(SceneLayer.Kind.GROUND_OBJECT, List.of(0))),
                List.of(), false, false);
    }
}
