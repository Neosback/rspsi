package com.rspsi.editor.generation;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Standardized schema classification for procedural generation and Wave Function Collapse algorithms.
 */
public record GenerationSchema(String id, String displayName, String description) {
    private static final ConcurrentMap<String, GenerationSchema> SCHEMAS = new ConcurrentHashMap<>();

    public static final GenerationSchema TERRAIN = of(
            "TERRAIN", "Terrain & Heightmap", "Synthesizes elevation, hydraulic erosion, and height smoothing");
    public static final GenerationSchema ROAD = of(
            "ROAD", "Road & Path Network", "Generates path layouts, edge blends, and street accessories");
    public static final GenerationSchema DUNGEON = of(
            "DUNGEON", "Dungeon & Interior (WFC)", "Synthesizes rooms, corridors, and doors using Wave Function Collapse");
    public static final GenerationSchema BUILDING = of(
            "BUILDING", "Architectural Synthesis", "Assembles multi-plane structures, walls, and roofs");
    public static final GenerationSchema BIOME = of(
            "BIOME", "Biome & Vegetation Scatter", "Distributes vegetation, rocks, and surface scatter based on constraints");
    public static final GenerationSchema OBJECT_DRESSING = of(
            "OBJECT_DRESSING", "Detail & Dressing", "Populates detail props matching semantic surface classifications");

    public GenerationSchema {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(description, "description");
    }

    public static GenerationSchema of(String id, String displayName, String description) {
        return SCHEMAS.computeIfAbsent(id.trim().toUpperCase(),
                key -> new GenerationSchema(key, displayName, description));
    }

    @Override
    public String toString() {
        return displayName + " (" + id + ")";
    }
}
