package com.rspsi.cache.definition;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves a placed OSRS object definition to the definition that supplies its
 * visible editor appearance when no live varbit/varp state is available.
 *
 * <p>The game client always transforms an object whose definition has a
 * transform table before asking that definition for its model. Studio has no
 * player var state, so the transform table's default/fallback entry is the
 * only deterministic client-compatible choice. A missing default is an
 * explicit unresolved state, not permission to render the placed shell.</p>
 */
public final class ObjectDefinitionResolver {
    private final DefinitionProvider definitions;

    public ObjectDefinitionResolver(DefinitionProvider definitions) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
    }

    public Resolution resolveEditorDisplay(int placedId) {
        if (placedId < 0) {
            throw new IllegalArgumentException("Placed object id cannot be negative");
        }

        Optional<ObjectDefinitionView> placed = definitions.object(placedId);
        if (placed.isEmpty()) {
            return new Resolution(placedId, Status.MISSING_PLACED_DEFINITION,
                    Optional.empty(), Optional.empty(), List.of(placedId), placedId);
        }

        ObjectDefinitionView placedDefinition = placed.orElseThrow();
        if (!placedDefinition.hasTransforms()) {
            return new Resolution(placedId, Status.RESOLVED,
                    Optional.of(placedDefinition), Optional.of(placedDefinition),
                    List.of(placedId), -1);
        }

        // RuneLite DynamicObject.getModel() performs exactly one
        // ObjectComposition.transform() before invoking getModelDynamic() on
        // the returned definition. It does not recursively transform a child
        // that itself owns another transform table.
        int nextId = placedDefinition.defaultTransform();
        if (nextId < 0) {
            return new Resolution(placedId, Status.NO_DEFAULT_TRANSFORM,
                    Optional.of(placedDefinition), Optional.empty(),
                    List.of(placedId), -1);
        }

        Optional<ObjectDefinitionView> next = definitions.object(nextId);
        if (next.isEmpty()) {
            return new Resolution(placedId, Status.MISSING_TRANSFORM_DEFINITION,
                    Optional.of(placedDefinition), Optional.empty(),
                    List.of(placedId, nextId), nextId);
        }

        ObjectDefinitionView displayDefinition = next.orElseThrow();
        return new Resolution(placedId,
                displayDefinition.hasTransforms()
                        ? Status.RESOLVED_NESTED_TRANSFORM_CHILD
                        : Status.RESOLVED,
                Optional.of(placedDefinition), Optional.of(displayDefinition),
                List.of(placedId, nextId), -1);
    }

    public enum Status {
        RESOLVED,
        MISSING_PLACED_DEFINITION,
        NO_DEFAULT_TRANSFORM,
        MISSING_TRANSFORM_DEFINITION,
        RESOLVED_NESTED_TRANSFORM_CHILD
    }

    public record Resolution(
            int placedId,
            Status status,
            Optional<ObjectDefinitionView> placedDefinition,
            Optional<ObjectDefinitionView> displayDefinition,
            List<Integer> transformPath,
            int unresolvedDefinitionId
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

        public boolean transformed() {
            return transformPath.size() > 1;
        }

        public int displayId() {
            return displayDefinition.map(ObjectDefinitionView::id).orElse(-1);
        }
    }
}
