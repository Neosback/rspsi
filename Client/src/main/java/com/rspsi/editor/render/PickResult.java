package com.rspsi.editor.render;

import com.rspsi.editor.model.TileCoordinate;

/**
 * Neutral result of viewport picking across both 2D and 3D backends.
 *
 * <p>Beyond identifying what was clicked, this carries the submission
 * metadata of the draw command that was actually hit - the scene layer,
 * face priority, depth bias, and texture. Those are the exact values that
 * decide how a surface resolves against a coplanar neighbour, so surfacing
 * them in the inspector makes a mis-rendered wall or decoration reportable
 * ("this one is WALL_DECORATION, priority 10, bias 0") instead of only
 * describable.</p>
 */
public record PickResult(TileCoordinate tile, int plane, int objectId, float distance,
                         SceneLayer.Kind layer, int priority, int depthBias, int textureId) {
    /** Compatibility constructor for tile-only legacy viewport picking. */
    public PickResult(TileCoordinate tile, int plane) {
        this(tile, plane, -1, Float.NaN);
    }

    /** Compatibility constructor from before submission metadata was carried. */
    public PickResult(TileCoordinate tile, int plane, int objectId, float distance) {
        this(tile, plane, objectId, distance, null, 0, 0, -1);
    }

    public PickResult {
        if (tile == null || plane < 0 || plane != tile.plane() || objectId < -1
                || (!Float.isNaN(distance) && (!Float.isFinite(distance) || distance < 0.0f))
                || priority < 0 || depthBias < 0 || textureId < -1) {
            throw new IllegalArgumentException("Pick result requires a valid tile and plane");
        }
    }

    public boolean objectHit() {
        return objectId >= 0;
    }

    /** True when the hit carries draw-command metadata worth inspecting. */
    public boolean hasSubmissionMetadata() {
        return layer != null;
    }
}
