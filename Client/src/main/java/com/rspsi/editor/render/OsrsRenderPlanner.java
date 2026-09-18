package com.rspsi.editor.render;

import java.util.Objects;

/** RuneScape packet planner; it owns ordering, not OpenGL calls. */
public final class OsrsRenderPlanner implements RenderPlanner {
    private final GpuScenePacketBuilder packets;

    public OsrsRenderPlanner() {
        this(new GpuScenePacketBuilder());
    }

    public OsrsRenderPlanner(GpuScenePacketBuilder packets) {
        this.packets = Objects.requireNonNull(packets, "packets");
    }

    @Override
    public GpuScenePacket plan(SceneWindow window, RenderScene scene, RenderConfig config) {
        Objects.requireNonNull(config, "render config");
        return config.apply(packets.build(window, scene));
    }

    @Override
    public GpuScenePacket plan(SceneWindow window, RenderWindowScene scene, RenderConfig config) {
        Objects.requireNonNull(config, "render config");
        return config.apply(packets.build(window, scene));
    }
}
