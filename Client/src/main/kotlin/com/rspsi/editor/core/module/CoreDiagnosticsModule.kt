package com.rspsi.editor.core.module

import com.rspsi.editor.core.CoreEditorModule
import com.rspsi.editor.debug.DebugColor
import com.rspsi.editor.plugin.EditorOverlayRegistration
import com.rspsi.editor.plugin.EditorPluginContext
import com.rspsi.editor.plugin.EditorSceneOverlay
import com.rspsi.editor.plugin.EditorSceneSnapshot
import com.rspsi.editor.render.OverlayDraw
import java.util.function.Supplier

/** Canonical always-on renderer/scene diagnostics module. */
class CoreDiagnosticsModule : CoreEditorModule {
    override fun id(): String = ID

    override fun order(): Int = 40

    override fun install(context: EditorPluginContext) {
        context.registry().registerOverlay(
            EditorOverlayRegistration(
                OVERLAY_ID,
                "Scene semantics",
                "Renderer diagnostics",
                false,
                Supplier { RendererDiagnosticsOverlay() },
            ),
        )
    }

    /**
     * Keeps scene diagnostics deliberately projection-driven. The overlay reads
     * immutable editor scene projections instead of reaching into mutable map
     * state, which keeps diagnostics safe to run while editing.
     */
    private class RendererDiagnosticsOverlay : EditorSceneOverlay {
        override fun render(scene: EditorSceneSnapshot, draw: OverlayDraw) {
            for (tile in scene.tileProjections().values) {
                if (!tile.hasBridge() && !tile.hasKnownObjects()) continue

                val world = scene.worldTile(tile.coordinate())
                draw.tileOutline(world)

                val worldX = world.x() * TILE_SIZE + TILE_CENTER
                val worldZ = world.y() * TILE_SIZE + TILE_CENTER

                if (tile.hasBridge()) {
                    draw.worldLabel(
                        "bridge",
                        worldX,
                        0.0f,
                        worldZ,
                        LABEL_TEXT_RGBA,
                        BRIDGE_LABEL_BACKGROUND_RGBA,
                    )
                    continue
                }

                // hasKnownObjects() guarantees at least one known category exists,
                // but UNKNOWN objects may still precede it. Select the first known
                // scene-layer object instead of assuming list element zero is known.
                val knownObject = tile.objectsBySceneLayer()
                    .firstOrNull { it.category().displayName().isNotBlank() && it.category().name != "UNKNOWN" }
                    ?: continue
                draw.worldLabel(
                    knownObject.category().displayName(),
                    worldX,
                    0.0f,
                    worldZ,
                    LABEL_TEXT_RGBA,
                    OBJECT_LABEL_BACKGROUND_RGBA,
                )
            }
        }
    }

    companion object {
        const val ID: String = "rspsi.tools.renderer-debug"

        private const val OVERLAY_ID: String = "renderer-debug.scene-semantics"
        private const val TILE_SIZE: Float = 128.0f
        private const val TILE_CENTER: Float = 64.0f
        private const val LABEL_TEXT_RGBA: Int = -1
        private const val BRIDGE_LABEL_BACKGROUND_RGBA: Int = -860283876
        private const val OBJECT_LABEL_BACKGROUND_RGBA: Int = -870438597

        @JvmStatic
        fun defaultMarkerColor(): DebugColor = DebugColor.YELLOW
    }
}
