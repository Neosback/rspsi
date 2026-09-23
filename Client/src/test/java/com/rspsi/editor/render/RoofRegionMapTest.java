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

class RoofRegionMapTest {
    @Test
    void buildsEightNeighbourRegionsAndLabelsOneTilePerimeter() {
        Set<Long> blocking = Set.of(key(1, 1), key(2, 2), key(8, 1));
        RoofRegionMap map = RoofRegionMap.build(window(), denseTiles(12, 5, blocking));

        assertEquals(2, map.regionCount());
        assertEquals(1, map.regionId(0, 1, 1));
        assertEquals(1, map.regionId(0, 2, 2),
                "diagonal blocking tiles are connected by RuneLite's eight-neighbour flood");
        assertEquals(1, map.regionId(0, 0, 0),
                "non-blocking immediate neighbours inherit the roof region id");
        assertEquals(1, map.regionId(0, 3, 3));
        assertFalse(map.blocking(0, 0, 0));

        assertEquals(2, map.regionId(0, 8, 1));
        assertEquals(2, map.regionId(0, 7, 0));
        assertEquals(0, map.regionId(0, 5, 4));
    }

    @Test
    void oneTilePerimeterDoesNotBridgeSeparateBlockingComponents() {
        Set<Long> blocking = Set.of(key(1, 1), key(3, 1));
        RoofRegionMap map = RoofRegionMap.build(window(), denseTiles(6, 3, blocking));

        assertEquals(2, map.regionCount());
        assertEquals(1, map.regionId(0, 1, 1));
        assertEquals(1, map.regionId(0, 2, 1),
                "the first component owns the shared non-blocking perimeter tile");
        assertEquals(2, map.regionId(0, 3, 1),
                "a pre-labelled perimeter tile does not merge the second blocking component");
    }

    @Test
    void regionIdsArePlaneLocalAndWorldAddressable() {
        List<SceneTileSnapshot> tiles = new ArrayList<>(denseTiles(3, 3, Set.of(key(1, 1))));
        tiles.add(tile(1, 1, 1, OsrsTileFlags.REMOVE_ROOFS));
        RoofRegionMap map = RoofRegionMap.build(window(), tiles);

        assertTrue(map.blocking(0, 1, 1));
        assertTrue(map.blocking(1, 1, 1));
        assertEquals(1, map.regionIdWorld(0, 1, 1));
        assertEquals(2, map.regionIdWorld(1, 1, 1));
    }

    private static SceneWindow window() {
        WorldRegionWindow source = new WorldRegionWindow(0, 0, 1, 1, Map.of());
        return new SceneWindow(source, 0, 0, 4, 0,
                0, -1, Set.of(), List.of());
    }

    private static List<SceneTileSnapshot> denseTiles(int width, int length, Set<Long> blocking) {
        List<SceneTileSnapshot> tiles = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < length; y++) {
                tiles.add(tile(0, x, y,
                        blocking.contains(key(x, y)) ? OsrsTileFlags.REMOVE_ROOFS : 0));
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

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}
