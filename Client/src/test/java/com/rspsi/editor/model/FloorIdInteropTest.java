package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

class FloorIdInteropTest {

    @Test
    void preservesJavaStaticUtilitySurface() throws Exception {
        assertTrue(Modifier.isFinal(FloorId.class.getModifiers()));
        assertTrue(Modifier.isStatic(FloorId.class.getMethod("definitionId", int.class).getModifiers()));
        assertTrue(Modifier.isStatic(FloorId.class.getMethod("encode", int.class).getModifiers()));

        assertEquals(0, FloorId.definitionId(1));
        assertEquals(41, FloorId.definitionId(42));
        assertEquals(1, FloorId.encode(0));
        assertEquals(42, FloorId.encode(41));
    }

    @Test
    void preservesNoFloorSentinels() {
        assertEquals(-1, FloorId.definitionId(0));
        assertEquals(-1, FloorId.definitionId(-1));
        assertEquals(-1, FloorId.definitionId(Integer.MIN_VALUE));

        assertEquals(0, FloorId.encode(-1));
        assertEquals(0, FloorId.encode(Integer.MIN_VALUE));
    }

    @Test
    void preservesHistoricalIntOverflowBehavior() {
        assertEquals(Integer.MIN_VALUE, FloorId.encode(Integer.MAX_VALUE));
    }

    @Test
    void canonicalValuesRoundTrip() {
        for (int definitionId = 0; definitionId < 256; definitionId++) {
            assertEquals(definitionId, FloorId.definitionId(FloorId.encode(definitionId)));
        }
    }
}
