package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ObjectCollisionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.editor.collision.CollisionMap;
import com.rspsi.editor.collision.CollisionTileSnapshot;
import com.rspsi.editor.collision.OsrsCollisionBuilder;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.DirtyRegion;
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
    private final DefinitionProvider definitions;

    public RenderSceneBuilder() {
        this(new TerrainMeshBuilder(), null);
    }

    public RenderSceneBuilder(TerrainMeshBuilder terrainMeshes) {
        this(terrainMeshes, null);
    }

    /** Builds scene materials from the neutral definition provider when supplied. */
    public RenderSceneBuilder(DefinitionProvider definitions) {
        this(new TerrainMeshBuilder(), Objects.requireNonNull(definitions, "definitions"));
    }

    public RenderSceneBuilder(TerrainMeshBuilder terrainMeshes, DefinitionProvider definitions) {
        this.terrainMeshes = Objects.requireNonNull(terrainMeshes, "terrainMeshes");
        this.definitions = definitions;
    }

    /**
     * Rebuilds the complete scene snapshot. Incremental chunk updates remain
     * a renderer concern until dirty-region consumers are connected.
     */
    public RenderScene build(WorldDocument document) {
        Objects.requireNonNull(document, "document");
        Map<TileCoordinate, TerrainMesh> meshes = new LinkedHashMap<>();
        Map<TileCoordinate, TerrainMaterial> materials = new LinkedHashMap<>();
        Map<TileCoordinate, CollisionTileSnapshot> collision = collision(document);
        List<WorldObject> objects = new ArrayList<>();
        List<RenderObject> renderObjects = new ArrayList<>();
        for (int plane = 0; plane < document.planes(); plane++) {
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    TileCoordinate coordinate = new TileCoordinate(plane, x, y);
                    var tile = document.tile(coordinate);
                    meshes.put(coordinate, terrainMeshes.build(tile.snapshot()));
                    if (definitions != null) {
                        materials.put(coordinate, material(tile.snapshot()));
                    }
                    for (WorldObject object : tile.objects()) {
                        objects.add(object);
                        renderObjects.add(resolve(object));
                    }
                }
            }
        }
        return new RenderScene(document, meshes, materials, TerrainLighting.build(document), collision,
                objects, renderObjects, document.bridgeLinks());
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
        Map<TileCoordinate, TerrainMaterial> materials = new LinkedHashMap<>(previous.terrainMaterials());
        Map<TileCoordinate, TerrainLight> lighting = new LinkedHashMap<>(TerrainLighting.build(document));
        Map<TileCoordinate, CollisionTileSnapshot> collision = collision(document);
        List<RenderObject> renderObjects = new ArrayList<>();
        Set<TileCoordinate> dirtyTiles = changes.dirtyTiles();
        for (TileCoordinate coordinate : dirtyTiles) {
            if (coordinate.plane() >= document.planes()
                    || coordinate.x() >= document.width()
                    || coordinate.y() >= document.length()) {
                throw new IllegalArgumentException("Dirty tile is outside the scene document: " + coordinate);
            }
            meshes.put(coordinate, terrainMeshes.build(document.tile(coordinate).snapshot()));
            if (definitions != null) {
                materials.put(coordinate, material(document.tile(coordinate).snapshot()));
            }
        }
        List<WorldObject> objects = collectObjects(document);
        for (WorldObject object : objects) renderObjects.add(resolve(object));
        return new RenderScene(document, meshes, materials, lighting, collision, objects, renderObjects,
                document.bridgeLinks());
    }

    /** Rebuilds the chunks drained from an editor session's invalidation queue. */
    public RenderScene update(RenderScene previous, Set<DirtyRegion> dirtyRegions) {
        Objects.requireNonNull(previous, "previous");
        return update(previous, RenderChanges.fromDirtyRegions(dirtyRegions, previous.document()));
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

    private TerrainMaterial material(com.rspsi.editor.model.TileSnapshot tile) {
        FloorDefinitionView underlay = tile.underlayId() <= 0
                ? null : definitions.underlay(tile.underlayId() - 1).orElse(null);
        FloorDefinitionView overlay = definitions.overlay(tile.overlayId()).orElse(null);
        return new TerrainMaterial(
                tile.underlayId(),
                tile.overlayId(),
                overlay == null ? -1 : overlay.texture(),
                underlay == null ? 0 : underlay.rgb() & 0xFFFFFF,
                overlay == null ? 0 : overlay.rgb() & 0xFFFFFF);
    }

    private Map<TileCoordinate, CollisionTileSnapshot> collision(WorldDocument document) {
        CollisionMap map = definitions == null
                ? OsrsCollisionBuilder.fromTerrain(document)
                : OsrsCollisionBuilder.fromTerrainAndObjects(document, definitions);
        Map<TileCoordinate, CollisionTileSnapshot> snapshots = new LinkedHashMap<>();
        for (int plane = 0; plane < document.planes(); plane++) {
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    TileCoordinate coordinate = new TileCoordinate(plane, x, y);
                    snapshots.put(coordinate, CollisionTileSnapshot.from(map, coordinate));
                }
            }
        }
        return snapshots;
    }

    private RenderObject resolve(WorldObject object) {
        ObjectDefinitionView definition = definitions == null
                ? null : definitions.object(object.id()).orElse(null);
        ObjectCollisionView collision = definitions == null
                ? null : definitions.objectCollision(object.id()).orElse(null);
        ObjectAppearanceView appearance = definitions == null
                ? null : definitions.objectAppearance(object.id()).orElse(null);
        return RenderObject.resolve(object, definition, collision, appearance);
    }
}
