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
        List<WorldObject> objects,
        List<BridgeLink> bridges
) {
    /** Compatibility constructor for callers that only need the document. */
    public RenderScene(WorldDocument document) {
        this(document, Map.of(), List.of(), List.of());
    }

    public RenderScene {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(terrainMeshes, "terrainMeshes");
        Objects.requireNonNull(objects, "objects");
        Objects.requireNonNull(bridges, "bridges");
        terrainMeshes = Map.copyOf(new LinkedHashMap<>(terrainMeshes));
        objects = List.copyOf(objects);
        bridges = List.copyOf(bridges);
    }
}
