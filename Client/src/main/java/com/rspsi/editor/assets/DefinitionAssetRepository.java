package com.rspsi.editor.assets;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ObjectCollisionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.TextureDefinitionView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Neutral asset-browser adapter over definition providers.
 *
 * <p>The repository deliberately exposes only small asset descriptors. A
 * future RSCM/GameVal adapter can improve names without changing tools or the
 * browser contract.</p>
 */
public final class DefinitionAssetRepository implements AssetRepository {
    private final DefinitionProvider definitions;
    private final SymbolicNameProvider symbolicNames;

    public DefinitionAssetRepository(DefinitionProvider definitions) {
        this(definitions, SymbolicNameProvider.none());
    }

    public DefinitionAssetRepository(DefinitionProvider definitions, SymbolicNameProvider symbolicNames) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.symbolicNames = Objects.requireNonNull(symbolicNames, "symbolicNames");
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
                        || asset.symbolicName().map(value -> value.toLowerCase(Locale.ROOT).contains(needle)).orElse(false)
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
                    descriptor("object", id, name(value.name(), "Object", id), objectDetails(value)));
            case "underlay" -> definitions.underlay(id).map(value ->
                    descriptor("underlay", id, "Underlay " + id, floorDetails(value)));
            case "overlay" -> definitions.overlay(id).map(value ->
                    descriptor("overlay", id, "Overlay " + id, floorDetails(value)));
            case "texture" -> definitions.texture(id).map(value ->
                    descriptor("texture", id, "Texture " + id, textureDetails(value)));
            default -> Optional.empty();
        };
    }

    private AssetDescriptor descriptor(String type, int id, String displayName) {
        return descriptor(type, id, displayName, List.of());
    }

    private AssetDescriptor descriptor(String type, int id, String displayName,
                                       List<String> details) {
        Optional<String> symbolicName = symbolicNames.name(type, id);
        return new AssetDescriptor(id, type, displayName,
                symbolicName == null ? Optional.empty() : symbolicName, details);
    }

    private List<String> objectDetails(ObjectDefinitionView object) {
        List<String> details = new ArrayList<>();
        details.add("Size: " + object.width() + " × " + object.length());
        details.add("Models: " + java.util.Arrays.toString(object.modelIds()));
        details.add("Actions: " + object.interactions());
        definitions.objectCollision(object.id()).ifPresent(collision ->
                details.add("Collision: " + collisionSummary(collision)));
        definitions.objectAppearance(object.id()).ifPresent(appearance -> {
            if (appearance.animationId() >= 0) details.add("Animation: " + appearance.animationId());
            if (!appearance.recolors().isEmpty()) details.add("Recolors: " + appearance.recolors());
            if (!appearance.retextures().isEmpty()) details.add("Retextures: " + appearance.retextures());
        });
        return List.copyOf(details);
    }

    private static String collisionSummary(ObjectCollisionView collision) {
        return "walk=" + collision.blockWalk() + ", projectile="
                + (collision.blockProjectile() ? "yes" : "no") + ", route="
                + (collision.breakRouteFinding() ? "breaks" : "normal");
    }

    private static List<String> floorDetails(FloorDefinitionView floor) {
        return List.of("Texture: " + floor.texture(),
                String.format("RGB: 0x%06X", floor.rgb() & 0xFFFFFF),
                "HSL: " + floor.hue() + " / " + floor.saturation() + " / " + floor.luminance());
    }

    private static List<String> textureDetails(TextureDefinitionView texture) {
        return List.of("File: " + texture.fileId(),
                "Average RGB: " + texture.averageRgb(),
                "Animated: " + (texture.animationSpeed() > 0 ? "yes" : "no"));
    }

    private static void add(List<AssetDescriptor> assets, Optional<AssetDescriptor> asset) {
        asset.ifPresent(assets::add);
    }

    private static String name(String value, String fallback, int id) {
        return value == null || value.isBlank() ? fallback + " " + id : value;
    }
}
