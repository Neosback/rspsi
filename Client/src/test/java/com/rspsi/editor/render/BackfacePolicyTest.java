package com.rspsi.editor.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackfacePolicyTest {
    @Test
    void softwareFrontFaceMapsToCounterClockwiseNativeWindowWinding() {
        float softwareArea = -42.0f;

        assertTrue(BackfacePolicy.isFrontFacingSoftware(softwareArea));
        assertTrue(BackfacePolicy.nativeWindowArea(softwareArea) > 0.0f);
        assertEquals(BackfacePolicy.NativeWinding.COUNTER_CLOCKWISE,
                BackfacePolicy.nativeWinding());
    }

    @Test
    void softwareBackFaceMapsToClockwiseNativeWindowWinding() {
        float softwareArea = 42.0f;

        assertTrue(!BackfacePolicy.isFrontFacingSoftware(softwareArea));
        assertTrue(BackfacePolicy.nativeWindowArea(softwareArea) < 0.0f);
    }

    @Test
    void degenerateTrianglesRemainRejected() {
        assertTrue(!BackfacePolicy.isFrontFacingSoftware(0.0f));
        assertTrue(!BackfacePolicy.isFrontFacingSoftware(-0.00001f));
    }
}
