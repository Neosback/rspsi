package com.rspsi.editor.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelFaceColorContractTest {
    @Test
    void smoothUntexturedFacesPreserveHueSaturationAndRelightEachVertex() {
        ModelFaceColorContract.LitFace lit =
                ModelFaceColorContract.shade(0x1234, false, 0,
                        64, 128, 256, 0);

        assertEquals(0x121A, lit.colorA());
        assertEquals(0x1234, lit.colorB());
        assertEquals(0x1268, lit.colorC());
        assertFalse(lit.flat());
        assertFalse(lit.skipped());
    }

    @Test
    void flatUntexturedFaceMatchesClientZeroInitializedSecondSlot() {
        ModelFaceColorContract.LitFace lit =
                ModelFaceColorContract.shade(0x1234, false, 1,
                        1, 1, 1, 64);

        assertEquals(0x121A, lit.colorA());
        assertEquals(0, lit.colorB());
        assertEquals(ModelFaceColorContract.FLAT_SENTINEL, lit.colorC());
        assertTrue(lit.flat());
    }

    @Test
    void renderTypeThreeMatchesClientConstantFaceColorSlots() {
        ModelFaceColorContract.LitFace lit =
                ModelFaceColorContract.shade(0x1234, false, 3,
                        20, 30, 40, 50);

        assertEquals(128, lit.colorA());
        assertEquals(0, lit.colorB());
        assertEquals(ModelFaceColorContract.FLAT_SENTINEL, lit.colorC());
    }

    @Test
    void renderTypeTwoPreservesSkippedFaceSentinel() {
        ModelFaceColorContract.LitFace lit =
                ModelFaceColorContract.shade(0x1234, false, 2,
                        20, 30, 40, 50);

        assertEquals(0, lit.colorA());
        assertEquals(0, lit.colorB());
        assertEquals(ModelFaceColorContract.SKIP_SENTINEL, lit.colorC());
        assertTrue(lit.skipped());
    }

    @Test
    void texturedSmoothFacesCarryOnlyClampedLightScalars() {
        ModelFaceColorContract.LitFace lit =
                ModelFaceColorContract.shade(0xFFFF, true, 0,
                        1, 64, 200, 0);

        assertEquals(2, lit.colorA());
        assertEquals(64, lit.colorB());
        assertEquals(126, lit.colorC());
    }

    @Test
    void texturedFlatFaceKeepsClientSentinelAndZeroSecondSlot() {
        ModelFaceColorContract.LitFace lit =
                ModelFaceColorContract.shade(0xFFFF, true, 1,
                        10, 20, 30, 200);

        assertEquals(126, lit.colorA());
        assertEquals(0, lit.colorB());
        assertEquals(ModelFaceColorContract.FLAT_SENTINEL, lit.colorC());
    }

    @Test
    void texturedSpecialFacesUseSkippedSentinel() {
        assertEquals(new ModelFaceColorContract.LitFace(0, 0, -2),
                ModelFaceColorContract.shade(100, true, 2, 1, 2, 3, 4));
        assertEquals(new ModelFaceColorContract.LitFace(0, 0, -2),
                ModelFaceColorContract.shade(100, true, 3, 1, 2, 3, 4));
    }

    @Test
    void modelTriangleRetainsUnlitColorAcrossRelighting() {
        ModelTriangle source = new ModelTriangle(
                0, 1, 2, 10, 20, 30,
                -1, 0, 0, 0,
                0, 0, 1, 0, 0, 1,
                0x4567, 0);

        ModelTriangle relit = source.withColors(40, 50, 60);

        assertEquals(0x4567, source.unlitColor());
        assertEquals(0x4567, relit.unlitColor());
    }
}
