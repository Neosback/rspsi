package com.rspsi.editor.assets;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.TextureDefinitionView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Neutral asset-browser adapter over definition providers.
 *
 * <p>The repository deliberately exposes only small asset descriptors. A
 * future RSCM/GameVal adapter can improve names without changing tools or the
 * browser contract.</p>
 */
public final class DefinitionAssetRepository implements AssetRepository {
    private final DefinitionProvider definitions;

    public DefinitionAssetRepository(DefinitionProvider definitions) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
    }

    @Override
    public List<AssetDescriptor> search(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<AssetDescriptor> assets = new ArrayList<>();
        definitions.objectIds().forEach(id -> add(assets, get(id, "object")));
        definitions.underlayIds().forEach(id -> add(assets, get(id, "underlay")));
        definitions.overlayIds().forEach(id -> add(assets, get(id, "overlay")));
        definitions.textureIds().forEach(id -> add(assets, get(id, "texture")));
        return assets.stream()
                .filter(asset -> needle.isEmpty()
                        || asset.name().toLowerCase(Locale.ROOT).contains(needle)
                        || asset.type().toLowerCase(Locale.ROOT).contains(needle)
                        || Integer.toString(asset.id()).equals(needle))
                .sorted(Comparator.comparing(AssetDescriptor::type).thenComparingInt(AssetDescriptor::id))
                .toList();
    }

    @Override
    public Optional<AssetDescriptor> get(int id, String type) {
        if (id < 0 || type == null) return Optional.empty();
        return switch (type.trim().toLowerCase(Locale.ROOT)) {
            case "object" -> definitions.object(id).map(value ->
                    new AssetDescriptor(id, "object", name(value.name(), "Object", id)));
            case "underlay" -> definitions.underlay(id).map(value ->
                    new AssetDescriptor(id, "underlay", "Underlay " + id));
            case "overlay" -> definitions.overlay(id).map(value ->
                    new AssetDescriptor(id, "overlay", "Overlay " + id));
            case "texture" -> definitions.texture(id).map(value ->
                    new AssetDescriptor(id, "texture", "Texture " + id));
            default -> Optional.empty();
        };
    }

    private static void add(List<AssetDescriptor> assets, Optional<AssetDescriptor> asset) {
        asset.ifPresent(assets::add);
    }

    private static String name(String value, String fallback, int id) {
        return value == null || value.isBlank() ? fallback + " " + id : value;
    }
}
