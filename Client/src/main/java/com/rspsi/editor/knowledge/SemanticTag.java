package com.rspsi.editor.knowledge;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Immutable semantic classification applied to tiles, regions, or scene elements.
 *
 * <p>Semantic tags allow procedural generators, smart tools, and validation
 * analyzers to reason about high-level world intent without hardcoding OSRS object
 * IDs or tile parameters. Tags support namespacing (e.g. {@code core:ROAD},
 * {@code 117hd:WATER}) to avoid cross-plugin or cross-module collisions.</p>
 */
public record SemanticTag(String namespace, String name) {
    public static final String DEFAULT_NAMESPACE = "core";
    private static final ConcurrentMap<String, SemanticTag> INTERNED = new ConcurrentHashMap<>();

    // Standard high-level terrain & architectural tags
    public static final SemanticTag ROAD = of("ROAD");
    public static final SemanticTag PATH = of("PATH");
    public static final SemanticTag BUILDING = of("BUILDING");
    public static final SemanticTag ROOM = of("ROOM");
    public static final SemanticTag WALL = of("WALL");
    public static final SemanticTag DOOR = of("DOOR");
    public static final SemanticTag BRIDGE = of("BRIDGE");
    public static final SemanticTag WATER = of("WATER");
    public static final SemanticTag WATER_EDGE = of("WATER_EDGE");
    public static final SemanticTag CLIFF = of("CLIFF");
    public static final SemanticTag FOREST = of("FOREST");
    public static final SemanticTag VEGETATION = of("VEGETATION");
    public static final SemanticTag MINE = of("MINE");
    public static final SemanticTag FARM = of("FARM");
    public static final SemanticTag DUNGEON_ENTRANCE = of("DUNGEON_ENTRANCE");
    public static final SemanticTag SPAWN_POINT = of("SPAWN_POINT");
    public static final SemanticTag QUEST_OBJECT = of("QUEST_OBJECT");

    // Standard high-level object classification tags
    public static final SemanticTag TREE = of("TREE");
    public static final SemanticTag ROCK = of("ROCK");
    public static final SemanticTag CONTAINER = of("CONTAINER");
    public static final SemanticTag LIGHT_SOURCE = of("LIGHT_SOURCE");
    public static final SemanticTag SEAT = of("SEAT");
    public static final SemanticTag BANK = of("BANK");
    public static final SemanticTag ALTAR = of("ALTAR");
    public static final SemanticTag ROOF = of("ROOF");
    public static final SemanticTag HAZARD = of("HAZARD");

    public SemanticTag {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(name, "name");
        if (namespace.isBlank()) {
            throw new IllegalArgumentException("Namespace cannot be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("Tag name cannot be blank");
        }
    }

    /**
     * Returns an interned semantic tag from a qualified or unqualified string.
     * If no colon is present, {@value #DEFAULT_NAMESPACE} is assumed.
     */
    public static SemanticTag of(String identifier) {
        Objects.requireNonNull(identifier, "identifier");
        String trimmed = identifier.trim();
        int colonIdx = trimmed.indexOf(':');
        if (colonIdx >= 0) {
            String ns = trimmed.substring(0, colonIdx).trim().toLowerCase();
            String n = trimmed.substring(colonIdx + 1).trim().toUpperCase();
            return of(ns, n);
        }
        return of(DEFAULT_NAMESPACE, trimmed.toUpperCase());
    }

    /**
     * Returns an interned semantic tag with an explicit namespace and tag name.
     */
    public static SemanticTag of(String namespace, String name) {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(name, "name");
        String normalizedNs = namespace.trim().toLowerCase();
        String normalizedName = name.trim().toUpperCase();
        String key = normalizedNs + ":" + normalizedName;
        return INTERNED.computeIfAbsent(key, k -> new SemanticTag(normalizedNs, normalizedName));
    }

    /** Returns the fully qualified tag identifier in {@code namespace:name} format. */
    public String qualifiedName() {
        return namespace + ":" + name;
    }

    @Override
    public String toString() {
        return qualifiedName();
    }
}
