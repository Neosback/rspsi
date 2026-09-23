package com.rspsi.cache.store;

import com.rspsi.cache.definition.ObjectDefinitionEditValue;
import com.rspsi.cache.definition.ObjectDefinitionRawView;
import dev.openrune.definition.codec.ObjectCodec;
import dev.openrune.definition.type.ObjectType;
import dev.openrune.definition.type.builders.ObjectTypeBuilder;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenRuneObjectDefinitionEditTransactionTest {
    @Test
    void scalarAndParamEditsRoundTripThroughTheRealObjectCodec() {
        ObjectTypeBuilder sourceBuilder = new ObjectTypeBuilder(321);
        sourceBuilder.setName("Old chest");
        sourceBuilder.setSizeX(1);
        sourceBuilder.setSizeY(2);
        sourceBuilder.setHollow(false);
        Map<Integer, Object> sourceParams = new HashMap<>();
        sourceParams.put(100, "old");
        sourceParams.put(200, 7);
        sourceBuilder.setParams(sourceParams);
        ObjectType source = sourceBuilder.build();

        OpenRuneObjectDefinitionEditTransaction transaction =
                new OpenRuneObjectDefinitionEditTransaction(source, 240);

        transaction.setField("name", ObjectDefinitionEditValue.stringValue("New chest"));
        transaction.setField("sizeX", ObjectDefinitionEditValue.intValue(3));
        transaction.setField("isHollow", ObjectDefinitionEditValue.booleanValue(true));
        transaction.putParam(100, ObjectDefinitionEditValue.longValue(99L));
        transaction.putParam(300, ObjectDefinitionEditValue.stringValue("added"));
        transaction.removeParam(200);

        assertTrue(transaction.dirty());
        assertEquals(Set.of("name", "sizeX", "isHollow"), transaction.dirtyFields());
        assertEquals(Set.of(100, 200, 300), transaction.dirtyParams());

        ObjectDefinitionRawView preview = transaction.preview();
        assertEquals("New chest", field(preview, "name").value());
        assertEquals("3", field(preview, "sizeX").value());
        assertEquals("true", field(preview, "isHollow").value());

        byte[] encoded = transaction.encodeValidated();
        assertTrue(encoded.length > 0);

        ObjectType decoded = new ObjectCodec(240).loadData(321, encoded);
        assertEquals("New chest", decoded.getName());
        assertEquals(3, decoded.getSizeX());
        assertTrue(decoded.isHollow());
        assertEquals(99L, decoded.getParams().get(100));
        assertFalse(decoded.getParams().containsKey(200));
        assertEquals("added", decoded.getParams().get(300));

        // The transaction is isolated from the source definition/provider.
        assertEquals("Old chest", source.getName());
        assertEquals(1, source.getSizeX());
        assertFalse(source.isHollow());
        assertEquals("old", source.getParams().get(100));
        assertEquals(7, source.getParams().get(200));
    }

    @Test
    void resetRestoresSourceAndClearsDirtyState() {
        ObjectTypeBuilder builder = new ObjectTypeBuilder(42);
        builder.setName("Rocks");
        builder.setSizeX(1);
        builder.setParams(new HashMap<>(Map.of(10, "mine")));

        OpenRuneObjectDefinitionEditTransaction transaction =
                new OpenRuneObjectDefinitionEditTransaction(builder.build(), 240);
        transaction.setField("name", ObjectDefinitionEditValue.stringValue("Edited rocks"));
        transaction.putParam(10, ObjectDefinitionEditValue.intValue(5));
        transaction.putParam(11, ObjectDefinitionEditValue.longValue(6L));

        transaction.reset();

        assertFalse(transaction.dirty());
        assertEquals(transaction.original(), transaction.preview());
        assertEquals("Rocks", field(transaction.preview(), "name").value());
        assertEquals(1, transaction.preview().params().size());
        assertEquals("mine", transaction.preview().params().get(0).value());
    }

    @Test
    void rejectsComplexFieldsAndUnsupportedBooleanParams() {
        ObjectTypeBuilder builder = new ObjectTypeBuilder(77);
        builder.setObjectModels(java.util.List.of(1, 2));
        OpenRuneObjectDefinitionEditTransaction transaction =
                new OpenRuneObjectDefinitionEditTransaction(builder.build(), 240);

        assertThrows(IllegalArgumentException.class,
                () -> transaction.setField(
                        "objectModels", ObjectDefinitionEditValue.intValue(5)));
        assertThrows(IllegalArgumentException.class,
                () -> transaction.putParam(
                        1, ObjectDefinitionEditValue.booleanValue(true)));
    }

    private static ObjectDefinitionRawView.Field field(
            ObjectDefinitionRawView raw, String name) {
        return raw.fields().stream()
                .filter(value -> value.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing raw field: " + name));
    }
}
