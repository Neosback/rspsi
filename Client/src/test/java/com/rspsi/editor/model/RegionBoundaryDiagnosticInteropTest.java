package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegionBoundaryDiagnosticInteropTest {

    @Test
    void directionEnumPreservesExistingJavaConstantsAndOrder() {
        assertArrayEquals(
                new RegionBoundaryDirection[]{
                        RegionBoundaryDirection.EAST,
                        RegionBoundaryDirection.NORTH
                },
                RegionBoundaryDirection.values());
    }

    @Test
    void mismatchRemainsJvmRecordWithRecordStyleAccessors() {
        RegionBoundaryMismatch mismatch = new RegionBoundaryMismatch(
                RegionBoundaryDirection.EAST,
                2,
                50,
                51,
                63,
                true,
                120,
                116);

        assertTrue(RegionBoundaryMismatch.class.isRecord());
        assertEquals(RegionBoundaryDirection.EAST, mismatch.direction());
        assertEquals(2, mismatch.plane());
        assertEquals(50, mismatch.regionX());
        assertEquals(51, mismatch.regionY());
        assertEquals(63, mismatch.alongEdge());
        assertTrue(mismatch.upperCorner());
        assertEquals(120, mismatch.expected());
        assertEquals(116, mismatch.actual());

        RegionBoundaryMismatch same = new RegionBoundaryMismatch(
                RegionBoundaryDirection.EAST, 2, 50, 51, 63, true, 120, 116);
        assertEquals(mismatch, same);
        assertEquals(mismatch.hashCode(), same.hashCode());
        assertTrue(mismatch.toString().startsWith("RegionBoundaryMismatch["));
    }

    @Test
    void mismatchPreservesBoundaryLocationValidation() {
        assertThrows(NullPointerException.class,
                () -> new RegionBoundaryMismatch(null, 0, 0, 0, 0, false, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new RegionBoundaryMismatch(RegionBoundaryDirection.EAST, -1, 0, 0, 0, false, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new RegionBoundaryMismatch(RegionBoundaryDirection.EAST, 0, -1, 0, 0, false, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new RegionBoundaryMismatch(RegionBoundaryDirection.EAST, 0, 0, -1, 0, false, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new RegionBoundaryMismatch(RegionBoundaryDirection.EAST, 0, 0, 0, -1, false, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new RegionBoundaryMismatch(RegionBoundaryDirection.NORTH, 0, 0, 0, 64, false, 0, 0));
    }

    @Test
    void mismatchAcceptsBothInclusiveEdgeExtremes() {
        assertDoesNotThrow(() -> new RegionBoundaryMismatch(
                RegionBoundaryDirection.EAST, 0, 0, 0, 0, false, 0, 0));
        assertDoesNotThrow(() -> new RegionBoundaryMismatch(
                RegionBoundaryDirection.NORTH, 0, 0, 0, 63, true, 0, 0));
    }
}
