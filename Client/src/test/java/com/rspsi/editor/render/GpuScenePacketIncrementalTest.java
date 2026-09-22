package com.rspsi.editor.render;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldRegionWindow;
import com.rspsi.editor.model.WorldTileAddress;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class GpuScenePacketIncrementalTest {
    @Test
    void reusesTilesOutsideDirtyWorldZonesAndMatchesFullPacket() {
        WorldTileAddress firstAddress = WorldTileAddress.of(1, 1, 0);
        WorldTileAddress secondAddress = WorldTileAddress.of(16, 1, 0);
        RenderWindowScene initialScene = scene(Map.of(
                firstAddress, terrain(1, 1, 100),
                secondAddress, terrain(16, 1, 200)));
        SceneWindow window = SceneWindow.from(initialScene.window());
        GpuScenePacketBuilder builder = new GpuScenePacketBuilder();
        GpuScenePacket initial = builder.build(window, initialScene);

        RenderWindowScene changedScene = scene(Map.of(
                firstAddress, terrain(1, 1, 100),
                secondAddress, terrain(16, 1, 300)));
        var update = builder.buildIncremental(initial, window, changedScene,
                Set.of(WorldZoneCoordinate.from(secondAddress)));
        GpuScenePacket expected = builder.build(window, changedScene);

        SceneTileSnapshot firstBefore = tile(initial, firstAddress);
        SceneTileSnapshot secondBefore = tile(initial, secondAddress);
        SceneTileSnapshot firstAfter = tile(update.packet(), firstAddress);
        SceneTileSnapshot secondAfter = tile(update.packet(), secondAddress);

        assertSame(firstBefore, firstAfter);
        assertNotSame(secondBefore, secondAfter);
        assertEquals(1, update.rebuiltTiles());
        assertEquals(1, update.reusedTiles());
        assertEquals(expected.tiles(), update.packet().tiles());
        assertEquals(expected.fingerprint(), update.packet().fingerprint());
    }

    private static RenderWindowScene scene(Map<WorldTileAddress, TerrainRenderPacket> terrain) {
        WorldRegionWindow window = new WorldRegionWindow(0, 0, 1, 1, Map.of());
        return new RenderWindowScene(window, Map.of(), Map.of(), Map.of(), Map.of(),
                terrain, Map.of(), Map.of(), LightingProfile.osrs(), Map.of(),
                List.of(), List.of(), Map.of());
    }

    private static TerrainRenderPacket terrain(int worldX, int worldY, int hsl) {
        TileCoordinate coordinate = new TileCoordinate(0, worldX, worldY);
        return new TerrainRenderPacket(
                coordinate,
                List.of(new TerrainRenderVertex(0, 0, 0, hsl, 0, 0),
                        new TerrainRenderVertex(128, 0, 0, hsl, 128, 0),
                        new TerrainRenderVertex(0, 128, 0, hsl, 0, 128)),
                List.of(new TerrainRenderFace(0, 1, 2, 0, -1, 255, 0)),
                0, 0, -1, hsl, -1, false, false, -1);
    }

    private static SceneTileSnapshot tile(GpuScenePacket packet, WorldTileAddress address) {
        return packet.tiles().stream()
                .filter(tile -> tile.worldAddress().equals(address))
                .findFirst().orElseThrow();
    }
}
