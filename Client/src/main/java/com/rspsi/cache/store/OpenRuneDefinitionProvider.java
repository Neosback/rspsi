package com.rspsi.cache.store;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.ObjectCollisionView;
import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.cache.definition.ModelDefinitionView;
import com.rspsi.cache.definition.TextureDefinitionView;
import com.rspsi.cache.definition.MapSceneSpriteView;
import dev.openrune.cache.filestore.definition.ModelDecoder;
import dev.openrune.cache.filestore.definition.SpriteDecoder;
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
    private final Map<Integer, Optional<ModelDefinitionView>> modelViews = new HashMap<>();
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
    public Optional<ObjectCollisionView> objectCollision(int id) {
        ObjectType definition = objects.get(id);
        if (definition == null) {
            return Optional.empty();
        }
        return Optional.of(new ObjectCollisionView(definition.getId(),
                Math.max(1, definition.getSizeX()), Math.max(1, definition.getSizeY()),
                Math.max(0, definition.getSolid()), definition.getImpenetrable(),
                definition.isHollow()));
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
     * The current OpenRune sprite decoder enumerates the sprite index, so
     * this remains an opt-in adapter surface until a targeted group decoder
     * can be used without eagerly decoding unrelated sprite archives.
     */
    private static Map<Integer, MapSceneSpriteView> loadMapScenes(Cache cache) {
        try {
            byte[] defaults = cache.data(17, 3, 0, null);
            int group = graphicsDefaultMapSceneGroup(defaults);
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
                java.awt.image.BufferedImage image = sprite.toBufferedImage();
                result.put(id, new MapSceneSpriteView(id, image.getWidth(), image.getHeight(),
                        sprite.getOffsetX(), sprite.getOffsetY(),
                        image.getRGB(0, 0, image.getWidth(), image.getHeight(), null,
                                0, image.getWidth())));
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
                definition.getSecondaryHue(), definition.getSecondarySaturation()));
    }

    @Override
    public Optional<TextureDefinitionView> texture(int id) {
        TextureType definition = textures.get(id);
        if (definition == null) {
            return Optional.empty();
        }
        return Optional.of(new TextureDefinitionView(id, definition.isTransparent(), definition.getFileId(),
                definition.getAverageRgb(), definition.getAnimationDirection(),
                definition.getAnimationSpeed(), definition.isLowDetail()));
    }

    /** Decodes model metadata lazily so opening a cache does not load every mesh. */
    @Override
    public synchronized Optional<ModelDefinitionView> model(int id) {
        if (id < 0) return Optional.empty();
        return modelViews.computeIfAbsent(id, this::decodeModelView);
    }

    private Optional<ModelDefinitionView> decodeModelView(int id) {
        ModelType model = modelDecoder.getModel(id);
        if (model == null) return Optional.empty();
        return Optional.of(new ModelDefinitionView(model.getId(), model.getVertexCount(),
                model.getTriangleCount(), model.getTextureTriangleCount(), model.getRenderPriority()));
    }
}
