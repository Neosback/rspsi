package com.rspsi.editor.core.module;

import com.rspsi.editor.core.CoreEditorModule;
import com.rspsi.editor.debug.DebugColor;
import com.rspsi.editor.plugin.EditorOverlayRegistration;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.EditorSceneOverlay;

/** Canonical always-on renderer/scene diagnostics module. */
public final class CoreDiagnosticsModule implements CoreEditorModule {
    public static final String ID = "rspsi.tools.renderer-debug";

    @Override public String id() { return ID; }
    @Override public int order() { return 40; }

    @Override
    public void install(EditorPluginContext context) {
        context.registry().registerOverlay(new EditorOverlayRegistration(
                "renderer-debug.scene-semantics",
                "Scene semantics",
                "Renderer diagnostics",
                false,
                RendererDiagnosticsOverlay::new));
    }

    private static final class RendererDiagnosticsOverlay implements EditorSceneOverlay {
        @Override
        public void render(com.rspsi.editor.plugin.EditorSceneSnapshot scene,
                           com.rspsi.editor.render.OverlayDraw draw) {
            scene.tileProjections().values().forEach(tile -> {
                if (!tile.hasBridge() && !tile.hasKnownObjects()) return;
                var world = scene.worldTile(tile.coordinate());
                draw.tileOutline(world);
                float wx = world.x() * 128.0f + 64.0f;
                float wz = world.y() * 128.0f + 64.0f;
                if (tile.hasBridge()) {
                    draw.worldLabel("bridge", wx, 0.0f, wz, 0xFFFFFFFF, 0xCCB91C1C);
                } else if (tile.hasKnownObjects()) {
                    draw.worldLabel(tile.objectsBySceneLayer().get(0).category().displayName(),
                            wx, 0.0f, wz, 0xFFFFFFFF, 0xCC1E293B);
                }
            });
        }
    }

    public static DebugColor defaultMarkerColor() {
        return DebugColor.YELLOW;
    }
}
