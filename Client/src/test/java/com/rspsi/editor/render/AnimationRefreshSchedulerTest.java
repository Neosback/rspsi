package com.rspsi.editor.render;

import com.rspsi.cache.definition.SequenceDefinitionView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnimationRefreshSchedulerTest {
    @Test
    void legacySequenceSchedulesOnlyAtPresentationBoundary() {
        SequenceDefinitionView sequence = new SequenceDefinitionView(
                77, new int[]{100, 101, 102}, new int[]{1, 2, 3}, 2,
                false, -1, -1, 99, 0, 0, 2, -1);

        assertEquals(2, AnimationRefreshScheduler.nextPresentationCycle(sequence, 0));
        assertEquals(2, AnimationRefreshScheduler.nextPresentationCycle(sequence, 1));
        assertEquals(5, AnimationRefreshScheduler.nextPresentationCycle(sequence, 2));
        assertEquals(9, AnimationRefreshScheduler.nextPresentationCycle(sequence, 6));
        assertEquals(12, AnimationRefreshScheduler.nextPresentationCycle(sequence, 9));
    }

    @Test
    void cachedSkeletalSequenceKeepsRealPerCycleCadence() {
        SequenceDefinitionView sequence = new SequenceDefinitionView(
                77, new int[0], new int[0], 2,
                false, -1, -1, 99, 0, 0, 2,
                900, 0, 4, 0);

        assertEquals(1, AnimationRefreshScheduler.nextPresentationCycle(sequence, 0));
        assertEquals(4, AnimationRefreshScheduler.nextPresentationCycle(sequence, 3));
        assertEquals(5, AnimationRefreshScheduler.nextPresentationCycle(sequence, 4));
    }

    @Test
    void terminalOrUnresolvedSequenceStopsPolling() {
        SequenceDefinitionView terminal = new SequenceDefinitionView(
                77, new int[]{100, 101}, new int[]{1, 2}, -1,
                false, -1, -1, 99, 0, 0, 2, -1);
        SequenceDefinitionView unresolved = new SequenceDefinitionView(
                78, new int[0], new int[0], -1,
                false, -1, -1, 99, 0, 0, 2, -1);

        assertEquals(-1, AnimationRefreshScheduler.nextPresentationCycle(terminal, 2));
        assertEquals(-1, AnimationRefreshScheduler.nextPresentationCycle(unresolved, 0));
    }
}
