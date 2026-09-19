package com.rspsi.editor.knowledge.cache;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ModelGeometryView;
import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.cache.definition.ObjectCollisionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.TextureDefinitionView;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Layer 1 Cache Facts Index: Authoritative, immutable indexed view of all cache assets.
 *
 * <p>Provides O(1) fact lookup, usage cross-references (which objects use model X, which
 * objects use texture Y), and fast prefix search without touching raw cache stores.</p>
 */
public final class CacheKnowledgeIndex {
    private final DefinitionProvider definitions;
    private final Map<Integer, ObjectKnowledge> objects = new ConcurrentHashMap<>();
    private final Map<Integer, ModelKnowledge> models = new ConcurrentHashMap<>();
    private final Map<Integer, TextureKnowledge> textures = new ConcurrentHashMap<>();
    private final Map<Integer, Set<Integer>> modelToObjectUsage = new ConcurrentHashMap<>();
    private final Map<Integer, Set<Integer>> textureToObjectUsage = new ConcurrentHashMap<>();

    public CacheKnowledgeIndex(DefinitionProvider definitions) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
    }

    /** Returns indexed object knowledge if available in the cache definitions. */
    public Optional<ObjectKnowledge> object(int objectId) {
        ObjectKnowledge existing = objects.get(objectId);
        if (existing != null) return Optional.of(existing);

        Optional<ObjectDefinitionView> def = definitions.object(objectId);
        if (def.isEmpty()) return Optional.empty();

        ObjectAppearanceView app = definitions.objectAppearance(objectId).orElse(null);
        ObjectCollisionView col = definitions.objectCollision(objectId).orElse(null);
        ObjectKnowledge created = ObjectKnowledge.from(def.get(), app, col);
        objects.put(objectId, created);

        for (int modelId : created.modelIds()) {
            modelToObjectUsage.computeIfAbsent(modelId, k -> ConcurrentHashMap.newKeySet()).add(objectId);
        }
        for (int texId : created.retextures().values()) {
            textureToObjectUsage.computeIfAbsent(texId, k -> ConcurrentHashMap.newKeySet()).add(objectId);
        }

        return Optional.of(created);
    }

    /** Returns indexed model geometric knowledge if available. */
    public Optional<ModelKnowledge> model(int modelId) {
        ModelKnowledge existing = models.get(modelId);
        if (existing != null) return Optional.of(existing);

        Optional<ModelGeometryView> geo = definitions.modelGeometry(modelId);
        if (geo.isEmpty()) return Optional.empty();

        ModelKnowledge created = ModelKnowledge.from(geo.get());
        models.put(modelId, created);
        return Optional.of(created);
    }

    /** Returns indexed texture knowledge if available. */
    public Optional<TextureKnowledge> texture(int textureId) {
        TextureKnowledge existing = textures.get(textureId);
        if (existing != null) return Optional.of(existing);

        Optional<TextureDefinitionView> tex = definitions.texture(textureId);
        if (tex.isEmpty()) return Optional.empty();

        TextureKnowledge created = TextureKnowledge.from(textureId, tex.get());
        textures.put(textureId, created);
        return Optional.of(created);
    }

    /** Returns all object IDs known to use the specified model. */
    public Set<Integer> objectsUsingModel(int modelId) {
        Set<Integer> set = modelToObjectUsage.get(modelId);
        return set == null ? Set.of() : Collections.unmodifiableSet(set);
    }

    /** Returns all object IDs known to use the specified texture. */
    public Set<Integer> objectsUsingTexture(int textureId) {
        Set<Integer> set = textureToObjectUsage.get(textureId);
        return set == null ? Set.of() : Collections.unmodifiableSet(set);
    }
}
