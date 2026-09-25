package com.rspsi.editor.core.module;

import com.rspsi.editor.core.CoreEditorModule;
import com.rspsi.editor.plugin.EditorInspectorRegistration;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.EditorToolContextRegistration;
import com.rspsi.editor.core.inspector.ObjectContentInspector;
import com.rspsi.editor.plugin.builtin.ObjectToolSettings;
import com.rspsi.editor.tool.DeleteObjectTool;
import com.rspsi.editor.tool.DuplicateObjectTool;
import com.rspsi.editor.tool.MoveObjectTool;
import com.rspsi.editor.tool.PlaceObjectTool;
import com.rspsi.editor.tool.RotateObjectTool;

import java.util.List;
import java.util.function.Supplier;

/** Canonical always-on object editing module. */
public final class CoreObjectModule implements CoreEditorModule {
    public static final String ID = "rspsi.tools.objects";

    @Override public String id() { return ID; }
    @Override public int order() { return 20; }

    @Override
    public void install(EditorPluginContext context) {
        ObjectToolSettings settings = new ObjectToolSettings(context.settings());

        register(context, settings, "object.place", "Place object",
                "Objects", "Objects", "ADD", "4", 10, () -> new PlaceObjectTool(0, 10, 0));
        register(context, settings, "object.move", "Move object",
                "Objects", "Objects", "MOVE", "Shift+4", 20, MoveObjectTool::new);
        register(context, settings, "object.rotate", "Rotate object",
                "Objects", "Objects", "ROTATE", null, 30, RotateObjectTool::new);
        register(context, settings, "object.duplicate", "Duplicate object",
                "Objects", "Objects", "DUPLICATE", null, 40, DuplicateObjectTool::new);
        register(context, settings, "object.delete", "Delete object",
                "Objects", "Objects", "DELETE", "Delete", 50, DeleteObjectTool::new);

        context.registry().registerToolContext(new EditorToolContextRegistration(
                "objects.context", "Object settings",
                List.of("object.place", "object.move", "object.rotate", "object.duplicate",
                        "object.delete"), 0, () -> ignored -> settings.settings()));
        context.registry().registerInspector(new EditorInspectorRegistration(
                "objects.content", "Server content", "Objects",
                ObjectContentInspector::new));
    }

    private static void register(
            EditorPluginContext context,
            ObjectToolSettings settings,
            String id,
            String label,
            String category,
            String group,
            String icon,
            String shortcut,
            int order,
            Supplier<? extends com.rspsi.editor.tool.EditorTool> factory) {
        context.registry().registerTool(
                id, label, category, group, icon, shortcut, order,
                () -> {
                    var tool = factory.get();
                    settings.configure(id, tool);
                    return tool;
                });
    }
}
