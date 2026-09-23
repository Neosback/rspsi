package com.rspsi.editor.render;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelContourContractTest {
    @Test
    void classifiesClientAndExtendedContourModes() {
        assertEquals(ModelContourContract.Mode.NONE,
                ModelContourContract.classify(-1, 0));
        assertEquals(ModelContourContract.Mode.FULL,
                ModelContourContract.classify(1, 0));
        assertEquals(ModelContourContract.Mode.PARTIAL,
                ModelContourContract.classify(1, 65536));
        assertEquals(ModelContourContract.Mode.PARTIAL,
                ModelContourContract.classify(2, 32768));
        assertEquals(ModelContourContract.Mode.CLAMPED_DELTA,
                ModelContourContract.classify(3, 64));
        assertEquals(ModelContourContract.Mode.ABOVE_PLANE_OFFSET,
                ModelContourContract.classify(4, 0));
        assertEquals(ModelContourContract.Mode.ABOVE_PLANE_BLEND,
                ModelContourContract.classify(5, 0));
        assertEquals(ModelContourContract.Mode.UNKNOWN_FULL,
                ModelContourContract.classify(9, 0));
    }

    @Test
    void appliedContractRetainsHillskewUnskewedYStream() {
        ModelContourContract contract = ModelContourContract.of(
                1, 0, 64, true, List.of(0, -128, -64));

        assertTrue(contract.present());
        assertTrue(contract.applied());
        assertTrue(contract.hasUnskewedModel());
        assertEquals(ModelContourContract.Mode.FULL, contract.mode());
        assertEquals(64, contract.placementHeight());
        assertEquals(-128, contract.unskewedY(1));
        assertEquals(3, contract.metadata().vertexCount());
        assertTrue(contract.metadata().hasUnskewedModel());
        contract.validateVertexCount(3);
    }

    @Test
    void configuredButSkippedContourHasNoUnskewedModel() {
        ModelContourContract contract = ModelContourContract.of(
                1, 0, 0, false, List.of(1, 2, 3));

        assertTrue(contract.present());
        assertTrue(!contract.applied());
        assertTrue(!contract.hasUnskewedModel());
        assertTrue(contract.unskewedVertexY().isEmpty());
        assertThrows(IllegalStateException.class, () -> contract.unskewedY(0));
    }

    @Test
    void unskewedStreamMustMatchRenderedVertexCount() {
        ModelContourContract contract = ModelContourContract.of(
                1, 0, 0, true, List.of(0, -64, -128));

        assertThrows(IllegalArgumentException.class,
                () -> contract.validateVertexCount(2));
    }
}
