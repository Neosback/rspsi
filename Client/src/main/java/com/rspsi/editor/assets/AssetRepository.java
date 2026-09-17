package com.rspsi.editor.assets;

import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.MapElementDefinitionView;
import com.rspsi.cache.definition.MapSceneSpriteView;
import com.rspsi.cache.definition.ModelGeometryView;
import com.rspsi.cache.definition.ModelDefinitionView;
import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.cache.definition.ObjectCollisionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.SequenceDefinitionView;
import com.rspsi.cache.definition.TextureDefinitionView;
import com.rspsi.cache.AssetCategory;
import com.rspsi.cache.AssetRepositoryCapabilities;

import java.util.List;
import java.util.Optional;

/**
 * Neutral, lazy asset lookup contract for tools, inspectors, and plugins.
 *
 * <p>The searchable descriptor methods are intentionally small enough for a
 * frontend catalog. The typed accessors form the stable OSRS asset facade:
 * callers can request the definition they need without knowing whether the
 * backing cache is OpenRune, a test fixture, or another adapter. Implementors
 * may leave any category unavailable by using the default empty result.</p>
 */
public interface AssetRepository {
    /** Reports the asset families available without exposing a cache backend. */
    default AssetRepositoryCapabilities capabilities() {
        return AssetRepositoryCapabilities.none();
    }

    /** Returns the IDs for a supported category without decoding every asset. */
    default List<Integer> ids(AssetCategory category) {
        return List.of();
    }

    List<AssetDescriptor> search(String query);

    Optional<AssetDescriptor> get(int id, String type);

    /** Lazy object/location definition, including footprint and interactions. */
    default Optional<ObjectDefinitionView> object(int id) {
        return Optional.empty();
    }

    default Optional<FloorDefinitionView> underlay(int id) {
        return Optional.empty();
    }

    default Optional<FloorDefinitionView> overlay(int id) {
        return Optional.empty();
    }

    default Optional<TextureDefinitionView> texture(int id) {
        return Optional.empty();
    }

    /** Lazy frontend-neutral texture pixels for previews and material upload. */
    default Optional<int[]> texturePixels(int id, double brightness, int textureSize) {
        return Optional.empty();
    }

    /** Lazy model metadata; geometry remains a separate, potentially heavier request. */
    default Optional<ModelDefinitionView> model(int id) {
        return Optional.empty();
    }

    /** Optional lazy geometry for a selected model asset. */
    default Optional<ModelGeometryView> modelGeometry(int id) {
        return Optional.empty();
    }

    default Optional<MapSceneSpriteView> mapScene(int id) {
        return Optional.empty();
    }

    default Optional<SequenceDefinitionView> sequence(int id) {
        return Optional.empty();
    }

    default Optional<MapElementDefinitionView> mapElement(int id) {
        return Optional.empty();
    }

    /** Object-derived collision and appearance are independent lazy projections. */
    default Optional<ObjectCollisionView> objectCollision(int id) {
        return Optional.empty();
    }

    default Optional<ObjectAppearanceView> objectAppearance(int id) {
        return Optional.empty();
    }
}
