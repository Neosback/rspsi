package com.rspsi.editor.render;

/** Full-precision terrain vertex prepared for a renderer backend. */
public record TerrainRenderVertex(
        int x,
        int y,
        int height,
        /** OSRS packed HSL after the vertex light has been applied. */
        int packedHsl,
        int u,
        int v,
        int normalX,
        int normalY,
        int normalZ,
        int normalMagnitude
) {
    /** Compatibility constructor for callers that do not yet supply terrain normals. */
    public TerrainRenderVertex(int x, int y, int height, int packedHsl, int u, int v) {
        this(x, y, height, packedHsl, u, v,
                TerrainNormal.FLAT.x(), TerrainNormal.FLAT.y(),
                TerrainNormal.FLAT.z(), TerrainNormal.FLAT.magnitude());
    }

    public TerrainRenderVertex {
        if (x < 0 || x > 128 || y < 0 || y > 128) {
            throw new IllegalArgumentException("Terrain vertex must be inside a tile");
        }
        if (u < 0 || v < 0 || normalMagnitude < 0) {
            throw new IllegalArgumentException("Invalid terrain UV or normal data");
        }
    }
}
