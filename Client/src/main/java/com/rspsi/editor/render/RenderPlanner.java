package com.rspsi.editor.render;

/** Plans renderer-neutral packets from one resolved scene and one frame config. */
public interface RenderPlanner {
    GpuScenePacket plan(SceneWindow window, RenderScene scene, RenderConfig config);

    default GpuScenePacket plan(SceneWindow window, RenderWindowScene scene, RenderConfig config) {
        throw new UnsupportedOperationException("World-window planning is not supported by this planner");
    }
}
