package com.rspsi.cache.definition;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectDefinitionViewTest {
    @Test
    void equalityAndHashIncludeModelTypePairing() {
        ObjectDefinitionView typeZero = new ObjectDefinitionView(
                1, "Wall", 1, 1, List.of(),
                new int[]{10}, new int[]{0}, -1, false);
        ObjectDefinitionView typeTwo = new ObjectDefinitionView(
                1, "Wall", 1, 1, List.of(),
                new int[]{10}, new int[]{2}, -1, false);
        ObjectDefinitionView equal = new ObjectDefinitionView(
                1, "Wall", 1, 1, List.of(),
                new int[]{10}, new int[]{0}, -1, false);

        assertEquals(typeZero, equal);
        assertEquals(typeZero.hashCode(), equal.hashCode());
        assertNotEquals(typeZero, typeTwo);

        HashSet<ObjectDefinitionView> set = new HashSet<>();
        set.add(typeZero);
        set.add(typeTwo);
        assertEquals(2, set.size());
        assertTrue(set.contains(equal));
    }

    @Test
    void clientNullNameIsNotAUserFacingName() {
        ObjectDefinitionView definition = new ObjectDefinitionView(
                42, "null", 1, 1, List.of(), new int[0]);

        assertTrue(!definition.hasDisplayName());
        assertEquals("Object #42", definition.displayName());
    }
}
