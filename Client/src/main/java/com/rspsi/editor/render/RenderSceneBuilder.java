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
import java.util.Set;

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

    /**
     * Rebuilds only the requested terrain tiles while refreshing the object
     * list from the document. Callers can include neighbouring tiles when a
     * floor blend or shared edge makes them part of the affected region.
     */
    public RenderScene update(RenderScene previous, RenderChanges changes) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(changes, "changes");
        WorldDocument document = previous.document();
        Map<TileCoordinate, TerrainMesh> meshes = new LinkedHashMap<>(previous.terrainMeshes());
        Set<TileCoordinate> dirtyTiles = changes.dirtyTiles();
        for (TileCoordinate coordinate : dirtyTiles) {
            if (coordinate.plane() >= document.planes()
                    || coordinate.x() >= document.width()
                    || coordinate.y() >= document.length()) {
                throw new IllegalArgumentException("Dirty tile is outside the scene document: " + coordinate);
            }
            meshes.put(coordinate, terrainMeshes.build(document.tile(coordinate).snapshot()));
        }
        List<WorldObject> objects = collectObjects(document);
        return new RenderScene(document, meshes, objects);
    }

    private static List<WorldObject> collectObjects(WorldDocument document) {
        List<WorldObject> objects = new ArrayList<>();
        for (int plane = 0; plane < document.planes(); plane++) {
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    objects.addAll(document.tile(plane, x, y).objects());
                }
            }
        }
        return objects;
    }
}
