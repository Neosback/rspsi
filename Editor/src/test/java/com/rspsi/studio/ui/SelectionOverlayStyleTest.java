package com.rspsi.studio.ui;

import com.rspsi.editor.model.ObjectCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SelectionOverlayStyleTest {

    @Test
    void clampsAlphaAndThicknessAtThePublicBoundary() {
        SelectionOverlayStyle style = new SelectionOverlayStyle();

        style.setFillAlpha(-10);
        style.setTileFillAlpha(999);
        style.setOutlineThickness(0.1f);

        assertEquals(0, style.fillAlpha());
        assertEquals(255, style.tileFillAlpha());
        assertEquals(0.5f, style.outlineThickness());
    }

    @Test
    void fillColorKeepsRgbAndReplacesAlpha() {
        SelectionOverlayStyle style = new SelectionOverlayStyle();
        style.setOutlineColor(ObjectCategory.WALL, 0x123456AA);
        style.setFillAlpha(0x44);

        assertEquals(0x12345644, style.fillColor(ObjectCategory.WALL));
    }

    @Test
    void resetRestoresAllMutableDefaults() {
        SelectionOverlayStyle style = new SelectionOverlayStyle();
        style.setObjectHullEnabled(false);
        style.setShowObjectInfo(true);
        style.setOutlineColor(ObjectCategory.WALL, 0);
        style.setFillAlpha(1);
        style.setOutlineThickness(9.0f);
        style.setPaintedEdge(false);
        style.setTileOutlineColor(0);
        style.setTileFillAlpha(1);

        style.resetToDefaults();

        assertTrue(style.objectHullEnabled());
        assertFalse(style.showObjectInfo());
        assertEquals(0xF59E0BFF, style.outlineColor(ObjectCategory.WALL));
        assertEquals(128, style.fillAlpha());
        assertEquals(2.0f, style.outlineThickness());
        assertTrue(style.paintedEdge());
        assertEquals(0x40E0D0FF, style.tileOutlineColor());
        assertEquals(40, style.tileFillAlpha());
    }

    @Test
    void sharedReturnsTheSameProcessWideStyle() {
        assertSame(SelectionOverlayStyle.shared(), SelectionOverlayStyle.shared());
    }
}
