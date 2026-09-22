package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTile;

import java.util.List;
import java.util.Objects;

/**
 * Neutral result of viewport picking across both 2D and 3D backends.
 *
 * <p>Beyond identifying what was clicked, this carries the exact ray-intersected
 * hit {@link #tile()}, the base anchor {@link #objectTile()} if an object was hit,
 * the client-style occupied scene rectangle for game objects, and the submission
 * metadata of the draw command that was actually hit.</p>
 */
public record PickResult(WorldTile tile, WorldTile objectTile, int plane, int objectId, float distance,
                         SceneLayer.Kind layer, int priority, int depthBias, int textureId,
                         GameObjectSceneMetadata gameObjectSceneMetadata,
                         List<ClientModelBounds> clientRenderableBounds,
                         SceneObjectIdentity sceneObjectIdentity) {
    /** Compatibility constructor for tile-only legacy viewport picking. */
    public PickResult(WorldTile tile, int plane) {
        this(tile, null, plane, -1, Float.NaN, null, 0, 0, -1,
                GameObjectSceneMetadata.none(), List.of(), SceneObjectIdentity.none());
    }

    /** Compatibility constructor from before submission metadata was carried. */
    public PickResult(WorldTile tile, int plane, int objectId, float distance) {
        this(tile, null, plane, objectId, distance, null, 0, 0, -1,
                GameObjectSceneMetadata.none(), List.of(), SceneObjectIdentity.none());
    }

    /** Compatibility constructor from before objectTile was carried. */
    public PickResult(WorldTile tile, int plane, int objectId, float distance,
                      SceneLayer.Kind layer, int priority, int depthBias, int textureId) {
        this(tile, null, plane, objectId, distance, layer, priority, depthBias, textureId,
                GameObjectSceneMetadata.none(), List.of(), SceneObjectIdentity.none());
    }

    /** Compatibility constructor from before game-object scene metadata was carried. */
    public PickResult(WorldTile tile, WorldTile objectTile, int plane, int objectId, float distance,
                      SceneLayer.Kind layer, int priority, int depthBias, int textureId) {
        this(tile, objectTile, plane, objectId, distance, layer, priority, depthBias, textureId,
                GameObjectSceneMetadata.none(), List.of(), SceneObjectIdentity.none());
    }

    /** Compatibility constructor from before client model bounds were carried. */
    public PickResult(WorldTile tile, WorldTile objectTile, int plane, int objectId, float distance,
                      SceneLayer.Kind layer, int priority, int depthBias, int textureId,
                      GameObjectSceneMetadata gameObjectSceneMetadata) {
        this(tile, objectTile, plane, objectId, distance, layer, priority, depthBias, textureId,
                gameObjectSceneMetadata, List.of(), SceneObjectIdentity.none());
    }

    /** Compatibility constructor from before stable scene-object identity was carried. */
    public PickResult(WorldTile tile, WorldTile objectTile, int plane, int objectId, float distance,
                      SceneLayer.Kind layer, int priority, int depthBias, int textureId,
                      GameObjectSceneMetadata gameObjectSceneMetadata,
                      List<ClientModelBounds> clientRenderableBounds) {
        this(tile, objectTile, plane, objectId, distance, layer, priority, depthBias, textureId,
                gameObjectSceneMetadata, clientRenderableBounds, SceneObjectIdentity.none());
    }

    public PickResult {
        gameObjectSceneMetadata = Objects.requireNonNull(
                gameObjectSceneMetadata, "gameObjectSceneMetadata");
        clientRenderableBounds = List.copyOf(Objects.requireNonNull(
                clientRenderableBounds, "clientRenderableBounds"));
        sceneObjectIdentity = Objects.requireNonNull(sceneObjectIdentity, "sceneObjectIdentity");
        if (clientRenderableBounds.stream().anyMatch(value -> value == null || !value.present())) {
            throw new IllegalArgumentException("Client renderable bounds must be present");
        }
        if (tile == null || plane < 0 || plane != tile.plane() || objectId < -1
                || (!Float.isNaN(distance) && (!Float.isFinite(distance) || distance < 0.0f))
                || priority < 0 || depthBias < 0 || textureId < -1) {
            throw new IllegalArgumentException("Pick result requires a valid tile and plane");
        }
        if (objectTile != null && objectTile.plane() != plane) {
            throw new IllegalArgumentException("Object anchor must be on the picked plane");
        }
        if (gameObjectSceneMetadata.present() && objectId < 0) {
            throw new IllegalArgumentException("Terrain picks cannot carry game-object scene metadata");
        }
        if (!clientRenderableBounds.isEmpty() && objectId < 0) {
            throw new IllegalArgumentException("Terrain picks cannot carry client model bounds");
        }
        if (sceneObjectIdentity.present() && objectId < 0) {
            throw new IllegalArgumentException("Terrain picks cannot carry scene object identity");
        }
        if (sceneObjectIdentity.present() && sceneObjectIdentity.objectId() != objectId) {
            throw new IllegalArgumentException("Picked object ID must match scene identity");
        }
    }

    public boolean objectHit() {
        return objectId >= 0;
    }

    /** True when the hit carries draw-command metadata worth inspecting. */
    public boolean hasSubmissionMetadata() {
        return layer != null;
    }

    public boolean hasGameObjectSceneMetadata() {
        return gameObjectSceneMetadata.present();
    }

    public boolean hasClientModelBounds() {
        return !clientRenderableBounds.isEmpty();
    }

    public boolean hasSceneObjectIdentity() {
        return sceneObjectIdentity.present();
    }

    public String sceneInstanceId() {
        return sceneObjectIdentity.stableId();
    }

    /** RuneLite-compatible minimum occupied scene tile for the picked game object. */
    public WorldTile objectSceneMinTile() {
        if (!gameObjectSceneMetadata.present()) return null;
        return new WorldTile(plane, gameObjectSceneMetadata.minTileX(),
                gameObjectSceneMetadata.minTileY());
    }

    /** RuneLite-compatible maximum occupied scene tile for the picked game object. */
    public WorldTile objectSceneMaxTile() {
        if (!gameObjectSceneMetadata.present()) return null;
        return new WorldTile(plane, gameObjectSceneMetadata.maxTileX(),
                gameObjectSceneMetadata.maxTileY());
    }
}
