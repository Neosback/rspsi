package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;

import java.util.List;
import java.util.Optional;

/**
 * Read-only queries over one immutable resolved scene packet.
 *
 * <p>Tools may use this boundary for inspection, overlays, and picking. It
 * deliberately exposes no cache decoder, mutable world object, or backend
 * handle. Mutations still go through the editor command service.</p>
 */
public interface SceneQuery {
    Optional<SceneTileSnapshot> tile(WorldTileAddress address);

    default Optional<TerrainRenderPacket> terrain(WorldTileAddress address) {
        return tile(address).flatMap(SceneTileSnapshot::terrain);
    }

    default List<ModelRenderPacket> models(WorldTileAddress address) {
        return tile(address).map(SceneTileSnapshot::models).orElseGet(List::of);
    }

    default int flags(WorldTileAddress address) {
        return tile(address).map(SceneTileSnapshot::tileFlags).orElse(0);
    }
}
