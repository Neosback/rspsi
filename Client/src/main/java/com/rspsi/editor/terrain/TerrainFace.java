package com.rspsi.editor.terrain;

/** One triangle of the terrain mesh; material 0 is underlay and 1 is overlay. */
public record TerrainFace(int material, int a, int b, int c) {
    public TerrainFace {
        if (material < 0 || material > 1 || a < 0 || b < 0 || c < 0) {
            throw new IllegalArgumentException("Invalid terrain face");
        }
    }
}
