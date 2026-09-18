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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Builds world-addressed neutral geometry while preserving window holes. */
public final class RenderWindowSceneBuilder {
    private static final int TERRAIN_CONTEXT_BORDER = 5;
    private final RenderSceneBuilder regions;
    private final DefinitionProvider definitions;

    public RenderWindowSceneBuilder() {
        this.regions = new RenderSceneBuilder();
        this.definitions = null;
    }

    public RenderWindowSceneBuilder(DefinitionProvider definitions) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.regions = new RenderSceneBuilder(this.definitions);
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
        var worldAppearances = definitions == null
                ? java.util.Map.<TileCoordinate, TerrainAppearance>of()
                : new TerrainAppearanceBuilder().build(worldDocument, definitions);
        TerrainShadowMap worldShadows = definitions == null ? null
                : TerrainShadowMap.from(worldDocument, definitions);
        var worldLighting = TerrainLighting.build(worldDocument, LightingProfile.osrs(), worldShadows);
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
            ModelPacketBuilder worldModelBuilder = new ModelPacketBuilder(definitions);
            for (ModelRenderPacket packet : worldModelBuilder.build(worldDocument, clientCycle)) {
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
                    continue;
                }
                ModelRenderPacket worldPacket = packet.withAnchor(new TileCoordinate(
                        packet.anchor().plane(), worldX, worldY));
                modelPackets.computeIfAbsent(WorldTileAddress.of(
                        worldX, worldY, packet.anchor().plane()), ignored -> new ArrayList<>())
                        .add(worldPacket);
            }
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

    private static TileCoordinate localCoordinate(int plane, int x, int y) {
        return new TileCoordinate(plane, x, y);
    }

    private static WorldTileAddress address(WorldRegion region, com.rspsi.editor.model.TileCoordinate coordinate) {
        return WorldTileAddress.of(region.regionX() * WorldRegion.REGION_SIZE + coordinate.x(),
                region.regionY() * WorldRegion.REGION_SIZE + coordinate.y(), coordinate.plane());
    }
}
