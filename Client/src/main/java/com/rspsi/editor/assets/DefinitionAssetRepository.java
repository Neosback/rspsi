package com.rspsi.editor.assets;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ObjectCollisionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.TextureDefinitionView;
import com.rspsi.cache.definition.ModelDefinitionView;
import com.rspsi.cache.definition.ModelGeometryView;
import com.rspsi.cache.definition.MapSceneSpriteView;
import com.rspsi.cache.definition.SequenceDefinitionView;
import com.rspsi.cache.definition.MapElementDefinitionView;
import com.rspsi.cache.AssetCategory;
import com.rspsi.cache.AssetRepositoryCapabilities;

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
    private volatile List<AssetDescriptor> catalog;

    public DefinitionAssetRepository(DefinitionProvider definitions) {
        this(definitions, SymbolicNameProvider.none());
    }

    public DefinitionAssetRepository(DefinitionProvider definitions, SymbolicNameProvider symbolicNames) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.symbolicNames = Objects.requireNonNull(symbolicNames, "symbolicNames");
    }

    @Override
    public AssetRepositoryCapabilities capabilities() {
        return new AssetRepositoryCapabilities(java.util.EnumSet.of(
                AssetCategory.OBJECTS,
                AssetCategory.UNDERLAYS,
                AssetCategory.OVERLAYS,
                AssetCategory.TEXTURES,
                AssetCategory.MODELS,
                AssetCategory.MAP_SCENES,
                AssetCategory.SEQUENCES,
                AssetCategory.MAP_ELEMENTS), true);
    }

    @Override
    public List<Integer> ids(AssetCategory category) {
        if (category == null) return List.of();
        return switch (category) {
            case OBJECTS -> definitions.objectIds();
            case UNDERLAYS -> definitions.underlayIds();
            case OVERLAYS -> definitions.overlayIds();
            case TEXTURES -> definitions.textureIds();
            case MODELS -> definitions.modelIds();
            case MAP_SCENES -> definitions.mapSceneIds();
            case SEQUENCES -> definitions.sequenceIds();
            case MAP_ELEMENTS -> definitions.mapElementIds();
            default -> List.of();
        };
    }

    @Override
    public List<AssetDescriptor> search(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return allAssets().stream()
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
        return descriptorFor(id, type.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public Optional<ObjectDefinitionView> object(int id) {
        return id < 0 ? Optional.empty() : definitions.object(id);
    }

    @Override
    public Optional<FloorDefinitionView> underlay(int id) {
        return id < 0 ? Optional.empty() : definitions.underlay(id);
    }

    @Override
    public Optional<FloorDefinitionView> overlay(int id) {
        return id < 0 ? Optional.empty() : definitions.overlay(id);
    }

    @Override
    public Optional<TextureDefinitionView> texture(int id) {
        return id < 0 ? Optional.empty() : definitions.texture(id);
    }

    @Override
    public Optional<int[]> texturePixels(int id, double brightness, int textureSize) {
        return id < 0 ? Optional.empty() : definitions.texturePixels(id, brightness, textureSize)
                .map(int[]::clone);
    }

    @Override
    public Optional<ModelDefinitionView> model(int id) {
        return id < 0 ? Optional.empty() : definitions.model(id);
    }

    @Override
    public Optional<ModelGeometryView> modelGeometry(int id) {
        return id < 0 ? Optional.empty() : definitions.modelGeometry(id);
    }

    @Override
    public Optional<MapSceneSpriteView> mapScene(int id) {
        return id < 0 ? Optional.empty() : definitions.mapScene(id);
    }

    @Override
    public Optional<SequenceDefinitionView> sequence(int id) {
        return id < 0 ? Optional.empty() : definitions.sequence(id);
    }

    @Override
    public Optional<MapElementDefinitionView> mapElement(int id) {
        return id < 0 ? Optional.empty() : definitions.mapElement(id);
    }

    @Override
    public Optional<ObjectCollisionView> objectCollision(int id) {
        return id < 0 ? Optional.empty() : definitions.objectCollision(id);
    }

    @Override
    public Optional<ObjectAppearanceView> objectAppearance(int id) {
        return id < 0 ? Optional.empty() : definitions.objectAppearance(id);
    }

    private Optional<AssetDescriptor> descriptorFor(int id, String type) {
        return switch (type.trim().toLowerCase(Locale.ROOT)) {
            case "object" -> definitions.object(id).map(value ->
                    descriptor("object", id, name(value.name(), "Object", id), objectDetails(value)));
            case "underlay" -> definitions.underlay(id).map(value ->
                    descriptor("underlay", id, "Underlay " + id, floorDetails(value)));
            case "overlay" -> definitions.overlay(id).map(value ->
                    descriptor("overlay", id, "Overlay " + id, floorDetails(value)));
            case "texture" -> definitions.texture(id).map(value ->
                    descriptor("texture", id, "Texture " + id, textureDetails(value)));
            case "model" -> definitions.model(id).map(value ->
                    descriptor("model", id, "Model " + id, modelDetails(value)));
            case "sprite", "mapscene", "map-scene" -> definitions.mapScene(id).map(value ->
                    descriptor("sprite", id, "Map scene sprite " + id, spriteDetails(value)));
            case "sequence", "seq" -> definitions.sequence(id).map(value ->
                    descriptor("sequence", id, "Sequence " + id, sequenceDetails(value)));
            case "map-element", "mapelement", "world-map-element" -> definitions.mapElement(id).map(value ->
                    descriptor("map-element", id, mapElementName(value), mapElementDetails(value)));
            default -> Optional.empty();
        };
    }

    /** Builds the immutable descriptor index once for the lifetime of a provider. */
    private List<AssetDescriptor> allAssets() {
        List<AssetDescriptor> current = catalog;
        if (current != null) return current;
        synchronized (this) {
            current = catalog;
            if (current == null) {
                List<AssetDescriptor> assets = new ArrayList<>();
                definitions.objectIds().forEach(id -> add(assets, descriptorFor(id, "object")));
                definitions.underlayIds().forEach(id -> add(assets, descriptorFor(id, "underlay")));
                definitions.overlayIds().forEach(id -> add(assets, descriptorFor(id, "overlay")));
                definitions.textureIds().forEach(id -> add(assets, descriptorFor(id, "texture")));
                // Model metadata is decoded lazily. A cache can contain a very
                // large model index, and searching it must not decode every mesh.
                definitions.modelIds().forEach(id -> add(assets, Optional.of(
                        descriptor("model", id, "Model " + id))));
                definitions.mapSceneIds().forEach(id -> add(assets, descriptorFor(id, "sprite")));
                definitions.sequenceIds().forEach(id -> add(assets, descriptorFor(id, "sequence")));
                definitions.mapElementIds().forEach(id -> add(assets, descriptorFor(id, "map-element")));
                current = assets.stream()
                        .sorted(Comparator.comparing(AssetDescriptor::type)
                                .thenComparingInt(AssetDescriptor::id))
                        .toList();
                catalog = current;
            }
        }
        return current;
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
        details.add("Interactive: " + object.interactive());
        if (object.mapSceneId() >= 0) {
            details.add("Map scene: " + object.mapSceneId());
        }
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
                "Average HSL: " + texture.averageHsl(),
                "Animated: " + (texture.animationSpeed() > 0 ? "yes" : "no"));
    }

    private static List<String> modelDetails(ModelDefinitionView model) {
        return List.of("Vertices: " + model.vertexCount(),
                "Triangles: " + model.triangleCount(),
                "Texture triangles: " + model.textureTriangleCount(),
                "Render priority: " + model.renderPriority());
    }

    private static List<String> spriteDetails(MapSceneSpriteView sprite) {
        return List.of("Dimensions: " + sprite.width() + " × " + sprite.height(),
                "Offset: " + sprite.offsetX() + ", " + sprite.offsetY(),
                "Pixels: " + (sprite.width() * sprite.height()));
    }

    private static List<String> sequenceDetails(SequenceDefinitionView sequence) {
        return List.of("Frames: " + sequence.frameIds().length,
                "Frame step: " + sequence.frameStep(),
                "Priority: " + sequence.priority(),
                "Skeletal ID: " + sequence.skeletalId());
    }

    private static String mapElementName(MapElementDefinitionView element) {
        return element.name().isBlank() ? "Map element " + element.id() : element.name();
    }

    private static List<String> mapElementDetails(MapElementDefinitionView element) {
        return List.of("Sprite: " + element.spriteId(),
                "Text size: " + element.textSize(),
                "World map: " + (element.worldMapVisible() ? "visible" : "hidden"),
                "Minimap: " + (element.minimapVisible() ? "visible" : "hidden"),
                "Actions: " + element.actions());
    }

    private static void add(List<AssetDescriptor> assets, Optional<AssetDescriptor> asset) {
        asset.ifPresent(assets::add);
    }

    private static String name(String value, String fallback, int id) {
        return value == null || value.isBlank() ? fallback + " " + id : value;
    }
}
