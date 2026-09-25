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
        return tileAt(x, y).map(SurfaceHit::terrain);
    }
}
