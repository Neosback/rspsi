package com.rspsi.editor.render;

import java.util.Optional;

/** Renderer contract independent of JavaFX, OpenGL, LWJGL, and ImGui. */
public interface SceneRenderer {
    void load(RenderScene scene);

    void update(RenderChanges changes);

    void render(CameraState camera);

    Optional<PickResult> pick(float x, float y);
}
