package com.rspsi.editor.transform;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WorldFragmentTransformInteropTest {

    @Test
    void footprintResolverRemainsJavaSamWithNestedRecord() {
        assertTrue(ObjectFootprintResolver.class.isInterface());
        assertTrue(ObjectFootprintResolver.class.isAnnotationPresent(FunctionalInterface.class));

        ObjectFootprintResolver resolver =
                object -> Optional.of(new ObjectFootprintResolver.ObjectFootprint(2, 3));

        var footprint = resolver.resolve(null).orElseThrow();
        assertEquals(2, footprint.width());
        assertEquals(3, footprint.length());
        assertTrue(ObjectFootprintResolver.ObjectFootprint.class.isRecord());
        assertEquals("ObjectFootprint[width=2, length=3]", footprint.toString());
    }

    @Test
    void footprintResolverPreservesValidationAndFactoryNullFailure() {
        IllegalArgumentException footprintFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ObjectFootprintResolver.ObjectFootprint(0, 1));
        assertEquals("Object footprint must be positive", footprintFailure.getMessage());

        NullPointerException assetsFailure = assertThrows(
                NullPointerException.class,
                () -> ObjectFootprintResolver.fromAssets(null));
        assertEquals("assets", assetsFailure.getMessage());
    }

    @Test
    void transformRemainsJvmRecordWithHistoricalConstructorsAndStaticFactories() throws Exception {
        assertTrue(WorldFragmentTransform.class.isRecord());
        assertNotNull(WorldFragmentTransform.class.getDeclaredConstructor(
                int.class, boolean.class, boolean.class, Optional.class));
        assertNotNull(WorldFragmentTransform.class.getDeclaredConstructor(
                int.class, boolean.class, boolean.class));

        assertTrue(Modifier.isStatic(
                WorldFragmentTransform.class.getDeclaredMethod("identity").getModifiers()));
        assertTrue(Modifier.isStatic(
                WorldFragmentTransform.class.getDeclaredMethod("rotate", int.class).getModifiers()));
        assertTrue(Modifier.isStatic(
                WorldFragmentTransform.class.getDeclaredMethod("reflectX").getModifiers()));
        assertTrue(Modifier.isStatic(
                WorldFragmentTransform.class.getDeclaredMethod("reflectY").getModifiers()));

        WorldFragmentTransform identity = WorldFragmentTransform.identity();
        assertEquals(0, identity.quarterTurns());
        assertFalse(identity.mirrorX());
        assertFalse(identity.mirrorY());
        assertEquals(Optional.empty(), identity.pivot());
        assertEquals(
                "WorldFragmentTransform[quarterTurns=0, mirrorX=false, mirrorY=false, pivot=Optional.empty]",
                identity.toString());
    }

    @Test
    void transformPreservesPivotAndValidationSemantics() {
        NullPointerException constructorFailure = assertThrows(
                NullPointerException.class,
                () -> new WorldFragmentTransform(0, false, false, null));
        assertEquals("pivot", constructorFailure.getMessage());

        IllegalArgumentException rotationFailure = assertThrows(
                IllegalArgumentException.class,
                () -> WorldFragmentTransform.rotate(4));
        assertEquals("Quarter turns must be in [0, 3]", rotationFailure.getMessage());

        var pivot = new WorldFragmentTransform.Pivot(12, 34);
        assertTrue(WorldFragmentTransform.Pivot.class.isRecord());
        assertEquals("Pivot[x=12, y=34]", pivot.toString());

        WorldFragmentTransform around = WorldFragmentTransform.rotate(1).around(pivot);
        assertEquals(Optional.of(pivot), around.pivot());

        NullPointerException aroundFailure = assertThrows(
                NullPointerException.class,
                () -> around.around(null));
        assertEquals("pivot", aroundFailure.getMessage());

        IllegalArgumentException pivotFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new WorldFragmentTransform.Pivot(-1, 0));
        assertEquals("Fragment pivot cannot be negative", pivotFailure.getMessage());
    }
}
