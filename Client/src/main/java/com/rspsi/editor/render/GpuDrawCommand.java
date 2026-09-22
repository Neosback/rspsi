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
        int objectId,
        RenderMode renderMode,
        WallDecorationPresentation wallDecorationPresentation
) {
    public enum SubmissionPass {
        OPAQUE,
        ALPHA
    }

    /** Depth/order contract carried by a RuneScape renderable. */
    public enum RenderMode {
        DEFAULT,
        SORTED,
        SORTED_NO_DEPTH,
        UNSORTED,
        UNSORTED_NO_DEPTH;

        public boolean noDepth() {
            return this == SORTED_NO_DEPTH || this == UNSORTED_NO_DEPTH;
        }
    }

    public GpuDrawCommand {
        tile = Objects.requireNonNull(tile, "tile");
        layer = Objects.requireNonNull(layer, "layer");
        pass = Objects.requireNonNull(pass, "pass");
        renderMode = Objects.requireNonNull(renderMode, "renderMode");
        wallDecorationPresentation = Objects.requireNonNull(
                wallDecorationPresentation, "wallDecorationPresentation");
        if (firstIndex < 0 || indexCount <= 0 || textureId < -1
                || priority < 0 || priority > 255 || depthBias < 0 || depthBias > 255
                || objectId < -1) {
            throw new IllegalArgumentException("Invalid GPU draw command");
        }
    }

    /** Compatibility constructor before wall-decoration presentation metadata. */
    public GpuDrawCommand(WorldTileAddress tile, SceneLayer.Kind layer, SubmissionPass pass,
                          int firstIndex, int indexCount, int textureId, int priority,
                          int depthBias, int objectId, RenderMode renderMode) {
        this(tile, layer, pass, firstIndex, indexCount, textureId, priority, depthBias,
                objectId, renderMode, WallDecorationPresentation.none());
    }

    /** Compatibility constructor before raw RuneScape face bias was carried. */
    public GpuDrawCommand(WorldTileAddress tile, SceneLayer.Kind layer, SubmissionPass pass,
                          int firstIndex, int indexCount, int textureId, int priority,
                          int objectId) {
        this(tile, layer, pass, firstIndex, indexCount, textureId, priority, 0, objectId,
                RenderMode.DEFAULT, WallDecorationPresentation.none());
    }

    /** Compatibility constructor before render modes were carried. */
    public GpuDrawCommand(WorldTileAddress tile, SceneLayer.Kind layer, SubmissionPass pass,
                          int firstIndex, int indexCount, int textureId, int priority,
                          int depthBias, int objectId) {
        this(tile, layer, pass, firstIndex, indexCount, textureId, priority, depthBias, objectId,
                RenderMode.DEFAULT);
    }

    boolean canMerge(WorldTileAddress nextTile, SceneLayer.Kind nextLayer,
                     SubmissionPass nextPass, int nextTextureId,
                     int nextPriority, int nextDepthBias, int nextObjectId, int nextFirstIndex) {
        return canMerge(nextTile, nextLayer, nextPass, nextTextureId, nextPriority,
                nextDepthBias, nextObjectId, nextFirstIndex, RenderMode.DEFAULT);
    }

    boolean canMerge(WorldTileAddress nextTile, SceneLayer.Kind nextLayer,
                     SubmissionPass nextPass, int nextTextureId,
                     int nextPriority, int nextDepthBias, int nextObjectId, int nextFirstIndex,
                     RenderMode nextRenderMode) {
        boolean tileCompatible = tile.equals(nextTile)
                || (layer == SceneLayer.Kind.TERRAIN && nextLayer == SceneLayer.Kind.TERRAIN
                    && tile.plane() == nextTile.plane());
        return tileCompatible && layer == nextLayer && pass == nextPass
                && textureId == nextTextureId && priority == nextPriority
                && depthBias == nextDepthBias
                && objectId == nextObjectId
                && renderMode == nextRenderMode
                && firstIndex + indexCount == nextFirstIndex;
    }

    GpuDrawCommand extend(int additionalIndices) {
        return new GpuDrawCommand(tile, layer, pass, firstIndex,
                indexCount + additionalIndices, textureId, priority, depthBias, objectId, renderMode,
                wallDecorationPresentation);
    }
}
