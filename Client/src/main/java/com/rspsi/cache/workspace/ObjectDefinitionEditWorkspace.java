package com.rspsi.cache.workspace;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ObjectDefinitionEditTransaction;
import com.rspsi.cache.definition.ObjectDefinitionRawView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Session-scoped registry for object-definition edit transactions.
 *
 * <p>The registry belongs to the loaded cache session rather than any one UI
 * panel. This keeps edits alive across panel/workspace navigation and gives
 * persistence code one authoritative set of modified definitions.</p>
 */
public final class ObjectDefinitionEditWorkspace {
    private final DefinitionProvider definitions;
    private final Map<Integer, ObjectDefinitionEditTransaction> transactions =
            new LinkedHashMap<>();

    public ObjectDefinitionEditWorkspace(DefinitionProvider definitions) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
    }

    /**
     * Returns the stable transaction for one object definition, creating it
     * lazily when the selected cache backend supports editing that definition.
     */
    public synchronized Optional<ObjectDefinitionEditTransaction> transaction(int objectId) {
        ObjectDefinitionEditTransaction existing = transactions.get(objectId);
        if (existing != null) {
            return Optional.of(existing);
        }
        Optional<ObjectDefinitionEditTransaction> created = definitions.editObject(objectId);
        created.ifPresent(transaction -> transactions.put(objectId, transaction));
        return created;
    }

    /** All transactions created during this cache session. */
    public synchronized List<ObjectDefinitionEditTransaction> transactions() {
        return List.copyOf(transactions.values());
    }

    /** Definitions whose current preview differs from the read-only source. */
    public synchronized List<ObjectDefinitionEditTransaction> modifiedTransactions() {
        return transactions.values().stream()
                .filter(ObjectDefinitionEditTransaction::dirty)
                .toList();
    }

    /** Definitions with current state that has not yet been published. */
    public synchronized List<ObjectDefinitionEditTransaction> unpublishedTransactions() {
        return transactions.values().stream()
                .filter(ObjectDefinitionEditTransaction::hasUnpublishedChanges)
                .toList();
    }

    public synchronized int modifiedCount() {
        return (int) transactions.values().stream()
                .filter(ObjectDefinitionEditTransaction::dirty)
                .count();
    }

    public synchronized int unpublishedCount() {
        return (int) transactions.values().stream()
                .filter(ObjectDefinitionEditTransaction::hasUnpublishedChanges)
                .count();
    }

    /**
     * Marks a successfully-published snapshot without mutating the current
     * transaction preview. If the user edited again while an output build was
     * running, the newer preview remains unpublished.
     */
    public synchronized void markPublished(
            int objectId,
            ObjectDefinitionRawView publishedPreview) {
        ObjectDefinitionEditTransaction transaction = transactions.get(objectId);
        if (transaction == null) {
            throw new IllegalArgumentException(
                    "No object definition transaction exists for id " + objectId);
        }
        transaction.markPublished(publishedPreview);
    }

    /**
     * Clears only the registry references. Transactions referenced by command
     * history remain valid until the owning editor session is discarded.
     */
    public synchronized void clear() {
        transactions.clear();
    }
}
