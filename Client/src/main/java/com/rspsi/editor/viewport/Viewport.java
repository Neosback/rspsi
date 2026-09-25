package com.rspsi.editor.viewport;

import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.model.WorldObject;

import java.util.Optional;

/** Viewport services exposed to tools without exposing a UI or renderer. */
public interface Viewport {
    Optional<WorldTile> tileAt(float x, float y);

    /** Optional object picking capability; legacy viewports may not provide it yet. */
    default Optional<WorldObject> objectAt(float x, float y) {
        return Optional.empty();
    }

    /**
     * Canonical semantic hit for editor tools.
     *
     * <p>Legacy viewport implementations automatically degrade to a terrain
     * hit using {@link #tileAt(float, float)}. Native/3D viewports should
     * override this to preserve exact object-hit identity without exposing
     * renderer-specific picking records.</p>
     */
    default Optional<SurfaceHit> hitAt(float x, float y) {
        Optional<WorldTile> tile = tileAt(x, y);
        if (tile.isEmpty()) return Optional.empty();

        Optional<WorldObject> object = objectAt(x, y);
        if (object.isPresent()) {
            WorldObject placement = object.orElseThrow();
            // A legacy Viewport does not expose a separate world-space object
            // anchor. Preserve its precise object identity and use the hit tile
            // as the best compatible anchor. Rich/native backends override
            // hitAt(...) and supply the exact anchor.
            return Optional.of(SurfaceHit.object(
                    tile.orElseThrow(),
                    placement.id(),
                    tile.orElseThrow(),
                    placement));
        }
        return Optional.of(SurfaceHit.terrain(tile.orElseThrow()));
    }
}
