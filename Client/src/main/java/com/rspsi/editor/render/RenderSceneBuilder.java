package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ObjectCollisionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.ObjectDefinitionResolver;
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
import com.rspsi.editor.terrain.CompiledTerrainTile;
import com.rspsi.editor.terrain.TerrainSceneCompiler;
import com.rspsi.editor.render.compiler.InvalidationGraph;

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
    private final ObjectDefinitionResolver definitionResolver;
    private final LightingProfile lightingProfile;

    public RenderSceneBuilder() {
        this(new TerrainMeshBuilder(), null, LightingProfile.osrs());
    }

    public RenderSceneBuilder(TerrainMeshBuilder terrainMeshes) {
        this(terrainMeshes, null, LightingProfile.osrs());
    }

    /** Builds scene materials from the neutral definition provider when supplied. */
    public RenderSceneBuilder(DefinitionProvider definitions) {
        this(new TerrainMeshBuilder(), Objects.requireNonNull(definitions, "definitions"),
                LightingProfile.osrs());
    }

    public RenderSceneBuilder(TerrainMeshBuilder terrainMeshes, DefinitionProvider definitions) {
        this(terrainMeshes, definitions, LightingProfile.osrs());
    }

    public RenderSceneBuilder(TerrainMeshBuilder terrainMeshes, DefinitionProvider definitions,
                              LightingProfile lightingProfile) {
        this.terrainMeshes = Objects.requireNonNull(terrainMeshes, "terrainMeshes");
        this.definitions = definitions;
        this.definitionResolver = definitions == null ? null : new ObjectDefinitionResolver(definitions);
        this.lightingProfile = Objects.requireNonNull(lightingProfile, "lightingProfile");
    }

    /**
     * Rebuilds the complete scene snapshot. Incremental chunk updates remain
     * a renderer concern until dirty-region consumers are connected.
     */
    public RenderScene build(WorldDocument document) {
        return build(document, 0);
    }

    /** Builds a scene snapshot at an explicit client-cycle position. */
    public RenderScene build(WorldDocument document, int clientCycle) {
        Objects.requireNonNull(document, "document");
        if (clientCycle < 0) throw new IllegalArgumentException("Client cycle cannot be negative");
        Map<TileCoordinate, TerrainMesh> meshes = new LinkedHashMap<>();
        Map<TileCoordinate, TerrainMaterial> materials = new LinkedHashMap<>();
        Map<TileCoordinate, TerrainAppearance> appearances = new LinkedHashMap<>();
        Map<TileCoordinate, TerrainRenderPacket> packets = new LinkedHashMap<>();
        Map<TileCoordinate, CompiledTerrainTile> compiledTerrain = definitions == null
                ? Map.of() : new TerrainSceneCompiler().compile(document, definitions, lightingProfile);
        Map<TileCoordinate, CollisionTileSnapshot> collision = collision(document);
        List<WorldObject> objects = new ArrayList<>();
        List<RenderObject> renderObjects = new ArrayList<>();
        for (int plane = 0; plane < document.planes(); plane++) {
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    TileCoordinate coordinate = new TileCoordinate(plane, x, y);
                    var tile = document.tile(coordinate);
                    if (definitions == null) {
                        meshes.put(coordinate, terrainMeshes.build(tile.snapshot()));
                    } else {
                        CompiledTerrainTile compiled = compiledTerrain.get(coordinate);
                        meshes.put(coordinate, compiled.mesh());
                        materials.put(coordinate, material(tile.snapshot()));
                        appearances.put(coordinate, compiled.appearance());
                    }
                    for (WorldObject object : tile.objects()) {
                        objects.add(object);
                        renderObjects.add(resolve(object));
                    }
                }
            }
        }
        Map<TileCoordinate, TerrainLight> lighting = definitions == null
                ? TerrainLighting.build(document, lightingProfile, null)
                : compiledTerrain.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, entry -> entry.getValue().lighting(),
                        (first, second) -> first, LinkedHashMap::new));
        List<ModelRenderPacket> modelPackets = definitions == null
                ? List.of() : new ModelPacketBuilder(definitions, lightingProfile).build(document, clientCycle);
        if (definitions != null) {
            compiledTerrain.forEach((coordinate, compiled) ->
                    packets.put(coordinate, compiled.renderPacket()));
        }
        Map<Integer, RenderTextureResource> textures = definitions == null ? Map.of()
                : RenderTextureResourceBuilder.build(definitions, lightingProfile,
                packets.values(), modelPackets);
        return new RenderScene(document, meshes, materials, appearances,
                lighting, packets, lightingProfile, collision,
                objects, renderObjects, modelPackets, document.bridgeLinks(), textures);
    }

    /**
     * Rebuilds only derived model animation geometry for a new client cycle.
     * Terrain, collision, authored objects, and texture resources remain unchanged.
     */
    public RenderScene refreshAnimations(RenderScene previous, int clientCycle) {
        Objects.requireNonNull(previous, "previous");
        if (clientCycle < 0) throw new IllegalArgumentException("Client cycle cannot be negative");
        if (definitions == null) return previous;

        Set<TileCoordinate> activeTiles = previous.modelPackets().stream()
                .filter(packet -> packet.animationState().active())
                .map(ModelRenderPacket::anchor)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (activeTiles.isEmpty()) return previous;

        WorldDocument document = previous.document();
        for (TileCoordinate coordinate : activeTiles) {
            for (WorldObject object : document.tile(coordinate).objects()) {
                if (definitions.objectAppearance(object.id())
                        .map(ObjectAppearanceView::mergeNormals)
                        .orElse(false)) {
                    return refreshAnimationsFull(previous, clientCycle);
                }
            }
        }

        ModelPacketBuilder builder = new ModelPacketBuilder(definitions, lightingProfile);
        Map<TileCoordinate, List<ModelRenderPacket>> packetsByTile = new LinkedHashMap<>();
        for (ModelRenderPacket packet : previous.modelPackets()) {
            packetsByTile.computeIfAbsent(packet.anchor(), ignored -> new ArrayList<>())
                    .add(packet);
        }
        for (TileCoordinate coordinate : activeTiles) {
            packetsByTile.put(coordinate, builder.buildTile(document, coordinate, clientCycle));
        }

        List<ModelRenderPacket> modelPackets = packetsByTile.values().stream()
                .flatMap(List::stream)
                .toList();
        if (modelPackets.equals(previous.modelPackets())) return previous;

        return new RenderScene(
                previous.document(),
                previous.terrainMeshes(),
                previous.terrainMaterials(),
                previous.terrainAppearances(),
                previous.terrainLighting(),
                previous.terrainPackets(),
                previous.lightingProfile(),
                previous.collision(),
                previous.objects(),
                previous.renderObjects(),
                modelPackets,
                previous.bridges(),
                previous.textures());
    }

    private RenderScene refreshAnimationsFull(RenderScene previous, int clientCycle) {
        List<ModelRenderPacket> modelPackets =
                new ModelPacketBuilder(definitions, lightingProfile)
                        .build(previous.document(), clientCycle);
        if (modelPackets.equals(previous.modelPackets())) return previous;

        return new RenderScene(
                previous.document(),
                previous.terrainMeshes(),
                previous.terrainMaterials(),
                previous.terrainAppearances(),
                previous.terrainLighting(),
                previous.terrainPackets(),
                previous.lightingProfile(),
                previous.collision(),
                previous.objects(),
                previous.renderObjects(),
                modelPackets,
                previous.bridges(),
                previous.textures());
    }

    /**
     * Rebuilds only the requested terrain tiles while refreshing the object
     * list from the document. Callers can include neighbouring tiles when a
     * floor blend or shared edge makes them part of the affected region.
     */
    public RenderScene update(RenderScene previous, RenderChanges changes) {
        return update(previous, changes, 0);
    }

    /** Updates a scene while selecting model animation frames for clientCycle. */
    public RenderScene update(RenderScene previous, RenderChanges changes, int clientCycle) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(changes, "changes");
        if (clientCycle < 0) throw new IllegalArgumentException("Client cycle cannot be negative");
        WorldDocument document = previous.document();
        Map<TileCoordinate, TerrainMesh> meshes = new LinkedHashMap<>(previous.terrainMeshes());
        Map<TileCoordinate, TerrainMaterial> materials = new LinkedHashMap<>(previous.terrainMaterials());
        Map<TileCoordinate, TerrainAppearance> appearances = new LinkedHashMap<>(previous.terrainAppearances());
        Map<TileCoordinate, TerrainRenderPacket> packets = new LinkedHashMap<>(previous.terrainPackets());
        Set<TileCoordinate> dirtyTiles = changes.dirtyTiles();
        Set<com.rspsi.editor.render.compiler.InvalidationGraph.ZoneCoordinate> dirtyZones =
                definitions == null ? Set.of() : InvalidationGraph.computeInvalidatedZones(
                        dirtyTiles, InvalidationGraph.InvalidationCause.UNDERLAY_EDIT,
                        document.width(), document.length());
        Map<TileCoordinate, CompiledTerrainTile> compiledTerrain = definitions == null
                ? Map.of() : new TerrainSceneCompiler().compileZones(
                        document, definitions, lightingProfile, dirtyZones);
        Map<TileCoordinate, TerrainLight> lighting =
                new LinkedHashMap<>(previous.terrainLighting());
        if (definitions == null) {
            lighting.putAll(TerrainLighting.build(document, lightingProfile, null));
        } else {
            compiledTerrain.forEach((coordinate, tile) ->
                    lighting.put(coordinate, tile.lighting()));
        }
        Map<TileCoordinate, CollisionTileSnapshot> collision = collision(document);
        List<RenderObject> renderObjects = new ArrayList<>();
        for (TileCoordinate coordinate : dirtyTiles) {
            if (coordinate.plane() < 0 || coordinate.plane() >= document.planes()
                    || coordinate.x() < 0 || coordinate.x() >= document.width()
                    || coordinate.y() < 0 || coordinate.y() >= document.length()) {
                throw new IllegalArgumentException("Dirty tile is outside the scene document: " + coordinate);
            }
            if (definitions == null) {
                meshes.put(coordinate, terrainMeshes.build(document.tile(coordinate).snapshot()));
            }
        }
        if (definitions != null) {
            compiledTerrain.forEach((coordinate, compiled) -> {
                meshes.put(coordinate, compiled.mesh());
                materials.put(coordinate, material(document.tile(coordinate).snapshot()));
                appearances.put(coordinate, compiled.appearance());
            });
        }
        List<WorldObject> objects = collectObjects(document);
        for (WorldObject object : objects) renderObjects.add(resolve(object));
        List<ModelRenderPacket> modelPackets = definitions == null
                ? List.of() : new ModelPacketBuilder(definitions, lightingProfile).build(document, clientCycle);
        if (definitions != null) {
            compiledTerrain.forEach((coordinate, compiled) ->
                    packets.put(coordinate, compiled.renderPacket()));
        }
        Map<Integer, RenderTextureResource> textures = definitions == null ? previous.textures()
                : RenderTextureResourceBuilder.build(definitions, lightingProfile,
                packets.values(), modelPackets);
        return new RenderScene(document, meshes, materials, appearances, lighting, packets, lightingProfile, collision,
                objects, renderObjects, modelPackets,
                document.bridgeLinks(), textures);
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
        FloorDefinitionView overlay = tile.overlayId() <= 0
                ? null : definitions.overlay(tile.overlayId() - 1).orElse(null);
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
        if (definitions == null) {
            return RenderObject.resolve(object, null, null, null);
        }

        ObjectDefinitionResolver.Resolution resolution =
                definitionResolver.resolveEditorDisplay(object.id());
        ObjectDefinitionView placementDefinition =
                resolution.placedDefinition().orElse(null);
        ObjectDefinitionView displayDefinition =
                resolution.displayDefinition().orElse(null);

        // Collision/scene occupancy are established by the placed definition
        // during map load, while visible model metadata comes from the
        // one-step transformed display definition used by DynamicObject.
        ObjectCollisionView collision = definitions.objectCollision(object.id()).orElse(null);
        ObjectAppearanceView appearance = displayDefinition == null
                ? null
                : definitions.objectAppearance(displayDefinition.id()).orElse(null);

        return RenderObject.resolve(object, placementDefinition, displayDefinition,
                collision, appearance);
    }
}
