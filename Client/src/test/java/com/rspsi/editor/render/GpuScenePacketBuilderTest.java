package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ModelGeometryView;
import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.editor.model.OsrsTileFlags;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldRegion;
import com.rspsi.editor.model.WorldRegionWindow;
import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.model.WorldObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuScenePacketBuilderTest {
    @Test
    void assemblesWorldTilesInStableOrderAndPreservesBridgeVisibility() {
        WorldDocument document = new WorldDocument(64, 64, 4);
        document.tile(1, 2, 3).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, OsrsTileFlags.BRIDGE, List.of()));
        WorldRegion region = new WorldRegion(10, 20, document);
        WorldRegionWindow window = new WorldRegionWindow(10, 20, 1, 1,
                Map.of(region.regionId(), region));
        RenderWindowScene scene = new RenderWindowSceneBuilder().build(window);

        GpuScenePacket packet = new GpuScenePacketBuilder().build(SceneWindow.from(window), scene);

        assertEquals(64 * 64 * 4, packet.tiles().size());
        assertFalse(packet.fingerprint().isBlank());
        SceneTileSnapshot bridge = packet.tiles().stream()
                .filter(tile -> tile.coordinate().plane() == 1
                        && tile.coordinate().x() == 642 && tile.coordinate().y() == 1283)
                .findFirst().orElseThrow();
        assertTrue(bridge.bridge().isPresent());
        assertEquals(0, bridge.effectivePlane());
        assertTrue(bridge.visibleBelow());
        assertEquals(10, bridge.worldAddress().regionX());
        assertEquals(2, bridge.worldAddress().regionLocalX());
        assertEquals(2, bridge.worldAddress().chunkLocalX());
        assertEquals(OsrsTileFlags.BRIDGE, bridge.tileFlags());
        assertTrue(bridge.layers().isEmpty());
    }

    @Test
    void emitsClientWallOccluderInputsForModelClippedStraightWalls() {
        WorldDocument document = new WorldDocument(64, 64, 1);
        document.tile(0, 2, 3).restore(new TileSnapshot(100, 100, 100, 100,
                1, 0, 0, 0, 0, List.of(new WorldObject(99, 0, 0, 0, 2, 3))));
        WorldRegion region = new WorldRegion(10, 20, document);
        WorldRegionWindow window = new WorldRegionWindow(10, 20, 1, 1,
                Map.of(region.regionId(), region));

        RenderWindowScene scene = new RenderWindowSceneBuilder(clippedWallDefinitions()).build(window);
        GpuScenePacket packet = new GpuScenePacketBuilder().build(SceneWindow.from(window), scene);
        WorldTileAddress address = WorldTileAddress.of(642, 1283, 0);

        SceneOccluder occluder = packet.tiles().stream()
                .filter(tile -> tile.worldAddress().equals(address))
                .flatMap(tile -> tile.occluders().stream())
                .filter(value -> value.type() == 1)
                .findFirst().orElseThrow();

        assertEquals(642 * 128, occluder.minWorldX());
        assertEquals(642 * 128, occluder.maxWorldX());
        assertEquals(1283 * 128, occluder.minWorldY());
        assertEquals(1283 * 128 + 128, occluder.maxWorldY());
        assertEquals(-140, occluder.minHeight());
        assertEquals(100, occluder.maxHeight());
    }

    private static DefinitionProvider clippedWallDefinitions() {
        ObjectAppearanceView appearance = new ObjectAppearanceView(
                -1, false, 128, 128, 128, 0, 0, 0, Map.of(), Map.of(),
                false, false, false, false, 0, 0, 16, -1, 0,
                true, false, false, 0);
        ModelGeometryView geometry = new ModelGeometryView(7,
                new int[]{0, 0, 0, 64, 0, 0, 0, 0, 64},
                new int[]{0, 1, 2}, new short[]{100}, new int[]{0}, new int[]{-1});
        return new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) {
                return Optional.of(new ObjectDefinitionView(id, "wall", 1, 1,
                        List.of(), new int[]{7}, new int[]{0}, -1, false));
            }
            @Override public Optional<FloorDefinitionView> underlay(int id) {
                return Optional.of(new FloorDefinitionView(id, -1, 0, 0, 0, 96, 0, 1));
            }
            @Override public Optional<FloorDefinitionView> overlay(int id) { return Optional.empty(); }
            @Override public Optional<ObjectAppearanceView> objectAppearance(int id) {
                return Optional.of(appearance);
            }
            @Override public Optional<ModelGeometryView> modelGeometry(int id) {
                return Optional.of(geometry);
            }
        };
    }
}
