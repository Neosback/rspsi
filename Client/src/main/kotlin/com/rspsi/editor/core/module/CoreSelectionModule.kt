package com.rspsi.editor.core.module

import com.rspsi.editor.core.CoreEditorModule
import com.rspsi.editor.core.settings.SelectionToolSettings
import com.rspsi.editor.plugin.EditorPluginContext
import com.rspsi.editor.plugin.EditorToolContextContribution
import com.rspsi.editor.plugin.EditorToolContextRegistration
import com.rspsi.editor.tool.AttributeSelectionTool
import com.rspsi.editor.tool.BoxSelectTool
import com.rspsi.editor.tool.DuplicateSelectionTool
import com.rspsi.editor.tool.EditorTool
import com.rspsi.editor.tool.LassoSelectTool
import com.rspsi.editor.tool.MoveSelectionTool
import com.rspsi.editor.tool.ReplaceSelectionTool
import com.rspsi.editor.tool.RotateSelectionTool
import java.util.function.Supplier

/** Canonical always-on selection and transform module. */
class CoreSelectionModule : CoreEditorModule {
    override fun id(): String = ID

    override fun order(): Int = 30

    override fun install(context: EditorPluginContext) {
        val settings = SelectionToolSettings(context.settings())

        register(
            context,
            settings,
            "selection.box",
            "Box select",
            "Selection",
            "Selector",
            "SELECT_BOX",
            "1",
            10,
        ) { BoxSelectTool() }

        register(
            context,
            settings,
            "selection.lasso",
            "Lasso select",
            "Selection",
            "Selector",
            "SELECT_LASSO",
            "Shift+1",
            20,
        ) { LassoSelectTool() }

        register(
            context,
            settings,
            "selection.attribute",
            "Select by attribute",
            "Selection",
            "Selector",
            "SELECT_ATTR",
            null,
            30,
        ) { AttributeSelectionTool() }

        register(
            context,
            settings,
            "selection.move",
            "Move selection",
            "Selection",
            "Selector",
            "MOVE",
            "W",
            40,
        ) { MoveSelectionTool() }

        register(
            context,
            settings,
            "selection.rotate",
            "Rotate selection",
            "Selection",
            "Selector",
            "ROTATE",
            "R",
            50,
        ) { RotateSelectionTool() }

        register(
            context,
            settings,
            "selection.duplicate",
            "Duplicate selection",
            "Selection",
            "Selector",
            "DUPLICATE",
            "Shift+D",
            60,
        ) { DuplicateSelectionTool() }

        register(
            context,
            settings,
            "selection.replace",
            "Replace selection",
            "Selection",
            "Selector",
            "REPLACE",
            null,
            70,
        ) { ReplaceSelectionTool(0) }

        context.registry().registerToolContext(
            EditorToolContextRegistration(
                "selection.context",
                "Selection settings",
                java.util.List.of(
                    "selection.box",
                    "selection.lasso",
                    "selection.attribute",
                    "selection.move",
                    "selection.rotate",
                    "selection.duplicate",
                    "selection.replace",
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
        settings: SelectionToolSettings,
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
        const val ID: String = "rspsi.tools.selection"
    }
}
