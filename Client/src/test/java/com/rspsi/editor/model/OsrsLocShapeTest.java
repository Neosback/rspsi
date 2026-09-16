package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsrsLocShapeTest {
    @Test
    void coversEveryCanonicalOsrsLocationShape() {
        for (int type = 0; type <= 22; type++) {
            assertTrue(OsrsLocShape.fromId(type).isPresent(), "missing shape " + type);
            assertTrue(ObjectCategory.fromType(type).isKnown(), "missing category " + type);
        }
        assertFalse(OsrsLocShape.fromId(-1).isPresent());
        assertFalse(OsrsLocShape.fromId(23).isPresent());
        assertEquals(ObjectCategory.UNKNOWN, ObjectCategory.fromType(23));
    }

    @Test
    void mapsShapesToTheSameLayersAsOpenRune() {
        assertEquals(ObjectCategory.WALL, ObjectCategory.fromType(0));
        assertEquals(ObjectCategory.WALL_DECOR, ObjectCategory.fromType(4));
        assertEquals(ObjectCategory.GROUND, ObjectCategory.fromType(10));
        assertEquals(ObjectCategory.GROUND, ObjectCategory.fromType(21));
        assertEquals(ObjectCategory.GROUND_DECOR, ObjectCategory.fromType(22));
    }

    @Test
    void worldObjectExposesInspectorSemanticsWithoutDonorTypes() {
        WorldObject wall = new WorldObject(100, 0, 2, 0, 10, 10);

        assertEquals(ObjectCategory.WALL, wall.category());
        assertEquals("Straight wall", wall.shape().orElseThrow().displayName());
    }
}
