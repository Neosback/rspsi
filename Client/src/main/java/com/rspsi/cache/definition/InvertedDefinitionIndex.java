package com.rspsi.cache.definition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable inverted index over editor-facing definitions.
 *
 * <p>Object names/actions/model references and floor RGB/texture metadata are
 * decoded once and then served from compact ID sets. Transform targets are
 * indexed in both directions for fast varbit/multiloc navigation.</p>
 */
public final class InvertedDefinitionIndex {
    private final DefinitionProvider definitions;
    private final Map<String, Set<Integer>> objectNameTokens;
    private final Map<String, Set<Integer>> objectActions;
    private final Map<Integer, Set<Integer>> objectsByModel;
    private final Set<Integer> interactiveObjects;
    private final Map<Integer, Set<Integer>> transformsTo;
    private final Map<Integer, Set<Integer>> underlaysByRgb;
    private final Map<Integer, Set<Integer>> overlaysByRgb;
    private final Map<Integer, Set<Integer>> underlaysByTexture;
    private final Map<Integer, Set<Integer>> overlaysByTexture;

    public InvertedDefinitionIndex(DefinitionProvider definitions) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");

        Map<String, Set<Integer>> names = new LinkedHashMap<>();
        Map<String, Set<Integer>> actions = new LinkedHashMap<>();
        Map<Integer, Set<Integer>> models = new LinkedHashMap<>();
        Set<Integer> interactive = new LinkedHashSet<>();
        Map<Integer, Set<Integer>> reverseTransforms = new LinkedHashMap<>();

        for (int id : definitions.objectIds()) {
            ObjectDefinitionView object = definitions.object(id).orElse(null);
            if (object == null) continue;
            indexText(names, id, object.name());
            for (String action : object.interactions()) indexText(actions, id, action);
            for (int modelId : object.modelIds()) add(models, modelId, id);
            if (object.interactive()) interactive.add(id);
            for (int target : object.transforms()) {
                if (target >= 0) add(reverseTransforms, target, id);
            }
            if (object.defaultTransform() >= 0) {
                add(reverseTransforms, object.defaultTransform(), id);
            }
        }

        Map<Integer, Set<Integer>> underlayRgb = new LinkedHashMap<>();
        Map<Integer, Set<Integer>> overlayRgb = new LinkedHashMap<>();
        Map<Integer, Set<Integer>> underlayTexture = new LinkedHashMap<>();
        Map<Integer, Set<Integer>> overlayTexture = new LinkedHashMap<>();
        for (int id : definitions.underlayIds()) {
            definitions.underlay(id).ifPresent(floor -> {
                add(underlayRgb, floor.rgb() & 0xFFFFFF, id);
                if (floor.texture() >= 0) add(underlayTexture, floor.texture(), id);
            });
        }
        for (int id : definitions.overlayIds()) {
            definitions.overlay(id).ifPresent(floor -> {
                add(overlayRgb, floor.rgb() & 0xFFFFFF, id);
                if (floor.secondaryRgb() >= 0) add(overlayRgb, floor.secondaryRgb() & 0xFFFFFF, id);
                if (floor.texture() >= 0) add(overlayTexture, floor.texture(), id);
            });
        }

        objectNameTokens = freeze(names);
        objectActions = freeze(actions);
        objectsByModel = freeze(models);
        interactiveObjects = Set.copyOf(interactive);
        transformsTo = freeze(reverseTransforms);
        underlaysByRgb = freeze(underlayRgb);
        overlaysByRgb = freeze(overlayRgb);
        underlaysByTexture = freeze(underlayTexture);
        overlaysByTexture = freeze(overlayTexture);
    }

    /** Token/phrase search across object names and actions. */
    public Set<Integer> searchObjects(String query) {
        String normalized = normalize(query);
        if (normalized.isEmpty()) return Set.copyOf(definitions.objectIds());
        Set<Integer> result = new LinkedHashSet<>();
        for (String token : tokens(normalized)) {
            result.addAll(objectNameTokens.getOrDefault(token, Set.of()));
            result.addAll(objectActions.getOrDefault(token, Set.of()));
        }
        // Also support exact numeric navigation without scanning definitions.
        try {
            int id = Integer.parseInt(normalized);
            if (definitions.object(id).isPresent()) result.add(id);
        } catch (NumberFormatException ignored) {
        }
        return Set.copyOf(result);
    }

    public Set<Integer> objectsNamed(String token) {
        return objectNameTokens.getOrDefault(normalize(token), Set.of());
    }

    public Set<Integer> objectsWithAction(String action) {
        return objectActions.getOrDefault(normalize(action), Set.of());
    }

    public Set<Integer> objectsUsingModel(int modelId) {
        return objectsByModel.getOrDefault(modelId, Set.of());
    }

    public Set<Integer> interactiveObjects() { return interactiveObjects; }

    /** Objects whose transform table can resolve to targetObjectId. */
    public Set<Integer> transformParents(int targetObjectId) {
        return transformsTo.getOrDefault(targetObjectId, Set.of());
    }

    /** Direct transform destinations for one object, including its default. */
    public List<Integer> transformLinks(int objectId) {
        ObjectDefinitionView object = definitions.object(objectId).orElse(null);
        if (object == null) return List.of();
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        Arrays.stream(object.transforms()).filter(value -> value >= 0).forEach(result::add);
        if (object.defaultTransform() >= 0) result.add(object.defaultTransform());
        return List.copyOf(result);
    }

    public Set<Integer> underlaysByRgb(int rgb) {
        return underlaysByRgb.getOrDefault(rgb & 0xFFFFFF, Set.of());
    }

    public Set<Integer> overlaysByRgb(int rgb) {
        return overlaysByRgb.getOrDefault(rgb & 0xFFFFFF, Set.of());
    }

    public Set<Integer> underlaysUsingTexture(int textureId) {
        return underlaysByTexture.getOrDefault(textureId, Set.of());
    }

    public Set<Integer> overlaysUsingTexture(int textureId) {
        return overlaysByTexture.getOrDefault(textureId, Set.of());
    }

    private static void indexText(Map<String, Set<Integer>> index, int id, String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) return;
        add(index, normalized, id);
        for (String token : tokens(normalized)) add(index, token, id);
    }

    private static List<String> tokens(String value) {
        if (value.isBlank()) return List.of();
        String[] split = value.split("[^a-z0-9]+");
        List<String> result = new ArrayList<>();
        for (String token : split) if (!token.isBlank()) result.add(token);
        return List.copyOf(result);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static <K> void add(Map<K, Set<Integer>> map, K key, int id) {
        map.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(id);
    }

    private static <K> Map<K, Set<Integer>> freeze(Map<K, Set<Integer>> source) {
        Map<K, Set<Integer>> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, Set.copyOf(value)));
        return Map.copyOf(result);
    }
}
