package com.rspsi.editor.core.module

import com.rspsi.editor.core.CoreEditorModule
import com.rspsi.editor.core.settings.TerrainToolSettings
import com.rspsi.editor.plugin.EditorPluginContext
import com.rspsi.editor.plugin.EditorToolContextContribution
import com.rspsi.editor.plugin.EditorToolContextRegistration
import com.rspsi.editor.tool.BlendTerrainTool
import com.rspsi.editor.tool.ChangeHeightTool
import com.rspsi.editor.tool.EditorTool
import com.rspsi.editor.tool.FlattenTerrainTool
import com.rspsi.editor.tool.PaintFlagsTool
import com.rspsi.editor.tool.PaintOverlayTool
import com.rspsi.editor.tool.PaintUnderlayTool
import com.rspsi.editor.tool.RampTerrainTool
import com.rspsi.editor.tool.SmoothTerrainTool
import com.rspsi.editor.tool.TerraceTerrainTool
import java.util.function.Supplier

/** Canonical always-on terrain editing module. */
class CoreTerrainModule : CoreEditorModule {
    override fun id(): String = ID

    override fun order(): Int = 10

    override fun install(context: EditorPluginContext) {
        val settings = TerrainToolSettings(context.settings())

        register(
            context,
            settings,
            "terrain.paint-underlay",
            "Paint underlay",
            "Terrain",
            "Paint",
            "PENCIL",
            "Shift+2",
            20,
        ) { PaintUnderlayTool(1) }

        register(
            context,
            settings,
            "terrain.paint-overlay",
            "Paint overlay",
            "Terrain",
            "Paint",
            "BRUSH",
            "2",
            10,
        ) { PaintOverlayTool(1) }

        register(
            context,
            settings,
            "terrain.flags",
            "Paint flags",
            "Terrain",
            "Paint",
            "FLAGS",
            null,
            30,
        ) { PaintFlagsTool(0) }

        register(
            context,
            settings,
            "terrain.raise",
            "Raise height",
            "Height",
            "Height",
            "UP",
            "3",
            10,
        ) { ChangeHeightTool(8) }

        register(
            context,
            settings,
            "terrain.lower",
            "Lower height",
            "Height",
            "Height",
            "DOWN",
            "Shift+3",
            20,
        ) { ChangeHeightTool(-8) }

        register(
            context,
            settings,
            "terrain.flatten",
            "Flatten terrain",
            "Height",
            "Height",
            "FLATTEN",
            null,
            30,
        ) { FlattenTerrainTool(0) }

        register(
            context,
            settings,
            "terrain.smooth",
            "Smooth terrain",
            "Height",
            "Height",
            "SMOOTH",
            null,
            40,
        ) { SmoothTerrainTool(50) }

        register(
            context,
            settings,
            "terrain.blend",
            "Blend terrain",
            "Height",
            "Height",
            "HEIGHT",
            null,
            45,
        ) { BlendTerrainTool(50, 56) }

        register(
            context,
            settings,
            "terrain.terrace",
            "Terrace terrain",
            "Height",
            "Height",
            "HEIGHT",
            null,
            47,
        ) { TerraceTerrainTool(16) }

        register(
            context,
            settings,
            "terrain.ramp",
            "Ramp terrain",
            "Height",
            "Height",
            "RAMP",
            null,
            50,
        ) { RampTerrainTool(0, 64) }

        context.registry().registerToolContext(
            EditorToolContextRegistration(
                "terrain.context",
                "Terrain settings",
                java.util.List.of(
                    "terrain.paint-underlay",
                    "terrain.paint-overlay",
                    "terrain.raise",
                    "terrain.lower",
                    "terrain.flatten",
                    "terrain.smooth",
                    "terrain.blend",
                    "terrain.terrace",
                    "terrain.ramp",
                    "terrain.flags",
                ),
                0,
                Supplier {
                    EditorToolContextContribution {
                        settings.settings()
                    }
                },
            ),
        )
    }

    private fun register(
        context: EditorPluginContext,
        settings: TerrainToolSettings,
        id: String,
        label: String,
        category: String,
        group: String,
        icon: String,
        shortcut: String?,
        order: Int,
        factory: () -> EditorTool,
    ) {
        context.registry().registerTool(
            id,
            label,
            category,
            group,
            icon,
            shortcut,
            order,
            Supplier {
                val tool = factory()
                settings.configure(id, tool)
                tool
            },
        )
    }

    companion object {
        const val ID: String = "rspsi.tools.terrain"
    }
}
