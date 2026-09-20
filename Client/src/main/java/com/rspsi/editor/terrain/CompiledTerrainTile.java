package com.rspsi.editor.terrain;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.render.TerrainAppearance;
import com.rspsi.editor.render.TerrainRenderPacket;
import com.rspsi.editor.render.TerrainLight;

import java.util.Objects;

/** Canonical compiled terrain projection shared by renderers, previews and diagnostics. */
public record CompiledTerrainTile(
        TileCoordinate coordinate,
        TerrainMesh mesh,
        TerrainAppearance appearance,
        TerrainLight lighting,
        TerrainRenderPacket renderPacket,
        int effectivePlane,
        boolean bridge,
        boolean removesRoofs,
        int flags,
        int minimapRgb) {

    public CompiledTerrainTile {
        Objects.requireNonNull(coordinate, "coordinate");
        Objects.requireNonNull(mesh, "mesh");
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(lighting, "lighting");
        Objects.requireNonNull(renderPacket, "renderPacket");
        if (effectivePlane < -1) throw new IllegalArgumentException("effectivePlane must be >= -1");
    }
}
