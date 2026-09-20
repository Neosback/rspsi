package com.rspsi.editor.render.compiler;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.render.ModelRenderPacket;
import com.rspsi.editor.render.TerrainRenderPacket;
import com.rspsi.editor.terrain.CompiledTerrainTile;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** An immutable 8x8 compiled scene zone with its source revision. */
public final class SceneZone {
    public static final int ZONE_SIZE = 8;

    private final int plane;
    private final int zoneX;
    private final int zoneY;
    private final long revision;
    private final Map<TileCoordinate, CompiledTerrainTile> compiledTerrain;
    private final Map<TileCoordinate, TerrainRenderPacket> terrainPackets;
    private final List<ModelRenderPacket> modelPackets;

    public SceneZone(
            int plane,
            int zoneX,
            int zoneY,
            long revision,
            Map<TileCoordinate, CompiledTerrainTile> compiledTerrain,
            Map<TileCoordinate, TerrainRenderPacket> terrainPackets,
            List<ModelRenderPacket> modelPackets) {
        if (plane < 0 || zoneX < 0 || zoneY < 0 || revision < 0) {
            throw new IllegalArgumentException("Invalid scene zone identity");
        }
        this.plane = plane;
        this.zoneX = zoneX;
        this.zoneY = zoneY;
        this.revision = revision;
        this.compiledTerrain = Collections.unmodifiableMap(Map.copyOf(
                compiledTerrain == null ? Map.of() : compiledTerrain));
        this.terrainPackets = Collections.unmodifiableMap(Map.copyOf(
                terrainPackets == null ? Map.of() : terrainPackets));
        this.modelPackets = List.copyOf(modelPackets == null ? List.of() : modelPackets);
    }

    /** Compatibility constructor used by the original packet-only zone cache. */
    public SceneZone(
            int plane,
            int zoneX,
            int zoneY,
            Map<TileCoordinate, TerrainRenderPacket> terrainPackets,
            List<ModelRenderPacket> modelPackets) {
        this(plane, zoneX, zoneY, 0L, Map.of(), terrainPackets, modelPackets);
    }

    public int plane() { return plane; }
    public int zoneX() { return zoneX; }
    public int zoneY() { return zoneY; }
    public long revision() { return revision; }
    public Map<TileCoordinate, CompiledTerrainTile> compiledTerrain() { return compiledTerrain; }
    public Map<TileCoordinate, TerrainRenderPacket> terrainPackets() { return terrainPackets; }
    public List<ModelRenderPacket> modelPackets() { return modelPackets; }

    public InvalidationGraph.ZoneCoordinate coordinate() {
        return new InvalidationGraph.ZoneCoordinate(plane, zoneX, zoneY);
    }
}
