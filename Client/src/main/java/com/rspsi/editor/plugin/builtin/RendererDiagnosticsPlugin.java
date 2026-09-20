package com.rspsi.editor.plugin.builtin;

import com.rspsi.editor.debug.DebugColor;
import com.rspsi.editor.plugin.EditorOverlayRegistration;
import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.EditorPluginRegistry;
import com.rspsi.editor.plugin.EditorSceneOverlay;

/**
 * Grouped first-party diagnostics contribution for scene/render inspection.
 * The overlay reads only the immutable scene projection and is safe for both
 * JavaFX and Dear ImGui frontends.
 */
public final class RendererDiagnosticsPlugin implements EditorPlugin {
    public static final String ID = "rspsi.tools.renderer-debug";

    @Override public String id() { return ID; }

    @Override public int loadOrder() { return 40; }

    @Override
    public void initialize(EditorPluginContext context) {
        EditorPluginRegistry registry = context.registry();
        registry.registerOverlay(new EditorOverlayRegistration(
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
                draw.tileOutline(tile.coordinate());
                float wx = tile.coordinate().x() * 128.0f + 64.0f;
                float wz = tile.coordinate().y() * 128.0f + 64.0f;
                if (tile.hasBridge()) {
                    draw.worldLabel("bridge", wx, 0.0f, wz, 0xFFFFFFFF, 0xCCB91C1C);
                } else if (tile.hasKnownObjects()) {
                    draw.worldLabel(tile.objectsBySceneLayer().get(0).category().displayName(),
                            wx, 0.0f, wz, 0xFFFFFFFF, 0xCC1E293B);
                }
            });
        }
    }

    /** Stable semantic defaults exposed for frontend palette adapters. */
    public static DebugColor defaultMarkerColor() {
        return DebugColor.YELLOW;
    }
}
