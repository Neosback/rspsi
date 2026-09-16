package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;
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

    public RenderWindowSceneBuilder() {
        this.regions = new RenderSceneBuilder();
    }

    public RenderWindowSceneBuilder(DefinitionProvider definitions) {
        this.regions = new RenderSceneBuilder(Objects.requireNonNull(definitions, "definitions"));
    }

    /**
     * Stitches loaded neighboring region edges before building local meshes.
     * Missing regions remain absent from every output map.
     */
    public RenderWindowScene build(WorldRegionWindow window) {
        Objects.requireNonNull(window, "window");
        window.stitchSharedEdges();
        var meshes = new LinkedHashMap<WorldTileAddress, com.rspsi.editor.terrain.TerrainMesh>();
        var materials = new LinkedHashMap<WorldTileAddress, TerrainMaterial>();
        var lighting = new LinkedHashMap<WorldTileAddress, TerrainLight>();
        List<WorldRenderObject> objects = new ArrayList<>();
        List<WorldBridgeLink> bridges = new ArrayList<>();

        for (WorldRegion region : window.regions().values().stream()
                .sorted(java.util.Comparator.comparingInt(WorldRegion::regionX)
                        .thenComparingInt(WorldRegion::regionY)).toList()) {
            RenderScene scene = regions.build(region.document());
            int originX = region.regionX() * WorldRegion.REGION_SIZE;
            int originY = region.regionY() * WorldRegion.REGION_SIZE;
            scene.terrainMeshes().forEach((local, mesh) ->
                    meshes.put(WorldTileAddress.of(originX + local.x(), originY + local.y(), local.plane()), mesh));
            scene.terrainMaterials().forEach((local, material) ->
                    materials.put(WorldTileAddress.of(originX + local.x(), originY + local.y(), local.plane()), material));
            scene.terrainLighting().forEach((local, light) ->
                    lighting.put(WorldTileAddress.of(originX + local.x(), originY + local.y(), local.plane()), light));
            for (RenderObject object : scene.renderObjects()) {
                objects.add(new WorldRenderObject(WorldTileAddress.of(
                        originX + object.object().x(), originY + object.object().y(), object.object().plane()), object));
            }
            for (BridgeLink bridge : scene.bridges()) {
                bridges.add(new WorldBridgeLink(address(region, bridge.upper()), address(region, bridge.lower())));
            }
        }
        return new RenderWindowScene(window, meshes, materials, lighting, objects, bridges);
    }

    private static WorldTileAddress address(WorldRegion region, com.rspsi.editor.model.TileCoordinate coordinate) {
        return WorldTileAddress.of(region.regionX() * WorldRegion.REGION_SIZE + coordinate.x(),
                region.regionY() * WorldRegion.REGION_SIZE + coordinate.y(), coordinate.plane());
    }
}
