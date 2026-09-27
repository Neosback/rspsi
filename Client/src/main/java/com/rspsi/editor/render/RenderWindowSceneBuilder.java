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
        return build(window, clientCycle, SceneFocus.of(window));
    }

    /**
     * Builds only the tiles inside {@code focus}. Every loaded region of the window
     * still stitches and feeds blending, lighting, contouring and collision, so tiles at
     * the focus edge match a full build; tiles outside the focus are simply not emitted.
     */
    public RenderWindowScene build(WorldRegionWindow window, int clientCycle, SceneFocus focus) {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(focus, "focus");
        if (clientCycle < 0) throw new IllegalArgumentException("Client cycle cannot be negative");
        WorldRegionWindow prepared = window.copy();
        prepared.stitchSharedEdges();
        SceneFocus emitted = focus.clampTo(prepared);
        WorldDocument worldDocument = prepared.materializePaddedWorldDocument(TERRAIN_CONTEXT_BORDER);
        int paddedOriginX = prepared.minRegionX() * WorldRegion.REGION_SIZE - TERRAIN_CONTEXT_BORDER;
        int paddedOriginY = prepared.minRegionY() * WorldRegion.REGION_SIZE - TERRAIN_CONTEXT_BORDER;
        var compiledWorld = definitions == null || emitted == null
                ? java.util.Map.<TileCoordinate, CompiledTerrainTile>of()
                : new TerrainSceneCompiler().compileZones(worldDocument, definitions,
                        LightingProfile.osrs(), focusZones(worldDocument, emitted,
                                paddedOriginX, paddedOriginY));
        var worldLighting = definitions == null
                ? TerrainLighting.build(worldDocument, LightingProfile.osrs(), null)
                : null;
        var meshes = new LinkedHashMap<WorldTileAddress, com.rspsi.editor.terrain.TerrainMesh>();
        var materials = new LinkedHashMap<WorldTileAddress, TerrainMaterial>();
        var appearances = new LinkedHashMap<WorldTileAddress, TerrainAppearance>();
        var lighting = new LinkedHashMap<WorldTileAddress, TerrainLight>();
        var packets = new LinkedHashMap<WorldTileAddress, TerrainRenderPacket>();
        var modelPackets = new LinkedHashMap<WorldTileAddress, List<ModelRenderPacket>>();
        var tileFlags = new LinkedHashMap<WorldTileAddress, Integer>();
        TerrainPacketBuilder packetBuilder = new TerrainPacketBuilder();
        com.rspsi.editor.terrain.TerrainMeshBuilder plainMeshes = new com.rspsi.editor.terrain.TerrainMeshBuilder();
        var collision = new LinkedHashMap<WorldTileAddress, CollisionTileSnapshot>();
        List<WorldRenderObject> objects = new ArrayList<>();
        List<WorldBridgeLink> bridges = new ArrayList<>();
        CollisionMap windowCollision = definitions == null
                ? OsrsCollisionBuilder.fromWindow(prepared)
                : OsrsCollisionBuilder.fromWindow(prepared, definitions);

        for (WorldRegion region : prepared.regions().values().stream()
                .sorted(java.util.Comparator.comparingInt(WorldRegion::regionX)
                        .thenComparingInt(WorldRegion::regionY)).toList()) {
            if (emitted == null || !emitted.intersectsRegion(region.regionX(), region.regionY())) continue;
            WorldDocument document = region.document();
            int originX = region.regionX() * WorldRegion.REGION_SIZE;
            int originY = region.regionY() * WorldRegion.REGION_SIZE;
            int fromX = Math.max(0, emitted.minX() - originX);
            int toX = Math.min(document.width() - 1, emitted.maxX() - originX);
            int fromY = Math.max(0, emitted.minY() - originY);
            int toY = Math.min(document.length() - 1, emitted.maxY() - originY);
            int collisionOffsetX = (region.regionX() - prepared.minRegionX()) * WorldRegion.REGION_SIZE;
            int collisionOffsetY = (region.regionY() - prepared.minRegionY()) * WorldRegion.REGION_SIZE;
            for (int plane = 0; plane < document.planes(); plane++) {
                for (int x = fromX; x <= toX; x++) {
                    for (int y = fromY; y <= toY; y++) {
                        WorldTileAddress address = WorldTileAddress.of(originX + x, originY + y, plane);
                        com.rspsi.editor.model.TileSnapshot snapshot = document.tile(plane, x, y).snapshot();
                        TileCoordinate worldCoordinate = new TileCoordinate(plane,
                                originX + x - paddedOriginX, originY + y - paddedOriginY);
                        tileFlags.put(address, snapshot.flags());
                        if (definitions != null) {
                            CompiledTerrainTile compiled = compiledWorld.get(worldCoordinate);
                            meshes.put(address, compiled.mesh());
                            materials.put(address, regions.material(snapshot));
                            appearances.put(address, compiled.appearance());
                            lighting.put(address, compiled.lighting());
                            packets.put(address, packetBuilder.build(
                                    new TileCoordinate(plane, x, y), compiled.mesh(),
                                    compiled.appearance(), compiled.lighting()));
                        } else {
                            meshes.put(address, plainMeshes.build(snapshot));
                            lighting.put(address, worldLighting.get(worldCoordinate));
                        }
                        collision.put(address, CollisionTileSnapshot.from(windowCollision,
                                new com.rspsi.editor.model.TileCoordinate(plane,
                                        collisionOffsetX + x, collisionOffsetY + y)));
                        for (com.rspsi.editor.model.WorldObject object : snapshot.objects()) {
                            objects.add(new WorldRenderObject(address, regions.resolve(object)));
                        }
                    }
                }
            }
            for (BridgeLink bridge : document.bridgeLinks()) {
                WorldTileAddress upper = address(region, bridge.upper());
                if (emitted.contains(upper)) {
                    bridges.add(new WorldBridgeLink(upper, address(region, bridge.lower())));
                }
            }
        }
        if (definitions != null && emitted != null) {
            modelPackets.putAll(buildWorldModelPackets(prepared, worldDocument, clientCycle,
                    emitted, paddedOriginX, paddedOriginY));
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
        long totalStart = System.nanoTime();
        Objects.requireNonNull(previous, "previous");
        if (clientCycle < 0) throw new IllegalArgumentException("Client cycle cannot be negative");
        if (definitions == null) {
            return new AnimationRefreshResult(
                    previous, Set.of(), Set.of(), 0,
                    new AnimationRefreshTimings(0L, 0L, 0L, 0L,
                            System.nanoTime() - totalStart));
        }

        long activeScanStart = System.nanoTime();
        Set<WorldTileAddress> activeAddresses = new LinkedHashSet<>();
        for (Map.Entry<WorldTileAddress, List<ModelRenderPacket>> entry : previous.modelPackets().entrySet()) {
            for (ModelRenderPacket packet : entry.getValue()) {
                if (packet.animationState().active() || packet.supportsAnimation() || packet.animationId() >= 0) {
                    activeAddresses.add(entry.getKey());
                    break;
                }
            }
        }
        long activeScanNanos = System.nanoTime() - activeScanStart;
        if (activeAddresses.isEmpty()) {
            return new AnimationRefreshResult(
                    previous, Set.of(), Set.of(), 0,
                    new AnimationRefreshTimings(
                            activeScanNanos, 0L, 0L, 0L,
                            System.nanoTime() - totalStart));
        }

        // The RenderWindowScene already retains the prepared, stitched source
        // window. Re-materialize only the padded document needed for contour,
        // placement-height and wall-displacement rules; do not re-copy and
        // re-stitch the whole window on every animation frame.
        WorldRegionWindow prepared = previous.window();
        long paddedWorldStart = System.nanoTime();
        WorldDocument worldDocument =
                prepared.materializePaddedWorldDocument(TERRAIN_CONTEXT_BORDER);
        long paddedWorldNanos = System.nanoTime() - paddedWorldStart;

        // Animated locations never join the scene normal merge, so each active
        // tile can be rebuilt alone; its static packets keep their merged normals.
        long modelRebuildStart = System.nanoTime();
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

            List<ModelRenderPacket> worldPackets = new ArrayList<>();
            for (ModelRenderPacket packet : modelBuilder.buildTile(worldDocument, local, clientCycle)) {
                ModelRenderPacket worldPacket = toWorldPacket(prepared, packet);
                if (worldPacket != null) worldPackets.add(worldPacket);
            }
            worldPackets = ModelPacketBuilder.keepStaticPackets(
                    worldPackets, previous.modelPackets().get(address));
            if (worldPackets.isEmpty()) {
                nextModels.remove(address);
            } else {
                nextModels.put(address, worldPackets);
            }
            rebuiltTiles++;
        }
        long modelRebuildNanos = System.nanoTime() - modelRebuildStart;

        long changeDetectionStart = System.nanoTime();
        Set<WorldTileAddress> changedAddresses = new LinkedHashSet<>();
        for (WorldTileAddress address : activeAddresses) {
            if (!sameModelPresentation(
                    previous.modelPackets().get(address), nextModels.get(address))) {
                changedAddresses.add(address);
            }
        }
        Set<WorldZoneCoordinate> dirtyZones = changedAddresses.stream()
                .map(WorldZoneCoordinate::from)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        // With no presentation change, callers still observe the refreshed
        // ModelAnimationState.clientCycle as diagnostic timing state, so the
        // rebuilt packets are published without marking any GPU zone dirty.
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
        long changeDetectionNanos = System.nanoTime() - changeDetectionStart;
        return new AnimationRefreshResult(
                refreshed, dirtyZones, changedAddresses, rebuiltTiles,
                new AnimationRefreshTimings(
                        activeScanNanos, paddedWorldNanos,
                        modelRebuildNanos, changeDetectionNanos,
                        System.nanoTime() - totalStart));
    }

    /** The padded-document zones, on every plane, that cover {@code focus}. */
    private static Set<com.rspsi.editor.render.compiler.InvalidationGraph.ZoneCoordinate> focusZones(
            WorldDocument padded, SceneFocus focus, int paddedOriginX, int paddedOriginY) {
        int minZoneX = Math.max(0, focus.minX() - paddedOriginX) >> 3;
        int minZoneY = Math.max(0, focus.minY() - paddedOriginY) >> 3;
        int maxZoneX = Math.min(padded.width() - 1, focus.maxX() - paddedOriginX) >> 3;
        int maxZoneY = Math.min(padded.length() - 1, focus.maxY() - paddedOriginY) >> 3;
        Set<com.rspsi.editor.render.compiler.InvalidationGraph.ZoneCoordinate> zones = new LinkedHashSet<>();
        for (int plane = 0; plane < padded.planes(); plane++) {
            for (int zoneX = minZoneX; zoneX <= maxZoneX; zoneX++) {
                for (int zoneY = minZoneY; zoneY <= maxZoneY; zoneY++) {
                    zones.add(new com.rspsi.editor.render.compiler.InvalidationGraph.ZoneCoordinate(
                            plane, zoneX, zoneY));
                }
            }
        }
        return zones;
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
            WorldRegionWindow prepared, WorldDocument worldDocument, int clientCycle,
            SceneFocus focus, int paddedOriginX, int paddedOriginY) {
        var modelPackets = new LinkedHashMap<WorldTileAddress, List<ModelRenderPacket>>();
        ModelPacketBuilder worldModelBuilder = new ModelPacketBuilder(definitions, LightingProfile.osrs(), presentation);
        // One extra tile on each side lets scene normal merging see locations that
        // touch the focus edge; their own packets are dropped below.
        for (ModelRenderPacket packet : worldModelBuilder.build(worldDocument, clientCycle,
                focus.minX() - paddedOriginX - 1, focus.minY() - paddedOriginY - 1,
                focus.maxX() - paddedOriginX + 1, focus.maxY() - paddedOriginY + 1)) {
            ModelRenderPacket worldPacket = toWorldPacket(prepared, packet);
            if (worldPacket == null
                    || !focus.contains(worldPacket.anchor().x(), worldPacket.anchor().y())) continue;
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

    public record AnimationRefreshTimings(
            long activeScanNanos,
            long paddedWorldNanos,
            long modelRebuildNanos,
            long changeDetectionNanos,
            long totalNanos
    ) {
        public AnimationRefreshTimings {
            if (activeScanNanos < 0L || paddedWorldNanos < 0L
                    || modelRebuildNanos < 0L
                    || changeDetectionNanos < 0L || totalNanos < 0L) {
                throw new IllegalArgumentException("Animation refresh timings cannot be negative");
            }
        }

        public static AnimationRefreshTimings empty() {
            return new AnimationRefreshTimings(0L, 0L, 0L, 0L, 0L);
        }
    }

    public record AnimationRefreshResult(
            RenderWindowScene scene,
            Set<WorldZoneCoordinate> dirtyZones,
            Set<WorldTileAddress> changedAddresses,
            int rebuiltModelTiles,
            AnimationRefreshTimings timings
    ) {
        public AnimationRefreshResult {
            scene = Objects.requireNonNull(scene, "scene");
            dirtyZones = Set.copyOf(Objects.requireNonNull(dirtyZones, "dirtyZones"));
            changedAddresses = Set.copyOf(Objects.requireNonNull(changedAddresses, "changedAddresses"));
            timings = Objects.requireNonNull(timings, "timings");
            if (rebuiltModelTiles < 0) {
                throw new IllegalArgumentException("Animation refresh counts cannot be negative");
            }
        }

        /** Tiles whose model presentation changed; their zones are {@link #dirtyZones()}. */
        public int changedTiles() {
            return changedAddresses.size();
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
