package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.editor.collision.CollisionMap;
import com.rspsi.editor.collision.CollisionTileSnapshot;
import com.rspsi.editor.collision.OsrsCollisionBuilder;
import com.rspsi.editor.model.BridgeLink;
import com.rspsi.editor.model.WorldRegion;
import com.rspsi.editor.model.WorldRegionWindow;
import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.terrain.CompiledTerrainTile;
import com.rspsi.editor.terrain.TerrainSceneCompiler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashSet;

/** Builds world-addressed neutral geometry while preserving window holes. */
public final class RenderWindowSceneBuilder {
    private static final int TERRAIN_CONTEXT_BORDER = 5;
    private final RenderSceneBuilder regions;
    private final DefinitionProvider definitions;
    private final ScenePresentation presentation;

    public RenderWindowSceneBuilder() {
        this.regions = new RenderSceneBuilder();
        this.definitions = null;
        this.presentation = ScenePresentation.PARITY;
    }

    public RenderWindowSceneBuilder(DefinitionProvider definitions) {
        this(definitions, ScenePresentation.PARITY);
    }

    /** Studio viewports pass {@link ScenePresentation#EDITOR}; everything else keeps parity. */
    public RenderWindowSceneBuilder(DefinitionProvider definitions, ScenePresentation presentation) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.presentation = Objects.requireNonNull(presentation, "presentation");
        this.regions = new RenderSceneBuilder(this.definitions, presentation);
    }

    /**
     * Stitches loaded neighboring region edges before building local meshes.
     * Missing regions remain absent from every output map.
     */
    public RenderWindowScene build(WorldRegionWindow window) {
        return build(window, 0);
    }

    /** Builds a world window while selecting model animation frames for clientCycle. */
    public RenderWindowScene build(WorldRegionWindow window, int clientCycle) {
        Objects.requireNonNull(window, "window");
        if (clientCycle < 0) throw new IllegalArgumentException("Client cycle cannot be negative");
        WorldRegionWindow prepared = window.copy();
        prepared.stitchSharedEdges();
        WorldDocument worldDocument = prepared.materializePaddedWorldDocument(TERRAIN_CONTEXT_BORDER);
        var compiledWorld = definitions == null
                ? java.util.Map.<TileCoordinate, CompiledTerrainTile>of()
                : new TerrainSceneCompiler().compile(worldDocument, definitions);
        var worldAppearances = definitions == null
                ? java.util.Map.<TileCoordinate, TerrainAppearance>of()
                : compiledWorld.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        java.util.Map.Entry::getKey, entry -> entry.getValue().appearance(),
                        (first, second) -> first, LinkedHashMap::new));
        var worldLighting = definitions == null
                ? TerrainLighting.build(worldDocument, LightingProfile.osrs(), null)
                : compiledWorld.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        java.util.Map.Entry::getKey, entry -> entry.getValue().lighting(),
                        (first, second) -> first, LinkedHashMap::new));
        var meshes = new LinkedHashMap<WorldTileAddress, com.rspsi.editor.terrain.TerrainMesh>();
        var materials = new LinkedHashMap<WorldTileAddress, TerrainMaterial>();
        var appearances = new LinkedHashMap<WorldTileAddress, TerrainAppearance>();
        var lighting = new LinkedHashMap<WorldTileAddress, TerrainLight>();
        var packets = new LinkedHashMap<WorldTileAddress, TerrainRenderPacket>();
        var modelPackets = new LinkedHashMap<WorldTileAddress, List<ModelRenderPacket>>();
        var tileFlags = new LinkedHashMap<WorldTileAddress, Integer>();
        TerrainPacketBuilder packetBuilder = new TerrainPacketBuilder();
        var collision = new LinkedHashMap<WorldTileAddress, CollisionTileSnapshot>();
        List<WorldRenderObject> objects = new ArrayList<>();
        List<WorldBridgeLink> bridges = new ArrayList<>();
        CollisionMap windowCollision = definitions == null
                ? OsrsCollisionBuilder.fromWindow(prepared)
                : OsrsCollisionBuilder.fromWindow(prepared, definitions);

        for (WorldRegion region : prepared.regions().values().stream()
                .sorted(java.util.Comparator.comparingInt(WorldRegion::regionX)
                        .thenComparingInt(WorldRegion::regionY)).toList()) {
            RenderScene scene = regions.build(region.document(), clientCycle);
            int originX = region.regionX() * WorldRegion.REGION_SIZE;
            int originY = region.regionY() * WorldRegion.REGION_SIZE;
            scene.terrainMeshes().forEach((local, mesh) ->
                    meshes.put(WorldTileAddress.of(originX + local.x(), originY + local.y(), local.plane()), mesh));
            scene.terrainMaterials().forEach((local, material) ->
                    materials.put(WorldTileAddress.of(originX + local.x(), originY + local.y(), local.plane()), material));
            for (int plane = 0; plane < region.document().planes(); plane++) {
                for (int x = 0; x < region.document().width(); x++) {
                    for (int y = 0; y < region.document().length(); y++) {
                        WorldTileAddress address = WorldTileAddress.of(originX + x, originY + y, plane);
                        tileFlags.put(address, region.document().tile(plane, x, y).snapshot().flags());
                        TileCoordinate worldCoordinate = new TileCoordinate(plane,
                                TERRAIN_CONTEXT_BORDER
                                        + (region.regionX() - prepared.minRegionX()) * WorldRegion.REGION_SIZE + x,
                                TERRAIN_CONTEXT_BORDER
                                        + (region.regionY() - prepared.minRegionY()) * WorldRegion.REGION_SIZE + y);
                        if (definitions != null) {
                            TerrainAppearance appearance = worldAppearances.get(worldCoordinate);
                            appearances.put(address, appearance);
                            packets.put(address, packetBuilder.build(
                                    new TileCoordinate(plane, x, y),
                                    scene.terrainMeshes().get(localCoordinate(plane, x, y)),
                                    appearance, worldLighting.get(worldCoordinate)));
                        }
                        lighting.put(address, worldLighting.get(worldCoordinate));
                    }
                }
            }
            int collisionOffsetX = (region.regionX() - prepared.minRegionX()) * WorldRegion.REGION_SIZE;
            int collisionOffsetY = (region.regionY() - prepared.minRegionY()) * WorldRegion.REGION_SIZE;
            for (int plane = 0; plane < region.document().planes(); plane++) {
                for (int x = 0; x < region.document().width(); x++) {
                    for (int y = 0; y < region.document().length(); y++) {
                        var local = new com.rspsi.editor.model.TileCoordinate(plane,
                                collisionOffsetX + x, collisionOffsetY + y);
                        collision.put(WorldTileAddress.of(originX + x, originY + y, plane),
                                CollisionTileSnapshot.from(windowCollision, local));
                    }
                }
            }
            for (RenderObject object : scene.renderObjects()) {
                objects.add(new WorldRenderObject(WorldTileAddress.of(
                        originX + object.object().x(), originY + object.object().y(), object.object().plane()), object));
            }
            for (BridgeLink bridge : scene.bridges()) {
                bridges.add(new WorldBridgeLink(address(region, bridge.upper()), address(region, bridge.lower())));
            }
        }
        if (definitions != null) {
            modelPackets.putAll(buildWorldModelPackets(prepared, worldDocument, clientCycle));
        }
        List<TerrainRenderPacket> textureTerrainPackets = packets.values().stream().toList();
        List<ModelRenderPacket> textureModelPackets = modelPackets.values().stream()
                .flatMap(List::stream).toList();
        var textures = definitions == null ? java.util.Map.<Integer, RenderTextureResource>of()
                : RenderTextureResourceBuilder.build(definitions, LightingProfile.osrs(),
                textureTerrainPackets, textureModelPackets);
        return new RenderWindowScene(prepared, meshes, materials, appearances, lighting,
                packets, modelPackets, tileFlags, LightingProfile.osrs(), collision, objects, bridges, textures);
    }

    /**
     * Rebuilds only model presentation state for a new client cycle.
     *
     * <p>Terrain, collision, materials, bridge state, and texture resources remain resident.
     * The returned dirty zones are exactly the absolute 8x8 zones whose model-packet lists
     * changed, allowing packet and GPU incremental builders to refresh animation without a
     * 50 Hz terrain rebuild.</p>
     */
    public AnimationRefreshResult refreshAnimations(RenderWindowScene previous, int clientCycle) {
        Objects.requireNonNull(previous, "previous");
        if (clientCycle < 0) throw new IllegalArgumentException("Client cycle cannot be negative");
        if (definitions == null) {
            return new AnimationRefreshResult(previous, Set.of(), 0, 0, false);
        }

        Set<WorldTileAddress> activeAddresses = previous.modelPackets().entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                        .anyMatch(packet -> packet.animationState().active()))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (activeAddresses.isEmpty()) {
            return new AnimationRefreshResult(previous, Set.of(), 0, 0, false);
        }

        // The RenderWindowScene already retains the prepared, stitched source
        // window. Re-materialize only the padded document needed for contour,
        // placement-height and wall-displacement rules; do not re-copy and
        // re-stitch the whole window on every animation frame.
        WorldRegionWindow prepared = previous.window();
        WorldDocument worldDocument =
                prepared.materializePaddedWorldDocument(TERRAIN_CONTEXT_BORDER);

        if (requiresSceneWideNormalMerge(prepared, worldDocument, activeAddresses)) {
            return refreshAnimationsFull(previous, prepared, worldDocument, clientCycle);
        }

        ModelPacketBuilder modelBuilder = new ModelPacketBuilder(definitions, LightingProfile.osrs(), presentation);
        Map<WorldTileAddress, List<ModelRenderPacket>> nextModels =
                new LinkedHashMap<>(previous.modelPackets());

        int rebuiltTiles = 0;
        for (WorldTileAddress address : activeAddresses) {
            TileCoordinate local = paddedCoordinate(prepared, address);
            if (!worldDocument.contains(local.plane(), local.x(), local.y())) {
                nextModels.remove(address);
                rebuiltTiles++;
                continue;
            }

            List<ModelRenderPacket> localPackets =
                    modelBuilder.buildTile(worldDocument, local, clientCycle);
            List<ModelRenderPacket> worldPackets = localPackets.stream()
                    .map(packet -> toWorldPacket(prepared, packet))
                    .filter(Objects::nonNull)
                    .toList();
            if (worldPackets.isEmpty()) {
                nextModels.remove(address);
            } else {
                nextModels.put(address, worldPackets);
            }
            rebuiltTiles++;
        }

        Set<WorldTileAddress> changedAddresses = new LinkedHashSet<>();
        for (WorldTileAddress address : activeAddresses) {
            if (!sameModelPresentation(
                    previous.modelPackets().get(address), nextModels.get(address))) {
                changedAddresses.add(address);
            }
        }
        if (changedAddresses.isEmpty()) {
            // Presentation is unchanged, but callers historically observe the
            // refreshed ModelAnimationState.clientCycle as diagnostic timing
            // state. Publish the rebuilt active-tile packets without marking
            // any GPU zone dirty.
            RenderWindowScene refreshed = new RenderWindowScene(
                    previous.window(),
                    previous.terrainMeshes(),
                    previous.terrainMaterials(),
                    previous.terrainAppearances(),
                    previous.terrainLighting(),
                    previous.terrainPackets(),
                    nextModels,
                    previous.tileFlags(),
                    previous.lightingProfile(),
                    previous.collision(),
                    previous.objects(),
                    previous.bridges(),
                    previous.textures());
            return new AnimationRefreshResult(
                    refreshed, Set.of(), 0, rebuiltTiles, false);
        }

        Set<WorldZoneCoordinate> dirtyZones = changedAddresses.stream()
                .map(WorldZoneCoordinate::from)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        RenderWindowScene refreshed = new RenderWindowScene(
                previous.window(),
                previous.terrainMeshes(),
                previous.terrainMaterials(),
                previous.terrainAppearances(),
                previous.terrainLighting(),
                previous.terrainPackets(),
                nextModels,
                previous.tileFlags(),
                previous.lightingProfile(),
                previous.collision(),
                previous.objects(),
                previous.bridges(),
                previous.textures());
        return new AnimationRefreshResult(
                refreshed, dirtyZones, changedAddresses.size(), rebuiltTiles, false);
    }

    private AnimationRefreshResult refreshAnimationsFull(RenderWindowScene previous,
                                                         WorldRegionWindow prepared,
                                                         WorldDocument worldDocument,
                                                         int clientCycle) {
        Map<WorldTileAddress, List<ModelRenderPacket>> nextModels =
                buildWorldModelPackets(prepared, worldDocument, clientCycle);
        if (previous.modelPackets().equals(nextModels)) {
            return new AnimationRefreshResult(
                    previous, Set.of(), 0, nextModels.size(), true);
        }

        Set<WorldTileAddress> changedAddresses = new LinkedHashSet<>();
        changedAddresses.addAll(previous.modelPackets().keySet());
        changedAddresses.addAll(nextModels.keySet());
        changedAddresses.removeIf(address -> sameModelPresentation(
                previous.modelPackets().get(address), nextModels.get(address)));

        Set<WorldZoneCoordinate> dirtyZones = changedAddresses.stream()
                .map(WorldZoneCoordinate::from)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        RenderWindowScene refreshed = new RenderWindowScene(
                previous.window(),
                previous.terrainMeshes(),
                previous.terrainMaterials(),
                previous.terrainAppearances(),
                previous.terrainLighting(),
                previous.terrainPackets(),
                nextModels,
                previous.tileFlags(),
                previous.lightingProfile(),
                previous.collision(),
                previous.objects(),
                previous.bridges(),
                previous.textures());
        return new AnimationRefreshResult(
                refreshed, dirtyZones, changedAddresses.size(), nextModels.size(), true);
    }

    private boolean requiresSceneWideNormalMerge(WorldRegionWindow prepared,
                                                 WorldDocument worldDocument,
                                                 Set<WorldTileAddress> activeAddresses) {
        for (WorldTileAddress address : activeAddresses) {
            TileCoordinate local = paddedCoordinate(prepared, address);
            if (!worldDocument.contains(local.plane(), local.x(), local.y())) continue;
            for (com.rspsi.editor.model.WorldObject object :
                    worldDocument.tile(local).objects()) {
                if (definitions.objectAppearance(object.id())
                        .map(com.rspsi.cache.definition.ObjectAppearanceView::mergeNormals)
                        .orElse(false)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static TileCoordinate paddedCoordinate(WorldRegionWindow prepared,
                                                   WorldTileAddress address) {
        return new TileCoordinate(
                address.plane(),
                TERRAIN_CONTEXT_BORDER
                        + address.worldX() - prepared.minRegionX() * WorldRegion.REGION_SIZE,
                TERRAIN_CONTEXT_BORDER
                        + address.worldY() - prepared.minRegionY() * WorldRegion.REGION_SIZE);
    }

    private static boolean sameModelPresentation(List<ModelRenderPacket> first,
                                                 List<ModelRenderPacket> second) {
        if (first == second) return true;
        if (first == null || second == null || first.size() != second.size()) return false;
        ModelAnimationState none = ModelAnimationState.none();
        for (int index = 0; index < first.size(); index++) {
            ModelRenderPacket a = first.get(index);
            ModelRenderPacket b = second.get(index);
            if (!a.animationState().samePresentation(b.animationState())) return false;
            if (!a.withAnimationState(none).equals(b.withAnimationState(none))) return false;
        }
        return true;
    }

    private Map<WorldTileAddress, List<ModelRenderPacket>> buildWorldModelPackets(
            WorldRegionWindow prepared, WorldDocument worldDocument, int clientCycle) {
        var modelPackets = new LinkedHashMap<WorldTileAddress, List<ModelRenderPacket>>();
        ModelPacketBuilder worldModelBuilder = new ModelPacketBuilder(definitions, LightingProfile.osrs(), presentation);
        for (ModelRenderPacket packet : worldModelBuilder.build(worldDocument, clientCycle)) {
            ModelRenderPacket worldPacket = toWorldPacket(prepared, packet);
            if (worldPacket == null) continue;
            modelPackets.computeIfAbsent(WorldTileAddress.of(
                    worldPacket.anchor().x(), worldPacket.anchor().y(),
                    worldPacket.anchor().plane()), ignored -> new ArrayList<>())
                    .add(worldPacket);
        }
        return modelPackets;
    }

    private static ModelRenderPacket toWorldPacket(WorldRegionWindow prepared,
                                                        ModelRenderPacket packet) {
        int worldX = prepared.minRegionX() * WorldRegion.REGION_SIZE
                + packet.anchor().x() - TERRAIN_CONTEXT_BORDER;
        int worldY = prepared.minRegionY() * WorldRegion.REGION_SIZE
                + packet.anchor().y() - TERRAIN_CONTEXT_BORDER;
        if (worldX < 0 || worldY < 0
                || worldX < prepared.minRegionX() * WorldRegion.REGION_SIZE
                || worldY < prepared.minRegionY() * WorldRegion.REGION_SIZE
                || worldX >= (prepared.minRegionX() + prepared.regionWidth())
                        * WorldRegion.REGION_SIZE
                || worldY >= (prepared.minRegionY() + prepared.regionHeight())
                        * WorldRegion.REGION_SIZE) {
            return null;
        }
        return packet.withAnchor(new TileCoordinate(
                packet.anchor().plane(), worldX, worldY));
    }

    public record AnimationRefreshResult(
            RenderWindowScene scene,
            Set<WorldZoneCoordinate> dirtyZones,
            int changedTiles,
            int rebuiltModelTiles,
            boolean fullModelRebuild
    ) {
        /** Source-compatible constructor from before refresh-scope diagnostics. */
        public AnimationRefreshResult(RenderWindowScene scene,
                                      Set<WorldZoneCoordinate> dirtyZones,
                                      int changedTiles) {
            this(scene, dirtyZones, changedTiles, changedTiles, false);
        }

        public AnimationRefreshResult {
            scene = Objects.requireNonNull(scene, "scene");
            dirtyZones = Set.copyOf(Objects.requireNonNull(dirtyZones, "dirtyZones"));
            if (changedTiles < 0 || rebuiltModelTiles < 0) {
                throw new IllegalArgumentException("Animation refresh counts cannot be negative");
            }
        }
    }

    private static TileCoordinate localCoordinate(int plane, int x, int y) {
        return new TileCoordinate(plane, x, y);
    }

    private static WorldTileAddress address(WorldRegion region, com.rspsi.editor.model.TileCoordinate coordinate) {
        return WorldTileAddress.of(region.regionX() * WorldRegion.REGION_SIZE + coordinate.x(),
                region.regionY() * WorldRegion.REGION_SIZE + coordinate.y(), coordinate.plane());
    }
}
