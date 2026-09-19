package com.rspsi.editor.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuPriorityTest {
    @Test
    void faceBiasChangesReferenceDepthWithoutChangingCompatibilityOverload() {
        float unbiassed = GpuPriority.biasedDepth(100.0f, 1.0f, 1000.0f, 0);
        float biassed = GpuPriority.biasedDepth(100.0f, 1.0f, 1000.0f, 0, 23);

        assertNotEquals(unbiassed, biassed);
        assertTrue(biassed > 1.0f);
        assertTrue(biassed < 1000.0f);
    }

    /**
     * The client subtracts {@code faceBias * 2} from the view-space depth in
     * world units (Model.java), so the pull toward the camera is the same
     * size no matter how far away the face is. An earlier clip-space
     * {@code z += bias / 128} formulation was distance-scaled and collapsed
     * to almost nothing up close, letting flush wall decorations z-fight.
     */
    @Test
    void faceBiasIsAConstantWorldSpaceOffsetAtEveryDistance() {
        assertEquals(500.0f - 46.0f, GpuPriority.biasedDepth(500.0f, 1.0f, 1000.0f, 0, 23));
        assertEquals(5000.0f - 46.0f, GpuPriority.biasedDepth(5000.0f, 1.0f, 100000.0f, 0, 23));
        assertEquals(1000.0f - 2.0f, GpuPriority.biasedDepth(1000.0f, 1.0f, 1000.0f, 0, 1));
    }

    @Test
    void zeroFaceBiasLeavesDepthUntouchedAndBiasNeverCrossesTheNearPlane() {
        assertEquals(700.0f, GpuPriority.biasedDepth(700.0f, 1.0f, 1000.0f, 0, 0));
        // A bias large enough to pull the face behind the eye must not
        // produce a negative or near-plane-crossing depth.
        assertEquals(10.0f, GpuPriority.biasedDepth(10.0f, 16.0f, 1000.0f, 0, 255));
    }
}
