package com.rspsi.editor.core.module

import com.rspsi.editor.core.CoreEditorModule
import com.rspsi.editor.plugin.EditorPluginContext
import com.rspsi.editor.plugin.ui.UiSurfaceContribution
import com.rspsi.editor.tool.SplinePathTool
import com.rspsi.editor.ui.DockRegion
import java.util.EnumSet
import java.util.function.Supplier

/** Canonical always-on spline/path editing module. */
class CorePathModule : CoreEditorModule {
    override fun id(): String = ID

    override fun order(): Int = 17

    override fun install(context: EditorPluginContext) {
        context.registry().registerTool(
            SplinePathTool.ID,
            "Spline Path",
            "Terrain",
            "Paint",
            "PATH",
            "P",
            45,
            Supplier { SplinePathTool() },
        )

        context.registry().registerUiSurface(
            UiSurfaceContribution(
                "studio.path-context",
                "Path Builder",
                "path",
                UiSurfaceContribution.SurfaceType.BOTTOM_CONTEXT,
                DockRegion.BOTTOM,
                EnumSet.of(DockRegion.BOTTOM, DockRegion.RIGHT),
                UiSurfaceContribution.SizeClass.EXPANDED,
                true,
                true,
                SplinePathTool.ID,
                15,
            ),
        )

        context.registry().registerUiSurface(
            UiSurfaceContribution(
                "studio.path-hud",
                "Path HUD",
                "path",
                UiSurfaceContribution.SurfaceType.VIEWPORT_HUD,
                DockRegion.OVERLAY,
                EnumSet.of(DockRegion.OVERLAY),
                UiSurfaceContribution.SizeClass.COMPACT,
                false,
                true,
                SplinePathTool.ID,
                35,
            ),
        )
    }

    companion object {
        const val ID: String = "rspsi.tools.path.spline"
    }
}
