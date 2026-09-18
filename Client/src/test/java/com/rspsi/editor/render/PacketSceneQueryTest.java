package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldRegionWindow;
import com.rspsi.editor.model.WorldTileAddress;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PacketSceneQueryTest {
    @Test
    void indexesTilesByWorldAddressWithoutReconstructingSceneSemantics() {
        WorldTileAddress address = WorldTileAddress.of(3200, 3200, 0);
        SceneTileSnapshot tile = new SceneTileSnapshot(
                new com.rspsi.editor.model.TileCoordinate(0, 3200, 3200), address,
                7, 0, Optional.empty(), Optional.empty(), List.of(), List.of(),
                List.of(), false, false);
        GpuScenePacket packet = new GpuScenePacket(
                new SceneWindow(new WorldRegionWindow(50, 50, 1, 1, Map.of()),
                        3200, 3200, 1, 0, Set.of(12850), List.of()),
                List.of(tile), LightingProfile.osrs(), "query", Map.of());

        SceneQuery query = new PacketSceneQuery(packet);

        assertEquals(tile, query.tile(address).orElseThrow());
        assertEquals(7, query.flags(address));
        assertEquals(List.of(), query.models(address));
        assertEquals(Optional.empty(), query.tile(WorldTileAddress.of(3201, 3200, 0)));
    }

    @Test
    void rejectsDuplicateWorldTiles() {
        WorldTileAddress address = WorldTileAddress.of(3200, 3200, 0);
        SceneTileSnapshot tile = new SceneTileSnapshot(
                new com.rspsi.editor.model.TileCoordinate(0, 3200, 3200), address,
                0, 0, Optional.empty(), Optional.empty(), List.of(), List.of(),
                List.of(), false, false);
        GpuScenePacket packet = new GpuScenePacket(
                new SceneWindow(new WorldRegionWindow(50, 50, 1, 1, Map.of()),
                        3200, 3200, 1, 0, Set.of(12850), List.of()),
                List.of(tile, tile), LightingProfile.osrs(), "duplicate", Map.of());

        assertThrows(IllegalArgumentException.class, () -> new PacketSceneQuery(packet));
    }
}
