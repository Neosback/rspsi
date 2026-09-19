package com.rspsi.editor.render.compiler;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.render.ModelRenderPacket;
import com.rspsi.editor.render.TerrainRenderPacket;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An 8x8 tile compiled scene zone (matching the canonical OSRS chunk dimensions).
 */
public final class SceneZone {
    public static final int ZONE_SIZE = 8;

    private final int plane;
    private final int zoneX;
    private final int zoneY;
    private final Map<TileCoordinate, TerrainRenderPacket> terrainPackets;
    private final List<ModelRenderPacket> modelPackets;

    public SceneZone(
            int plane,
            int zoneX,
            int zoneY,
            Map<TileCoordinate, TerrainRenderPacket> terrainPackets,
            List<ModelRenderPacket> modelPackets
    ) {
        this.plane = plane;
        this.zoneX = zoneX;
        this.zoneY = zoneY;
        this.terrainPackets = Collections.unmodifiableMap(Map.copyOf(terrainPackets));
        this.modelPackets = List.copyOf(modelPackets);
    }

    public int plane() {
        return plane;
    }

    public int zoneX() {
        return zoneX;
    }

    public int zoneY() {
        return zoneY;
    }

    public Map<TileCoordinate, TerrainRenderPacket> terrainPackets() {
        return terrainPackets;
    }

    public List<ModelRenderPacket> modelPackets() {
        return modelPackets;
    }
}
