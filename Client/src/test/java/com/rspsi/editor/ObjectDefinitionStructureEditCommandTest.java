package com.rspsi.editor;

import com.rspsi.cache.definition.ObjectDefinitionEditTransaction;
import com.rspsi.cache.definition.ObjectDefinitionEditValue;
import com.rspsi.cache.definition.ObjectDefinitionRawView;
import com.rspsi.editor.model.WorldDocument;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectDefinitionStructureEditCommandTest {
    @Test
    void pairedListsChangeAndUndoTogether() {
        ListTransaction transaction = new ListTransaction();
        transaction.lists.put("originalColours", List.of(100));
        transaction.lists.put("modifiedColours", List.of(200));
        EditorSession session = new EditorSession(new WorldDocument(1, 1, 1));

        Map<String, List<Integer>> change = new LinkedHashMap<>();
        change.put("originalColours", List.of(100, 101));
        change.put("modifiedColours", List.of(200, 201));
        session.execute(ObjectDefinitionStructureEditCommand.lists(transaction, change));

        assertEquals(List.of(100, 101), transaction.intList("originalColours"));
        assertEquals(List.of(200, 201), transaction.intList("modifiedColours"));
        assertEquals(1, transaction.listWrites, "both lists are written in one transaction call");

        assertTrue(session.undo());
        assertEquals(List.of(100), transaction.intList("originalColours"));
        assertEquals(List.of(200), transaction.intList("modifiedColours"));
        assertTrue(session.redo());
        assertEquals(List.of(100, 101), transaction.intList("originalColours"));
    }

    @Test
    void actionEditsUndoToThePreviousOption() {
        ListTransaction transaction = new ListTransaction();
        transaction.actions[0] = "Open";
        EditorSession session = new EditorSession(new WorldDocument(1, 1, 1));

        session.execute(ObjectDefinitionStructureEditCommand.action(transaction, 0, "  "));
        assertNull(transaction.actions().get(0), "blank removes the option");
        session.execute(ObjectDefinitionStructureEditCommand.action(transaction, 2, "Search"));
        assertEquals("Search", transaction.actions().get(2));

        assertTrue(session.undo());
        assertNull(transaction.actions().get(2));
        assertTrue(session.undo());
        assertEquals("Open", transaction.actions().get(0));
    }

    @Test
    void noOpEditsAreRejected() {
        ListTransaction transaction = new ListTransaction();
        transaction.lists.put("transforms", List.of(1, 2));

        assertThrows(IllegalArgumentException.class, () -> ObjectDefinitionStructureEditCommand.lists(
                transaction, Map.of("transforms", List.of(1, 2))));
        assertThrows(IllegalArgumentException.class, () -> ObjectDefinitionStructureEditCommand.action(
                transaction, 1, null));
    }

    private static final class ListTransaction implements ObjectDefinitionEditTransaction {
        final Map<String, List<Integer>> lists = new HashMap<>();
        final String[] actions = new String[5];
        int listWrites;

        @Override public int id() { return 9; }
        @Override public ObjectDefinitionRawView original() { return new ObjectDefinitionRawView(9, List.of(), List.of()); }
        @Override public ObjectDefinitionRawView preview() { return original(); }
        @Override public Set<String> dirtyFields() { return Set.of(); }
        @Override public Set<Integer> dirtyParams() { return Set.of(); }
        @Override public void setField(String fieldName, ObjectDefinitionEditValue value) { }
        @Override public void putParam(int paramId, ObjectDefinitionEditValue value) { }
        @Override public void removeParam(int paramId) { }
        @Override public void reset() { }
        @Override public byte[] encodeValidated() { return new byte[0]; }

        @Override public List<Integer> intList(String fieldName) {
            return lists.getOrDefault(fieldName, List.of());
        }

        @Override public void setIntLists(Map<String, List<Integer>> values) {
            listWrites++;
            values.forEach((field, list) -> lists.put(field, List.copyOf(list)));
        }

        @Override public List<String> actions() {
            return new ArrayList<>(Arrays.asList(actions));
        }

        @Override public void setAction(int index, String text) {
            actions[index] = text == null || text.isBlank() ? null : text;
        }
    }
}
