package com.rspsi.editor.render;

import com.rspsi.editor.collision.CollisionTileSnapshot;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldRegionWindow;
import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.terrain.TerrainMesh;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Neutral scene snapshot for a bounded set of loaded OSRS regions. */
public record RenderWindowScene(
        WorldRegionWindow window,
        Map<WorldTileAddress, TerrainMesh> terrainMeshes,
        Map<WorldTileAddress, TerrainMaterial> terrainMaterials,
        Map<WorldTileAddress, TerrainLight> terrainLighting,
        Map<WorldTileAddress, CollisionTileSnapshot> collision,
        List<WorldRenderObject> objects,
        List<WorldBridgeLink> bridges
) {
    /** Source-compatible constructor for callers that do not publish collision yet. */
    public RenderWindowScene(WorldRegionWindow window,
                             Map<WorldTileAddress, TerrainMesh> terrainMeshes,
                             Map<WorldTileAddress, TerrainMaterial> terrainMaterials,
                             Map<WorldTileAddress, TerrainLight> terrainLighting,
                             List<WorldRenderObject> objects,
                             List<WorldBridgeLink> bridges) {
        this(window, terrainMeshes, terrainMaterials, terrainLighting, Map.of(), objects, bridges);
    }

    public RenderWindowScene {
        window = Objects.requireNonNull(window, "window");
        terrainMeshes = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(terrainMeshes, "terrainMeshes")));
        terrainMaterials = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(terrainMaterials, "terrainMaterials")));
        terrainLighting = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(terrainLighting, "terrainLighting")));
        collision = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(collision, "collision")));
        objects = List.copyOf(Objects.requireNonNull(objects, "objects"));
        bridges = List.copyOf(Objects.requireNonNull(bridges, "bridges"));
    }

    public boolean hasTile(WorldTileAddress address) {
        return terrainMeshes.containsKey(Objects.requireNonNull(address, "address"));
    }

    /** Returns the local tile coordinate within its source region. */
    public static TileCoordinate local(WorldTileAddress address) {
        Objects.requireNonNull(address, "address");
        return new TileCoordinate(address.plane(), address.regionLocalX(), address.regionLocalY());
    }
}
