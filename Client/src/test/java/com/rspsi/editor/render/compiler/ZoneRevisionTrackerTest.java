package com.rspsi.editor.render.compiler;

import com.rspsi.editor.model.TileCoordinate;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ZoneRevisionTrackerTest {
    @Test
    void underlayRadiusFiveInvalidatesOnlyOverlappingEightByEightZones() {
        ZoneRevisionTracker tracker = new ZoneRevisionTracker(32, 32, 1);
        long baseline = tracker.baseline();

        tracker.markTerrainDirty(0, 8, 8, ZoneRevisionTracker.UNDERLAY_RADIUS);

        Set<InvalidationGraph.ZoneCoordinate> dirty = tracker.dirtyZones(baseline);
        assertEquals(Set.of(
                new InvalidationGraph.ZoneCoordinate(0, 0, 0),
                new InvalidationGraph.ZoneCoordinate(0, 0, 1),
                new InvalidationGraph.ZoneCoordinate(0, 1, 0),
                new InvalidationGraph.ZoneCoordinate(0, 1, 1)), dirty);
        assertTrue(tracker.revision(0, 1, 1) > baseline);
        assertEquals(0L, tracker.revision(0, 3, 3));
    }

    @Test
    void causeRadiiMatchTerrainDependencies() {
        assertEquals(5, ZoneRevisionTracker.radius(
                InvalidationGraph.InvalidationCause.UNDERLAY_EDIT));
        assertEquals(2, ZoneRevisionTracker.radius(
                InvalidationGraph.InvalidationCause.HEIGHT_EDIT));
        assertEquals(1, ZoneRevisionTracker.radius(
                InvalidationGraph.InvalidationCause.OVERLAY_EDIT));
        assertEquals(1, ZoneRevisionTracker.radius(
                InvalidationGraph.InvalidationCause.OBJECT_EDIT));
    }

    @Test
    void markDirtyAdvancesMonotonicRevision() {
        ZoneRevisionTracker tracker = new ZoneRevisionTracker(16, 16, 1);
        long first = tracker.markDirty(Set.of(new TileCoordinate(0, 1, 1)),
                InvalidationGraph.InvalidationCause.OVERLAY_EDIT);
        long second = tracker.markDirty(Set.of(new TileCoordinate(0, 14, 14)),
                InvalidationGraph.InvalidationCause.OVERLAY_EDIT);

        assertTrue(second > first);
        assertTrue(tracker.baseline() >= second);
    }
}
