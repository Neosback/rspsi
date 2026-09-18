package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable {@link SceneQuery} backed by one GPU-neutral scene packet. */
public final class PacketSceneQuery implements SceneQuery {
    private final Map<WorldTileAddress, SceneTileSnapshot> tiles;

    public PacketSceneQuery(GpuScenePacket packet) {
        Objects.requireNonNull(packet, "GPU packet");
        Map<WorldTileAddress, SceneTileSnapshot> indexed = new LinkedHashMap<>();
        for (SceneTileSnapshot tile : packet.tiles()) {
            SceneTileSnapshot previous = indexed.put(tile.worldAddress(), tile);
            if (previous != null) {
                throw new IllegalArgumentException("GPU packet contains duplicate tile "
                        + tile.worldAddress());
            }
        }
        tiles = Map.copyOf(indexed);
    }

    @Override
    public Optional<SceneTileSnapshot> tile(WorldTileAddress address) {
        return Optional.ofNullable(tiles.get(Objects.requireNonNull(address, "tile address")));
    }
}
