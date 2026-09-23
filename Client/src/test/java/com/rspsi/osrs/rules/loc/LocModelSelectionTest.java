package com.rspsi.osrs.rules.loc;

import com.rspsi.cache.definition.ObjectDefinitionView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocModelSelectionTest {
    @Test
    void untypedModelsBelongToShapeTenOnly() {
        ObjectDefinitionView definition = new ObjectDefinitionView(
                1, "crate", 1, 1, List.of(), new int[]{10, 11});

        assertEquals(List.of(10, 11), LocModelSelection.select(definition, 10));
        assertEquals(List.of(), LocModelSelection.select(definition, 22));
    }

    @Test
    void typedModelsSelectOnlyMatchingSourceType() {
        ObjectDefinitionView definition = new ObjectDefinitionView(
                1, "wall", 1, 1, List.of(),
                new int[]{10, 11, 12}, new int[]{0, 2, 0}, -1, false);

        assertEquals(List.of(10, 12), LocModelSelection.select(definition, 0));
        assertEquals(List.of(11), LocModelSelection.select(definition, 2));
        assertEquals(List.of(), LocModelSelection.select(definition, 10));
    }
}
