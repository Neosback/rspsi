package com.rspsi.editor.plugin.builtin;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.EditorPluginRegistry;
import com.rspsi.editor.plugin.EditorToolContextRegistration;
import com.rspsi.editor.tool.DeleteObjectTool;
import com.rspsi.editor.tool.DuplicateObjectTool;
import com.rspsi.editor.tool.MoveObjectTool;
import com.rspsi.editor.tool.PlaceObjectTool;
import com.rspsi.editor.tool.RotateObjectTool;

import java.util.List;

/** First-party vertical plugin for object editing tools. */
public final class ObjectToolsPlugin implements EditorPlugin {
    public static final String ID = "rspsi.tools.objects";
    private final ObjectToolSettings settings = new ObjectToolSettings();

    @Override public String id() { return ID; }

    @Override public int loadOrder() { return 20; }

    @Override
    public void initialize(EditorPluginContext context) {
        EditorPluginRegistry registry = context.registry();
        register(registry, "object.place", "Place object", () -> new PlaceObjectTool(0, 10, 0));
        register(registry, "object.move", "Move object", MoveObjectTool::new);
        register(registry, "object.rotate", "Rotate object", RotateObjectTool::new);
        register(registry, "object.duplicate", "Duplicate object", DuplicateObjectTool::new);
        register(registry, "object.delete", "Delete object", DeleteObjectTool::new);
        registry.registerToolContext(new EditorToolContextRegistration(
                "objects.context", "Object settings",
                List.of("object.place", "object.move", "object.rotate", "object.duplicate",
                        "object.delete"), 0, () -> ignored -> settings.settings()));
    }

    private void register(EditorPluginRegistry registry, String id, String label,
                           java.util.function.Supplier<? extends com.rspsi.editor.tool.EditorTool> factory) {
        registry.registerTool(id, label, "Objects", () -> {
            var tool = factory.get();
            settings.configure(id, tool);
            return tool;
        });
    }
}
