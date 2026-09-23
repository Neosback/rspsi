package com.rspsi.editor.render;

import com.rspsi.editor.model.OsrsTileFlags;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldRegionWindow;
import com.rspsi.editor.model.WorldTileAddress;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoofRemovalStateTest {
    @Test
    void positionHoveredAndDestinationSelectWholeConnectedRegions() {
        RoofRegionMap map = RoofRegionMap.build(window(), tiles());
        RoofRemovalState state = new RoofRemovalState(
                RoofRemovalState.POSITION | RoofRemovalState.HOVERED | RoofRemovalState.DESTINATION,
                point(1, 1),
                point(8, 1),
                point(1, 2),
                null,
                200);

        Set<Integer> selected = state.selectedRegionIds(map, 0);

        assertEquals(Set.of(map.regionId(0, 1, 1), map.regionId(0, 8, 1)), selected);
        assertTrue(selected.contains(map.regionId(0, 0, 0)),
                "selecting a blocking tile removes its labelled perimeter region too");
    }

    @Test
    void betweenUsesRuneLiteTraceAndOnlySelectsBlockingTilesOnThePath() {
        RoofRegionMap map = RoofRegionMap.build(window(), tiles());
        RoofRemovalState lowPitch = new RoofRemovalState(
                RoofRemovalState.BETWEEN,
                point(6, 1),
                null,
                null,
                point(0, 1),
                309);

        Set<Integer> selected = lowPitch.selectedRegionIds(map, 0);

        assertEquals(Set.of(map.regionId(0, 1, 1)), selected,
                "the camera-to-player walk crosses only the first blocking component");
    }

    @Test
    void betweenStopsAtPitch310AndDoesNotIncludeThePlayerFinalTile() {
        RoofRegionMap map = RoofRegionMap.build(window(), tiles());

        RoofRemovalState atCutoff = new RoofRemovalState(
                RoofRemovalState.BETWEEN,
                point(8, 1), null, null, point(6, 1), 310);
        assertTrue(atCutoff.selectedRegionIds(map, 0).isEmpty());

        RoofRemovalState excludesFinalPlayerTile = new RoofRemovalState(
                RoofRemovalState.BETWEEN,
                point(8, 1), null, null, point(7, 1), 200);
        assertTrue(excludesFinalPlayerTile.selectedRegionIds(map, 0).isEmpty(),
                "RuneLite's while loop checks camera/intermediate tiles but not the final player tile");
    }

    @Test
    void disabledStateNeverSelectsRegions() {
        RoofRegionMap map = RoofRegionMap.build(window(), tiles());
        assertFalse(RoofRemovalState.disabled().enabled());
        assertTrue(RoofRemovalState.disabled().selectedRegionIds(map, 0).isEmpty());
    }

    private static RoofRemovalState.ScenePoint point(int x, int y) {
        return new RoofRemovalState.ScenePoint(x, y);
    }

    private static SceneWindow window() {
        WorldRegionWindow source = new WorldRegionWindow(0, 0, 1, 1, Map.of());
        return new SceneWindow(source, 0, 0, 4, 0,
                0, -1, Set.of(), List.of());
    }

    private static List<SceneTileSnapshot> tiles() {
        List<SceneTileSnapshot> tiles = new ArrayList<>();
        for (int x = 0; x < 12; x++) {
            for (int y = 0; y < 4; y++) {
                int flags = (x == 1 && y == 1) || (x == 8 && y == 1)
                        ? OsrsTileFlags.REMOVE_ROOFS : 0;
                tiles.add(tile(0, x, y, flags));
            }
        }
        return tiles;
    }

    private static SceneTileSnapshot tile(int plane, int x, int y, int flags) {
        TileCoordinate coordinate = new TileCoordinate(plane, x, y);
        return new SceneTileSnapshot(
                coordinate,
                WorldTileAddress.of(x, y, plane),
                flags,
                plane,
                plane,
                plane,
                plane,
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(),
                false,
                false);
    }
}
