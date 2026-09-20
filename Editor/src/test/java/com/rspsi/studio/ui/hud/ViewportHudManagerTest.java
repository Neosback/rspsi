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
    void opposingQuadrantsAnchorToViewportEdges() {
        ViewportHudManager manager = new ViewportHudManager();
        manager.beginFrame(10.0f, 20.0f, 500.0f, 300.0f);

        var left = manager.place(ViewportHudManager.Quadrant.TOP_LEFT, 100.0f, 20.0f);
        var right = manager.place(ViewportHudManager.Quadrant.TOP_RIGHT, 100.0f, 20.0f);

        assertTrue(left.x() < right.x());
        assertEquals(left.y(), right.y());
    }
}
