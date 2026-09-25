package com.rspsi.studio.ui.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ViewportHudManagerTest {

    @Test
    void hudsInSameQuadrantStackWithoutOverlap() {
        ViewportHudManager manager = new ViewportHudManager();
        manager.beginFrame(100.0f, 50.0f, 800.0f, 600.0f);

        var first = manager.place(ViewportHudManager.Quadrant.BOTTOM_LEFT, 180.0f, 22.0f);
        var second = manager.place(ViewportHudManager.Quadrant.BOTTOM_LEFT, 220.0f, 30.0f);

        assertEquals(first.x(), second.x());
        assertTrue(second.y() + second.height() < first.y());
    }

    @Test
    void opacityAndOffsetsRoundTripThroughWorkspaceState() {
        ViewportHudManager manager = new ViewportHudManager();
        manager.register("tile", ViewportHudManager.Quadrant.TOP_LEFT, 5, true, 0.72f);
        manager.setUserOffset("tile", 34.0f, 18.0f);
        manager.setOpacity("tile", 0.43f);

        var snapshot = manager.snapshot();

        ViewportHudManager restored = new ViewportHudManager();
        restored.restore(snapshot);

        assertEquals(0.43f, restored.opacity("tile"), 0.001f);
        assertEquals(34.0f, snapshot.get("tile").offsetX(), 0.001f);
        assertEquals(18.0f, snapshot.get("tile").offsetY(), 0.001f);
    }

    @Test
    void immovableHudIgnoresDragDeltas() {
        ViewportHudManager manager = new ViewportHudManager();
        manager.register("locked", ViewportHudManager.Quadrant.TOP_LEFT, 5, false, 0.8f);
        manager.moveBy("locked", 30.0f, 20.0f);

        var state = manager.snapshot().get("locked");
        assertEquals(0.0f, state.offsetX(), 0.001f);
        assertEquals(0.0f, state.offsetY(), 0.001f);
    }

    @Test
    void opposingQuadrantsAnchorToViewportEdges() {
        ViewportHudManager manager = new ViewportHudManager();
        manager.beginFrame(10.0f, 20.0f, 500.0f, 300.0f);

        var left = manager.place(ViewportHudManager.Quadrant.TOP_LEFT, 100.0f, 20.0f);
        var right = manager.place(ViewportHudManager.Quadrant.TOP_RIGHT, 100.0f, 20.0f);

        assertTrue(left.x() < right.x());
        assertEquals(left.y(), right.y());
    }
}
