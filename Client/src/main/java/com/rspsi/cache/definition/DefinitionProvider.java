package com.rspsi.cache.definition;

import java.util.Optional;
import java.util.List;

/** Provides cache definitions without exposing a backend library. */
public interface DefinitionProvider {
    Optional<ObjectDefinitionView> object(int id);

    /** Looks up the zero-based cache definition ID for a non-empty underlay. */
    Optional<FloorDefinitionView> underlay(int id);

    Optional<FloorDefinitionView> overlay(int id);

    /** Available IDs for neutral asset-browser adapters; empty when unknown. */
    default List<Integer> objectIds() { return List.of(); }

    default List<Integer> underlayIds() { return List.of(); }

    default List<Integer> overlayIds() { return List.of(); }

    default List<Integer> textureIds() { return List.of(); }

    /** Optional until the selected backend exposes a decoded model index. */
    default Optional<ModelDefinitionView> model(int id) {
        return Optional.empty();
    }

    /** Optional until the selected backend exposes texture definitions. */
    default Optional<TextureDefinitionView> texture(int id) {
        return Optional.empty();
    }

    /** Optional map-scene sprite pixels for minimap/world-map composition. */
    default Optional<MapSceneSpriteView> mapScene(int id) {
        return Optional.empty();
    }

    /** Optional until a backend has supplied collision-relevant object fields. */
    default Optional<ObjectCollisionView> objectCollision(int id) {
        return Optional.empty();
    }

    /** Optional model/animation/transform data for object scene previews. */
    default Optional<ObjectAppearanceView> objectAppearance(int id) {
        return Optional.empty();
    }
}
