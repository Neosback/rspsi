package com.rspsi.editor.render;

/** Per-corner neutral light values for one terrain tile. */
public record TerrainLight(
        int southWest,
        int southEast,
        int northEast,
        int northWest
) {
}
