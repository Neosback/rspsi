package com.rspsi.cache.definition;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InvertedDefinitionIndexTest {
    @Test
    void indexesObjectNamesActionsModelsTransformsAndFloors() {
        DefinitionProvider definitions = new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) {
                if (id == 10) return Optional.of(new ObjectDefinitionView(
                        10, "Castle ladder", 1, 1, List.of("Climb-up", "Examine"),
                        new int[]{200, 201}, new int[0], -1, true,
                        5, -1, new int[]{11, 12}, 13));
                if (id == 11) return Optional.of(new ObjectDefinitionView(
                        11, "Open door", 1, 1, List.of("Open"), new int[]{202}));
                return Optional.empty();
            }
            @Override public Optional<FloorDefinitionView> underlay(int id) {
                return id == 2 ? Optional.of(new FloorDefinitionView(
                        2, 7, 0x112233, 0, 0, 0, 0, 1)) : Optional.empty();
            }
            @Override public Optional<FloorDefinitionView> overlay(int id) {
                return id == 3 ? Optional.of(new FloorDefinitionView(
                        3, 8, 0x445566, 0, 0, 0, 0, 1)) : Optional.empty();
            }
            @Override public List<Integer> objectIds() { return List.of(10, 11); }
            @Override public List<Integer> underlayIds() { return List.of(2); }
            @Override public List<Integer> overlayIds() { return List.of(3); }
        };

        InvertedDefinitionIndex index = new InvertedDefinitionIndex(definitions);

        assertEquals(java.util.Set.of(10), index.objectsNamed("ladder"));
        assertEquals(java.util.Set.of(10), index.objectsWithAction("climb-up"));
        assertEquals(java.util.Set.of(10), index.objectsUsingModel(201));
        assertEquals(java.util.Set.of(10, 11), index.interactiveObjects());
        assertEquals(List.of(11, 12, 13), index.transformLinks(10));
        assertEquals(java.util.Set.of(10), index.transformParents(11));
        assertEquals(java.util.Set.of(2), index.underlaysByRgb(0x112233));
        assertEquals(java.util.Set.of(3), index.overlaysUsingTexture(8));
    }
}
