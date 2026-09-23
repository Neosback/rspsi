package com.rspsi.cache.workspace;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ObjectDefinitionEditTransaction;
import com.rspsi.cache.definition.ObjectDefinitionEditValue;
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
                applyRestoredPublication(transaction, published);
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
        if (snapshots.isEmpty()) {
            throw new IllegalArgumentException(
                    "Restored publication requires at least one snapshot");
        }
        if (publicationTarget != null) {
            throw new IllegalStateException(
                    "Definition publication state is already bound to "
                            + publicationTarget);
        }

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

        /*
         * Preflight hydration against fresh transactions before mutating any live
         * workspace state. Studio deliberately treats failed provenance restore
         * as ignorable, so a failure here must leave the workspace completely
         * unbound and existing previews untouched.
         */
        for (Map.Entry<Integer, ObjectDefinitionEditTransaction> entry
                : transactions.entrySet()) {
            ObjectDefinitionRawView published = checked.get(entry.getKey());
            if (published == null || entry.getValue().dirty()) {
                continue;
            }
            ObjectDefinitionEditTransaction probe = definitions.editObject(entry.getKey())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Persisted publication cannot reopen object "
                                    + entry.getKey()));
            applyRestoredPublication(probe, published);
        }

        publicationTarget = target;
        publishedSnapshots.clear();
        publishedSnapshots.putAll(checked);
        for (Map.Entry<Integer, ObjectDefinitionEditTransaction> entry
                : transactions.entrySet()) {
            ObjectDefinitionRawView published = publishedSnapshots.get(entry.getKey());
            if (published != null) {
                applyRestoredPublication(entry.getValue(), published);
            }
        }
    }

    private static void applyRestoredPublication(
            ObjectDefinitionEditTransaction transaction,
            ObjectDefinitionRawView published) {
        if (transaction.dirty()) {
            transaction.markPublished(published);
            return;
        }

        Map<String, ObjectDefinitionRawView.Field> originalFields =
                fieldsByName(transaction.original());
        for (ObjectDefinitionRawView.Field target : published.fields()) {
            ObjectDefinitionRawView.Field original =
                    originalFields.get(target.name());
            if (Objects.equals(original, target)) {
                continue;
            }
            if (original == null) {
                throw new IllegalArgumentException(
                        "Persisted publication contains unknown object field "
                                + target.name() + " for object " + transaction.id());
            }
            transaction.setField(target.name(), editValue(target));
        }

        Map<Integer, ObjectDefinitionRawView.Param> originalParams =
                paramsById(transaction.original());
        Map<Integer, ObjectDefinitionRawView.Param> publishedParams =
                paramsById(published);

        for (int paramId : originalParams.keySet()) {
            if (!publishedParams.containsKey(paramId)) {
                transaction.removeParam(paramId);
            }
        }
        for (Map.Entry<Integer, ObjectDefinitionRawView.Param> entry
                : publishedParams.entrySet()) {
            if (!Objects.equals(originalParams.get(entry.getKey()), entry.getValue())) {
                transaction.putParam(entry.getKey(), editValue(entry.getValue()));
            }
        }

        if (!transaction.preview().equals(published)) {
            throw new IllegalArgumentException(
                    "Persisted publication snapshot cannot be restored for object "
                            + transaction.id());
        }
        transaction.markPublished(published);
    }

    private static Map<String, ObjectDefinitionRawView.Field> fieldsByName(
            ObjectDefinitionRawView view) {
        LinkedHashMap<String, ObjectDefinitionRawView.Field> result =
                new LinkedHashMap<>();
        for (ObjectDefinitionRawView.Field field : view.fields()) {
            result.put(field.name(), field);
        }
        return result;
    }

    private static Map<Integer, ObjectDefinitionRawView.Param> paramsById(
            ObjectDefinitionRawView view) {
        LinkedHashMap<Integer, ObjectDefinitionRawView.Param> result =
                new LinkedHashMap<>();
        for (ObjectDefinitionRawView.Param param : view.params()) {
            result.put(param.id(), param);
        }
        return result;
    }

    private static ObjectDefinitionEditValue editValue(
            ObjectDefinitionRawView.Field field) {
        return editValue(field.type(), field.value());
    }

    private static ObjectDefinitionEditValue editValue(
            ObjectDefinitionRawView.Param param) {
        return editValue(param.type(), param.value());
    }

    private static ObjectDefinitionEditValue editValue(
            ObjectDefinitionRawView.ValueType type,
            String value) {
        return switch (type) {
            case STRING -> ObjectDefinitionEditValue.stringValue(value);
            case INTEGER -> ObjectDefinitionEditValue.intValue(
                    Integer.parseInt(value.trim()));
            case LONG -> ObjectDefinitionEditValue.longValue(
                    Long.parseLong(value.trim()));
            case BOOLEAN -> ObjectDefinitionEditValue.booleanValue(
                    Boolean.parseBoolean(value.trim()));
            default -> throw new IllegalArgumentException(
                    "Persisted publication contains unsupported editable value type "
                            + type);
        };
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
        publishedSnapshots.put(objectId, publishedPreview);
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
