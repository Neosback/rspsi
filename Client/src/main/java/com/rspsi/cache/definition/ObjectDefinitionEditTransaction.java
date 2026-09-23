package com.rspsi.cache.definition;

import java.util.List;

/**
 * Immutable intent for one object-definition edit preview/commit.
 *
 * <p>The transaction contains no cache-library types and does not itself
 * mutate a cache. Backends apply it to their native builder/codec pipeline.</p>
 */
public record ObjectDefinitionEditTransaction(
        int objectId,
        List<Mutation> mutations
) {
    public ObjectDefinitionEditTransaction {
        if (objectId < 0) {
            throw new IllegalArgumentException("Object definition id cannot be negative");
        }
        mutations = List.copyOf(mutations == null ? List.of() : mutations);
        if (mutations.isEmpty()) {
            throw new IllegalArgumentException("Definition edit transaction needs at least one mutation");
        }
    }

    public sealed interface Mutation permits SetField, SetParam, RemoveParam {
    }

    public record SetField(String field, DefinitionEditValue value) implements Mutation {
        public SetField {
            if (field == null || field.isBlank()) {
                throw new IllegalArgumentException("Definition field name cannot be blank");
            }
            if (value == null) {
                throw new IllegalArgumentException("Definition field value cannot be null");
            }
        }
    }

    public record SetParam(int id, DefinitionEditValue value) implements Mutation {
        public SetParam {
            requireParamId(id);
            if (!(value instanceof DefinitionEditValue.StringValue)
                    && !(value instanceof DefinitionEditValue.IntValue)
                    && !(value instanceof DefinitionEditValue.LongValue)) {
                throw new IllegalArgumentException(
                        "Opcode 249 params support string, int and long values");
            }
        }
    }

    public record RemoveParam(int id) implements Mutation {
        public RemoveParam {
            requireParamId(id);
        }
    }

    private static void requireParamId(int id) {
        if (id < 0 || id > 0xFFFFFF) {
            throw new IllegalArgumentException(
                    "Param id outside unsigned-medium range: " + id);
        }
    }
}
