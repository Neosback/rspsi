package com.rspsi.editor.core.module

import com.rspsi.editor.core.CoreEditorModule
import com.rspsi.editor.plugin.EditorPluginContext
import com.rspsi.editor.plugin.ui.UiSurfaceContribution
import com.rspsi.editor.tool.CompositeTilePainterTool
import com.rspsi.editor.ui.DockRegion
import java.util.EnumSet
import java.util.function.Supplier

/** Canonical always-on composite tile painter module. */
class CoreTilePainterModule : CoreEditorModule {
    override fun id(): String = ID

    override fun order(): Int = 11

    override fun install(context: EditorPluginContext) {
        context.registry().registerTool(
            TOOL_ID,
            "Tile painter",
            "Terrain",
            "Paint",
            "BRUSH",
            "2",
            15,
            Supplier { CompositeTilePainterTool() },
        )

        context.registry().registerUiSurface(
            UiSurfaceContribution(
                "studio.tile-palette",
                "Tile Painter",
                "palette",
                UiSurfaceContribution.SurfaceType.BOTTOM_CONTEXT,
                DockRegion.BOTTOM,
                EnumSet.of(DockRegion.BOTTOM, DockRegion.RIGHT),
                UiSurfaceContribution.SizeClass.EXPANDED,
                true,
                true,
                TOOL_ID,
                5,
            ),
        )

        context.registry().registerUiSurface(
            UiSurfaceContribution(
                "studio.tile-painter-hud",
                "Tile Painter HUD",
                "brush",
                UiSurfaceContribution.SurfaceType.VIEWPORT_HUD,
                DockRegion.OVERLAY,
                EnumSet.of(DockRegion.OVERLAY),
                UiSurfaceContribution.SizeClass.COMPACT,
                false,
                true,
                TOOL_ID,
                30,
            ),
        )
    }

    companion object {
        const val ID: String = "rspsi.tools.terrain.painter"

        /**
         * Registry identity is intentionally centralized so the tool and both
         * managed UI surfaces cannot drift onto different activation IDs.
         */
        private const val TOOL_ID: String = "terrain.tile-painter"
    }
}
