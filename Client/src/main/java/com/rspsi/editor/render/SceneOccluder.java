package com.rspsi.editor.render;

/** Camera-independent occluder input derived from the scene definition. */
public record SceneOccluder(
        int type,
        int minTileX,
        int maxTileX,
        int minTileY,
        int maxTileY,
        int minPlane,
        int maxPlane,
        int minWorldX,
        int maxWorldX,
        int minWorldY,
        int maxWorldY,
        int minHeight,
        int maxHeight
) {
    public SceneOccluder {
        if (type <= 0 || minTileX > maxTileX || minTileY > maxTileY
                || minPlane > maxPlane || minWorldX > maxWorldX || minWorldY > maxWorldY
                || minHeight > maxHeight) {
            throw new IllegalArgumentException("Invalid scene occluder bounds");
        }
        if ((type == 1 && minWorldX != maxWorldX)
                || (type == 2 && minWorldY != maxWorldY)) {
            throw new IllegalArgumentException("Vertical occluders must have a fixed world axis");
        }
    }
}
