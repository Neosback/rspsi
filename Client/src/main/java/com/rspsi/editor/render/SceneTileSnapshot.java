package com.rspsi.editor.render;

import com.rspsi.editor.model.BridgeLink;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldTileAddress;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable renderer-facing tile projection. Authored data remains available
 * through the editor snapshot, while this type contains only derived packets.
 */
public record SceneTileSnapshot(
        TileCoordinate coordinate,
        WorldTileAddress worldAddress,
        int tileFlags,
        int effectivePlane,
        Optional<BridgeLink> bridge,
        Optional<TerrainRenderPacket> terrain,
        List<ModelRenderPacket> models,
        List<SceneLayer> layers,
        List<SceneOccluder> occluders,
        boolean roofRelated,
        boolean visibleBelow
) {
    public SceneTileSnapshot {
        coordinate = Objects.requireNonNull(coordinate, "coordinate");
        worldAddress = Objects.requireNonNull(worldAddress, "worldAddress");
        if (coordinate.plane() != worldAddress.plane()
                || coordinate.x() != worldAddress.worldX()
                || coordinate.y() != worldAddress.worldY()) {
            throw new IllegalArgumentException("Scene tile coordinate and world address must agree");
        }
        if (tileFlags < 0) {
            throw new IllegalArgumentException("Scene tile flags cannot be negative");
        }
        bridge = Objects.requireNonNull(bridge, "bridge");
        terrain = Objects.requireNonNull(terrain, "terrain");
        models = List.copyOf(Objects.requireNonNull(models, "models"));
        layers = List.copyOf(Objects.requireNonNull(layers, "layers"));
        occluders = List.copyOf(Objects.requireNonNull(occluders, "occluders"));
        if (effectivePlane < -1) {
            throw new IllegalArgumentException("Effective plane must be -1 or greater");
        }
    }

    /** Compatibility constructor before explicit tile draw ordering was exposed. */
    public SceneTileSnapshot(TileCoordinate coordinate, int effectivePlane, Optional<BridgeLink> bridge,
                             Optional<TerrainRenderPacket> terrain, List<ModelRenderPacket> models,
                             List<SceneOccluder> occluders, boolean roofRelated, boolean visibleBelow) {
        this(coordinate, WorldTileAddress.of(coordinate.x(), coordinate.y(), coordinate.plane()),
                0, effectivePlane, bridge, terrain, models, List.of(), occluders,
                roofRelated, visibleBelow);
    }
}
