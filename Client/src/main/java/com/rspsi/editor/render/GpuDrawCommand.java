package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;

import java.util.List;
import java.util.Objects;

/** Ordered material submission range in a backend-neutral upload plan. */
public record GpuDrawCommand(
        WorldTileAddress tile,
        int scenePlane,
        int planeCullLevel,
        SceneLayer.Kind layer,
        SubmissionPass pass,
        int firstIndex,
        int indexCount,
        int textureId,
        int priority,
        int depthBias,
        int objectId,
        RenderMode renderMode,
        WallDecorationPresentation wallDecorationPresentation,
        GameObjectSceneMetadata gameObjectSceneMetadata,
        List<ClientModelBounds> clientRenderableBounds,
        List<ClientRenderablePlacement> clientRenderablePlacements,
        ModelContourContract.Metadata contourMetadata,
        SceneObjectIdentity sceneObjectIdentity,
        int placementHeight,
        int modelAnchorX,
        int modelAnchorY
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
        UNSORTED_NO_DEPTH,
        /**
         * Editor-only translucent stand-in for a placed loc that draws nothing
         * (hidden in the current var state, or authored-empty geometry). Draws
         * like a depth-tested alpha model; never part of parity or exports.
         */
        EDITOR_GHOST;

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
        gameObjectSceneMetadata = Objects.requireNonNull(
                gameObjectSceneMetadata, "gameObjectSceneMetadata");
        clientRenderableBounds = List.copyOf(Objects.requireNonNull(
                clientRenderableBounds, "clientRenderableBounds"));
        clientRenderablePlacements = List.copyOf(Objects.requireNonNull(
                clientRenderablePlacements, "clientRenderablePlacements"));
        contourMetadata = Objects.requireNonNull(contourMetadata, "contourMetadata");
        sceneObjectIdentity = Objects.requireNonNull(sceneObjectIdentity, "sceneObjectIdentity");
        if (clientRenderableBounds.stream().anyMatch(value -> value == null || !value.present())) {
            throw new IllegalArgumentException("Client renderable bounds must be present");
        }
        if (clientRenderablePlacements.size() != clientRenderableBounds.size()
                || clientRenderablePlacements.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Client renderable placements must match bounds");
        }
        if (scenePlane < 0 || scenePlane > 3 || planeCullLevel < 0 || planeCullLevel > 3) {
            throw new IllegalArgumentException("Invalid scene-plane command metadata");
        }
        if (firstIndex < 0 || indexCount <= 0 || textureId < -1
                || priority < 0 || priority > 255 || depthBias < 0 || depthBias > 255
                || objectId < -1) {
            throw new IllegalArgumentException("Invalid GPU draw command");
        }
    }

    /** Compatibility constructor before contour metadata was retained. */
    public GpuDrawCommand(WorldTileAddress tile, int scenePlane, int planeCullLevel,
                          SceneLayer.Kind layer, SubmissionPass pass,
                          int firstIndex, int indexCount, int textureId, int priority,
                          int depthBias, int objectId, RenderMode renderMode,
                          WallDecorationPresentation wallDecorationPresentation,
                          GameObjectSceneMetadata gameObjectSceneMetadata,
                          List<ClientModelBounds> clientRenderableBounds,
                          List<ClientRenderablePlacement> clientRenderablePlacements,
                          SceneObjectIdentity sceneObjectIdentity,
                          int placementHeight,
                          int modelAnchorX,
                          int modelAnchorY) {
        this(tile, scenePlane, planeCullLevel, layer, pass, firstIndex, indexCount,
                textureId, priority, depthBias, objectId, renderMode,
                wallDecorationPresentation, gameObjectSceneMetadata, clientRenderableBounds,
                clientRenderablePlacements, ModelContourContract.Metadata.none(),
                sceneObjectIdentity, placementHeight, modelAnchorX, modelAnchorY);
    }

    /** Compatibility constructor before render-space model anchors were explicit. */
    public GpuDrawCommand(WorldTileAddress tile, int scenePlane, int planeCullLevel,
                          SceneLayer.Kind layer, SubmissionPass pass,
                          int firstIndex, int indexCount, int textureId, int priority,
                          int depthBias, int objectId, RenderMode renderMode,
                          WallDecorationPresentation wallDecorationPresentation,
                          GameObjectSceneMetadata gameObjectSceneMetadata,
                          List<ClientModelBounds> clientRenderableBounds,
                          SceneObjectIdentity sceneObjectIdentity,
                          int placementHeight) {
        this(tile, scenePlane, planeCullLevel, layer, pass, firstIndex, indexCount,
                textureId, priority, depthBias, objectId, renderMode,
                wallDecorationPresentation, gameObjectSceneMetadata, clientRenderableBounds,
                defaultPlacements(clientRenderableBounds), ModelContourContract.Metadata.none(),
                sceneObjectIdentity, placementHeight, tile.worldX(), tile.worldY());
    }

    /** Compatibility constructor before stable scene identity and placement height were explicit. */
    public GpuDrawCommand(WorldTileAddress tile, int scenePlane, int planeCullLevel,
                          SceneLayer.Kind layer, SubmissionPass pass,
                          int firstIndex, int indexCount, int textureId, int priority,
                          int depthBias, int objectId, RenderMode renderMode,
                          WallDecorationPresentation wallDecorationPresentation,
                          GameObjectSceneMetadata gameObjectSceneMetadata,
                          List<ClientModelBounds> clientRenderableBounds) {
        this(tile, scenePlane, planeCullLevel, layer, pass, firstIndex, indexCount,
                textureId, priority, depthBias, objectId, renderMode,
                wallDecorationPresentation, gameObjectSceneMetadata, clientRenderableBounds,
                defaultPlacements(clientRenderableBounds), ModelContourContract.Metadata.none(),
                SceneObjectIdentity.none(), 0, tile.worldX(), tile.worldY());
    }

    /** Compatibility constructor before client model bounds were explicit. */
    public GpuDrawCommand(WorldTileAddress tile, int scenePlane, int planeCullLevel,
                          SceneLayer.Kind layer, SubmissionPass pass,
                          int firstIndex, int indexCount, int textureId, int priority,
                          int depthBias, int objectId, RenderMode renderMode,
                          WallDecorationPresentation wallDecorationPresentation,
                          GameObjectSceneMetadata gameObjectSceneMetadata) {
        this(tile, scenePlane, planeCullLevel, layer, pass, firstIndex, indexCount,
                textureId, priority, depthBias, objectId, renderMode,
                wallDecorationPresentation, gameObjectSceneMetadata, List.of());
    }

    /** Compatibility constructor before game-object scene metadata was explicit. */
    public GpuDrawCommand(WorldTileAddress tile, int scenePlane, int planeCullLevel,
                          SceneLayer.Kind layer, SubmissionPass pass,
                          int firstIndex, int indexCount, int textureId, int priority,
                          int depthBias, int objectId, RenderMode renderMode,
                          WallDecorationPresentation wallDecorationPresentation) {
        this(tile, scenePlane, planeCullLevel, layer, pass, firstIndex, indexCount,
                textureId, priority, depthBias, objectId, renderMode,
                wallDecorationPresentation, GameObjectSceneMetadata.none());
    }

    /** Compatibility constructor before scene-plane metadata was explicit. */
    public GpuDrawCommand(WorldTileAddress tile, SceneLayer.Kind layer, SubmissionPass pass,
                          int firstIndex, int indexCount, int textureId, int priority,
                          int depthBias, int objectId, RenderMode renderMode,
                          WallDecorationPresentation wallDecorationPresentation) {
        this(tile, tile.plane(), tile.plane(), layer, pass, firstIndex, indexCount,
                textureId, priority, depthBias, objectId, renderMode,
                wallDecorationPresentation, GameObjectSceneMetadata.none());
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
        return canMerge(nextTile, nextTile.plane(), nextTile.plane(), nextLayer, nextPass,
                nextTextureId, nextPriority, nextDepthBias, nextObjectId, nextFirstIndex,
                RenderMode.DEFAULT, WallDecorationPresentation.none(), gameObjectSceneMetadata,
                clientRenderableBounds);
    }

    boolean canMerge(WorldTileAddress nextTile, SceneLayer.Kind nextLayer,
                     SubmissionPass nextPass, int nextTextureId,
                     int nextPriority, int nextDepthBias, int nextObjectId, int nextFirstIndex,
                     RenderMode nextRenderMode) {
        return canMerge(nextTile, nextTile.plane(), nextTile.plane(), nextLayer, nextPass,
                nextTextureId, nextPriority, nextDepthBias, nextObjectId, nextFirstIndex,
                nextRenderMode, WallDecorationPresentation.none(), gameObjectSceneMetadata,
                clientRenderableBounds);
    }

    boolean canMerge(WorldTileAddress nextTile, SceneLayer.Kind nextLayer,
                     SubmissionPass nextPass, int nextTextureId,
                     int nextPriority, int nextDepthBias, int nextObjectId, int nextFirstIndex,
                     RenderMode nextRenderMode,
                     WallDecorationPresentation nextWallDecorationPresentation) {
        return canMerge(nextTile, nextTile.plane(), nextTile.plane(), nextLayer, nextPass,
                nextTextureId, nextPriority, nextDepthBias, nextObjectId, nextFirstIndex,
                nextRenderMode, nextWallDecorationPresentation, gameObjectSceneMetadata,
                clientRenderableBounds);
    }

    boolean canMerge(WorldTileAddress nextTile, int nextScenePlane, int nextPlaneCullLevel,
                     SceneLayer.Kind nextLayer, SubmissionPass nextPass, int nextTextureId,
                     int nextPriority, int nextDepthBias, int nextObjectId, int nextFirstIndex,
                     RenderMode nextRenderMode,
                     WallDecorationPresentation nextWallDecorationPresentation) {
        return canMerge(nextTile, nextScenePlane, nextPlaneCullLevel, nextLayer, nextPass,
                nextTextureId, nextPriority, nextDepthBias, nextObjectId, nextFirstIndex,
                nextRenderMode, nextWallDecorationPresentation, gameObjectSceneMetadata,
                clientRenderableBounds);
    }

    boolean canMerge(WorldTileAddress nextTile, int nextScenePlane, int nextPlaneCullLevel,
                     SceneLayer.Kind nextLayer, SubmissionPass nextPass, int nextTextureId,
                     int nextPriority, int nextDepthBias, int nextObjectId, int nextFirstIndex,
                     RenderMode nextRenderMode,
                     WallDecorationPresentation nextWallDecorationPresentation,
                     GameObjectSceneMetadata nextGameObjectSceneMetadata) {
        return canMerge(nextTile, nextScenePlane, nextPlaneCullLevel, nextLayer, nextPass,
                nextTextureId, nextPriority, nextDepthBias, nextObjectId, nextFirstIndex,
                nextRenderMode, nextWallDecorationPresentation, nextGameObjectSceneMetadata,
                clientRenderableBounds);
    }

    boolean canMerge(WorldTileAddress nextTile, int nextScenePlane, int nextPlaneCullLevel,
                     SceneLayer.Kind nextLayer, SubmissionPass nextPass, int nextTextureId,
                     int nextPriority, int nextDepthBias, int nextObjectId, int nextFirstIndex,
                     RenderMode nextRenderMode,
                     WallDecorationPresentation nextWallDecorationPresentation,
                     GameObjectSceneMetadata nextGameObjectSceneMetadata,
                     List<ClientModelBounds> nextClientRenderableBounds) {
        return canMerge(nextTile, nextScenePlane, nextPlaneCullLevel, nextLayer, nextPass,
                nextTextureId, nextPriority, nextDepthBias, nextObjectId, nextFirstIndex,
                nextRenderMode, nextWallDecorationPresentation, nextGameObjectSceneMetadata,
                nextClientRenderableBounds, clientRenderablePlacements, contourMetadata,
                sceneObjectIdentity, placementHeight, modelAnchorX, modelAnchorY);
    }

    /** Compatibility overload from before contour metadata participated in batching. */
    boolean canMerge(WorldTileAddress nextTile, int nextScenePlane, int nextPlaneCullLevel,
                     SceneLayer.Kind nextLayer, SubmissionPass nextPass, int nextTextureId,
                     int nextPriority, int nextDepthBias, int nextObjectId, int nextFirstIndex,
                     RenderMode nextRenderMode,
                     WallDecorationPresentation nextWallDecorationPresentation,
                     GameObjectSceneMetadata nextGameObjectSceneMetadata,
                     List<ClientModelBounds> nextClientRenderableBounds,
                     List<ClientRenderablePlacement> nextClientRenderablePlacements,
                     SceneObjectIdentity nextSceneObjectIdentity,
                     int nextPlacementHeight,
                     int nextModelAnchorX,
                     int nextModelAnchorY) {
        return canMerge(nextTile, nextScenePlane, nextPlaneCullLevel, nextLayer, nextPass,
                nextTextureId, nextPriority, nextDepthBias, nextObjectId, nextFirstIndex,
                nextRenderMode, nextWallDecorationPresentation, nextGameObjectSceneMetadata,
                nextClientRenderableBounds, nextClientRenderablePlacements, contourMetadata,
                nextSceneObjectIdentity, nextPlacementHeight, nextModelAnchorX, nextModelAnchorY);
    }

    boolean canMerge(WorldTileAddress nextTile, int nextScenePlane, int nextPlaneCullLevel,
                     SceneLayer.Kind nextLayer, SubmissionPass nextPass, int nextTextureId,
                     int nextPriority, int nextDepthBias, int nextObjectId, int nextFirstIndex,
                     RenderMode nextRenderMode,
                     WallDecorationPresentation nextWallDecorationPresentation,
                     GameObjectSceneMetadata nextGameObjectSceneMetadata,
                     List<ClientModelBounds> nextClientRenderableBounds,
                     List<ClientRenderablePlacement> nextClientRenderablePlacements,
                     ModelContourContract.Metadata nextContourMetadata,
                     SceneObjectIdentity nextSceneObjectIdentity,
                     int nextPlacementHeight,
                     int nextModelAnchorX,
                     int nextModelAnchorY) {


        boolean sameWorldZone = (tile.worldX() >> 3) == (nextTile.worldX() >> 3)
                && (tile.worldY() >> 3) == (nextTile.worldY() >> 3);
        boolean tileCompatible = tile.equals(nextTile)
                || (layer == SceneLayer.Kind.TERRAIN && nextLayer == SceneLayer.Kind.TERRAIN
                    && tile.plane() == nextTile.plane() && sameWorldZone);
        boolean modelMetadataCompatible = layer == SceneLayer.Kind.TERRAIN
                || (wallDecorationPresentation.equals(nextWallDecorationPresentation)
                    && gameObjectSceneMetadata.equals(nextGameObjectSceneMetadata)
                    && clientRenderableBounds.equals(nextClientRenderableBounds)
                    && clientRenderablePlacements.equals(nextClientRenderablePlacements)
                    && contourMetadata.equals(nextContourMetadata)
                    && sceneObjectIdentity.equals(nextSceneObjectIdentity)
                    && placementHeight == nextPlacementHeight
                    && modelAnchorX == nextModelAnchorX
                    && modelAnchorY == nextModelAnchorY);
        return tileCompatible
                && scenePlane == nextScenePlane
                && planeCullLevel == nextPlaneCullLevel
                && layer == nextLayer && pass == nextPass
                && textureId == nextTextureId && priority == nextPriority
                && depthBias == nextDepthBias
                && objectId == nextObjectId
                && renderMode == nextRenderMode
                && modelMetadataCompatible
                && firstIndex + indexCount == nextFirstIndex;
    }

    private static List<ClientRenderablePlacement> defaultPlacements(
            List<ClientModelBounds> bounds) {
        Objects.requireNonNull(bounds, "bounds");
        return java.util.stream.IntStream.range(0, bounds.size())
                .mapToObj(ignored -> ClientRenderablePlacement.none())
                .toList();
    }

    GpuDrawCommand extend(int additionalIndices) {
        return new GpuDrawCommand(tile, scenePlane, planeCullLevel, layer, pass, firstIndex,
                indexCount + additionalIndices, textureId, priority, depthBias, objectId, renderMode,
                wallDecorationPresentation, gameObjectSceneMetadata, clientRenderableBounds,
                clientRenderablePlacements, contourMetadata, sceneObjectIdentity,
                placementHeight, modelAnchorX, modelAnchorY);
    }
}
