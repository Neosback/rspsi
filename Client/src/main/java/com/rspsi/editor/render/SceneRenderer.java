package com.rspsi.editor.render;

import java.util.Optional;

/** Renderer contract independent of JavaFX, OpenGL, LWJGL, and ImGui. */
public interface SceneRenderer {
    void load(RenderScene scene);

    void update(RenderChanges changes);

    /**
     * Publishes the newly derived scene together with its invalidation set.
     * The default preserves source compatibility for incremental renderers
     * that only need the changed coordinates.
     */
    default void update(RenderScene scene, RenderChanges changes) {
        update(changes);
    }

    void render(CameraState camera);

    Optional<PickResult> pick(float x, float y);
}
