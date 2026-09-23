package com.rspsi.cache.definition;

import java.util.Optional;
import java.util.Set;

/**
 * Isolated, in-memory edit transaction for one object definition.
 *
 * <p>No method in this contract persists data. {@link #encodeValidated()}
 * returns a validated raw object-definition payload that a later explicit
 * output-cache transaction may choose to write.</p>
 */
public interface ObjectDefinitionEditTransaction {
    int id();

    ObjectDefinitionRawView original();

    ObjectDefinitionRawView preview();

    Set<String> dirtyFields();

    Set<Integer> dirtyParams();

    default boolean dirty() {
        return !dirtyFields().isEmpty() || !dirtyParams().isEmpty();
    }

    /**
     * Returns whether the current preview differs from the publication baseline.
     * Before the first successful publish, the immutable source definition is
     * the baseline. After publication, the exact published preview is the
     * baseline, so undoing back to the source can still require an output-cache
     * update. This is intentionally distinct from {@link #dirty()}.
     */
    default boolean hasUnpublishedChanges() {
        return dirty();
    }

    /**
     * Returns the last preview successfully published for this transaction when
     * the backend tracks publication state. Persistence code uses this as the
     * expected base when transactionally updating an existing output cache.
     */
    default Optional<ObjectDefinitionRawView> publishedPreview() {
        return Optional.empty();
    }

    /**
     * Records the exact decoded preview that was successfully published as the
     * new output baseline. Backends that do not track publication state may
     * ignore this signal and continue reporting {@link #dirty()} as unpublished.
     */
    default void markPublished(ObjectDefinitionRawView publishedPreview) {
        java.util.Objects.requireNonNull(publishedPreview, "publishedPreview");
    }

    /**
     * Edits one scalar field. Collection/map fields are intentionally excluded
     * until dedicated typed operations exist for them.
     */
    void setField(String fieldName, ObjectDefinitionEditValue value);

    void putParam(int paramId, ObjectDefinitionEditValue value);

    void removeParam(int paramId);

    /** Restores the transaction to the decoded source definition. */
    void reset();

    /**
     * Encodes the current preview with the backend codec, decodes those bytes,
     * re-encodes them, and returns the canonical bytes only when the round trip
     * is stable.
     */
    byte[] encodeValidated();
}
