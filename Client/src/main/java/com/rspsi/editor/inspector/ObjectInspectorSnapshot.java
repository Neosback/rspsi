package com.rspsi.editor.inspector;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ObjectCollisionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.editor.model.ObjectCategory;
import com.rspsi.editor.model.OsrsLocShape;
import com.rspsi.editor.model.WorldObject;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Backend- and frontend-neutral object inspector data.
 *
 * <p>Definitions are resolved at the boundary and flattened into small
 * immutable summaries. A missing definition remains explicit instead of being
 * replaced with guessed size or collision data.</p>
 */
public record ObjectInspectorSnapshot(
        int id,
        int x,
        int y,
        int plane,
        int type,
        int rotation,
        ObjectCategory category,
        Optional<OsrsLocShape> shape,
        Optional<ObjectDefinitionSummary> definition,
        Optional<ObjectCollisionSummary> collision,
        Optional<ObjectAppearanceView> appearance,
        ObjectResolutionSummary resolution
) {
    public ObjectInspectorSnapshot {
        Objects.requireNonNull(category, "category");
        shape = Objects.requireNonNull(shape, "shape");
        definition = Objects.requireNonNull(definition, "definition");
        collision = Objects.requireNonNull(collision, "collision");
        appearance = Objects.requireNonNull(appearance, "appearance");
        resolution = Objects.requireNonNull(resolution, "resolution");
    }

    /** Compatibility constructor for callers that do not yet supply resolution diagnostics. */
    public ObjectInspectorSnapshot(
            int id,
            int x,
            int y,
            int plane,
            int type,
            int rotation,
            ObjectCategory category,
            Optional<OsrsLocShape> shape,
            Optional<ObjectDefinitionSummary> definition,
            Optional<ObjectCollisionSummary> collision,
            Optional<ObjectAppearanceView> appearance
    ) {
        this(id, x, y, plane, type, rotation, category, shape, definition, collision,
                appearance, unresolvedCompatibility(id));
    }

    public String categoryName() {
        return category.displayName();
    }

    public String shapeName() {
        return shape.map(OsrsLocShape::displayName).orElse("Unknown shape");
    }

    public static ObjectInspectorSnapshot capture(WorldObject object, DefinitionProvider definitions) {
        Objects.requireNonNull(object, "object");
        Objects.requireNonNull(definitions, "definitions");
        Optional<ObjectDefinitionView> definition = definitions.object(object.id());
        Optional<ObjectCollisionView> collision = definitions.objectCollision(object.id());

        return new ObjectInspectorSnapshot(
                object.id(), object.x(), object.y(), object.plane(), object.type(), object.rotation(),
                object.category(), object.shape(),
                definition.map(ObjectInspectorSnapshot::summary),
                collision.map(value -> new ObjectCollisionSummary(
                        value.blockWalk(), value.blockProjectile(), value.breakRouteFinding())),
                definitions.objectAppearance(object.id()),
                ObjectResolutionSummary.capture(object, definitions));
    }

    static ObjectDefinitionSummary summary(ObjectDefinitionView definition) {
        List<Integer> models = Arrays.stream(definition.modelIds()).boxed().toList();
        List<Integer> modelTypes = Arrays.stream(definition.modelTypes()).boxed().toList();
        return new ObjectDefinitionSummary(definition.id(), definition.displayName(),
                Math.max(1, definition.width()), Math.max(1, definition.length()),
                models, modelTypes, definition.interactions());
    }

    private static ObjectResolutionSummary unresolvedCompatibility(int id) {
        return new ObjectResolutionSummary(
                com.rspsi.cache.definition.ObjectDefinitionResolver.Status.MISSING_PLACED_DEFINITION,
                List.of(Math.max(0, id)),
                Optional.empty(),
                ObjectResolutionSummary.GeometryStatus.DEFINITION_UNRESOLVED,
                List.of(),
                List.of(),
                List.of());
    }
}
