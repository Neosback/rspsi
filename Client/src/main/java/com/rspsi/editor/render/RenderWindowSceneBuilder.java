package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.editor.collision.CollisionMap;
import com.rspsi.editor.collision.CollisionTileSnapshot;
import com.rspsi.editor.collision.OsrsCollisionBuilder;
import com.rspsi.editor.model.BridgeLink;
import com.rspsi.editor.model.WorldRegion;
import com.rspsi.editor.model.WorldRegionWindow;
import com.rspsi.editor.model.WorldTileAddress;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Builds world-addressed neutral geometry while preserving window holes. */
public final class RenderWindowSceneBuilder {
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
        Objects.requireNonNull(window, "window");
        WorldRegionWindow prepared = window.copy();
        prepared.stitchSharedEdges();
        var meshes = new LinkedHashMap<WorldTileAddress, com.rspsi.editor.terrain.TerrainMesh>();
        var materials = new LinkedHashMap<WorldTileAddress, TerrainMaterial>();
        var appearances = new LinkedHashMap<WorldTileAddress, TerrainAppearance>();
        var lighting = new LinkedHashMap<WorldTileAddress, TerrainLight>();
        var collision = new LinkedHashMap<WorldTileAddress, CollisionTileSnapshot>();
        List<WorldRenderObject> objects = new ArrayList<>();
        List<WorldBridgeLink> bridges = new ArrayList<>();
        CollisionMap windowCollision = definitions == null
                ? OsrsCollisionBuilder.fromWindow(prepared)
                : OsrsCollisionBuilder.fromWindow(prepared, definitions);

        for (WorldRegion region : prepared.regions().values().stream()
                .sorted(java.util.Comparator.comparingInt(WorldRegion::regionX)
                        .thenComparingInt(WorldRegion::regionY)).toList()) {
            RenderScene scene = regions.build(region.document());
            int originX = region.regionX() * WorldRegion.REGION_SIZE;
            int originY = region.regionY() * WorldRegion.REGION_SIZE;
            scene.terrainMeshes().forEach((local, mesh) ->
                    meshes.put(WorldTileAddress.of(originX + local.x(), originY + local.y(), local.plane()), mesh));
            scene.terrainMaterials().forEach((local, material) ->
                    materials.put(WorldTileAddress.of(originX + local.x(), originY + local.y(), local.plane()), material));
            scene.terrainAppearances().forEach((local, appearance) ->
                    appearances.put(WorldTileAddress.of(originX + local.x(), originY + local.y(), local.plane()), appearance));
            scene.terrainLighting().forEach((local, light) ->
                    lighting.put(WorldTileAddress.of(originX + local.x(), originY + local.y(), local.plane()), light));
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
        return new RenderWindowScene(prepared, meshes, materials, appearances, lighting,
                LightingProfile.osrs(), collision, objects, bridges);
    }

    private static WorldTileAddress address(WorldRegion region, com.rspsi.editor.model.TileCoordinate coordinate) {
        return WorldTileAddress.of(region.regionX() * WorldRegion.REGION_SIZE + coordinate.x(),
                region.regionY() * WorldRegion.REGION_SIZE + coordinate.y(), coordinate.plane());
    }
}
