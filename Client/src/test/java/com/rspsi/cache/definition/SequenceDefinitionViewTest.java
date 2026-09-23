package com.rspsi.cache.definition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SequenceDefinitionViewTest {
    @Test
    void retainsSignedAnimationHeightOffset() {
        SequenceDefinitionView sequence = new SequenceDefinitionView(
                42, new int[]{100}, new int[]{3}, 1, false,
                -1, -1, 99, 0, 0, 2, -1, -12);

        assertEquals(-12, sequence.animationHeightOffset());
    }

    @Test
    void compatibilityConstructorDefaultsAnimationHeightOffsetToZero() {
        SequenceDefinitionView sequence = new SequenceDefinitionView(
                42, new int[]{100}, new int[]{3}, 1, false,
                -1, -1, 99, 0, 0, 2, -1);

        assertEquals(0, sequence.animationHeightOffset());
    }

    @Test
    void retainsCachedSkeletalFrameRange() {
        SequenceDefinitionView sequence = new SequenceDefinitionView(
                42, new int[0], new int[0], 3, false,
                -1, -1, 99, 0, 0, 2,
                0x12340005, 10, 18, -7);

        assertEquals(0x12340005, sequence.skeletalId());
        assertEquals(10, sequence.skeletalRangeBegin());
        assertEquals(18, sequence.skeletalRangeEnd());
        assertEquals(8, sequence.cachedFrameCount());
        org.junit.jupiter.api.Assertions.assertTrue(sequence.cachedSkeletal());
        assertEquals(-7, sequence.animationHeightOffset());
    }

    @Test
    void rejectsOffsetsOutsideTheSignedClientByteRange() {
        assertThrows(IllegalArgumentException.class, () -> new SequenceDefinitionView(
                42, new int[]{100}, new int[]{3}, 1, false,
                -1, -1, 99, 0, 0, 2, -1, 128));
    }
}
