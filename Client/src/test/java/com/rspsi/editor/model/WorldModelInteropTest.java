package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("deprecation")
class WorldModelInteropTest {

    @Test
    void remainsFinalDeprecatedWorldDocumentBridge() throws Exception {
        assertEquals(WorldDocument.class, WorldModel.class.getSuperclass());
        assertTrue(Modifier.isFinal(WorldModel.class.getModifiers()));
        assertTrue(WorldModel.class.isAnnotationPresent(Deprecated.class));

        assertTrue(Modifier.isPublic(
                WorldModel.class.getConstructor(int.class, int.class).getModifiers()));
        assertTrue(Modifier.isPublic(
                WorldModel.class.getConstructor(int.class, int.class, int.class).getModifiers()));
    }

    @Test
    void twoArgumentConstructorPreservesDefaultPlaneBehavior() {
        WorldModel model = new WorldModel(8, 9);

        assertEquals(8, model.width());
        assertEquals(9, model.length());
        assertEquals(WorldDocument.DEFAULT_PLANES, model.planes());
        assertEquals(WorldDocument.DEFAULT_PLANES, WorldModel.DEFAULT_PLANES);
    }

    @Test
    void threeArgumentConstructorPreservesExplicitPlanesAndValidation() {
        WorldModel model = new WorldModel(8, 9, 2);

        assertEquals(8, model.width());
        assertEquals(9, model.length());
        assertEquals(2, model.planes());

        assertThrows(IllegalArgumentException.class, () -> new WorldModel(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new WorldModel(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new WorldModel(1, 1, 0));
    }

    @Test
    void defaultPlanesRemainsDeclaredStaticFinalConstant() throws Exception {
        var field = WorldModel.class.getDeclaredField("DEFAULT_PLANES");

        assertTrue(Modifier.isPublic(field.getModifiers()));
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertEquals(4, field.getInt(null));
    }
}
