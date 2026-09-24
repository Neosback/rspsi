package com.rspsi.editor;

import com.rspsi.cache.definition.ObjectDefinitionEditTransaction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Undoable edit of an object definition's list fields (models, recolours,
 * transforms, sound ids) or one right-click action. Paired lists change in
 * one command, so undo never leaves a model/type or recolour from/to pair
 * with mismatched lengths.
 */
public final class ObjectDefinitionStructureEditCommand implements EditorCommand {
    private final ObjectDefinitionEditTransaction transaction;
    private final Map<String, List<Integer>> listsBefore;
    private final Map<String, List<Integer>> listsAfter;
    private final int actionIndex;
    private final String actionBefore;
    private final String actionAfter;
    private final String description;

    private ObjectDefinitionStructureEditCommand(ObjectDefinitionEditTransaction transaction,
                                                 Map<String, List<Integer>> listsBefore,
                                                 Map<String, List<Integer>> listsAfter,
                                                 int actionIndex, String actionBefore, String actionAfter,
                                                 String description) {
        this.transaction = Objects.requireNonNull(transaction, "transaction");
        this.listsBefore = listsBefore;
        this.listsAfter = listsAfter;
        this.actionIndex = actionIndex;
        this.actionBefore = actionBefore;
        this.actionAfter = actionAfter;
        this.description = description;
    }

    /** Replaces the given list fields; their current values become the undo state. */
    public static ObjectDefinitionStructureEditCommand lists(ObjectDefinitionEditTransaction transaction,
                                                             Map<String, List<Integer>> after) {
        Objects.requireNonNull(after, "after");
        if (after.isEmpty()) throw new IllegalArgumentException("No list fields to edit");
        Map<String, List<Integer>> before = new LinkedHashMap<>();
        Map<String, List<Integer>> copy = new LinkedHashMap<>();
        after.forEach((field, values) -> {
            before.put(field, transaction.intList(field));
            copy.put(field, List.copyOf(values));
        });
        if (before.equals(copy)) throw new IllegalArgumentException("Definition edit command must change a value");
        return new ObjectDefinitionStructureEditCommand(transaction, Map.copyOf(before), Map.copyOf(copy),
                -1, null, null, "Edit object " + transaction.id() + " " + String.join(", ", copy.keySet()));
    }

    /** Sets right-click option {@code index}; null or blank removes it. */
    public static ObjectDefinitionStructureEditCommand action(ObjectDefinitionEditTransaction transaction,
                                                              int index, String after) {
        String normalized = after == null || after.isBlank() ? null : after;
        String before = transaction.actions().get(index);
        if (Objects.equals(before, normalized)) {
            throw new IllegalArgumentException("Definition edit command must change a value");
        }
        return new ObjectDefinitionStructureEditCommand(transaction, null, null, index, before, normalized,
                "Edit object " + transaction.id() + " option " + (index + 1));
    }

    @Override
    public void apply(EditorSession session) {
        Objects.requireNonNull(session, "session");
        if (listsAfter != null) transaction.setIntLists(listsAfter);
        else transaction.setAction(actionIndex, actionAfter);
    }

    @Override
    public void undo(EditorSession session) {
        Objects.requireNonNull(session, "session");
        if (listsBefore != null) transaction.setIntLists(listsBefore);
        else transaction.setAction(actionIndex, actionBefore);
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public boolean savedBySessionSave() {
        return false;
    }

    @Override
    public boolean hasUnsavedExternalState() {
        return transaction.hasUnpublishedChanges();
    }
}
