package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldRegionWindowMaterializationTest {
    @Test
    void placesLoadedRegionsAtWindowRelativeCoordinatesAndLeavesHolesEmpty() {
        WorldDocument source = new WorldDocument(64, 64, 1);
        source.tile(0, 2, 3).restore(new TileSnapshot(
                10, 20, 30, 40, 1, 0, 0, 0, 0, List.of()));
        WorldRegion region = new WorldRegion(12, 8, source);
        WorldRegionWindow window = new WorldRegionWindow(12, 8, 2, 1,
                Map.of(region.regionId(), region));

        WorldDocument materialized = window.materializeWorldDocument();

        assertEquals(128, materialized.width());
        assertEquals(64, materialized.length());
        assertEquals(source.tile(0, 2, 3).snapshot(), materialized.tile(0, 2, 3).snapshot());
        assertEquals(0, materialized.tile(0, 64 + 2, 3).snapshot().underlayId());
        assertTrue(materialized.tile(0, 2, 3).heightSource().equals(source.tile(0, 2, 3).heightSource()));
    }

    @Test
    void paddedMaterializationDoesNotRepeatVisibleEdgesAndShiftsDerivedObjects() {
        WorldDocument source = new WorldDocument(64, 64, 1);
        source.tile(0, 0, 0).restore(new TileSnapshot(
                10, 20, 30, 40, 7, 0, 0, 0, 0,
                List.of(new WorldObject(42, 10, 0, 0, 0, 0))));
        WorldRegion region = new WorldRegion(12, 8, source);
        WorldRegionWindow window = new WorldRegionWindow(12, 8, 1, 1,
                Map.of(region.regionId(), region));

        WorldDocument padded = window.materializePaddedWorldDocument(5);

        assertEquals(74, padded.width());
        assertEquals(74, padded.length());
        assertEquals(0, padded.tile(0, 4, 5).snapshot().underlayId());
        assertEquals(7, padded.tile(0, 5, 5).snapshot().underlayId());
        assertEquals(5, padded.tile(0, 5, 5).snapshot().objects().get(0).x());
        assertEquals(5, padded.tile(0, 5, 5).snapshot().objects().get(0).y());
    }
}
