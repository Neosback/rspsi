package com.rspsi.osrs.rules.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnimationResolverTest {
    @Test
    void legacyNextChangeSkipsCyclesInsideTheSameFrame() {
        int[] lengths = {1, 2, 3};

        assertEquals(2, AnimationResolver.nextAnimationFrameChangeCycle(
                3, lengths, 2, 0));
        assertEquals(2, AnimationResolver.nextAnimationFrameChangeCycle(
                3, lengths, 2, 1));
        assertEquals(5, AnimationResolver.nextAnimationFrameChangeCycle(
                3, lengths, 2, 2));
        assertEquals(9, AnimationResolver.nextAnimationFrameChangeCycle(
                3, lengths, 2, 6));
        assertEquals(12, AnimationResolver.nextAnimationFrameChangeCycle(
                3, lengths, 2, 9));
    }

    @Test
    void terminalLegacySequenceStopsSchedulingAfterItsLastFrame() {
        int[] lengths = {1, 2};

        assertEquals(2, AnimationResolver.nextAnimationFrameChangeCycle(
                2, lengths, -1, 0));
        assertEquals(-1, AnimationResolver.nextAnimationFrameChangeCycle(
                2, lengths, -1, 2));
        assertEquals(-1, AnimationResolver.nextAnimationFrameChangeCycle(
                1, new int[]{100}, 1, 0));
    }

    @Test
    void cachedNextChangePreservesPerCycleAnimationAndStopsOneFrameLoop() {
        assertEquals(1, AnimationResolver.nextCachedFrameChangeCycle(4, 2, 0));
        assertEquals(4, AnimationResolver.nextCachedFrameChangeCycle(4, 2, 3));
        assertEquals(5, AnimationResolver.nextCachedFrameChangeCycle(4, -1, 4));
        assertEquals(-1, AnimationResolver.nextCachedFrameChangeCycle(4, 1, 3));
        assertEquals(-1, AnimationResolver.nextCachedFrameChangeCycle(1, -1, 0));
    }

    @Test
    void cachedSequenceUsesClientFrameCountLoopBack() {
        assertEquals(0, AnimationResolver.cachedFrameIndex(4, 2, 0));
        assertEquals(1, AnimationResolver.cachedFrameIndex(4, 2, 1));
        assertEquals(3, AnimationResolver.cachedFrameIndex(4, 2, 3));
        assertEquals(2, AnimationResolver.cachedFrameIndex(4, 2, 4));
        assertEquals(3, AnimationResolver.cachedFrameIndex(4, 2, 5));
        assertEquals(2, AnimationResolver.cachedFrameIndex(4, 2, 6));
    }

    @Test
    void cachedSequenceWithInvalidLoopBackRestartsAtZero() {
        assertEquals(0, AnimationResolver.cachedFrameIndex(4, -1, 4));
        assertEquals(1, AnimationResolver.cachedFrameIndex(4, -1, 5));
    }
}
