package com.rspsi.cache.store;

import dev.openrune.definition.constants.ConstantProvider;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenRuneSymbolicNameProviderTest {
    @Test
    void translatesLoadedReverseMappingsWithoutExposingBackendKeysToCallers() {
        ConstantProvider constants = ConstantProvider.INSTANCE;
        Map<String, Map<String, Integer>> previous = new HashMap<>(constants.getMappings());
        try {
            constants.setMappings(Map.of(
                    "objects", Map.of("objects.castle_wall", 12),
                    "overlays", Map.of("overlays.grass", 4)));

            OpenRuneSymbolicNameProvider names = new OpenRuneSymbolicNameProvider();
            assertEquals(Optional.of("objects.castle_wall"), names.name("object", 12));
            assertEquals(Optional.of("overlays.grass"), names.name("overlay", 4));
            assertTrue(names.name("npc", 12).isEmpty());
        } finally {
            constants.setMappings(previous);
        }
    }
}
