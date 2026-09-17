package com.rspsi.cache.store;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.ObjectCollisionView;
import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.cache.definition.ModelDefinitionView;
import com.rspsi.cache.definition.ModelGeometryView;
import com.rspsi.cache.definition.TextureDefinitionView;
import com.rspsi.cache.definition.MapSceneSpriteView;
import dev.openrune.cache.filestore.definition.ModelDecoder;
import dev.openrune.cache.filestore.definition.SpriteDecoder;
import static dev.openrune.cache.ArchiveIndexKt.MODELS;
import dev.openrune.definition.game.IndexedSprite;
import dev.openrune.definition.type.model.ModelType;
import dev.openrune.OsrsCacheProvider;
import dev.openrune.definition.type.ObjectType;
import dev.openrune.definition.type.OverlayType;
import dev.openrune.definition.type.SpriteType;
import dev.openrune.definition.type.TextureType;
import dev.openrune.definition.type.UnderlayType;
import dev.openrune.filesystem.Cache;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * OpenRune definition adapter. OpenRune objects are decoded once into maps,
 * then reduced to RSPSi-owned views before they reach editor code.
 */
public final class OpenRuneDefinitionProvider implements DefinitionProvider {
    private final Map<Integer, ObjectType> objects = new HashMap<>();
    private final Map<Integer, UnderlayType> underlays = new HashMap<>();
    private final Map<Integer, OverlayType> overlays = new HashMap<>();
    private final Map<Integer, TextureType> textures = new HashMap<>();
    private final ModelDecoder modelDecoder;
    private final List<Integer> modelIds;
    private final Map<Integer, Optional<ModelType>> models = new HashMap<>();
    private final Map<Integer, Optional<ModelDefinitionView>> modelViews = new HashMap<>();
    private final Map<Integer, Optional<ModelGeometryView>> modelGeometryViews = new HashMap<>();
    private final Map<Integer, MapSceneSpriteView> mapScenes;

    private OpenRuneDefinitionProvider(Cache cache, int revision) {
        Objects.requireNonNull(cache, "cache");
        if (revision <= 0) {
            throw new IllegalArgumentException("OSRS cache revision must be positive");
        }
        new OsrsCacheProvider.ObjectDecoder(revision).load(cache, objects);
        new OsrsCacheProvider.UnderlayDecoder().load(cache, underlays);
        new OsrsCacheProvider.OverlayDecoder().load(cache, overlays);
        new OsrsCacheProvider.TextureDecoder(revision).load(cache, textures);
        modelDecoder = new ModelDecoder(cache, java.util.Collections.emptyList());
        modelIds = archiveIds(cache, MODELS);
        mapScenes = loadMapScenes(cache);
    }

    public static OpenRuneDefinitionProvider load(Cache cache, int revision) {
        return new OpenRuneDefinitionProvider(cache, revision);
    }

    @Override
    public Optional<ObjectDefinitionView> object(int id) {
        ObjectType definition = objects.get(id);
        if (definition == null) {
            return Optional.empty();
        }
        List<String> interactions = java.util.stream.IntStream.range(0, 5)
                .mapToObj(index -> definition.getActions() == null
                        ? null : definition.getActions().getOpOrNull(index))
                .filter(Objects::nonNull)
                .toList();
        int[] modelIds = definition.getObjectModels() == null
                ? new int[0]
                : definition.getObjectModels().stream().mapToInt(Integer::intValue).toArray();
        return Optional.of(new ObjectDefinitionView(definition.getId(), definition.getName(),
                Math.max(1, definition.getSizeX()), Math.max(1, definition.getSizeY()),
                interactions, modelIds, definition.getMapSceneID()));
    }

    @Override
    public Optional<MapSceneSpriteView> mapScene(int id) {
        return Optional.ofNullable(mapScenes.get(id));
    }

    @Override
    public List<Integer> mapSceneIds() {
        return mapScenes.keySet().stream().sorted().toList();
    }

    @Override
    public List<Integer> objectIds() {
        return objects.keySet().stream().sorted().toList();
    }

    @Override
    public List<Integer> underlayIds() {
        return underlays.keySet().stream().sorted().toList();
    }

    @Override
    public List<Integer> overlayIds() {
        return overlays.keySet().stream().sorted().toList();
    }

    @Override
    public List<Integer> textureIds() {
        return textures.keySet().stream().sorted().toList();
    }

    @Override
    public List<Integer> modelIds() {
        return modelIds;
    }

    @Override
    public Optional<ObjectCollisionView> objectCollision(int id) {
        ObjectType definition = objects.get(id);
        if (definition == null) {
            return Optional.empty();
        }
        int clipType = definition.getClipType() < 0 ? 2 : definition.getClipType();
        return Optional.of(new ObjectCollisionView(definition.getId(),
                Math.max(1, definition.getSizeX()), Math.max(1, definition.getSizeY()),
                Math.max(0, definition.getSolid()), definition.getImpenetrable(),
                definition.isHollow(), clipType));
    }

