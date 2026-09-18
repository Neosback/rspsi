package com.rspsi.editor.render;

import com.rspsi.editor.model.BridgeLink;
import com.rspsi.editor.model.TileCoordinate;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable renderer-facing tile projection. Authored data remains available
 * through the editor snapshot, while this type contains only derived packets.
 */
public record SceneTileSnapshot(
        TileCoordinate coordinate,
        int effectivePlane,
        Optional<BridgeLink> bridge,
        Optional<TerrainRenderPacket> terrain,
        List<ModelRenderPacket> models,
        List<SceneOccluder> occluders,
        boolean roofRelated,
        boolean visibleBelow
) {
    public SceneTileSnapshot {
        coordinate = Objects.requireNonNull(coordinate, "coordinate");
        bridge = Objects.requireNonNull(bridge, "bridge");
        terrain = Objects.requireNonNull(terrain, "terrain");
        models = List.copyOf(Objects.requireNonNull(models, "models"));
        occluders = List.copyOf(Objects.requireNonNull(occluders, "occluders"));
        if (effectivePlane < -1) {
            throw new IllegalArgumentException("Effective plane must be -1 or greater");
        }
    }
}
