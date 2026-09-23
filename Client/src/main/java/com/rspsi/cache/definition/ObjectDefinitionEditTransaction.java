package com.rspsi.cache.definition;

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