    @Override
    public Optional<ObjectAppearanceView> objectAppearance(int id) {
        ObjectType definition = objects.get(id);
        if (definition == null) return Optional.empty();
        return Optional.of(new ObjectAppearanceView(
                definition.getAnimationId(), false,
                Math.max(1, definition.getModelSizeX()), Math.max(1, definition.getModelSizeY()),
                Math.max(1, definition.getModelSizeZ()), definition.getOffsetX(),
                definition.getOffsetY(), definition.getOffsetZ(),
                ObjectAppearanceView.pairs(toArray(definition.getOriginalColours()),
                        toArray(definition.getModifiedColours())),
                ObjectAppearanceView.pairs(toArray(definition.getOriginalTextureColours()),
                        toArray(definition.getModifiedTextureColours()))));
    }

    private static int[] toArray(List<Integer> values) {
        return values == null ? null : values.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Loads the graphics-defaults map-scene group when the cache exposes it.
     * Some DAT2-style caches do not expose a usable graphics-defaults file;
     * OpenRune-Editor handles those through the named {@code mapscene}
     * archive in the OSRS sprite index, so keep that fallback inside this
     * adapter rather than making minimap/editor code understand either cache
     * layout.
     */
    private static Map<Integer, MapSceneSpriteView> loadMapScenes(Cache cache) {
        try {
            byte[] defaults = cache.data(17, 3, 0, null);
            int group = graphicsDefaultMapSceneGroup(defaults);
            if (group < 0) {
                group = cache.archiveId(8, "mapscene");
            }
            if (group < 0) return Map.of();
            Map<Integer, SpriteType> spriteGroups = new HashMap<>();
            new SpriteDecoder().load(cache, spriteGroups);
            SpriteType spriteType = spriteGroups.get(group);
            if (spriteType == null) return Map.of();
            Map<Integer, MapSceneSpriteView> result = new HashMap<>();
            IndexedSprite[] sprites = spriteType.getSprites();
            for (int id = 0; id < sprites.length; id++) {
                IndexedSprite sprite = sprites[id];
                if (sprite == null || sprite.getWidth() <= 0 || sprite.getHeight() <= 0) continue;
                /*
                 * MinimapImageRenderer follows the client rasterizer: palette
                 * index zero is transparent and every other index is copied as
                 * an opaque palette colour. Do not use OpenRune's
                 * toBufferedImage() here; its optional per-pixel alpha is a
                 * general-purpose image concern, while the OSRS minimap
                 * compositor deliberately ignores that alpha channel.
                 */
                byte[] raster = sprite.getRaster();
                int[] palette = sprite.getPalette();
                int[] argb = new int[sprite.getWidth() * sprite.getHeight()];
                for (int pixel = 0; pixel < argb.length; pixel++) {
                    int paletteIndex = raster[pixel] & 0xFF;
                    if (paletteIndex != 0) {
                        argb[pixel] = 0xFF000000 | (palette[paletteIndex] & 0x00FFFFFF);
                    }
                }
                result.put(id, new MapSceneSpriteView(id, sprite.getWidth(), sprite.getHeight(),
                        sprite.getOffsetX(), sprite.getOffsetY(), argb));
            }
            return Map.copyOf(result);
        } catch (RuntimeException ignored) {
            // Graphics defaults and sprite groups are optional across cache
            // families. A missing/unsupported group must not make terrain or
            // definitions unavailable.
            return Map.of();
        }
    }

    /** Reads opcode 2 from the OSRS graphics-defaults file. */
    private static int graphicsDefaultMapSceneGroup(byte[] data) {
        if (data == null) return -1;
        int offset = 0;
        while (offset < data.length) {
            int opcode = data[offset++] & 0xFF;
            if (opcode == 0) return -1;
            if (opcode != 2) {
                if (opcode == 1) {
                    if (offset + 3 > data.length) return -1;
                    offset += 3;
                } else {
                    return -1;
                }
                continue;
            }
            int mapScene = -1;
            for (int field = 0; field < 11; field++) {
                int value = readBigSmart(data, offset);
                if (value == Integer.MIN_VALUE) return -1;
                offset += bigSmartLength(data, offset);
                if (field == 2) mapScene = value;
            }
            return mapScene;
        }
        return -1;
    }

    private static int readBigSmart(byte[] data, int offset) {
        if (offset >= data.length) return Integer.MIN_VALUE;
        if ((data[offset] & 0x80) != 0) {
            if (offset + 4 > data.length) return Integer.MIN_VALUE;
            return ((data[offset] & 0x7F) << 24)
                    | ((data[offset + 1] & 0xFF) << 16)
                    | ((data[offset + 2] & 0xFF) << 8)
                    | (data[offset + 3] & 0xFF);
        }
        if (offset + 2 > data.length) return Integer.MIN_VALUE;
        int value = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
        return value == 32767 ? -1 : value;
    }

    private static int bigSmartLength(byte[] data, int offset) {
        return offset < data.length && (data[offset] & 0x80) != 0 ? 4 : 2;
    }

    @Override
    public Optional<FloorDefinitionView> underlay(int id) {
        UnderlayType definition = underlays.get(id);
        if (definition == null) {
            return Optional.empty();
        }
        // OpenRune stores both the raw HSL hue and the derived weighted hue
        // used by the client’s radius-5 underlay blend. Keep those meanings
        // intact at the neutral boundary: weightedHue is the numerator and
        // chroma is the hue multiplier/denominator.
        return Optional.of(new FloorDefinitionView(id, -1, definition.getRgb(), definition.getRawHue(),
                definition.getSaturation(), definition.getLightness(), definition.getHue(),
                definition.getHueMultiplier()));
    }

    @Override
    public Optional<FloorDefinitionView> overlay(int id) {
        OverlayType definition = overlays.get(id);
        if (definition == null) {
            return Optional.empty();
        }
        return Optional.of(new FloorDefinitionView(id, definition.getTexture(), definition.getPrimaryRgb(),
                definition.getHue(), definition.getSaturation(), definition.getLightness(),
                definition.getSecondaryHue(), definition.getSecondarySaturation(),
                definition.getSecondaryRgb(), definition.getSecondaryHue(),
                definition.getSecondarySaturation(), definition.getSecondaryLightness()));
    }

    @Override
    public Optional<TextureDefinitionView> texture(int id) {
        TextureType definition = textures.get(id);
        if (definition == null) {
            return Optional.empty();
        }
        return Optional.of(new TextureDefinitionView(id, definition.isTransparent(), definition.getFileId(),
                -1, definition.getAverageRgb(), definition.getAnimationDirection(),
                definition.getAnimationSpeed(), definition.isLowDetail()));
    }

    /** Decodes model metadata lazily so opening a cache does not load every mesh. */
    @Override
    public synchronized Optional<ModelDefinitionView> model(int id) {
        if (id < 0) return Optional.empty();
        return modelViews.computeIfAbsent(id, key -> decodedModel(key).map(this::modelView));
    }

    @Override
    public synchronized Optional<ModelGeometryView> modelGeometry(int id) {
        if (id < 0) return Optional.empty();
        return modelGeometryViews.computeIfAbsent(id,
                key -> decodedModel(key).flatMap(this::geometryView));
    }

    private ModelDefinitionView modelView(ModelType model) {
        return new ModelDefinitionView(model.getId(), model.getVertexCount(),
                model.getTriangleCount(), model.getTextureTriangleCount(), model.getRenderPriority());
    }

    private Optional<ModelType> decodedModel(int id) {
        return models.computeIfAbsent(id, key -> Optional.ofNullable(modelDecoder.getModel(key)));
    }

    private Optional<ModelGeometryView> geometryView(ModelType model) {
        try {
            int[] verticesX = required(model.getVertexPositionsX());
            int[] verticesY = required(model.getVertexPositionsY());
            int[] verticesZ = required(model.getVertexPositionsZ());
            int[] triangleA = required(model.getTriangleVertex1());
            int[] triangleB = required(model.getTriangleVertex2());
            int[] triangleC = required(model.getTriangleVertex3());
            int vertexCount = model.getVertexCount();
            int triangleCount = model.getTriangleCount();
            if (verticesX.length != vertexCount || verticesY.length != vertexCount
                    || verticesZ.length != vertexCount || triangleA.length != triangleCount
                    || triangleB.length != triangleCount || triangleC.length != triangleCount) {
                return Optional.empty();
            }
            int[] vertices = new int[vertexCount * 3];
            for (int index = 0; index < vertexCount; index++) {
                int offset = index * 3;
                vertices[offset] = verticesX[index];
                vertices[offset + 1] = verticesY[index];
                vertices[offset + 2] = verticesZ[index];
            }
            int[] triangles = new int[triangleCount * 3];
            for (int index = 0; index < triangleCount; index++) {
                int offset = index * 3;
                triangles[offset] = triangleA[index];
                triangles[offset + 1] = triangleB[index];
                triangles[offset + 2] = triangleC[index];
            }
            short[] colors = model.getTriangleColors();
            int[] alphas = model.getTriangleAlphas();
            int[] textures = model.getTriangleTextures();
            return Optional.of(new ModelGeometryView(model.getId(), vertices, triangles,
                    colors, alphas, textures));
        } catch (RuntimeException ignored) {
            // A malformed or partially supported model remains browseable by
            // metadata but cannot be handed to a renderer as unsafe geometry.
            return Optional.empty();
        }
    }

    private static int[] required(int[] values) {
        if (values == null) throw new IllegalArgumentException("Missing model array");
        return values;
    }

    private static List<Integer> archiveIds(Cache cache, int index) {
        try {
            return java.util.Arrays.stream(cache.archives(index)).boxed().sorted().toList();
        } catch (RuntimeException ignored) {
            // Model archives are optional for definition-only or partial caches.
            return List.of();
        }
    }
}
