package com.rspsi.cache.store;

import com.rspsi.cache.definition.DefinitionEditValue;
import com.rspsi.cache.definition.ObjectDefinitionEditPreview;
import com.rspsi.cache.definition.ObjectDefinitionEditTransaction;
import com.rspsi.cache.definition.ObjectDefinitionRawView;
import dev.openrune.definition.codec.ObjectCodec;
import dev.openrune.definition.type.ObjectType;
import dev.openrune.definition.type.builders.ObjectTypeBuilder;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenRuneObjectDefinitionEditTransactionTest {

    @Test
    void scalarAndParamEditsSurviveProductionCodecRoundTripWithoutMutatingSource() {
        ObjectTypeBuilder sourceBuilder = new ObjectTypeBuilder(321);
        sourceBuilder.setName("Old chest");
        sourceBuilder.setSizeX(2);
        sourceBuilder.setSizeY(3);
        sourceBuilder.setAnimationId(100);
        sourceBuilder.setHollow(false);
        sourceBuilder.setRotated(false);
        Map<Integer, Object> sourceParams = new LinkedHashMap<>();
        sourceParams.put(10, "keep");
        sourceParams.put(20, 99);
        sourceBuilder.setParams(sourceParams);
        ObjectType source = sourceBuilder.build();

        ObjectDefinitionEditTransaction transaction =
                new ObjectDefinitionEditTransaction(321, List.of(
                        new ObjectDefinitionEditTransaction.SetField(
                                "name", new DefinitionEditValue.StringValue("Edited chest")),
                        new ObjectDefinitionEditTransaction.SetField(
                                "sizeX", new DefinitionEditValue.IntValue(4)),
                        new ObjectDefinitionEditTransaction.SetField(
                                "animationId", new DefinitionEditValue.IntValue(456)),
                        new ObjectDefinitionEditTransaction.SetField(
                                "isRotated", new DefinitionEditValue.BooleanValue(true)),
                        new ObjectDefinitionEditTransaction.SetParam(
                                100, new DefinitionEditValue.StringValue("hello")),
                        new ObjectDefinitionEditTransaction.SetParam(
                                200, new DefinitionEditValue.IntValue(42)),
                        new ObjectDefinitionEditTransaction.SetParam(
                                300, new DefinitionEditValue.LongValue(9_000_000_000L)),
                        new ObjectDefinitionEditTransaction.RemoveParam(20)));

        ObjectDefinitionEditPreview preview =
                OpenRuneDefinitionProvider.previewObjectEdit(source, 240, transaction);

        assertEquals("Old chest", field(preview.before(), "name").value());
        assertEquals("Edited chest", field(preview.after(), "name").value());
        assertEquals("4", field(preview.after(), "sizeX").value());
        assertEquals("456", field(preview.after(), "animationId").value());
        assertEquals("true", field(preview.after(), "isRotated").value());

        assertEquals("keep", param(preview.after(), 10).value());
        assertEquals("hello", param(preview.after(), 100).value());
        assertEquals(ObjectDefinitionRawView.ValueType.STRING,
                param(preview.after(), 100).type());
        assertEquals("42", param(preview.after(), 200).value());
        assertEquals(ObjectDefinitionRawView.ValueType.INTEGER,
                param(preview.after(), 200).type());
        assertEquals("9000000000", param(preview.after(), 300).value());
        assertEquals(ObjectDefinitionRawView.ValueType.LONG,
                param(preview.after(), 300).type());
        assertFalse(preview.after().params().stream().anyMatch(value -> value.id() == 20));

        // The source definition is immutable and remains untouched.
        assertEquals("Old chest", source.getName());
        assertEquals(2, source.getSizeX());
        assertEquals(100, source.getAnimationId());
        assertFalse(source.isRotated());
        assertEquals(99, source.getParams().get(20));

        // Decode the preview payload again independently through OpenRune's
        // production codec. The provider preview already does this internally;
        // this assertion proves the returned bytes are the same canonical data.
        ObjectType decoded = new ObjectCodec(240).loadData(321, preview.encodedBytes());
        assertEquals("Edited chest", decoded.getName());
        assertEquals(4, decoded.getSizeX());
        assertEquals(456, decoded.getAnimationId());
        assertTrue(decoded.isRotated());
        assertEquals("hello", decoded.getParams().get(100));
        assertEquals(42, decoded.getParams().get(200));
        assertEquals(9_000_000_000L, decoded.getParams().get(300));
        assertFalse(decoded.getParams().containsKey(20));

        byte[] copy = preview.encodedBytes();
        copy[0] ^= 0x7F;
        assertNotEquals(copy[0], preview.encodedBytes()[0],
                "preview bytes must remain defensively owned");
    }

    @Test
    void rejectsUnsupportedFieldsAndInvalidScalarRangesBeforeEncoding() {
        ObjectType source = new ObjectTypeBuilder(7).build();

        var unsupported = new ObjectDefinitionEditTransaction(7, List.of(
                new ObjectDefinitionEditTransaction.SetField(
                        "objectModels", new DefinitionEditValue.IntValue(1))));
        var invalidSize = new ObjectDefinitionEditTransaction(7, List.of(
                new ObjectDefinitionEditTransaction.SetField(
                        "sizeX", new DefinitionEditValue.IntValue(256))));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> OpenRuneDefinitionProvider.previewObjectEdit(source, 240, unsupported));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> OpenRuneDefinitionProvider.previewObjectEdit(source, 240, invalidSize));
    }

    @Test
    void transactionValidationRejectsBooleanOpcode249AndOutOfRangeParamIds() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new ObjectDefinitionEditTransaction.SetParam(
                        10, new DefinitionEditValue.BooleanValue(true)));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new ObjectDefinitionEditTransaction.RemoveParam(0x1000000));
    }

    private static ObjectDefinitionRawView.Field field(
            ObjectDefinitionRawView raw, String name) {
        return raw.fields().stream()
                .filter(value -> value.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static ObjectDefinitionRawView.Param param(
            ObjectDefinitionRawView raw, int id) {
        return raw.params().stream()
                .filter(value -> value.id() == id)
                .findFirst()
                .orElseThrow();
    }
}
