package com.rspsi.api.scene;

import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldTileAddress;

import java.util.Objects;
import java.util.Optional;

/**
 * Authored tile data (heights, floor ids, shapes) behind a resolved scene.
 *
 * <p>The resolved {@code GpuScenePacket} carries only derived render data; the
 * RuneLite scene arrays ({@code getTileHeights}, {@code getUnderlayIds}, ...)
 * describe authored state, so a scene view needs both.</p>
 */
@FunctionalInterface
public interface AuthoredTileSource {
    Optional<TileSnapshot> tile(WorldTileAddress address);

    /** A single-region document whose local (0,0) sits at the given world origin. */
    static AuthoredTileSource of(WorldDocument document, int originX, int originY) {
        Objects.requireNonNull(document, "document");
        return address -> {
            int x = address.worldX() - originX;
            int y = address.worldY() - originY;
            if (x < 0 || y < 0 || x >= document.width() || y >= document.length()
                    || address.plane() >= document.planes()) {
                return Optional.empty();
            }
            return Optional.of(document.tile(address.plane(), x, y).snapshot());
        };
    }
}
