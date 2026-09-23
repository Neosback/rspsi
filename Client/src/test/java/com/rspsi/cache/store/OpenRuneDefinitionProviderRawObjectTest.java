package com.rspsi.cache.store;

import com.rspsi.cache.definition.ObjectDefinitionRawView;
import dev.openrune.definition.type.builders.ObjectTypeBuilder;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenRuneDefinitionProviderRawObjectTest {
    @Test
    void exposesDecodedFieldsAndTypedOpcode249ParamsWithoutBackendTypes() {
        ObjectTypeBuilder builder = new ObjectTypeBuilder(321);
        builder.setName("Test chest");
        builder.setSizeX(2);
        builder.setSizeY(3);
        builder.setAnimationId(456);

        Map<Integer, Object> params = new HashMap<>();
        params.put(100, "hello");
        params.put(200, 42);
        params.put(300, 99L);
        builder.setParams(params);

        ObjectDefinitionRawView raw =
                OpenRuneDefinitionProvider.toRawView(builder.build());

        assertEquals(321, raw.id());
        assertEquals("Test chest", field(raw, "name").value());
        assertEquals("2", field(raw, "name").opcode());
        assertEquals("2", field(raw, "sizeX").value());
        assertEquals("14", field(raw, "sizeX").opcode());
        assertEquals("456", field(raw, "animationId").value());
        assertEquals("24", field(raw, "animationId").opcode());
        assertEquals("74", field(raw, "isHollow").opcode());
        assertEquals("62", field(raw, "isRotated").opcode());
        assertFalse(raw.fields().stream().anyMatch(value -> value.name().equals("params")),
                "opcode 249 parameters have a dedicated typed surface");

        assertEquals(3, raw.params().size());
        assertEquals(100, raw.params().get(0).id());
        assertEquals(ObjectDefinitionRawView.ValueType.STRING, raw.params().get(0).type());
        assertEquals("hello", raw.params().get(0).value());
        assertEquals(200, raw.params().get(1).id());
        assertEquals(ObjectDefinitionRawView.ValueType.INTEGER, raw.params().get(1).type());
        assertEquals("42", raw.params().get(1).value());
        assertEquals(300, raw.params().get(2).id());
        assertEquals(ObjectDefinitionRawView.ValueType.LONG, raw.params().get(2).type());
        assertEquals("99", raw.params().get(2).value());

        assertTrue(raw.fields().size() > 25,
                "raw inspector should expose the decoded object surface rather than a hand-picked subset");
    }

    private static ObjectDefinitionRawView.Field field(
            ObjectDefinitionRawView raw, String name) {
        return raw.fields().stream()
                .filter(value -> value.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing raw object field: " + name));
    }
}
