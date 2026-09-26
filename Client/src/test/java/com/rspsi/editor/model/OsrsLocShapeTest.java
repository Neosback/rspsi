package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OsrsLocShapeTest {

    @Test
    void coversEveryCanonicalOsrsLocationShape() {
        OsrsLocShape[] shapes = OsrsLocShape.values();

        assertEquals(23, shapes.length);
        for (int type = 0; type <= 22; type++) {
            OsrsLocShape shape = OsrsLocShape.fromId(type).orElseThrow();

            assertSame(shapes[type], shape, "shape enum order/id drifted for " + type);
            assertEquals(type, shape.id());
            assertTrue(ObjectCategory.fromType(type).isKnown(), "missing category " + type);
        }

        assertTrue(OsrsLocShape.fromId(-1).isEmpty());
        assertTrue(OsrsLocShape.fromId(23).isEmpty());
        assertTrue(OsrsLocShape.fromId(Integer.MAX_VALUE).isEmpty());
        assertEquals(ObjectCategory.UNKNOWN, ObjectCategory.fromType(23));
    }

    @Test
    void preservesObjectCategoryMetadataAndOrdering() {
        assertArrayEquals(
                new ObjectCategory[]{
                        ObjectCategory.WALL,
                        ObjectCategory.WALL_DECOR,
                        ObjectCategory.GROUND,
                        ObjectCategory.GROUND_DECOR,
                        ObjectCategory.UNKNOWN
                },
                ObjectCategory.values());

        assertEquals(0, ObjectCategory.WALL.layerId());
        assertEquals(1, ObjectCategory.WALL_DECOR.layerId());
        assertEquals(2, ObjectCategory.GROUND.layerId());
        assertEquals(3, ObjectCategory.GROUND_DECOR.layerId());
        assertEquals(-1, ObjectCategory.UNKNOWN.layerId());

        assertEquals("Wall", ObjectCategory.WALL.displayName());
        assertEquals("Ground decor", ObjectCategory.GROUND_DECOR.displayName());
        assertTrue(ObjectCategory.WALL.isKnown());
        assertFalse(ObjectCategory.UNKNOWN.isKnown());
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
    void preservesRepresentativeShapeMetadata() {
        assertEquals("Straight wall", OsrsLocShape.WALL_STRAIGHT.displayName());
        assertEquals(ObjectCategory.WALL, OsrsLocShape.WALL_STRAIGHT.category());

        assertEquals("Straight game object", OsrsLocShape.CENTREPIECE_STRAIGHT.displayName());
        assertEquals(ObjectCategory.GROUND, OsrsLocShape.CENTREPIECE_STRAIGHT.category());

        assertEquals("Ground decor", OsrsLocShape.GROUND_DECOR.displayName());
        assertEquals(ObjectCategory.GROUND_DECOR, OsrsLocShape.GROUND_DECOR.category());
    }

    @Test
    void worldObjectExposesInspectorSemanticsWithoutDonorTypes() {
        WorldObject wall = new WorldObject(100, 0, 2, 0, 10, 10);

        assertEquals(ObjectCategory.WALL, wall.category());
        assertEquals("Straight wall", wall.shape().orElseThrow().displayName());
    }

    @Test
    void exposesRuneliteWallOrientationBitfields() {
        assertEquals(1, new WorldObject(1, 0, 0, 0, 0, 0).wallOrientationA());
        assertEquals(2, new WorldObject(1, 0, 1, 0, 0, 0).wallOrientationA());
        assertEquals(4, new WorldObject(1, 0, 2, 0, 0, 0).wallOrientationA());
        assertEquals(8, new WorldObject(1, 0, 3, 0, 0, 0).wallOrientationA());
        assertEquals(16, new WorldObject(1, 1, 0, 0, 0, 0).wallOrientationA());
        assertEquals(32, new WorldObject(1, 1, 1, 0, 0, 0).wallOrientationA());
        assertEquals(2, new WorldObject(1, 2, 0, 0, 0, 0).wallOrientationB());
        assertEquals(0, new WorldObject(1, 10, 0, 0, 0, 0).wallOrientationA());
    }
}
