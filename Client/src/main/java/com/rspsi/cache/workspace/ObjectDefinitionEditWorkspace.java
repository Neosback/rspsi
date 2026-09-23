package com.rspsi.cache.workspace;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ObjectDefinitionEditTransaction;
import com.rspsi.cache.definition.ObjectDefinitionRawView;

import java.nio.file.Path;
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
    private final Map<Integer, ObjectDefinitionRawView> publishedSnapshots =
            new LinkedHashMap<>();
    private Path publicationTarget;

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
        created.ifPresent(transaction -> {
            ObjectDefinitionRawView published = publishedSnapshots.get(objectId);
            if (published != null) {
                transaction.markPublished(published);
            }
            transactions.put(objectId, transaction);
        });
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
     * Output directory bound to this cache session after its first successful
     * definition publication. Publication snapshots are meaningful only
     * relative to this target during the session.
     */
    public synchronized Optional<Path> publicationTarget() {
        return Optional.ofNullable(publicationTarget);
    }

    /** Exact verified publication snapshots known for this loaded cache session. */
    public synchronized Map<Integer, ObjectDefinitionRawView> publishedSnapshots() {
        return Map.copyOf(publishedSnapshots);
    }

    /**
     * Restores a previously persisted publication target and its already
     * revalidated snapshots. Transactions remain lazy; when one is opened, its
     * restored publication baseline is applied before editing begins.
     */
    public synchronized void restorePublication(
            Path outputCache,
            Map<Integer, ObjectDefinitionRawView> snapshots) {
        Path target = Objects.requireNonNull(outputCache, "outputCache")
                .toAbsolutePath().normalize();
        Objects.requireNonNull(snapshots, "snapshots");

        LinkedHashMap<Integer, ObjectDefinitionRawView> checked =
                new LinkedHashMap<>();
        snapshots.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    int objectId = entry.getKey();
                    ObjectDefinitionRawView snapshot =
                            Objects.requireNonNull(entry.getValue(), "published snapshot");
                    if (objectId < 0 || snapshot.id() != objectId) {
                        throw new IllegalArgumentException(
                                "Published object snapshot does not match id " + objectId);
                    }
                    checked.put(objectId, snapshot);
                });

        publicationTarget = target;
        publishedSnapshots.clear();
        publishedSnapshots.putAll(checked);
        for (Map.Entry<Integer, ObjectDefinitionEditTransaction> entry
                : transactions.entrySet()) {
            ObjectDefinitionRawView published = publishedSnapshots.get(entry.getKey());
            if (published != null) {
                entry.getValue().markPublished(published);
            }
        }
    }

    /**
     * Marks a successfully-published snapshot and binds publication state to
     * the explicit output cache. Later publishes in this loaded-cache session
     * must target the same directory.
     */
    public synchronized void markPublished(
            Path outputCache,
            int objectId,
            ObjectDefinitionRawView publishedPreview) {
        Path target = Objects.requireNonNull(outputCache, "outputCache")
                .toAbsolutePath().normalize();
        if (publicationTarget != null && !publicationTarget.equals(target)) {
            throw new IllegalArgumentException(
                    "Definition publication is already bound to output cache "
                            + publicationTarget);
        }

        ObjectDefinitionEditTransaction transaction = transactions.get(objectId);
        if (transaction == null) {
            throw new IllegalArgumentException(
                    "No object definition transaction exists for id " + objectId);
        }
        transaction.markPublished(publishedPreview);
        publishedSnapshots.put(objectId, publishedPreview);
        publicationTarget = target;
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
        publishedSnapshots.clear();
        publicationTarget = null;
    }
}
