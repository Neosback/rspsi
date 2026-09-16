package com.rspsi.cache.store;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.ObjectCollisionView;
import com.rspsi.cache.definition.ModelDefinitionView;
import com.rspsi.cache.definition.TextureDefinitionView;
import dev.openrune.cache.filestore.definition.ModelDecoder;
import dev.openrune.definition.type.model.ModelType;
import dev.openrune.OsrsCacheProvider;
import dev.openrune.definition.type.ObjectType;
import dev.openrune.definition.type.OverlayType;
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
                interactions, modelIds));
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
    public Optional<FloorDefinitionView> underlay(int id) {
        UnderlayType definition = underlays.get(id);
        if (definition == null) {
            return Optional.empty();
        }
        return Optional.of(new FloorDefinitionView(id, -1, definition.getRgb(), definition.getHue(),
                definition.getSaturation(), definition.getLightness(), definition.getHueMultiplier(), 0));
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
