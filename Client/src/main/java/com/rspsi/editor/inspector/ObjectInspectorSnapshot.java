package com.rspsi.editor.inspector;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ObjectCollisionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
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
        Optional<ObjectCollisionSummary> collision
) {
    public ObjectInspectorSnapshot {
        Objects.requireNonNull(category, "category");
        shape = Objects.requireNonNull(shape, "shape");
        definition = Objects.requireNonNull(definition, "definition");
        collision = Objects.requireNonNull(collision, "collision");
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
                        value.blockWalk(), value.blockProjectile(), value.breakRouteFinding())));
    }

    private static ObjectDefinitionSummary summary(ObjectDefinitionView definition) {
        List<Integer> models = Arrays.stream(definition.modelIds()).boxed().toList();
        return new ObjectDefinitionSummary(definition.id(), definition.name(),
                Math.max(1, definition.width()), Math.max(1, definition.length()),
                models, definition.interactions());
    }
}
