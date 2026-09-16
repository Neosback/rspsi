package com.rspsi.cache.definition;

import java.util.Optional;

/** Provides cache definitions without exposing a backend library. */
public interface DefinitionProvider {
    Optional<ObjectDefinitionView> object(int id);

    Optional<FloorDefinitionView> underlay(int id);

    Optional<FloorDefinitionView> overlay(int id);

    /** Optional until the selected backend exposes a decoded model index. */
    default Optional<ModelDefinitionView> model(int id) {
        return Optional.empty();
    }

    /** Optional until the selected backend exposes texture definitions. */
    default Optional<TextureDefinitionView> texture(int id) {
        return Optional.empty();
    }

    /** Optional until a backend has supplied collision-relevant object fields. */
    default Optional<ObjectCollisionView> objectCollision(int id) {
        return Optional.empty();
    }
}
