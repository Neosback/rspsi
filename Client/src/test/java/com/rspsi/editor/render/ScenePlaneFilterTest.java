package com.rspsi.editor.render;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldTileAddress;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScenePlaneFilterTest {
    /**
     * Filtering a full-plane plan per frame must draw exactly what rebuilding the plan with the
     * same plane policy drew, for every combination of currentHeight and allHeightsVisible.
     */
    @Test
    void frameFilterMatchesRebuildingThePlanForEveryHeightMode() {
        GpuScenePacket packet = packet();
        RenderConfig base = RenderConfig.vanillaDefault();
        GpuUploadPlan fullPlan = new GpuUploadPlanBuilder().build(base.forPlan().apply(packet));
        SceneContract contract = fullPlan.sceneWindow().map(SceneWindow::contract).orElse(null);

        for (boolean allHeights : new boolean[]{true, false}) {
            for (int height = 0; height < 4; height++) {
                for (boolean bridges : new boolean[]{true, false}) {
                    RenderConfig config = with(base, height, allHeights, bridges);
                    GpuUploadPlan rebuilt = new GpuUploadPlanBuilder().build(config.apply(packet));
                    ScenePlaneFilter filter = config.planeFilter();
                    Set<String> expected = new TreeSet<>();
                    rebuilt.commands().forEach(command -> expected.add(key(command)));
                    Set<String> actual = new TreeSet<>();
                    for (GpuDrawCommand command : fullPlan.commands()) {
                        if (filter.includes(command, contract)) actual.add(key(command));
                    }
                    assertEquals(expected, actual,
                            "allHeights=" + allHeights + " height=" + height + " bridges=" + bridges);
                }
            }
        }
    }

    private static String key(GpuDrawCommand command) {
        return command.tile() + "/" + command.scenePlane();
    }

    private static RenderConfig with(RenderConfig c, int currentHeight,
                                     boolean allHeightsVisible, boolean bridges) {
        return new RenderConfig(c.profile(), c.terrainVisible(), c.objectsVisible(), c.wallsVisible(),
                c.wallDecorationsVisible(), c.groundObjectsVisible(), c.groundDecorationsVisible(),
                bridges, c.hiddenTilesVisible(), c.emptyTilesVisible(), c.collisionVisible(),
                c.wireframe(), currentHeight, allHeightsVisible,
                c.brightness(), c.exposure(), c.msaaSamples(), c.fogDepthTiles(),
                c.fogColor(), c.invisibleObjectsVisible(), c.nativeCullingMode(), c.gpuDebugView());
    }

    /** One terrain tile per (authored plane, effective plane, cull level) case, incl. a bridge. */
    private static GpuScenePacket packet() {
        List<SceneTileSnapshot> tiles = new ArrayList<>();
        int x = 3200;
        int[][] cases = {
                // authored, effective, cullLevel
                {0, 0, 0}, {1, 1, 1}, {2, 2, 2}, {3, 3, 3},
                {1, 0, 0},   // bridge: authored on plane 1, drawn on scene plane 0
                {2, 1, 1}, {1, 1, 3}, {0, 0, 2}};
        for (int[] c : cases) {
            TileCoordinate coordinate = new TileCoordinate(c[0], x, 3200);
            WorldTileAddress address = WorldTileAddress.of(x, 3200, c[0]);
            TerrainRenderPacket terrain = new TerrainRenderPacket(coordinate,
                    List.of(new TerrainRenderVertex(0, 0, 0, 100, 0, 0),
                            new TerrainRenderVertex(128, 0, 0, 100, 128, 0),
                            new TerrainRenderVertex(0, 128, 0, 100, 0, 128)),
                    List.of(new TerrainRenderFace(0, 1, 2, 0, -1, 255, 0)),
                    0, 0, -1, 100, -1, false, false, -1);
            tiles.add(new SceneTileSnapshot(coordinate, address, 0, c[1], c[0], c[1], c[2],
                    Optional.empty(), Optional.of(terrain), List.of(),
                    List.of(new SceneLayer(SceneLayer.Kind.TERRAIN, List.of())), List.of(), false,
                    c[1] < c[0]));
            x += 8;
        }
        return new GpuScenePacket(
                new SceneWindow(new com.rspsi.editor.model.WorldRegionWindow(50, 50, 1, 1, Map.of()),
                        3200, 3200, 4, 0, java.util.Set.of(), List.of()),
                tiles, LightingProfile.osrs(), "plane-filter", Map.of());
    }
}
