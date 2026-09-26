package com.rspsi.editor.core.module

import com.rspsi.editor.core.CoreEditorModule
import com.rspsi.editor.core.inspector.ObjectContentInspector
import com.rspsi.editor.core.settings.ObjectToolSettings
import com.rspsi.editor.plugin.EditorInspectorRegistration
import com.rspsi.editor.plugin.EditorPluginContext
import com.rspsi.editor.plugin.EditorToolContextContribution
import com.rspsi.editor.plugin.EditorToolContextRegistration
import com.rspsi.editor.tool.DeleteObjectTool
import com.rspsi.editor.tool.DuplicateObjectTool
import com.rspsi.editor.tool.EditorTool
import com.rspsi.editor.tool.MoveObjectTool
import com.rspsi.editor.tool.PlaceObjectTool
import com.rspsi.editor.tool.RotateObjectTool
import java.util.function.Supplier

/** Canonical always-on object editing module. */
class CoreObjectModule : CoreEditorModule {
    override fun id(): String = ID

    override fun order(): Int = 20

    override fun install(context: EditorPluginContext) {
        val settings = ObjectToolSettings(context.settings())

        register(
            context,
            settings,
            "object.place",
            "Place object",
            "Objects",
            "Objects",
            "ADD",
            "4",
            10,
        ) { PlaceObjectTool(0, 10, 0) }

        register(
            context,
            settings,
            "object.move",
            "Move object",
            "Objects",
            "Objects",
            "MOVE",
            "Shift+4",
            20,
        ) { MoveObjectTool() }

        register(
            context,
            settings,
            "object.rotate",
            "Rotate object",
            "Objects",
            "Objects",
            "ROTATE",
            null,
            30,
        ) { RotateObjectTool() }

        register(
            context,
            settings,
            "object.duplicate",
            "Duplicate object",
            "Objects",
            "Objects",
            "DUPLICATE",
            null,
            40,
        ) { DuplicateObjectTool() }

        register(
            context,
            settings,
            "object.delete",
            "Delete object",
            "Objects",
            "Objects",
            "DELETE",
            "Delete",
            50,
        ) { DeleteObjectTool() }

        context.registry().registerToolContext(
            EditorToolContextRegistration(
                "objects.context",
                "Object settings",
                java.util.List.of(
                    "object.place",
                    "object.move",
                    "object.rotate",
                    "object.duplicate",
                    "object.delete",
                ),
                0,
                Supplier {
                    EditorToolContextContribution {
                        settings.settings()
                    }
                },
            ),
        )

        context.registry().registerInspector(
            EditorInspectorRegistration(
                "objects.content",
                "Server content",
                "Objects",
                Supplier { ObjectContentInspector() },
            ),
        )
    }

    private fun register(
        context: EditorPluginContext,
        settings: ObjectToolSettings,
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
        const val ID: String = "rspsi.tools.objects"
    }
}
