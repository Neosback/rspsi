package com.rspsi.editor.render.compiler;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.cache.definition.ObjectCollisionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.editor.collision.CollisionMap;
import com.rspsi.editor.collision.CollisionTileSnapshot;
import com.rspsi.editor.collision.OsrsCollisionBuilder;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.render.LightingProfile;
import com.rspsi.editor.render.ModelPacketBuilder;
import com.rspsi.editor.render.ModelRenderPacket;
import com.rspsi.editor.render.RenderChanges;
import com.rspsi.editor.render.RenderObject;
import com.rspsi.editor.render.RenderScene;
import com.rspsi.editor.render.RenderSceneBuilder;
import com.rspsi.editor.render.RenderTextureResource;
import com.rspsi.editor.render.RenderTextureResourceBuilder;
import com.rspsi.editor.render.TerrainAppearance;
import com.rspsi.editor.render.TerrainLight;
import com.rspsi.editor.render.TerrainMaterial;
import com.rspsi.editor.render.TerrainRenderPacket;
import com.rspsi.editor.terrain.CompiledTerrainTile;
import com.rspsi.editor.terrain.TerrainMesh;
import com.rspsi.editor.terrain.TerrainSceneCompiler;
import com.rspsi.editor.render.compiler.InvalidationGraph.ZoneCoordinate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Revision-driven incremental scene compiler over canonical 8x8 zones.
 *
 * <p>Terrain compilation is bounded to dirty zones. Unchanged terrain maps
 * remain the exact immutable instances from the previous RenderScene. Model
 * geometry is rebuilt only when height/object edits can affect placement or
 * contouring; collision is rebuilt only for object edits.</p>
 */
public final class IncrementalSceneCompiler {
    private final DefinitionProvider definitions;
    private final LightingProfile lightingProfile;
    private final RenderSceneBuilder initialBuilder;
    private final TerrainSceneCompiler terrainCompiler = new TerrainSceneCompiler();
    private final Map<ZoneCoordinate, SceneZone> zoneCache = new ConcurrentHashMap<>();

    private WorldDocument trackedDocument;
    private ZoneRevisionTracker revisions;
    private long compiledBaseline;

    public IncrementalSceneCompiler(DefinitionProvider definitions, LightingProfile lightingProfile) {
        this.definitions = definitions;
        this.lightingProfile = Objects.requireNonNull(lightingProfile, "lightingProfile");
        this.initialBuilder = new RenderSceneBuilder(definitions);
    }

    /** Builds a complete initial scene and seeds all zone cache entries. */
    public synchronized RenderScene compileInitial(WorldDocument document, int clientCycle) {
        Objects.requireNonNull(document, "document");
        ensureDocument(document);
        RenderScene scene = initialBuilder.build(document, clientCycle);
        seedZoneCache(scene);
        compiledBaseline = revisions.baseline();
        return scene;
    }

    /**
     * Compatibility update. Without mutation metadata, use the conservative
     * radius-five underlay invalidation.
     */
    public RenderScene compile(RenderScene previous, RenderChanges changes, int clientCycle) {
        return compile(previous, changes, InvalidationGraph.InvalidationCause.UNDERLAY_EDIT,
                clientCycle);
    }

    /** Compiles only zones affected by the supplied mutation cause. */
    public synchronized RenderScene compile(RenderScene previous, RenderChanges changes,
                                            InvalidationGraph.InvalidationCause cause,
                                            int clientCycle) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(changes, "changes");
        Objects.requireNonNull(cause, "cause");
        if (clientCycle < 0) throw new IllegalArgumentException("Client cycle cannot be negative");

        WorldDocument document = previous.document();
        ensureDocument(document);
        if (changes.dirtyTiles().isEmpty()) return previous;

        if (definitions == null) {
            // Definition-free fixtures retain the old neutral path.
            return initialBuilder.update(previous, changes, clientCycle);
        }

        revisions.markDirty(changes.dirtyTiles(), cause);
        Set<ZoneCoordinate> dirtyZones = revisions.dirtyZones(compiledBaseline);
        if (dirtyZones.isEmpty()) return previous;

