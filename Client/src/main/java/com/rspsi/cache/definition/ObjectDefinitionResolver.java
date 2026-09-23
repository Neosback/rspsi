package com.rspsi.cache.definition;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves a placed OSRS object definition to the definition that supplies its
 * visible appearance for an explicit {@link ObjectVarState}.
 *
 * <p>The game client always transforms an object whose definition has a
 * transform table before asking that definition for its model
 * ({@code runescape-client/ObjectComposition.transform()}): the var value
 * selects {@code transforms[value]} when it is in range, otherwise the last
 * entry (the default). The editor uses a fresh account (every var 0) unless a
 * caller supplies another state. A state that selects {@code -1} is an
 * explicit "hidden in this state" result, not permission to render the placed
 * shell.</p>
 */
public final class ObjectDefinitionResolver {
    private final DefinitionProvider definitions;
    private final ObjectVarState varState;

    public ObjectDefinitionResolver(DefinitionProvider definitions) {
        this(definitions, ObjectVarState.freshAccount());
    }

    public ObjectDefinitionResolver(DefinitionProvider definitions, ObjectVarState varState) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.varState = Objects.requireNonNull(varState, "varState");
    }

    public Resolution resolveEditorDisplay(int placedId) {
        if (placedId < 0) {
            throw new IllegalArgumentException("Placed object id cannot be negative");
        }

        Optional<ObjectDefinitionView> placed = definitions.object(placedId);
        if (placed.isEmpty()) {
            return new Resolution(placedId, Status.MISSING_PLACED_DEFINITION,
                    Optional.empty(), Optional.empty(), List.of(placedId));
        }

        ObjectDefinitionView placedDefinition = placed.orElseThrow();
        if (!placedDefinition.hasTransforms()) {
            return new Resolution(placedId, Status.RESOLVED,
                    Optional.of(placedDefinition), Optional.of(placedDefinition),
                    List.of(placedId));
        }

        // RuneLite DynamicObject.getModel() performs exactly one
        // ObjectComposition.transform() before invoking getModelDynamic() on
        // the returned definition. It does not recursively transform a child
        // that itself owns another transform table.
        int nextId = transform(placedDefinition);
        if (nextId < 0) {
            return new Resolution(placedId, Status.HIDDEN_IN_VAR_STATE,
                    Optional.of(placedDefinition), Optional.empty(),
                    List.of(placedId));
        }

        Optional<ObjectDefinitionView> next = definitions.object(nextId);
        if (next.isEmpty()) {
            return new Resolution(placedId, Status.MISSING_TRANSFORM_DEFINITION,
                    Optional.of(placedDefinition), Optional.empty(),
                    List.of(placedId, nextId));
        }

        ObjectDefinitionView displayDefinition = next.orElseThrow();
        return new Resolution(placedId,
                displayDefinition.hasTransforms()
                        ? Status.RESOLVED_NESTED_TRANSFORM_CHILD
                        : Status.RESOLVED,
                Optional.of(placedDefinition), Optional.of(displayDefinition),
                List.of(placedId, nextId));
    }

    /** {@code ObjectComposition.transform()} for this resolver's var state. */
    private int transform(ObjectDefinitionView definition) {
        int[] transforms = definition.transforms();
        if (transforms.length == 0) return definition.defaultTransform();
        int value = definition.varbit() != -1 ? varState.varbitValue(definition.varbit())
                : definition.varp() != -1 ? varState.varpValue(definition.varp())
                : -1;
        return value >= 0 && value < transforms.length - 1
                ? transforms[value]
                : transforms[transforms.length - 1];
    }

    /**
     * First state of a multiloc that shows a definition, for editor ghosts of
     * locs hidden in the current state; empty when every state is hidden.
     */
    public Optional<ObjectDefinitionView> firstVisibleState(int placedId) {
        return definitions.object(placedId).flatMap(placed -> {
            for (int child : placed.transforms()) {
                if (child < 0) continue;
                Optional<ObjectDefinitionView> definition = definitions.object(child);
                if (definition.isPresent()) return definition;
            }
            return Optional.empty();
        });
    }

    public enum Status {
        RESOLVED,
        MISSING_PLACED_DEFINITION,
        /** The var state selects -1: the client draws nothing for this loc right now. */
        HIDDEN_IN_VAR_STATE,
        MISSING_TRANSFORM_DEFINITION,
        RESOLVED_NESTED_TRANSFORM_CHILD
    }

    public record Resolution(
            int placedId,
            Status status,
            Optional<ObjectDefinitionView> placedDefinition,
            Optional<ObjectDefinitionView> displayDefinition,
            List<Integer> transformPath
    ) {
        public Resolution {
            if (placedId < 0) {
                throw new IllegalArgumentException("Placed object id cannot be negative");
            }
            status = Objects.requireNonNull(status, "status");
            placedDefinition = Objects.requireNonNull(placedDefinition, "placedDefinition");
            displayDefinition = Objects.requireNonNull(displayDefinition, "displayDefinition");
            transformPath = List.copyOf(Objects.requireNonNull(transformPath, "transformPath"));
            if (transformPath.isEmpty() || transformPath.get(0) != placedId) {
                throw new IllegalArgumentException("Transform path must start with the placed object id");
            }
        }

        public boolean resolved() {
            return (status == Status.RESOLVED || status == Status.RESOLVED_NESTED_TRANSFORM_CHILD)
                    && displayDefinition.isPresent();
        }

    }
}
