package com.rspsi.editor.knowledge.scene;

import com.rspsi.editor.model.ObjectCategory;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldObject;

import java.util.List;
import java.util.Objects;

/**
 * Layer 2 Scene Fact: Deterministic, resolved state of an authored object placed in the scene.
 */
public record SceneObjectFact(
        TileCoordinate coordinate,
        int objectId,
        int type,
        int rotation,
        ObjectCategory category,
        int width,
        int length,
        int effectivePlane,
        int placementElevation,
        List<Integer> resolvedModelIds
) {
    public SceneObjectFact {
        Objects.requireNonNull(coordinate, "coordinate");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(resolvedModelIds, "resolvedModelIds");
        resolvedModelIds = List.copyOf(resolvedModelIds);
    }

    public static SceneObjectFact of(
            WorldObject object,
            int width,
            int length,
            int effectivePlane,
            int placementElevation,
            List<Integer> resolvedModelIds
    ) {
        Objects.requireNonNull(object, "object");
        return new SceneObjectFact(
                new TileCoordinate(object.plane(), object.x(), object.y()),
                object.id(),
                object.type(),
                object.rotation(),
                object.category(),
                width,
                length,
                effectivePlane,
                placementElevation,
                resolvedModelIds
        );
    }
}
