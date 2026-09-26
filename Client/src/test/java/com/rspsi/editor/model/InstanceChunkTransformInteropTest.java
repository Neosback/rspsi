package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("deprecation")
class InstanceChunkTransformInteropTest {

    @Test
    void preservesPublicFinalClassAndAccessorSurface() throws Exception {
        assertTrue(Modifier.isPublic(InstanceChunkTransform.class.getModifiers()));
        assertTrue(Modifier.isFinal(InstanceChunkTransform.class.getModifiers()));

        InstanceChunkTemplate template =
                new InstanceChunkTemplate(1, 2, 3, 0, 400, 500, 1);
        InstanceChunkTransform transform =
                new InstanceChunkTransform(template, 3200, 3216);

        assertSame(template, transform.template());
        assertEquals(3200, transform.sceneBaseX());
        assertEquals(3216, transform.sceneBaseY());

        assertEquals(InstanceChunkTemplate.class,
                InstanceChunkTransform.class.getMethod("template").getReturnType());
    }

    @Test
    void preservesExplicitNullFailuresAndNullableContainsChecks() {
        NullPointerException constructorFailure = assertThrows(
                NullPointerException.class,
                () -> new InstanceChunkTransform(null, 0, 0));
        assertEquals("template", constructorFailure.getMessage());

        InstanceChunkTransform transform = new InstanceChunkTransform(
                new InstanceChunkTemplate(0, 0, 0, 0, 0, 0, 0), 0, 0);

        assertFalse(transform.containsSource(null));
        assertFalse(transform.containsScene(null));

        NullPointerException sourceFailure = assertThrows(
                NullPointerException.class,
                () -> transform.sourceToScene(null));
        assertEquals("source", sourceFailure.getMessage());

        NullPointerException sceneFailure = assertThrows(
                NullPointerException.class,
                () -> transform.sceneToSource(null));
        assertEquals("scene", sceneFailure.getMessage());

        NullPointerException objectFailure = assertThrows(
                NullPointerException.class,
                () -> transform.sourceObjectToScene(null));
        assertEquals("source", objectFailure.getMessage());
    }

    @Test
    void preservesSceneBaseAndObjectValidation() {
        InstanceChunkTemplate template =
                new InstanceChunkTemplate(0, 0, 0, 0, 0, 0, 0);

        assertThrows(IllegalArgumentException.class,
                () -> new InstanceChunkTransform(template, -1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new InstanceChunkTransform(template, 0, -1));

        InstanceChunkTransform transform =
                new InstanceChunkTransform(template, 0, 0);

        assertThrows(IllegalArgumentException.class,
                () -> transform.sourceObjectRotationToScene(-1));
        assertThrows(IllegalArgumentException.class,
                () -> transform.sourceObjectRotationToScene(4));

        WorldObject object = new WorldObject(1, 10, 0, 0, 1, 1);
        assertThrows(IllegalArgumentException.class,
                () -> transform.sourceObjectToScene(object, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> transform.sourceObjectToScene(object, 1, 0));
    }

    @Test
    void preservesOutOfChunkFailureMessages() {
        InstanceChunkTransform transform = new InstanceChunkTransform(
                new InstanceChunkTemplate(1, 0, 0, 0, 10, 20, 0),
                3200,
                3216);

        TileCoordinate source = new TileCoordinate(0, 79, 160);
        IndexOutOfBoundsException sourceFailure = assertThrows(
                IndexOutOfBoundsException.class,
                () -> transform.sourceToScene(source));
        assertEquals(
                "Source tile is outside instance chunk: " + source,
                sourceFailure.getMessage());

        TileCoordinate scene = new TileCoordinate(1, 3199, 3216);
        IndexOutOfBoundsException sceneFailure = assertThrows(
                IndexOutOfBoundsException.class,
                () -> transform.sceneToSource(scene));
        assertEquals(
                "Scene tile is outside instance chunk: " + scene,
                sceneFailure.getMessage());
    }
}
