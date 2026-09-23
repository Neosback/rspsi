package com.rspsi.osrs.rules.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnimationResolverTest {
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
