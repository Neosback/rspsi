package com.rspsi.editor.render;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.terrain.TerrainMesh;
import com.rspsi.editor.terrain.TerrainMeshBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Builds a renderer-neutral scene from the RSPSi-owned world model. */
public final class RenderSceneBuilder {
    private final TerrainMeshBuilder terrainMeshes;

    public RenderSceneBuilder() {
        this(new TerrainMeshBuilder());
    }

    public RenderSceneBuilder(TerrainMeshBuilder terrainMeshes) {
        this.terrainMeshes = Objects.requireNonNull(terrainMeshes, "terrainMeshes");
    }

    /**
     * Rebuilds the complete scene snapshot. Incremental chunk updates remain
     * a renderer concern until dirty-region consumers are connected.
     */
    public RenderScene build(WorldDocument document) {
        Objects.requireNonNull(document, "document");
        Map<TileCoordinate, TerrainMesh> meshes = new LinkedHashMap<>();
        List<WorldObject> objects = new ArrayList<>();
        for (int plane = 0; plane < document.planes(); plane++) {
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    TileCoordinate coordinate = new TileCoordinate(plane, x, y);
                    var tile = document.tile(coordinate);
                    meshes.put(coordinate, terrainMeshes.build(tile.snapshot()));
                    objects.addAll(tile.objects());
                }
            }
        }
        return new RenderScene(document, meshes, objects);
    }
}
