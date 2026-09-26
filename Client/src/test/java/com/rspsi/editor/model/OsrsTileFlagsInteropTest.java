package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

class OsrsTileFlagsInteropTest {

    @Test
    void preservesJavaStaticConstantSurface() throws Exception {
        assertConstant("BLOCK_MAP_SQUARE", 0x1);
        assertConstant("BRIDGE", 0x2);
        assertConstant("REMOVE_ROOFS", 0x4);
        assertConstant("VIS_BELOW", 0x8);
        assertConstant("MINIMAP_BRIDGE", 0x8);
        assertConstant("MINIMAP_HIDDEN", 0x18);

        assertTrue(OsrsTileFlags.class.getField("MINIMAP_BRIDGE")
                .isAnnotationPresent(Deprecated.class));
    }

    @Test
    void preservesJavaStaticPredicateSurface() throws Exception {
        assertTrue(Modifier.isStatic(
                OsrsTileFlags.class.getMethod("hasBridge", int.class).getModifiers()));
        assertTrue(Modifier.isStatic(
                OsrsTileFlags.class.getMethod("removesRoofs", int.class).getModifiers()));
        assertTrue(Modifier.isStatic(
                OsrsTileFlags.class.getMethod("visibleBelow", int.class).getModifiers()));
        assertTrue(Modifier.isStatic(
                OsrsTileFlags.class.getMethod("isBlocked", int.class).getModifiers()));
    }

    @Test
    void predicatesInspectOnlyTheirOwnedBits() {
        int combined = OsrsTileFlags.BLOCK_MAP_SQUARE
                | OsrsTileFlags.BRIDGE
                | OsrsTileFlags.REMOVE_ROOFS
                | OsrsTileFlags.VIS_BELOW;

        assertTrue(OsrsTileFlags.hasBridge(combined));
        assertTrue(OsrsTileFlags.removesRoofs(combined));
        assertTrue(OsrsTileFlags.visibleBelow(combined));
        assertTrue(OsrsTileFlags.isBlocked(combined));

        assertFalse(OsrsTileFlags.hasBridge(OsrsTileFlags.REMOVE_ROOFS));
        assertFalse(OsrsTileFlags.removesRoofs(OsrsTileFlags.BRIDGE));
        assertFalse(OsrsTileFlags.visibleBelow(OsrsTileFlags.BLOCK_MAP_SQUARE));
        assertFalse(OsrsTileFlags.isBlocked(OsrsTileFlags.VIS_BELOW));
    }

    @Test
    void preservesAliasAndMinimapMaskSemantics() {
        assertEquals(OsrsTileFlags.VIS_BELOW, OsrsTileFlags.MINIMAP_BRIDGE);
        assertEquals(0x18, OsrsTileFlags.MINIMAP_HIDDEN);
    }

    private static void assertConstant(String name, int expected) throws Exception {
        Field field = OsrsTileFlags.class.getField(name);
        assertTrue(Modifier.isPublic(field.getModifiers()));
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertEquals(expected, field.getInt(null));
    }
}
