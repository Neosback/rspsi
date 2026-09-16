package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldRegionBoundaryTest {
    @Test
    void reportsEastAndNorthHeightDiscontinuitiesOnlyWhenBothRegionsExist() {
        WorldDocument west = new WorldDocument(64, 64, 1);
        WorldDocument east = new WorldDocument(64, 64, 1);
        WorldDocument north = new WorldDocument(64, 64, 1);
        west.tile(0, 63, 7).restore(new TileSnapshot(0, 10, 0, 12, 0, 0, 0, 0, 0, List.of()));
        east.tile(0, 0, 7).restore(new TileSnapshot(0, 9, 0, 0, 0, 0, 0, 0, 0, List.of()));
        west.tile(0, 7, 63).restore(new TileSnapshot(0, 0, 11, 12, 0, 0, 0, 0, 0, List.of()));
        north.tile(0, 7, 0).restore(new TileSnapshot(12, 10, 0, 0, 0, 0, 0, 0, 0, List.of()));
        WorldRegionWindow window = new WorldRegionWindow(50, 50, 2, 2, Map.of(
                (50 << 8) | 50, new WorldRegion(50, 50, west),
                (51 << 8) | 50, new WorldRegion(51, 50, east),
                (50 << 8) | 51, new WorldRegion(50, 51, north)));

        List<RegionBoundaryMismatch> mismatches = window.boundaryMismatches();

        assertEquals(2, mismatches.size());
        assertTrue(mismatches.stream().anyMatch(m -> m.direction() == RegionBoundaryDirection.EAST
                && m.alongEdge() == 7 && !m.upperCorner()));
        assertTrue(mismatches.stream().anyMatch(m -> m.direction() == RegionBoundaryDirection.NORTH
                && m.alongEdge() == 7 && m.upperCorner()));
    }
}
