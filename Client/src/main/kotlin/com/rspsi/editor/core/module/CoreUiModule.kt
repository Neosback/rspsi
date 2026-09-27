package com.rspsi.editor.core.module

import com.rspsi.editor.core.CoreEditorModule
import com.rspsi.editor.plugin.EditorPluginContext
import com.rspsi.editor.plugin.ui.UiSurfaceContribution
import com.rspsi.editor.ui.DockRegion
import java.util.EnumSet

/** Canonical always-on declarations for Studio-managed utility/HUD surfaces. */
class CoreUiModule : CoreEditorModule {
    override fun id(): String = ID

    override fun order(): Int = 50

    override fun install(context: EditorPluginContext) {
        context.registry().registerUiSurface(
            UiSurfaceContribution(
                "studio.minimap-hud",
                "Minimap HUD",
                "map",
                UiSurfaceContribution.SurfaceType.VIEWPORT_HUD,
                DockRegion.OVERLAY,
                EnumSet.of(DockRegion.OVERLAY),
                UiSurfaceContribution.SizeClass.EXPANDED,
                false,
                true,
                "",
                10,
            ),
        )
        context.registry().registerUiSurface(
            UiSurfaceContribution(
                "studio.tile-info-hud",
                "Tile Information HUD",
                "explore",
                UiSurfaceContribution.SurfaceType.VIEWPORT_HUD,
                DockRegion.OVERLAY,
                EnumSet.of(DockRegion.OVERLAY),
                UiSurfaceContribution.SizeClass.COMPACT,
                false,
                true,
                "",
                20,
            ),
        )
    }

    companion object {
        const val ID: String = "rspsi.ui.core-surfaces"
    }
}
