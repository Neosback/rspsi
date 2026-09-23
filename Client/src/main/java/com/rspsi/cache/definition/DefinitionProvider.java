package com.rspsi.cache.definition;

import java.util.Optional;
import java.util.List;

/** Provides cache definitions without exposing a backend library. */
public interface DefinitionProvider {
    Optional<ObjectDefinitionView> object(int id);

    /**
     * Optional backend-neutral raw decoded object metadata for inspector/debug
     * tooling. Implementations should not expose backend library types here.
     */
    default Optional<ObjectDefinitionRawView> objectRaw(int id) {
        return Optional.empty();
    }

    /**
     * Starts an isolated in-memory edit transaction when the backend supports
     * writable object-definition semantics. The transaction itself never
     * persists to the source cache.
     */
    default Optional<ObjectDefinitionEditTransaction> editObject(int id) {
        return Optional.empty();
    }

    /** Looks up the zero-based cache definition ID for a non-empty underlay. */
    Optional<FloorDefinitionView> underlay(int id);

    Optional<FloorDefinitionView> overlay(int id);

    /** Available IDs for neutral asset-browser adapters; empty when unknown. */
    default List<Integer> objectIds() { return List.of(); }

    default List<Integer> underlayIds() { return List.of(); }

    default List<Integer> overlayIds() { return List.of(); }

    default List<Integer> textureIds() { return List.of(); }

    /** Available model IDs for neutral asset-browser adapters; empty when unknown. */
    default List<Integer> modelIds() { return List.of(); }

    /** Optional until the selected backend exposes a decoded model index. */
    default Optional<ModelDefinitionView> model(int id) {
        return Optional.empty();
    }

    /** Optional decoded geometry for model previews and renderer preparation. */
    default Optional<ModelGeometryView> modelGeometry(int id) {
        return Optional.empty();
    }

    /** Optional until the selected backend exposes texture definitions. */
    default Optional<TextureDefinitionView> texture(int id) {
        return Optional.empty();
    }

    /** Optional decoded texture pixels; the returned array is owned by the caller. */
    default Optional<int[]> texturePixels(int id, double brightness, int textureSize) {
        return Optional.empty();
    }

    /** Optional map-scene sprite pixels for minimap/world-map composition. */
    default Optional<MapSceneSpriteView> mapScene(int id) {
        return Optional.empty();
    }

    /** Optional lazy animation sequence metadata. */
    default Optional<SequenceDefinitionView> sequence(int id) {
        return Optional.empty();
    }

    /** Optional decoded legacy frame transform data for animated models. */
    default Optional<AnimationFrameView> animationFrame(int id) {
        return Optional.empty();
    }

    /** Optional decoded skeleton transform groups for animated models. */
    default Optional<SkeletonDefinitionView> skeleton(int id) {
        return Optional.empty();
    }

    /** Optional decoded current-client cached-model skeletal animation. */
    default Optional<CachedSkeletalAnimationView> cachedSkeletalAnimation(int id) {
        return Optional.empty();
    }

    /** Optional per-vertex cached-model bone indices and influence weights. */
    default Optional<ModelSkeletalSkinView> modelSkeletalSkin(int modelId) {
        return Optional.empty();
    }

    /** Available sequence IDs when the backend exposes a sequence index. */
    default List<Integer> sequenceIds() {
        return List.of();
    }

    /** Optional lazy world-map/minimap element metadata. */
    default Optional<MapElementDefinitionView> mapElement(int id) {
        return Optional.empty();
    }

    /** Available map-element IDs when the backend exposes a map-element index. */
    default List<Integer> mapElementIds() {
        return List.of();
    }

    /** Available zero-based map-scene sprite IDs, when the backend exposes them. */
    default List<Integer> mapSceneIds() {
        return List.of();
    }

    /** Optional until a backend has supplied collision-relevant object fields. */
    default Optional<ObjectCollisionView> objectCollision(int id) {
        return Optional.empty();
    }

    /** Optional model/animation/transform data for object scene previews. */
    default Optional<ObjectAppearanceView> objectAppearance(int id) {
        return Optional.empty();
    }

    /**
     * Immutable decode-failure diagnostics for definitions that were indexed
     * but could not be decoded. Providers that decode eagerly report failures
     * during load; lazy providers report failures when a decode was attempted
     * and threw. A corrupt definition must never be indistinguishable from an
     * absent one in verifier output.
     */
    default List<DecodeFailure> decodeFailures() {
        return List.of();
    }

    /** Describes one definition that was indexed but failed to decode. */
    record DecodeFailure(String family, int id, String message) {
        public DecodeFailure {
            family = family == null ? "unknown" : family;
            message = message == null ? "decode failed" : message;
        }
    }

    /** Varbit layout (varp and bit range), for resolving multiloc and other var-driven state. */
    default Optional<VarbitDefinitionView> varbit(int id) {
        return Optional.empty();
    }

    /** Map element (map function icon, e.g. a bank) an object shows on the minimap/world map. */
    default java.util.OptionalInt objectMapElement(int objectId) {
        return java.util.OptionalInt.empty();
    }

    /** One frame of a sprite group as opaque-palette ARGB (index 0 transparent). */
    default Optional<MapSceneSpriteView> sprite(int groupId, int frame) {
        return Optional.empty();
    }
}
