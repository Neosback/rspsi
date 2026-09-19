package com.rspsi.editor.render.compiler;

import com.rspsi.editor.model.TileCoordinate;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvalidationGraphTest {

    @Test
    void toZoneMapping() {
        assertEquals(new InvalidationGraph.ZoneCoordinate(0, 0, 0),
                InvalidationGraph.toZone(new TileCoordinate(0, 0, 0)));
        assertEquals(new InvalidationGraph.ZoneCoordinate(0, 0, 0),
                InvalidationGraph.toZone(new TileCoordinate(0, 7, 7)));
        assertEquals(new InvalidationGraph.ZoneCoordinate(0, 1, 1),
                InvalidationGraph.toZone(new TileCoordinate(0, 8, 8)));
        assertEquals(new InvalidationGraph.ZoneCoordinate(2, 2, 3),
                InvalidationGraph.toZone(new TileCoordinate(2, 16, 24)));
    }

    @Test
    void emptyDirtyTilesYieldsEmptyZones() {
        Set<InvalidationGraph.ZoneCoordinate> zones = InvalidationGraph.computeInvalidatedZones(
                Set.of(), InvalidationGraph.InvalidationCause.UNDERLAY_EDIT, 64, 64);
        assertTrue(zones.isEmpty());
    }

    @Test
    void interiorObjectEditOnlyInvalidatesSingleZone() {
        // Tile (12, 12) is well within zone (1, 1) (which spans tiles 8..15 in both dimensions)
        // With radius 1, bounding box is 11..13, entirely within zone (1, 1)
        TileCoordinate interior = new TileCoordinate(0, 12, 12);
        Set<InvalidationGraph.ZoneCoordinate> zones = InvalidationGraph.computeInvalidatedZones(
                Set.of(interior), InvalidationGraph.InvalidationCause.OBJECT_EDIT, 64, 64);

        assertEquals(1, zones.size());
        assertTrue(zones.contains(new InvalidationGraph.ZoneCoordinate(0, 1, 1)));
    }

    @Test
    void underlayEditSpillsOver5TileRadiusAcrossNeighboringZones() {
        // Tile (8, 8) is the SW corner of zone (1, 1).
        // With a 5-tile blend radius, x ranges from 3 to 13 (zones 0 and 1)
        // and y ranges from 3 to 13 (zones 0 and 1).
        // This must invalidate 4 adjacent 8x8 zones: (0,0), (0,1), (1,0), (1,1).
        TileCoordinate boundaryTile = new TileCoordinate(0, 8, 8);
        Set<InvalidationGraph.ZoneCoordinate> zones = InvalidationGraph.computeInvalidatedZones(
                Set.of(boundaryTile), InvalidationGraph.InvalidationCause.UNDERLAY_EDIT, 64, 64);

        assertEquals(4, zones.size());
        assertTrue(zones.contains(new InvalidationGraph.ZoneCoordinate(0, 0, 0)));
        assertTrue(zones.contains(new InvalidationGraph.ZoneCoordinate(0, 1, 0)));
        assertTrue(zones.contains(new InvalidationGraph.ZoneCoordinate(0, 0, 1)));
        assertTrue(zones.contains(new InvalidationGraph.ZoneCoordinate(0, 1, 1)));
    }

    @Test
    void boundaryClampingPreservesValidZoneCoordinates() {
        TileCoordinate cornerTile = new TileCoordinate(0, 0, 0);
        Set<InvalidationGraph.ZoneCoordinate> zones = InvalidationGraph.computeInvalidatedZones(
                Set.of(cornerTile), InvalidationGraph.InvalidationCause.UNDERLAY_EDIT, 64, 64);

        // Clamped at 0..5 in both axes -> only zone (0, 0)
        assertEquals(1, zones.size());
        assertTrue(zones.contains(new InvalidationGraph.ZoneCoordinate(0, 0, 0)));
    }
}