        Map<TileCoordinate, CompiledTerrainTile> compiled =
                terrainCompiler.compileZones(document, definitions, lightingProfile, dirtyZones);

        Map<TileCoordinate, TerrainMesh> meshes =
                new LinkedHashMap<>(previous.terrainMeshes());
        Map<TileCoordinate, TerrainMaterial> materials =
                new LinkedHashMap<>(previous.terrainMaterials());
        Map<TileCoordinate, TerrainAppearance> appearances =
                new LinkedHashMap<>(previous.terrainAppearances());
        Map<TileCoordinate, TerrainLight> lighting =
                new LinkedHashMap<>(previous.terrainLighting());
        Map<TileCoordinate, TerrainRenderPacket> terrainPackets =
                new LinkedHashMap<>(previous.terrainPackets());

        for (Map.Entry<TileCoordinate, CompiledTerrainTile> entry : compiled.entrySet()) {
            TileCoordinate coordinate = entry.getKey();
            CompiledTerrainTile tile = entry.getValue();
            meshes.put(coordinate, tile.mesh());
            materials.put(coordinate, material(document, coordinate));
            appearances.put(coordinate, tile.appearance());
            lighting.put(coordinate, tile.lighting());
            terrainPackets.put(coordinate, tile.renderPacket());
        }

        List<WorldObject> objects = previous.objects();
        List<RenderObject> renderObjects = previous.renderObjects();
        List<ModelRenderPacket> modelPackets = previous.modelPackets();
        Map<TileCoordinate, CollisionTileSnapshot> collision = previous.collision();

        if (cause == InvalidationGraph.InvalidationCause.HEIGHT_EDIT
                || cause == InvalidationGraph.InvalidationCause.OBJECT_EDIT) {
            modelPackets = new ModelPacketBuilder(definitions, lightingProfile)
                    .build(document, clientCycle);
        }
        if (cause == InvalidationGraph.InvalidationCause.OBJECT_EDIT) {
            objects = collectObjects(document);
            renderObjects = resolveObjects(objects);
            collision = collision(document);
        }

        Map<Integer, RenderTextureResource> textures =
                RenderTextureResourceBuilder.build(definitions, lightingProfile,
                        terrainPackets.values(), modelPackets);

        RenderScene updated = new RenderScene(document, meshes, materials, appearances,
                lighting, terrainPackets, lightingProfile, collision,
                objects, renderObjects, modelPackets, document.bridgeLinks(), textures);

