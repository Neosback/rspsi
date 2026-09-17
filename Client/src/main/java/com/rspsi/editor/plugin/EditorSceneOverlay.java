package com.rspsi.editor.plugin;

import com.rspsi.editor.render.OverlayDraw;

/** Renderer-neutral scene overlay contributed by a plugin. */
@FunctionalInterface
public interface EditorSceneOverlay {
    void render(EditorSceneSnapshot scene, OverlayDraw draw);
}
