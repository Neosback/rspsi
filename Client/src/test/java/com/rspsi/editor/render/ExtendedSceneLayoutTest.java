package com.rspsi.editor.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtendedSceneLayoutTest {
    @Test
    void matchesRuneLiteDocumentedSceneAndExtendedDimensions() {
        ExtendedSceneLayout layout = ExtendedSceneLayout.runeLite();

        assertEquals(104, layout.sceneSize());
        assertEquals(184, layout.extendedSceneSize());
        assertEquals(40, layout.offset());

        assertEquals(new ExtendedSceneLayout.Point(40, 40), layout.toExtended(0, 0));
        assertEquals(new ExtendedSceneLayout.Point(143, 143), layout.toExtended(103, 103));
        assertEquals(new ExtendedSceneLayout.Point(0, 0),
                layout.toScene(40, 40).orElseThrow());
        assertEquals(new ExtendedSceneLayout.Point(103, 103),
                layout.toScene(143, 143).orElseThrow());
    }

    @Test
    void distinguishesExtendedBorderFromNormalSceneCoordinates() {
        ExtendedSceneLayout layout = ExtendedSceneLayout.runeLite();

        assertTrue(layout.containsExtended(0, 0));
        assertTrue(layout.containsExtended(183, 183));
        assertFalse(layout.containsExtended(184, 183));

        assertTrue(layout.isBorderCoordinate(39, 40));
        assertTrue(layout.isBorderCoordinate(144, 40));
        assertTrue(layout.isBorderCoordinate(40, 39));
        assertTrue(layout.isBorderCoordinate(40, 144));

        assertFalse(layout.isBorderCoordinate(40, 40));
        assertFalse(layout.isBorderCoordinate(143, 143));
        assertTrue(layout.toScene(39, 40).isEmpty());
        assertTrue(layout.toScene(144, 40).isEmpty());
    }
}
