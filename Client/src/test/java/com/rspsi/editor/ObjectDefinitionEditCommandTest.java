package com.rspsi.editor;

import com.rspsi.cache.definition.ObjectDefinitionEditTransaction;
import com.rspsi.cache.definition.ObjectDefinitionEditValue;
import com.rspsi.cache.definition.ObjectDefinitionRawView;
import com.rspsi.editor.model.WorldDocument;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectDefinitionEditCommandTest {

    @Test
    void fieldEditsUseExistingSessionHistory() {
        FakeTransaction transaction = new FakeTransaction();
        EditorSession session = new EditorSession(new WorldDocument(1, 1, 1));

        session.execute(ObjectDefinitionEditCommand.field(
                transaction,
                "name",
                ObjectDefinitionEditValue.stringValue("Tree"),
                ObjectDefinitionEditValue.stringValue("Copper rocks")));

        assertEquals("Copper rocks", transaction.field("name").value());
        assertTrue(transaction.dirty());
        assertTrue(session.isDirty());
        assertFalse(session.isSessionSaveDirty());
        assertTrue(session.hasUnsavedExternalState());
        assertEquals(1, session.history().size());

        session.markSaved();
        assertTrue(session.isDirty(),
                "a map/session save must not mark an in-memory definition edit as durable");
        assertFalse(session.isSessionSaveDirty());
        assertTrue(session.hasUnsavedExternalState());

        assertTrue(session.undo());
        assertEquals("Tree", transaction.field("name").value());
        assertFalse(transaction.dirty());

        assertTrue(session.redo());
        assertEquals("Copper rocks", transaction.field("name").value());
        assertTrue(transaction.dirty());
    }

    @Test
    void parameterAddReplaceAndRemoveAreUndoable() {
        FakeTransaction transaction = new FakeTransaction();
        EditorSession session = new EditorSession(new WorldDocument(1, 1, 1));

        ObjectDefinitionEditValue first = ObjectDefinitionEditValue.intValue(42);
        session.execute(ObjectDefinitionEditCommand.param(transaction, 451, null, first));
        assertEquals(first, transaction.param(451));

        ObjectDefinitionEditValue replacement =
                ObjectDefinitionEditValue.stringValue("mining_level");
        session.execute(ObjectDefinitionEditCommand.param(
                transaction, 451, first, replacement));
        assertEquals(replacement, transaction.param(451));

        session.execute(ObjectDefinitionEditCommand.param(
                transaction, 451, replacement, null));
        assertEquals(null, transaction.param(451));

        assertTrue(session.undo());
        assertEquals(replacement, transaction.param(451));
        assertTrue(session.undo());
        assertEquals(first, transaction.param(451));
        assertTrue(session.undo());
        assertEquals(null, transaction.param(451));
        assertFalse(transaction.dirty());
    }

    private static final class FakeTransaction implements ObjectDefinitionEditTransaction {
        private static final ObjectDefinitionRawView ORIGINAL = new ObjectDefinitionRawView(
                1276,
                List.of(new ObjectDefinitionRawView.Field(
                        "name", "2", ObjectDefinitionRawView.ValueType.STRING, "Tree")),
                List.of());

        private String name = "Tree";
        private final Map<Integer, ObjectDefinitionEditValue> params = new LinkedHashMap<>();

        @Override
        public int id() {
            return 1276;
        }

        @Override
        public ObjectDefinitionRawView original() {
            return ORIGINAL;
        }

        @Override
        public ObjectDefinitionRawView preview() {
            List<ObjectDefinitionRawView.Param> rawParams = new ArrayList<>();
            params.forEach((id, value) -> rawParams.add(new ObjectDefinitionRawView.Param(
                    id, value.type(), value.value())));
            return new ObjectDefinitionRawView(
                    id(),
                    List.of(new ObjectDefinitionRawView.Field(
                            "name", "2", ObjectDefinitionRawView.ValueType.STRING, name)),
                    rawParams);
        }

        @Override
        public Set<String> dirtyFields() {
            return name.equals("Tree") ? Set.of() : Set.of("name");
        }

        @Override
        public Set<Integer> dirtyParams() {
            return Set.copyOf(new LinkedHashSet<>(params.keySet()));
        }

        @Override
        public void setField(String fieldName, ObjectDefinitionEditValue value) {
            if (!fieldName.equals("name")
                    || value.type() != ObjectDefinitionRawView.ValueType.STRING) {
                throw new IllegalArgumentException("Unsupported fake field");
            }
            name = value.value();
        }

        @Override
        public void putParam(int paramId, ObjectDefinitionEditValue value) {
            params.put(paramId, value);
        }

        @Override
        public void removeParam(int paramId) {
            params.remove(paramId);
        }

        @Override
        public void reset() {
            name = "Tree";
            params.clear();
        }

        @Override
        public byte[] encodeValidated() {
            return name.getBytes(StandardCharsets.UTF_8);
        }

        private ObjectDefinitionRawView.Field field(String fieldName) {
            return preview().fields().stream()
                    .filter(field -> field.name().equals(fieldName))
                    .findFirst()
                    .orElseThrow();
        }

        private ObjectDefinitionEditValue param(int paramId) {
            return params.get(paramId);
        }
    }
}
