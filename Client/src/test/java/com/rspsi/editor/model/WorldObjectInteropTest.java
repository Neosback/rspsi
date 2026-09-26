package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldObjectInteropTest {

    @Test
    void remainsJvmRecordWithJavaComponentAccessors() {
        WorldObject object = new WorldObject(100, 10, 2, 1, 3200, 3201);

        assertTrue(WorldObject.class.isRecord());
        assertEquals(100, object.id());
        assertEquals(10, object.type());
        assertEquals(2, object.rotation());
        assertEquals(1, object.plane());
        assertEquals(3200, object.x());
        assertEquals(3201, object.y());
        assertEquals(new WorldObject(100, 10, 2, 1, 3200, 3201), object);
        assertTrue(object.toString().startsWith("WorldObject["));
    }

    @Test
    void preservesShapeAndCategorySemantics() {
        WorldObject wall = new WorldObject(1, 0, 0, 0, 10, 10);
        WorldObject unsupported = new WorldObject(2, 99, 0, 0, 10, 10);

        assertEquals(ObjectCategory.WALL, wall.category());
        assertEquals(OsrsLocShape.WALL_STRAIGHT, wall.shape().orElseThrow());

        assertEquals(ObjectCategory.UNKNOWN, unsupported.category());
        assertTrue(unsupported.shape().isEmpty());
    }

    @Test
    void preservesRuneliteWallOrientationAForCardinalAndDiagonalShapes() {
        assertEquals(1, new WorldObject(1, 0, 0, 0, 0, 0).wallOrientationA());
        assertEquals(2, new WorldObject(1, 0, 1, 0, 0, 0).wallOrientationA());
        assertEquals(4, new WorldObject(1, 2, 2, 0, 0, 0).wallOrientationA());
        assertEquals(8, new WorldObject(1, 2, 3, 0, 0, 0).wallOrientationA());

        assertEquals(16, new WorldObject(1, 1, 0, 0, 0, 0).wallOrientationA());
        assertEquals(32, new WorldObject(1, 1, 1, 0, 0, 0).wallOrientationA());
        assertEquals(64, new WorldObject(1, 3, 2, 0, 0, 0).wallOrientationA());
        assertEquals(128, new WorldObject(1, 3, 3, 0, 0, 0).wallOrientationA());

        assertEquals(0, new WorldObject(1, 10, 0, 0, 0, 0).wallOrientationA());
    }

    @Test
    void preservesSecondLWallOrientationWrapping() {
        assertEquals(2, new WorldObject(1, 2, 0, 0, 0, 0).wallOrientationB());
        assertEquals(4, new WorldObject(1, 2, 1, 0, 0, 0).wallOrientationB());
        assertEquals(8, new WorldObject(1, 2, 2, 0, 0, 0).wallOrientationB());
        assertEquals(1, new WorldObject(1, 2, 3, 0, 0, 0).wallOrientationB());
        assertEquals(0, new WorldObject(1, 0, 0, 0, 0, 0).wallOrientationB());
    }

    @Test
    void preservesValidationContract() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorldObject(-1, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldObject(1, -1, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldObject(1, 0, -1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldObject(1, 0, 4, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldObject(1, 0, 0, -1, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldObject(1, 0, 0, 0, -1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldObject(1, 0, 0, 0, 0, -1));
    }
}
