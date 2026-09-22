package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTile;

/**
 * Neutral result of viewport picking across both 2D and 3D backends.
 *
 * <p>Beyond identifying what was clicked, this carries the exact ray-intersected
 * hit {@link #tile()}, the base anchor {@link #objectTile()} if an object was hit,
 * and the submission metadata of the draw command that was actually hit - the scene layer,
 * face priority, depth bias, and texture.</p>
 */
public record PickResult(WorldTile tile, WorldTile objectTile, int plane, int objectId, float distance,
                         SceneLayer.Kind layer, int priority, int depthBias, int textureId) {
    /** Compatibility constructor for tile-only legacy viewport picking. */
    public PickResult(WorldTile tile, int plane) {
        this(tile, null, plane, -1, Float.NaN, null, 0, 0, -1);
    }

    /** Compatibility constructor from before submission metadata was carried. */
    public PickResult(WorldTile tile, int plane, int objectId, float distance) {
        this(tile, null, plane, objectId, distance, null, 0, 0, -1);
    }

    /** Compatibility constructor from before objectTile was carried. */
    public PickResult(WorldTile tile, int plane, int objectId, float distance,
                      SceneLayer.Kind layer, int priority, int depthBias, int textureId) {
        this(tile, null, plane, objectId, distance, layer, priority, depthBias, textureId);
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
