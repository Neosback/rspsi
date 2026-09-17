package com.rspsi.editor.render;

import com.rspsi.editor.collision.CollisionTileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.BridgeLink;
import com.rspsi.editor.terrain.TerrainMesh;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;

/**
 * Neutral scene input; renderers observe the canonical document through this
 * derived snapshot. Geometry is renderer-independent and can therefore be
 * consumed by the legacy rasterizer or a later GPU frontend.
 */
public record RenderScene(
        WorldDocument document,
        Map<TileCoordinate, TerrainMesh> terrainMeshes,
        Map<TileCoordinate, TerrainMaterial> terrainMaterials,
        Map<TileCoordinate, TerrainAppearance> terrainAppearances,
        Map<TileCoordinate, TerrainLight> terrainLighting,
        LightingProfile lightingProfile,
        Map<TileCoordinate, CollisionTileSnapshot> collision,
        List<WorldObject> objects,
        List<RenderObject> renderObjects,
        List<BridgeLink> bridges
) {
    /** Compatibility constructor for callers that only need the document. */
    public RenderScene(WorldDocument document) {
        this(document, Map.of(), Map.of(), Map.of(), Map.of(), LightingProfile.osrs(),
                Map.of(), List.of(), List.of(), List.of());
    }

    /** Compatibility constructor for callers without definition data. */
    public RenderScene(WorldDocument document, Map<TileCoordinate, TerrainMesh> terrainMeshes,
                       List<WorldObject> objects, List<BridgeLink> bridges) {
        this(document, terrainMeshes, Map.of(), Map.of(), Map.of(), LightingProfile.osrs(),
                Map.of(), objects, List.of(), bridges);
    }

    /** Compatibility constructor for scenes with materials but no lighting. */
    public RenderScene(WorldDocument document, Map<TileCoordinate, TerrainMesh> terrainMeshes,
                       Map<TileCoordinate, TerrainMaterial> terrainMaterials,
                       List<WorldObject> objects, List<BridgeLink> bridges) {
        this(document, terrainMeshes, terrainMaterials, Map.of(), Map.of(), LightingProfile.osrs(),
                Map.of(), objects, List.of(), bridges);
    }

    /** Compatibility constructor for scenes created before collision publication. */
    public RenderScene(WorldDocument document, Map<TileCoordinate, TerrainMesh> terrainMeshes,
                       Map<TileCoordinate, TerrainMaterial> terrainMaterials,
                       Map<TileCoordinate, TerrainLight> terrainLighting,
                       List<WorldObject> objects, List<RenderObject> renderObjects,
                       List<BridgeLink> bridges) {
        this(document, terrainMeshes, terrainMaterials, Map.of(), terrainLighting, LightingProfile.osrs(), Map.of(),
                objects, renderObjects, bridges);
    }

    public RenderScene {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(terrainMeshes, "terrainMeshes");
        Objects.requireNonNull(terrainMaterials, "terrainMaterials");
        Objects.requireNonNull(terrainAppearances, "terrainAppearances");
        Objects.requireNonNull(terrainLighting, "terrainLighting");
        Objects.requireNonNull(lightingProfile, "lightingProfile");
        Objects.requireNonNull(collision, "collision");
        Objects.requireNonNull(objects, "objects");
        Objects.requireNonNull(renderObjects, "renderObjects");
        Objects.requireNonNull(bridges, "bridges");
        terrainMeshes = orderedImmutableMap(terrainMeshes);
        terrainMaterials = orderedImmutableMap(terrainMaterials);
        terrainAppearances = orderedImmutableMap(terrainAppearances);
        lightingProfile = Objects.requireNonNull(lightingProfile, "lightingProfile");
        terrainLighting = orderedImmutableMap(terrainLighting);
        collision = orderedImmutableMap(collision);
        objects = List.copyOf(objects);
        renderObjects = List.copyOf(renderObjects);
        bridges = List.copyOf(bridges);
    }

    private static <K, V> Map<K, V> orderedImmutableMap(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
