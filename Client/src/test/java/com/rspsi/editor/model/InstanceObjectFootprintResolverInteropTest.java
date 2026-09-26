package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

class InstanceObjectFootprintResolverInteropTest {

    @Test
    void remainsJavaFunctionalInterface() throws Exception {
        assertTrue(InstanceObjectFootprintResolver.class.isInterface());
        assertTrue(InstanceObjectFootprintResolver.class
                .isAnnotationPresent(FunctionalInterface.class));
        assertTrue(Modifier.isAbstract(
                InstanceObjectFootprintResolver.class
                        .getMethod("resolve", WorldObject.class)
                        .getModifiers()));

        InstanceObjectFootprintResolver resolver =
                object -> new InstanceObjectFootprintResolver.Footprint(2, 3);

        assertEquals(
                new InstanceObjectFootprintResolver.Footprint(2, 3),
                resolver.resolve(new WorldObject(1, 10, 0, 0, 5, 6)));
    }

    @Test
    void footprintRemainsNestedJvmRecord() {
        InstanceObjectFootprintResolver.Footprint footprint =
                new InstanceObjectFootprintResolver.Footprint(2, 3);

        assertTrue(InstanceObjectFootprintResolver.Footprint.class.isRecord());
        assertEquals(InstanceObjectFootprintResolver.class,
                InstanceObjectFootprintResolver.Footprint.class.getEnclosingClass());
        assertTrue(Modifier.isStatic(
                InstanceObjectFootprintResolver.Footprint.class.getModifiers()));

        assertEquals(2, footprint.width());
        assertEquals(3, footprint.length());
        assertEquals(
                new InstanceObjectFootprintResolver.Footprint(2, 3),
                footprint);
        assertEquals("Footprint[width=2, length=3]", footprint.toString());
    }

    @Test
    void footprintPreservesPositiveDimensionValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> new InstanceObjectFootprintResolver.Footprint(0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new InstanceObjectFootprintResolver.Footprint(1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new InstanceObjectFootprintResolver.Footprint(-1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new InstanceObjectFootprintResolver.Footprint(1, -1));
    }

    @Test
    void unitRemainsTrueJavaStaticFactory() throws Exception {
        assertTrue(Modifier.isStatic(
                InstanceObjectFootprintResolver.class
                        .getMethod("unit")
                        .getModifiers()));

        InstanceObjectFootprintResolver resolver =
                InstanceObjectFootprintResolver.unit();

        assertEquals(
                new InstanceObjectFootprintResolver.Footprint(1, 1),
                resolver.resolve(new WorldObject(1, 10, 0, 0, 5, 6)));
        assertEquals(
                new InstanceObjectFootprintResolver.Footprint(1, 1),
                resolver.resolve(null));
    }

    @Test
    void requireNonNullPreservesIdentityAndNullFailure() throws Exception {
        assertTrue(Modifier.isStatic(
                InstanceObjectFootprintResolver.class
                        .getMethod("requireNonNull", InstanceObjectFootprintResolver.class)
                        .getModifiers()));

        InstanceObjectFootprintResolver resolver =
                object -> new InstanceObjectFootprintResolver.Footprint(1, 2);

        assertSame(resolver, InstanceObjectFootprintResolver.requireNonNull(resolver));

        NullPointerException failure = assertThrows(
                NullPointerException.class,
                () -> InstanceObjectFootprintResolver.requireNonNull(null));

        assertEquals("resolver", failure.getMessage());
    }
}
