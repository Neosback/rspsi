package com.rspsi.api.cache;

import com.rspsi.api.ObjectComposition;
import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ObjectDefinitionRawView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.ObjectVarState;
import com.rspsi.cache.definition.VarbitDefinitionView;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class CacheObjectCompositionTest {
    private static final DefinitionProvider DEFINITIONS = new DefinitionProvider() {
        @Override
        public Optional<ObjectDefinitionView> object(int id) {
            return switch (id) {
                case 1 -> Optional.of(new ObjectDefinitionView(1, "Bank booth", 1, 1, List.of("Bank", "Collect"),
                        new int[]{5}, new int[]{10}, -1, true, -1, -1, new int[0], -1));
                // Multiloc on varp 7: value 0 shows object 3, anything else the default (object 4).
                case 2 -> Optional.of(new ObjectDefinitionView(2, "", 1, 1, List.of(), new int[0], new int[0],
                        -1, false, -1, 7, new int[]{3, 4}, 4));
                case 3 -> Optional.of(new ObjectDefinitionView(3, "Closed", 1, 1, List.of(), new int[]{5}));
                case 4 -> Optional.of(new ObjectDefinitionView(4, "Open", 1, 1, List.of(), new int[]{6}));
                default -> Optional.empty();
            };
        }

        @Override
        public Optional<List<String>> objectActions(int id) {
            return id == 1 ? Optional.of(Arrays.asList(null, "Bank", "Collect", null, null)) : Optional.empty();
        }

        @Override
        public Optional<ObjectDefinitionRawView> objectRaw(int id) {
            return Optional.of(new ObjectDefinitionRawView(id, List.of(), List.of(
                    new ObjectDefinitionRawView.Param(10, ObjectDefinitionRawView.ValueType.INTEGER, "42"),
                    new ObjectDefinitionRawView.Param(11, ObjectDefinitionRawView.ValueType.STRING, "hello"))));
        }

        @Override public Optional<FloorDefinitionView> underlay(int id) { return Optional.empty(); }
        @Override public Optional<FloorDefinitionView> overlay(int id) { return Optional.empty(); }
        @Override public Optional<VarbitDefinitionView> varbit(int id) { return Optional.empty(); }
    };

    @Test
    void actionsKeepTheirPositions() {
        ObjectComposition booth = CacheObjectComposition.of(1, DEFINITIONS, ObjectVarState.freshAccount()).orElseThrow();

        assertArrayEquals(new String[]{null, "Bank", "Collect", null, null}, booth.getActions());
        assertEquals("Bank booth", booth.getName());
        assertNull(booth.getImpostorIds());
        assertSame(booth, booth.getImpostor(), "a plain object is its own impostor");
    }

    @Test
    void impostorFollowsTheVarState() {
        ObjectComposition fresh = CacheObjectComposition.of(2, DEFINITIONS, ObjectVarState.freshAccount()).orElseThrow();
        assertEquals("null", fresh.getName());
        assertEquals(7, fresh.getVarPlayerId());
        assertEquals(3, fresh.getImpostor().getId());

        ObjectVarState varpSet = new ObjectVarState() {
            @Override public int varbitValue(int varbitId) { return 0; }
            @Override public int varpValue(int varpId) { return varpId == 7 ? 5 : 0; }
        };
        ObjectComposition changed = CacheObjectComposition.of(2, DEFINITIONS, varpSet).orElseThrow();
        assertEquals(4, changed.getImpostor().getId(), "out-of-range value shows the default (last) state");
    }

    @Test
    void paramsReadIntAndStringValues() {
        ObjectComposition booth = CacheObjectComposition.of(1, DEFINITIONS, ObjectVarState.freshAccount()).orElseThrow();

        assertEquals(42, booth.getIntValue(10, -1));
        assertEquals(-1, booth.getIntValue(99, -1));
        assertEquals("hello", booth.getStringValue(11));
        assertNull(booth.getStringValue(10));
    }
}
