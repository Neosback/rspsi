package com.rspsi.editor.render;

/** Full-precision terrain vertex prepared for a renderer backend. */
public record TerrainRenderVertex(
        int x,
        int y,
        int height,
        /** OSRS packed HSL after the vertex light has been applied. */
        int packedHsl,
        int u,
        int v
) {
    public TerrainRenderVertex {
        if (x < 0 || x > 128 || y < 0 || y > 128) {
            throw new IllegalArgumentException("Terrain vertex must be inside a tile");
        }
        if (u < 0 || v < 0) {
            throw new IllegalArgumentException("Terrain UV coordinates cannot be negative");
        }
    }
}
