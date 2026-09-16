package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.BridgeLink;
import com.rspsi.editor.terrain.TerrainMesh;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Neutral scene input; renderers observe the canonical document through this
 * derived snapshot. Geometry is renderer-independent and can therefore be
 * consumed by the legacy rasterizer or a later GPU frontend.
 */
public record RenderScene(
        WorldDocument document,
        Map<TileCoordinate, TerrainMesh> terrainMeshes,
        Map<TileCoordinate, TerrainMaterial> terrainMaterials,
        Map<TileCoordinate, TerrainLight> terrainLighting,
        List<WorldObject> objects,
        List<RenderObject> renderObjects,
        List<BridgeLink> bridges
) {
    /** Compatibility constructor for callers that only need the document. */
    public RenderScene(WorldDocument document) {
        this(document, Map.of(), Map.of(), Map.of(), List.of(), List.of(), List.of());
    }

    /** Compatibility constructor for callers without definition data. */
    public RenderScene(WorldDocument document, Map<TileCoordinate, TerrainMesh> terrainMeshes,
                       List<WorldObject> objects, List<BridgeLink> bridges) {
        this(document, terrainMeshes, Map.of(), Map.of(), objects, List.of(), bridges);
    }

    /** Compatibility constructor for scenes with materials but no lighting. */
    public RenderScene(WorldDocument document, Map<TileCoordinate, TerrainMesh> terrainMeshes,
                       Map<TileCoordinate, TerrainMaterial> terrainMaterials,
                       List<WorldObject> objects, List<BridgeLink> bridges) {
        this(document, terrainMeshes, terrainMaterials, Map.of(), objects, List.of(), bridges);
    }

    public RenderScene {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(terrainMeshes, "terrainMeshes");
        Objects.requireNonNull(terrainMaterials, "terrainMaterials");
        Objects.requireNonNull(terrainLighting, "terrainLighting");
        Objects.requireNonNull(objects, "objects");
        Objects.requireNonNull(renderObjects, "renderObjects");
        Objects.requireNonNull(bridges, "bridges");
        terrainMeshes = Map.copyOf(new LinkedHashMap<>(terrainMeshes));
        terrainMaterials = Map.copyOf(new LinkedHashMap<>(terrainMaterials));
        terrainLighting = Map.copyOf(new LinkedHashMap<>(terrainLighting));
        objects = List.copyOf(objects);
        renderObjects = List.copyOf(renderObjects);
        bridges = List.copyOf(bridges);
    }
}
