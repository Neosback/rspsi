package com.rspsi.cache.definition;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionViewTest {
    @Test
    void sequenceArraysAreDefensivelyCopiedAndKeepAnimationSemantics() {
        int[] frames = {10, 11};
        int[] lengths = {3, 4};
        SequenceDefinitionView sequence = new SequenceDefinitionView(
                7, frames, lengths, 1, true, -1, 4151, 99, -1, 5, 2, -1);

        frames[0] = 999;
        lengths[0] = 999;
        assertEquals(10, sequence.frameIds()[0]);
        assertEquals(3, sequence.frameLengths()[0]);
    }

    @Test
    void mapElementPreservesNeutralDisplayAndActionMetadata() {
        MapElementDefinitionView element = new MapElementDefinitionView(
                4, 12, -1, "Bank", 0xFFFFFF, 0xFF00FF, 2,
                true, true, false, List.of("Open"));

        assertEquals("Bank", element.name());
        assertEquals(List.of("Open"), element.actions());
    }

    @Test
    void invalidDefinitionArraysAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SequenceDefinitionView(
                1, new int[]{1}, new int[0], -1, false, -1, -1,
                99, -1, -1, 2, -1));
    }

    @Test
    void objectViewPreservesExplicitSceneInteractivity() {
        ObjectDefinitionView interactive = new ObjectDefinitionView(
                9, "Door", 1, 1, List.of(), new int[]{100}, -1, true);
        ObjectDefinitionView passive = new ObjectDefinitionView(
                9, "Door", 1, 1, List.of(), new int[]{100}, -1, false);

        assertTrue(interactive.interactive());
        assertNotEquals(interactive, passive);
    }
}
