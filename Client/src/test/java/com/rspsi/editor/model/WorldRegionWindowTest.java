package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldRegionWindowTest {
    @Test
    void resolvesLoadedTilesAcrossRegionEdgesAndKeepsHolesExplicit() {
        WorldDocument west = new WorldDocument(64, 64, 1);
        WorldDocument east = new WorldDocument(64, 64, 1);
        west.tile(0, 63, 10).restore(new TileSnapshot(1, 2, 3, 4, 7, 0, 0, 0, 0, java.util.List.of()));
        east.tile(0, 0, 10).restore(new TileSnapshot(5, 6, 7, 8, 8, 0, 0, 0, 0, java.util.List.of()));
        WorldRegionWindow window = new WorldRegionWindow(50, 50, 2, 1, Map.of(
                (50 << 8) | 50, new WorldRegion(50, 50, west),
                (51 << 8) | 50, new WorldRegion(51, 50, east)));

        assertTrue(window.complete());
        assertEquals(7, window.tile(0, 50 * 64 + 63, 50 * 64 + 10).orElseThrow().underlayId());
        assertEquals(8, window.tile(0, 51 * 64, 50 * 64 + 10).orElseThrow().underlayId());
        assertFalse(window.tile(0, 52 * 64, 50 * 64 + 10).isPresent());
        assertTrue(window.containsWorldTile(51 * 64, 50 * 64));
        assertFalse(window.containsWorldTile(52 * 64, 50 * 64));
    }

    @Test
    void reportsMissingRegionsWithoutFabricatingTerrain() {
        WorldRegionWindow window = new WorldRegionWindow(10, 20, 2, 2,
                Map.of((10 << 8) | 20, new WorldRegion(10, 20, new WorldDocument(64, 64, 4))));

        assertFalse(window.complete());
        assertEquals(3, window.missingRegionIds().size());
        assertEquals(1, window.loadedRegionCount());
    }
}
