package com.rspsi.editor;

import com.rspsi.cache.definition.ObjectDefinitionEditTransaction;
import com.rspsi.cache.definition.ObjectDefinitionEditValue;

import java.util.Objects;

/**
 * Undoable mutation of one isolated object-definition edit transaction.
 *
 * <p>The command never owns or writes a cache. It only replays neutral
 * transaction operations, which lets definition editing participate in the
 * editor's existing {@link CommandHistory} without introducing a second
 * undo/redo stack.</p>
 */
public final class ObjectDefinitionEditCommand implements EditorCommand {
    private enum Target {
        FIELD,
        PARAM
    }

    private final ObjectDefinitionEditTransaction transaction;
    private final Target target;
    private final String fieldName;
    private final int paramId;
    private final ObjectDefinitionEditValue before;
    private final ObjectDefinitionEditValue after;

    private ObjectDefinitionEditCommand(ObjectDefinitionEditTransaction transaction,
                                        Target target,
                                        String fieldName,
                                        int paramId,
                                        ObjectDefinitionEditValue before,
                                        ObjectDefinitionEditValue after) {
        this.transaction = Objects.requireNonNull(transaction, "transaction");
        this.target = Objects.requireNonNull(target, "target");
        this.fieldName = fieldName;
        this.paramId = paramId;
        this.before = before;
        this.after = after;
        if (Objects.equals(before, after)) {
            throw new IllegalArgumentException("Definition edit command must change a value");
        }
    }

    public static ObjectDefinitionEditCommand field(
            ObjectDefinitionEditTransaction transaction,
            String fieldName,
            ObjectDefinitionEditValue before,
            ObjectDefinitionEditValue after) {
        String checkedName = Objects.requireNonNull(fieldName, "fieldName").trim();
        if (checkedName.isEmpty()) {
            throw new IllegalArgumentException("Object field name cannot be blank");
        }
        return new ObjectDefinitionEditCommand(
                transaction,
                Target.FIELD,
                checkedName,
                -1,
                Objects.requireNonNull(before, "before"),
                Objects.requireNonNull(after, "after"));
    }

    /**
     * Creates an opcode-249 parameter mutation. A null value means that the
     * parameter is absent, so add, replace, and remove share one command type.
     */
    public static ObjectDefinitionEditCommand param(
            ObjectDefinitionEditTransaction transaction,
            int paramId,
            ObjectDefinitionEditValue before,
            ObjectDefinitionEditValue after) {
        if (paramId < 0 || paramId > 0xFFFFFF) {
            throw new IllegalArgumentException(
                    "Param id outside unsigned-medium range: " + paramId);
        }
        if (before == null && after == null) {
            throw new IllegalArgumentException("Parameter command must add, replace, or remove a value");
        }
        return new ObjectDefinitionEditCommand(
                transaction, Target.PARAM, null, paramId, before, after);
    }

    @Override
    public void apply(EditorSession session) {
        Objects.requireNonNull(session, "session");
        mutate(after);
    }

    @Override
    public void undo(EditorSession session) {
        Objects.requireNonNull(session, "session");
        mutate(before);
    }

    @Override
    public String description() {
        return target == Target.FIELD
                ? "Edit object " + transaction.id() + " field " + fieldName
                : "Edit object " + transaction.id() + " param " + paramId;
    }

    @Override
    public boolean savedBySessionSave() {
        return false;
    }

    @Override
    public boolean hasUnsavedExternalState() {
        return transaction.hasUnpublishedChanges();
    }

    private void mutate(ObjectDefinitionEditValue value) {
        if (target == Target.FIELD) {
            if (value == null) {
                throw new IllegalStateException("Scalar object fields cannot be removed");
            }
            transaction.setField(fieldName, value);
            return;
        }

        if (value == null) {
            transaction.removeParam(paramId);
        } else {
            transaction.putParam(paramId, value);
        }
    }
}