        refreshZones(dirtyZones, compiled, modelPackets, terrainPackets);
        compiledBaseline = revisions.baseline();
        return updated;
    }

    public synchronized long revision(int plane, int zoneX, int zoneY) {
        if (revisions == null) return 0L;
        return revisions.revision(plane, zoneX, zoneY);
    }

    public synchronized long baselineRevision() {
        return compiledBaseline;
    }

    /** Clears all compiled zones and revision state. */
    public synchronized void invalidateAll() {
        zoneCache.clear();
        if (trackedDocument != null) {
            revisions = new ZoneRevisionTracker(
                    trackedDocument.width(), trackedDocument.length(), trackedDocument.planes());
        }
        compiledBaseline = 0L;
    }

    public int cachedZoneCount() {
        return zoneCache.size();
    }

    public Map<ZoneCoordinate, SceneZone> cachedZones() {
        return Map.copyOf(zoneCache);
    }

    private void ensureDocument(WorldDocument document) {
        if (trackedDocument == document && revisions != null) return;
        trackedDocument = document;
        revisions = new ZoneRevisionTracker(document.width(), document.length(), document.planes());
        zoneCache.clear();
        compiledBaseline = 0L;
    }

    private void seedZoneCache(RenderScene scene) {
        zoneCache.clear();
        WorldDocument document = scene.document();
        for (ZoneCoordinate zone : revisions.allZones()) {
            Map<TileCoordinate, TerrainRenderPacket> packets = new LinkedHashMap<>();
            scene.terrainPackets().forEach((coordinate, packet) -> {
                if (InvalidationGraph.toZone(coordinate).equals(zone)) packets.put(coordinate, packet);
            });
            List<ModelRenderPacket> models = scene.modelPackets().stream()
                    .filter(packet -> InvalidationGraph.toZone(packet.anchor()).equals(zone))
                    .toList();
            zoneCache.put(zone, new SceneZone(zone.plane(), zone.zoneX(), zone.zoneY(),
                    revisions.revision(zone.plane(), zone.zoneX(), zone.zoneY()),
                    Map.of(), packets, models));
        }
    }

    private void refreshZones(Set<ZoneCoordinate> dirtyZones,
                              Map<TileCoordinate, CompiledTerrainTile> compiled,
                              List<ModelRenderPacket> models,
                              Map<TileCoordinate, TerrainRenderPacket> allPackets) {
        for (ZoneCoordinate zone : dirtyZones) {
            Map<TileCoordinate, CompiledTerrainTile> zoneTerrain = new LinkedHashMap<>();
            Map<TileCoordinate, TerrainRenderPacket> zonePackets = new LinkedHashMap<>();
            compiled.forEach((coordinate, tile) -> {
                if (InvalidationGraph.toZone(coordinate).equals(zone)) {
                    zoneTerrain.put(coordinate, tile);
                }
            });
            allPackets.forEach((coordinate, packet) -> {
                if (InvalidationGraph.toZone(coordinate).equals(zone)) {
                    zonePackets.put(coordinate, packet);
                }
            });
            List<ModelRenderPacket> zoneModels = models.stream()
                    .filter(packet -> InvalidationGraph.toZone(packet.anchor()).equals(zone))
                    .toList();
            zoneCache.put(zone, new SceneZone(zone.plane(), zone.zoneX(), zone.zoneY(),
                    revisions.revision(zone.plane(), zone.zoneX(), zone.zoneY()),
                    zoneTerrain, zonePackets, zoneModels));
        }
    }

    private TerrainMaterial material(WorldDocument document, TileCoordinate coordinate) {
        var tile = document.tile(coordinate).snapshot();
        FloorDefinitionView underlay = tile.underlayId() <= 0
                ? null : definitions.underlay(tile.underlayId() - 1).orElse(null);
        FloorDefinitionView overlay = tile.overlayId() <= 0
                ? null : definitions.overlay(tile.overlayId() - 1).orElse(null);
        return new TerrainMaterial(tile.underlayId(), tile.overlayId(),
                overlay == null ? -1 : overlay.texture(),
                underlay == null ? 0 : underlay.rgb() & 0xFFFFFF,
                overlay == null ? 0 : overlay.rgb() & 0xFFFFFF);
    }

    private List<WorldObject> collectObjects(WorldDocument document) {
        List<WorldObject> result = new ArrayList<>();
        for (int plane = 0; plane < document.planes(); plane++) {
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    result.addAll(document.tile(plane, x, y).objects());
                }
            }
        }
        return List.copyOf(result);
    }

    private List<RenderObject> resolveObjects(List<WorldObject> objects) {
        List<RenderObject> result = new ArrayList<>(objects.size());
        for (WorldObject object : objects) {
            ObjectDefinitionView definition = definitions.object(object.id()).orElse(null);
            ObjectCollisionView objectCollision = definitions.objectCollision(object.id()).orElse(null);
            ObjectAppearanceView appearance = definitions.objectAppearance(object.id()).orElse(null);
            result.add(RenderObject.resolve(object, definition, objectCollision, appearance));
        }
        return List.copyOf(result);
    }

    private Map<TileCoordinate, CollisionTileSnapshot> collision(WorldDocument document) {
        CollisionMap map = OsrsCollisionBuilder.fromTerrainAndObjects(document, definitions);
        Map<TileCoordinate, CollisionTileSnapshot> result = new LinkedHashMap<>();
        for (int plane = 0; plane < document.planes(); plane++) {
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    TileCoordinate coordinate = new TileCoordinate(plane, x, y);
                    result.put(coordinate, CollisionTileSnapshot.from(map, coordinate));
                }
            }
        }
        return Map.copyOf(result);
    }
}
