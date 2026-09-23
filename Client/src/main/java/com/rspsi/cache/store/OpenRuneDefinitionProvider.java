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
import com.rspsi.cache.definition.MapElementDefinitionView;
import com.rspsi.cache.definition.SequenceDefinitionView;
import com.rspsi.cache.definition.AnimationFrameView;
import com.rspsi.cache.definition.SkeletonDefinitionView;
import com.rspsi.cache.OsrsCacheIndexLayout;
import dev.openrune.cache.filestore.definition.ModelDecoder;
import dev.openrune.cache.filestore.definition.SpriteDecoder;
import static dev.openrune.cache.ArchiveIndexKt.MODELS;
import static dev.openrune.cache.ArchiveIndexKt.CONFIGS;
import static dev.openrune.cache.ConfigTypeKt.SEQUENCE;
import static dev.openrune.cache.ConfigTypeKt.MAP_ELEMENT;
import dev.openrune.definition.game.IndexedSprite;
import dev.openrune.definition.type.model.ModelType;
import dev.openrune.definition.game.render.model.FaceNormal;
import dev.openrune.definition.game.render.model.VertexNormal;
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
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenRune definition adapter. OpenRune objects are decoded once into maps,
 * then reduced to RSPSi-owned views before they reach editor code.
 */
public final class OpenRuneDefinitionProvider implements DefinitionProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenRuneDefinitionProvider.class);
    private static final int OSRS_SEQUENCE_REVISION = 226;
    private final Cache cache;
    private final int revision;
    private final Map<Integer, ObjectType> objects = new HashMap<>();
    private final Map<Integer, UnderlayType> underlays = new HashMap<>();
    private final Map<Integer, OverlayType> overlays = new HashMap<>();
    private final Map<Integer, TextureType> textures = new HashMap<>();
    private final java.util.List<DecodeFailure> decodeFailures =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    private volatile Map<Integer, SpriteType> textureSprites;
    private final ModelDecoder modelDecoder;
    private final List<Integer> modelIds;
    private final Map<Integer, Optional<ModelType>> models = new HashMap<>();
    private final Map<Integer, Optional<ModelDefinitionView>> modelViews = new HashMap<>();
    private final Map<Integer, Optional<ModelGeometryView>> modelGeometryViews = new HashMap<>();
    private final Map<Integer, MapSceneSpriteView> mapScenes;
    private final List<Integer> sequenceIds;
    private final List<Integer> mapElementIds;
    private final Map<Integer, Optional<SequenceDefinitionView>> sequences = new HashMap<>();
    private final Map<Integer, Optional<AnimationFrameView>> animationFrames = new HashMap<>();
    private final Map<Integer, Optional<SkeletonDefinitionView>> skeletons = new HashMap<>();
    private final Map<Integer, Optional<MapElementDefinitionView>> mapElements = new HashMap<>();

    private OpenRuneDefinitionProvider(Cache cache, int revision) {
        this.cache = Objects.requireNonNull(cache, "cache");
        if (revision <= 0) {
            throw new IllegalArgumentException("OSRS cache revision must be positive");
        }
        this.revision = revision;
        decodeEager("object", () -> new OsrsCacheProvider.ObjectDecoder(revision).load(cache, objects));
        decodeEager("underlay", () -> new OsrsCacheProvider.UnderlayDecoder().load(cache, underlays));
        decodeEager("overlay", () -> new OsrsCacheProvider.OverlayDecoder().load(cache, overlays));
        OpenRuneTextureDefinitionDecoder.DecodeResult textureResult =
                OpenRuneTextureDefinitionDecoder.decode(cache, revision);
        textures.putAll(textureResult.definitions());
        LOGGER.info("Texture definitions decoded: {} / {} (skipped {})",
                textureResult.definitions().size(), textureResult.scanned(), textureResult.skipped());
        if (!textureResult.failures().isEmpty()) {
            LOGGER.warn("Texture definition diagnostics: {}", textureResult.failures());
        }
        modelDecoder = new ModelDecoder(cache, java.util.Collections.emptyList());
        modelIds = archiveIds(cache, MODELS);
        mapScenes = loadMapScenes(cache);
        sequenceIds = archiveFileIds(cache, SEQUENCE);
        mapElementIds = archiveFileIds(cache, MAP_ELEMENT);
    }

    public static OpenRuneDefinitionProvider load(Cache cache, int revision) {
        return new OpenRuneDefinitionProvider(cache, revision);
    }

    /** Records one eager-decode family failure instead of losing it silently. */
    private void decodeEager(String family, Runnable decode) {
        try {
            decode.run();
        } catch (RuntimeException failure) {
            decodeFailures.add(new DecodeFailure(family, -1,
                    failure.getClass().getSimpleName() + ": " + failure.getMessage()));
        }
    }

    @Override
    public Optional<ObjectDefinitionView> object(int id) {
        ObjectType definition = objects.get(id);
        return definition == null ? Optional.empty() : Optional.of(toView(definition));
    }

    static ObjectDefinitionView toView(ObjectType definition) {
        List<String> interactions = java.util.stream.IntStream.range(0, 5)
                .mapToObj(index -> definition.getActions() == null
                        ? null : definition.getActions().getOpOrNull(index))
                .filter(Objects::nonNull)
                .toList();
        int[] modelIds = definition.getObjectModels() == null
                ? new int[0]
                : definition.getObjectModels().stream().mapToInt(Integer::intValue).toArray();
        int[] modelTypes = definition.getObjectTypes() == null
                ? new int[0]
                : definition.getObjectTypes().stream().mapToInt(Integer::intValue).toArray();
        boolean interactive = definition.getInteractive() > 0;
        if (definition.getInteractive() == -1) {
            java.util.List<Integer> objectTypes = definition.getObjectTypes();
            boolean modelDefaultsToInteractive = definition.getObjectModels() != null
                    && (objectTypes == null
                    || (!objectTypes.isEmpty() && objectTypes.get(0) == 10));
            interactive = modelDefaultsToInteractive || !interactions.isEmpty();
        }
        int varbit = definition.getMultiVarBit();
        int varp = definition.getMultiVarp();
        int defaultTransform = definition.getMultiDefault();
        int[] transforms = definition.getTransforms() == null
                ? new int[0]
                : definition.getTransforms().stream().mapToInt(Integer::intValue).toArray();

        return new ObjectDefinitionView(definition.getId(), definition.getName(),
                Math.max(1, definition.getSizeX()), Math.max(1, definition.getSizeY()),
                interactions, modelIds, modelTypes, definition.getMapSceneID(), interactive,
                varbit, varp, transforms, defaultTransform);
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
    public synchronized Optional<SequenceDefinitionView> sequence(int id) {
        if (id < 0 || !sequenceIds.contains(id)) return Optional.empty();
        return sequences.computeIfAbsent(id, this::decodeSequence);
    }

    @Override
    public List<Integer> sequenceIds() {
        return sequenceIds;
    }

    @Override
    public synchronized Optional<MapElementDefinitionView> mapElement(int id) {
        if (id < 0 || !mapElementIds.contains(id)) return Optional.empty();
        return mapElements.computeIfAbsent(id, this::decodeMapElement);
    }

    @Override
    public List<Integer> mapElementIds() {
        return mapElementIds;
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
        return definition == null ? Optional.empty() : Optional.of(toAppearanceView(definition));
    }

    static ObjectAppearanceView toAppearanceView(ObjectType definition) {
        return new ObjectAppearanceView(
                definition.getAnimationId(), false,
                Math.max(1, definition.getModelSizeX()), Math.max(1, definition.getModelSizeY()),
                Math.max(1, definition.getModelSizeZ()), definition.getOffsetX(),
                definition.getOffsetY(), definition.getOffsetZ(),
                ObjectAppearanceView.pairs(toArray(definition.getOriginalColours()),
                        toArray(definition.getModifiedColours())),
                ObjectAppearanceView.pairs(toArray(definition.getOriginalTextureColours()),
                        toArray(definition.getModifiedTextureColours())),
                // Opcode 64 clears clipped, meaning no shadow (default true).
                definition.getClipped(),
                // Opcode 23 sets modelClipped, meaning occludes (default false).
                definition.getModelClipped(),
                // Opcode 22 is nonFlatShading, which is also the opt-in for mergeNormals (default false).
                definition.getNonFlatShading(), definition.getNonFlatShading(),
                definition.getAmbient(),
                // FileStore decodes raw opcode 39 byte; client scale is raw * 25.
                definition.getContrast() * 25,
                definition.getDecorDisplacement(),
                contourGroundType(definition.getClipType()),
                contourGroundParameter(definition.getClipType()),
                definition.getModelClipped(), definition.isRotated(),
                definition.getObstructive(), Math.max(0, definition.getClipMask()),
                definition.getRandomizeAnimStart(), definition.getDelayAnimationUpdate());
    }

    /**
     * Maps the cache clipType to the neutral contour mode, mirroring the
     * client render gate {@code clipType * 65536 >= 0}: the sentinel -1 (and
     * any value whose scaled parameter overflows negative) leaves the model
     * un-contoured, 0 requests full ground attachment, and positive values
     * request the partial contour with parameter clipType * 65536.
     */
    static int contourGroundType(int clipType) {
        return clipType * 65536 >= 0 && clipType != -1 ? 1 : -1;
    }

    /** Scaled partial-contour parameter, matching the client's int overflow. */
    static int contourGroundParameter(int clipType) {
        return contourGroundType(clipType) < 0 ? 0 : clipType * 65536;
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
                definition.getAverageRgb(), -1, definition.getAnimationDirection(),
                definition.getAnimationSpeed(), definition.isLowDetail()));
    }

    @Override
    public synchronized Optional<int[]> texturePixels(int id, double brightness, int textureSize) {
        // The pinned 3.0.2 artifact exposes the client-compatible 128px,
        // BRIGHTNESS_MAX texture path only. Do not pretend a requested
        // alternative gamma/size was honored at the neutral boundary.
        if (id < 0 || !Double.isFinite(brightness) || Math.abs(brightness - 0.6) > 0.0001
                || textureSize != 128) {
            return Optional.empty();
        }
        TextureType definition = textures.get(id);
        if (definition == null) return Optional.empty();
        // Do not use TextureType.load(sprites): that compatibility overload
        // defaults to brightness 0 and the library's default texture size.
        // The native renderer's contract is the client-compatible 0.6/128
        // decode used by the revision-240 texture path.
        int[] pixels = definition.load(textureSprites(), brightness, textureSize);
        return pixels == null ? Optional.empty() : Optional.of(pixels.clone());
    }

    private Map<Integer, SpriteType> textureSprites() {
        Map<Integer, SpriteType> current = textureSprites;
        if (current != null) return current;
        synchronized (this) {
            current = textureSprites;
            if (current == null) {
                Map<Integer, SpriteType> decoded = new HashMap<>();
                try {
                    new SpriteDecoder().load(cache, decoded);
                } catch (RuntimeException ignored) {
                    decoded.clear();
                }
                current = Map.copyOf(decoded);
                textureSprites = current;
            }
        }
        return current;
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
            int[] triangleSkins = model.getTriangleSkins();
            int[] textures = model.getTriangleTextures();
            int[] renderTypes = model.getTriangleRenderTypes();
            int[] renderPriorities = model.getTriangleRenderPriorities();
            int[] depthBias = unsignedBytes(model.getFaceZOffsets());
            int[] textureCoordinates = model.getTextureCoordinates();
            int[] textureTriangles = flattenTextureTriangles(model);
            int[] textureRenderTypes = model.getTextureRenderTypes();
            int[] textureScaleX = model.getTextureScaleX();
            int[] textureScaleY = model.getTextureScaleY();
            int[] textureScaleZ = model.getTextureScaleZ();
            int[] textureRotations = model.getTextureRotation();
            int[] textureDirections = model.getTextureDirection();
            int[] textureSpeeds = model.getTextureSpeed();
            int[] textureTranslationsU = model.getTextureTransU();
            int[] textureTranslationsV = model.getTextureTransV();
            int[] vertexSkins = model.getVertexSkins();
            model.computeNormals();
            int[] vertexNormals = flattenVertexNormals(model.getVertexNormals());
            int[] faceNormals = flattenFaceNormals(model.getFaceNormals());
            return Optional.of(new ModelGeometryView(model.getId(), vertices, triangles,
                    colors, alphas, textures, renderTypes, renderPriorities,
                    textureCoordinates, textureTriangles, textureRenderTypes, textureScaleX,
                    textureScaleY, textureScaleZ, textureRotations, textureDirections,
                    textureSpeeds, textureTranslationsU, textureTranslationsV, vertexSkins,
                    vertexNormals, faceNormals, triangleSkins, depthBias));
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

    /** Preserves the raw cache byte used by RuneLite as Model.faceBias. */
    private static int[] unsignedBytes(byte[] values) {
        if (values == null || values.length == 0) return new int[0];
        int[] result = new int[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = values[index] & 0xFF;
        }
        return result;
    }

    private static int[] flattenTextureTriangles(ModelType model) {
        int count = model.getTextureTriangleCount();
        int[] first = model.getTextureTriangleVertex1();
        int[] second = model.getTextureTriangleVertex2();
        int[] third = model.getTextureTriangleVertex3();
        if (count <= 0 || first == null || second == null || third == null
                || first.length < count || second.length < count || third.length < count) {
            return new int[0];
        }
        int[] result = new int[count * 3];
        for (int index = 0; index < count; index++) {
            int offset = index * 3;
            result[offset] = first[index];
            result[offset + 1] = second[index];
            result[offset + 2] = third[index];
        }
        return result;
    }

    private static int[] flattenVertexNormals(VertexNormal[] normals) {
        if (normals == null || normals.length == 0) return new int[0];
        int[] result = new int[normals.length * 4];
        for (int index = 0; index < normals.length; index++) {
            VertexNormal normal = normals[index];
            int offset = index * 4;
            result[offset] = normal.getX();
            result[offset + 1] = normal.getY();
            result[offset + 2] = normal.getZ();
            result[offset + 3] = normal.getMagnitude();
        }
        return result;
    }

    private static int[] flattenFaceNormals(FaceNormal[] normals) {
        if (normals == null || normals.length == 0) return new int[0];
        int[] result = new int[normals.length * 3];
        for (int index = 0; index < normals.length; index++) {
            FaceNormal normal = normals[index];
            int offset = index * 3;
            result[offset] = normal.getX();
            result[offset + 1] = normal.getY();
            result[offset + 2] = normal.getZ();
        }
        return result;
    }

    private static List<Integer> archiveIds(Cache cache, int index) {
        try {
            return java.util.Arrays.stream(cache.archives(index)).boxed().sorted().toList();
        } catch (RuntimeException ignored) {
            // Model archives are optional for definition-only or partial caches.
            return List.of();
        }
    }

    private static List<Integer> archiveFileIds(Cache cache, int archive) {
        try {
            return java.util.Arrays.stream(cache.files(CONFIGS, archive)).boxed().sorted().toList();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private Optional<SequenceDefinitionView> decodeSequence(int id) {
        byte[] data;
        try {
            data = cache.data(CONFIGS, SEQUENCE, id, null);
        } catch (RuntimeException failure) {
            recordFailure("sequence", id, failure);
            return Optional.empty();
        }
        if (data == null) return Optional.empty();

        try {
            ByteCursor cursor = new ByteCursor(data);
            int[] frameIds = new int[0];
            int[] frameLengths = new int[0];
            int frameStep = -1;
            boolean stretches = false;
            int leftHandItem = -1;
            int rightHandItem = -1;
            int maxLoops = 99;
            int precedenceAnimating = -1;
            int priority = -1;
            int replyMode = 2;
            int skeletalId = -1;
            int animationHeightOffset = 0;
            while (cursor.remaining() > 0) {
                int opcode = cursor.readUnsignedByte();
                if (opcode == 0) break;
                switch (opcode) {
                    case 1 -> {
                        int count = cursor.readUnsignedShort();
                        frameLengths = new int[count];
                        frameIds = new int[count];
                        for (int index = 0; index < count; index++) {
                            frameLengths[index] = cursor.readUnsignedShort();
                        }
                        for (int index = 0; index < count; index++) {
                            frameIds[index] = cursor.readUnsignedShort();
                        }
                        for (int index = 0; index < count; index++) {
                            frameIds[index] |= cursor.readUnsignedShort() << 16;
                        }
                    }
                    case 2 -> frameStep = cursor.readUnsignedShort();
                    case 3 -> cursor.skip(cursor.readUnsignedByte());
                    case 4 -> stretches = true;
                    case 5 -> cursor.skip(1);
                    case 6 -> leftHandItem = cursor.readUnsignedShort();
                    case 7 -> rightHandItem = cursor.readUnsignedShort();
                    case 8 -> maxLoops = cursor.readUnsignedByte();
                    case 9 -> precedenceAnimating = cursor.readUnsignedByte();
                    case 10 -> priority = cursor.readUnsignedByte();
                    case 11 -> replyMode = cursor.readUnsignedByte();
                    case 12 -> {
                        int count = cursor.readUnsignedByte();
                        cursor.skip(count * 4);
                    }
                    case 13 -> {
                        // OSRS revision 226+ stores a skeletal ID here.
                        if (revision >= OSRS_SEQUENCE_REVISION) skeletalId = cursor.readInt();
                        else skipFrameSounds(cursor);
                    }
                    case 14 -> {
                        if (revision >= OSRS_SEQUENCE_REVISION) skipSparseFrameSounds(cursor, true);
                        else skeletalId = cursor.readInt();
                    }
                    case 15 -> {
                        if (revision >= OSRS_SEQUENCE_REVISION) cursor.skip(4);
                        else skipSparseFrameSounds(cursor, false);
                    }
                    case 16 -> {
                        if (revision < OSRS_SEQUENCE_REVISION) cursor.skip(4);
                        else if (revision >= 233) animationHeightOffset = cursor.readByte();
                    }
                    case 17 -> cursor.skip(cursor.readUnsignedByte());
                    case 18 -> cursor.readString();
                    case 19 -> { }
                    case 20 -> {
                        cursor.skip(1);
                        cursor.skip(4);
                    }
                    default -> throw new IllegalArgumentException("Unsupported sequence opcode " + opcode);
                }
            }
            return Optional.of(new SequenceDefinitionView(id, frameIds, frameLengths,
                    frameStep, stretches, normalizeSentinel(leftHandItem),
                    normalizeSentinel(rightHandItem), maxLoops,
                    normalizeSentinel(precedenceAnimating), normalizeSentinel(priority),
                    replyMode, skeletalId, animationHeightOffset));
        } catch (RuntimeException failure) {
            recordFailure("sequence", id, failure);
            return Optional.empty();
        }
    }

    @Override
    public synchronized Optional<AnimationFrameView> animationFrame(int id) {
        if (id < 0) return Optional.empty();
        return animationFrames.computeIfAbsent(id, this::decodeAnimationFrame);
    }

    @Override
    public synchronized Optional<SkeletonDefinitionView> skeleton(int id) {
        if (id < 0) return Optional.empty();
        return skeletons.computeIfAbsent(id, this::decodeSkeleton);
    }

    private Optional<SkeletonDefinitionView> decodeSkeleton(int id) {
        byte[] data;
        try {
            data = cache.data(OsrsCacheIndexLayout.SKELETONS, id, 0, null);
        } catch (RuntimeException failure) {
            recordFailure("skeleton", id, failure);
            return Optional.empty();
        }
        if (data == null) return Optional.empty();
        try {
            ByteCursor cursor = new ByteCursor(data);
            int count = cursor.readUnsignedByte();
            int[] types = new int[count];
            for (int index = 0; index < count; index++) types[index] = cursor.readUnsignedByte();
            int[][] labels = new int[count][];
            for (int index = 0; index < count; index++) {
                int length = cursor.readUnsignedByte();
                labels[index] = new int[length];
                for (int label = 0; label < length; label++) {
                    labels[index][label] = cursor.readUnsignedByte();
                }
            }
            return Optional.of(new SkeletonDefinitionView(id, types, labels));
        } catch (RuntimeException failure) {
            recordFailure("skeleton", id, failure);
            return Optional.empty();
        }
    }

    /** Decodes the legacy OSRS frame format used by index 0 animation archives. */
    private Optional<AnimationFrameView> decodeAnimationFrame(int id) {
        int archive = id >>> 16;
        int file = id & 0xFFFF;
        byte[] data;
        try {
            data = cache.data(OsrsCacheIndexLayout.ANIMATIONS, archive, file, null);
        } catch (RuntimeException failure) {
            recordFailure("animation frame", id, failure);
            return Optional.empty();
        }
        if (data == null || data.length < 3) return Optional.empty();
        try {
            ByteCursor header = new ByteCursor(data);
            int skeletonId = header.readUnsignedShort();
            int count = header.readUnsignedByte();
            if (count > header.remaining()) throw new IllegalArgumentException("Invalid frame opcode count");
            byte[] opcodes = new byte[count];
            for (int index = 0; index < count; index++) opcodes[index] = (byte) header.readUnsignedByte();
            SkeletonDefinitionView skeleton = skeleton(skeletonId).orElseThrow(
                    () -> new IllegalArgumentException("Missing skeleton " + skeletonId));
            int[] types = skeleton.transformTypes();
            int[][] labels = skeleton.labels();
            if (types.length < count) throw new IllegalArgumentException("Frame exceeds skeleton transform count");

            ByteCursor values = new ByteCursor(data, header.position());
            java.util.ArrayList<Integer> indices = new java.util.ArrayList<>();
            java.util.ArrayList<Integer> x = new java.util.ArrayList<>();
            java.util.ArrayList<Integer> y = new java.util.ArrayList<>();
            java.util.ArrayList<Integer> z = new java.util.ArrayList<>();
            int last = -1;
            boolean showing = false;
            for (int index = 0; index < count; index++) {
                int opcode = opcodes[index] & 0xFF;
                if (opcode == 0) continue;
                if (types[index] != 0) {
                    for (int previous = index - 1; previous > last; previous--) {
                        if (types[previous] == 0) {
                            indices.add(previous); x.add(0); y.add(0); z.add(0);
                        }
                    }
                }
                int defaultValue = types[index] == 3 ? 128 : 0;
                indices.add(index);
                x.add((opcode & 1) != 0 ? values.readShortSmart() : defaultValue);
                y.add((opcode & 2) != 0 ? values.readShortSmart() : defaultValue);
                z.add((opcode & 4) != 0 ? values.readShortSmart() : defaultValue);
                showing |= types[index] == 5;
                last = index;
            }
            return Optional.of(new AnimationFrameView(id, skeletonId,
                    toIntArray(indices), toIntArray(x), toIntArray(y), toIntArray(z), showing));
        } catch (RuntimeException failure) {
            recordFailure("animation frame", id, failure);
            return Optional.empty();
        }
    }

    private static int[] toIntArray(java.util.List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).toArray();
    }

    private void recordFailure(String family, int id, RuntimeException failure) {
        decodeFailures.add(new DecodeFailure(family, id,
                failure.getClass().getSimpleName() + ": " + failure.getMessage()));
    }

    private Optional<MapElementDefinitionView> decodeMapElement(int id) {
        byte[] data;
        try {
            data = cache.data(CONFIGS, MAP_ELEMENT, id, null);
        } catch (RuntimeException failure) {
            recordFailure("map element", id, failure);
            return Optional.empty();
        }
        if (data == null) return Optional.empty();

        try {
            ByteCursor cursor = new ByteCursor(data);
            int spriteId = -1;
            int hoverSpriteId = -1;
            String name = "";
            int textColor = 0;
            int hoverTextColor = 0;
            int textSize = 0;
            boolean worldMapVisible = true;
            boolean minimapVisible = false;
            boolean randomizePosition = true;
            List<String> actions = new java.util.ArrayList<>();
            while (cursor.remaining() > 0) {
                int opcode = cursor.readUnsignedByte();
                if (opcode == 0) break;
                switch (opcode) {
                    case 1 -> spriteId = cursor.readBigSmart();
                    case 2 -> hoverSpriteId = cursor.readBigSmart();
                    case 3 -> name = cursor.readString();
                    case 4 -> textColor = cursor.readMedium();
                    case 5 -> hoverTextColor = cursor.readMedium();
                    case 6 -> textSize = cursor.readUnsignedByte();
                    case 7 -> {
                        int flags = cursor.readUnsignedByte();
                        worldMapVisible = (flags & 1) != 0;
                        minimapVisible = (flags & 2) != 0;
                    }
                    case 8 -> randomizePosition = cursor.readUnsignedByte() == 1;
                    case 9 -> cursor.skip(12);
                    case 10, 11, 12, 13, 14 -> actions.add(cursor.readString());
                    case 15 -> skipMapElementPolygon(cursor);
                    case 16 -> { }
                    case 17 -> cursor.readString();
                    case 18 -> cursor.readBigSmart();
                    case 19 -> cursor.skip(2);
                    case 20 -> cursor.skip(12);
                    case 21, 22 -> cursor.skip(4);
                    case 23 -> cursor.skip(3);
                    case 24 -> cursor.skip(4);
                    case 25 -> cursor.readBigSmart();
                    case 28, 29, 30 -> cursor.skip(1);
                    case 249 -> skipParams(cursor);
                    default -> throw new IllegalArgumentException("Unsupported map element opcode " + opcode);
                }
            }
            return Optional.of(new MapElementDefinitionView(id, spriteId, hoverSpriteId, name,
                    textColor, hoverTextColor, textSize, worldMapVisible,
                    minimapVisible, randomizePosition, actions));
        } catch (RuntimeException failure) {
            recordFailure("map element", id, failure);
            return Optional.empty();
        }
    }

    private static int normalizeSentinel(int value) {
        return value == 65535 ? -1 : value;
    }

    private static void skipFrameSounds(ByteCursor cursor) {
        int count = cursor.readUnsignedByte();
        for (int index = 0; index < count; index++) {
            cursor.skip(3);
        }
    }

    private static void skipSparseFrameSounds(ByteCursor cursor, boolean hasUnknown) {
        int count = cursor.readUnsignedShort();
        for (int index = 0; index < count; index++) {
            cursor.skip(2);
            cursor.skip(hasUnknown ? 6 : 5);
        }
    }

    private static void skipMapElementPolygon(ByteCursor cursor) {
        int count = cursor.readUnsignedByte();
        cursor.skip(count * 4);
        cursor.skip(4);
        int secondaryCount = cursor.readUnsignedByte();
        cursor.skip(secondaryCount * 4);
        cursor.skip(count);
    }

    @Override
    public List<DecodeFailure> decodeFailures() {
        synchronized (decodeFailures) {
            return List.copyOf(decodeFailures);
        }
    }

    private static void skipParams(ByteCursor cursor) {
        int count = cursor.readUnsignedByte();
        for (int index = 0; index < count; index++) {
            boolean string = cursor.readUnsignedByte() == 1;
            cursor.skip(3);
            if (string) cursor.readString();
            else cursor.skip(4);
        }
    }

    private static final class ByteCursor {
        private final byte[] data;
        private int offset;

        private ByteCursor(byte[] data) {
            this.data = data;
        }

        private ByteCursor(byte[] data, int offset) {
            this.data = data;
            if (offset < 0 || offset > data.length) throw new IllegalArgumentException("Invalid cursor offset");
            this.offset = offset;
        }

        private int position() {
            return offset;
        }

        private int remaining() {
            return data.length - offset;
        }

        private void require(int count) {
            if (count < 0 || remaining() < count) {
                throw new IllegalArgumentException("Definition payload ended unexpectedly");
            }
        }

        private int readUnsignedByte() {
            require(1);
            return data[offset++] & 0xFF;
        }

        private int readByte() {
            require(1);
            return data[offset++];
        }

        private int readUnsignedShort() {
            return (readUnsignedByte() << 8) | readUnsignedByte();
        }

        private int readShortSmart() {
            require(1);
            return data[offset] < 0 ? readUnsignedShort() - 49152 : readUnsignedByte() - 64;
        }

        private int readMedium() {
            return (readUnsignedByte() << 16) | (readUnsignedByte() << 8) | readUnsignedByte();
        }

        private int readInt() {
            return (readUnsignedByte() << 24) | (readUnsignedByte() << 16)
                    | (readUnsignedByte() << 8) | readUnsignedByte();
        }

        private int readBigSmart() {
            require(1);
            if ((data[offset] & 0x80) != 0) return readInt() & 0x7FFFFFFF;
            int value = readUnsignedShort();
            return value == 32767 ? -1 : value;
        }

        private String readString() {
            int start = offset;
            while (offset < data.length && data[offset] != 0) offset++;
            require(1);
            String value = new String(data, start, offset - start, StandardCharsets.ISO_8859_1);
            offset++;
            return value;
        }

        private void skip(int count) {
            require(count);
            offset += count;
        }
    }
}
