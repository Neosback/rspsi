package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;

import java.util.Objects;

/** Ordered material submission range in a backend-neutral upload plan. */
public record GpuDrawCommand(
        WorldTileAddress tile,
        SceneLayer.Kind layer,
        SubmissionPass pass,
        int firstIndex,
        int indexCount,
        int textureId,
        int priority,
        int depthBias,
        int objectId
) {
    public enum SubmissionPass {
        OPAQUE,
        ALPHA
    }

    public GpuDrawCommand {
        tile = Objects.requireNonNull(tile, "tile");
        layer = Objects.requireNonNull(layer, "layer");
        pass = Objects.requireNonNull(pass, "pass");
        if (firstIndex < 0 || indexCount <= 0 || textureId < -1
                || priority < 0 || priority > 255 || depthBias < 0 || depthBias > 255
                || objectId < -1) {
            throw new IllegalArgumentException("Invalid GPU draw command");
        }
    }

    /** Compatibility constructor before raw RuneScape face bias was carried. */
    public GpuDrawCommand(WorldTileAddress tile, SceneLayer.Kind layer, SubmissionPass pass,
                          int firstIndex, int indexCount, int textureId, int priority,
                          int objectId) {
        this(tile, layer, pass, firstIndex, indexCount, textureId, priority, 0, objectId);
    }

    boolean canMerge(WorldTileAddress nextTile, SceneLayer.Kind nextLayer,
                     SubmissionPass nextPass, int nextTextureId,
                     int nextPriority, int nextDepthBias, int nextObjectId, int nextFirstIndex) {
        return tile.equals(nextTile) && layer == nextLayer && pass == nextPass
                && textureId == nextTextureId && priority == nextPriority
                && depthBias == nextDepthBias
                && objectId == nextObjectId
                && firstIndex + indexCount == nextFirstIndex;
    }

    GpuDrawCommand extend(int additionalIndices) {
        return new GpuDrawCommand(tile, layer, pass, firstIndex,
                indexCount + additionalIndices, textureId, priority, depthBias, objectId);
    }
}
