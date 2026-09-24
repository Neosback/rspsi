package com.rspsi.osrs.rules.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelTransformPipelineTest {

    @Test
    void fixedAngleLookupMatchesLegacyClientFormulaAcrossWholeCircle() {
        int[][] samples = {
                {0, 0},
                {128, 64},
                {-512, 1024},
                {4096, -2048}
        };

        for (int angle = 0; angle < 2048; angle++) {
            int sine = (int) (65536.0
                    * Math.sin(angle * Math.PI * 2.0 / 2048.0));
            int cosine = (int) (65536.0
                    * Math.cos(angle * Math.PI * 2.0 / 2048.0));

            for (int[] sample : samples) {
                int x = sample[0];
                int z = sample[1];
                int expectedX = (sine * z + cosine * x) >> 16;
                int expectedZ = (cosine * z - sine * x) >> 16;

                long packed = ModelTransformPipeline.rotateJagexAnglePacked(
                        x, z, angle);
                assertEquals(expectedX,
                        ModelTransformPipeline.unpackX(packed),
                        "x angle=" + angle);
                assertEquals(expectedZ,
                        ModelTransformPipeline.unpackZ(packed),
                        "z angle=" + angle);
                assertArrayEquals(
                        new int[]{expectedX, expectedZ},
                        ModelTransformPipeline.rotateJagexAngle(x, z, angle));
            }
        }
    }

    @Test
    void packedQuarterTurnsMatchCompatibilityHelper() {
        for (int rotation = 0; rotation < 12; rotation++) {
            long packed = ModelTransformPipeline.rotateQuarterTurnPacked(
                    37, -91, rotation);
            assertArrayEquals(
                    ModelTransformPipeline.rotateQuarterTurn(37, -91, rotation),
                    new int[]{
                            ModelTransformPipeline.unpackX(packed),
                            ModelTransformPipeline.unpackZ(packed)
                    });
        }
    }

    @Test
    void jagexAnglesWrapAt2048Units() {
        long base = ModelTransformPipeline.rotateJagexAnglePacked(
                321, -654, 256);
        long wrapped = ModelTransformPipeline.rotateJagexAnglePacked(
                321, -654, 2304);
        long negativeWrapped = ModelTransformPipeline.rotateJagexAnglePacked(
                321, -654, -1792);

        assertEquals(base, wrapped);
        assertEquals(base, negativeWrapped);
    }
}
