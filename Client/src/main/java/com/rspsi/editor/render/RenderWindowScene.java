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
import java.util.Collections;

/** Neutral scene snapshot for a bounded set of loaded OSRS regions. */
public record RenderWindowScene(
        WorldRegionWindow window,
        Map<WorldTileAddress, TerrainMesh> terrainMeshes,
        Map<WorldTileAddress, TerrainMaterial> terrainMaterials,
        Map<WorldTileAddress, TerrainAppearance> terrainAppearances,
        Map<WorldTileAddress, TerrainLight> terrainLighting,
        Map<WorldTileAddress, TerrainRenderPacket> terrainPackets,
        Map<WorldTileAddress, List<ModelRenderPacket>> modelPackets,
        Map<WorldTileAddress, Integer> tileFlags,
        LightingProfile lightingProfile,
        Map<WorldTileAddress, CollisionTileSnapshot> collision,
        List<WorldRenderObject> objects,
        List<WorldBridgeLink> bridges,
        Map<Integer, RenderTextureResource> textures
) {
    /** Source-compatible constructor for callers that do not publish collision yet. */
    public RenderWindowScene(WorldRegionWindow window,
                             Map<WorldTileAddress, TerrainMesh> terrainMeshes,
                             Map<WorldTileAddress, TerrainMaterial> terrainMaterials,
                             Map<WorldTileAddress, TerrainLight> terrainLighting,
                             List<WorldRenderObject> objects,
                             List<WorldBridgeLink> bridges) {
        this(window, terrainMeshes, terrainMaterials, Map.of(), terrainLighting,
                Map.of(), Map.of(), Map.of(), LightingProfile.osrs(), Map.of(), objects, bridges, Map.of());
    }

    public RenderWindowScene {
        window = Objects.requireNonNull(window, "window");
        terrainMeshes = orderedImmutableMap(terrainMeshes, "terrainMeshes");
        terrainMaterials = orderedImmutableMap(terrainMaterials, "terrainMaterials");
        terrainAppearances = orderedImmutableMap(terrainAppearances, "terrainAppearances");
        terrainLighting = orderedImmutableMap(terrainLighting, "terrainLighting");
        terrainPackets = orderedImmutableMap(terrainPackets, "terrainPackets");
        modelPackets = orderedImmutableListMap(modelPackets, "modelPackets");
        tileFlags = orderedImmutableMap(tileFlags, "tileFlags");
        lightingProfile = Objects.requireNonNull(lightingProfile, "lightingProfile");
        collision = orderedImmutableMap(collision, "collision");
        objects = List.copyOf(Objects.requireNonNull(objects, "objects"));
        bridges = List.copyOf(Objects.requireNonNull(bridges, "bridges"));
        textures = orderedImmutableMap(textures, "textures");
    }

    private static <K, V> Map<K, V> orderedImmutableMap(Map<K, V> values, String name) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(values, name)));
    }

    private static <K, V> Map<K, List<V>> orderedImmutableListMap(Map<K, List<V>> values, String name) {
        Map<K, List<V>> copy = new LinkedHashMap<>();
        Objects.requireNonNull(values, name).forEach((key, value) ->
                copy.put(key, List.copyOf(Objects.requireNonNull(value, name + " value"))));
        return Collections.unmodifiableMap(copy);
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
