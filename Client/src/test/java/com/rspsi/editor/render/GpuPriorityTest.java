package com.rspsi.editor.render;

import org.junit.jupiter.api.Test;

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
}
