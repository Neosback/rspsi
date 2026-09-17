package com.rspsi.editor.plugin.builtin;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.EditorPluginRegistry;
import com.rspsi.editor.plugin.EditorToolContextRegistration;
import com.rspsi.editor.tool.AttributeSelectionTool;
import com.rspsi.editor.tool.BoxSelectTool;
import com.rspsi.editor.tool.DuplicateSelectionTool;
import com.rspsi.editor.tool.LassoSelectTool;
import com.rspsi.editor.tool.MoveSelectionTool;
import com.rspsi.editor.tool.ReplaceSelectionTool;
import com.rspsi.editor.tool.RotateSelectionTool;

import java.util.List;

/** First-party vertical plugin for selection and transform tools. */
public final class SelectionToolsPlugin implements EditorPlugin {
    public static final String ID = "rspsi.tools.selection";
    private final SelectionToolSettings settings = new SelectionToolSettings();

    @Override public String id() { return ID; }

    @Override public int loadOrder() { return 30; }

    @Override
    public void initialize(EditorPluginContext context) {
        EditorPluginRegistry registry = context.registry();
        register(registry, "selection.box", "Box select", BoxSelectTool::new);
        register(registry, "selection.lasso", "Lasso select", LassoSelectTool::new);
        register(registry, "selection.attribute", "Select by attribute", AttributeSelectionTool::new);
        register(registry, "selection.move", "Move selection", MoveSelectionTool::new);
        register(registry, "selection.rotate", "Rotate selection", RotateSelectionTool::new);
        register(registry, "selection.duplicate", "Duplicate selection", DuplicateSelectionTool::new);
        register(registry, "selection.replace", "Replace selection", () -> new ReplaceSelectionTool(0));
        registry.registerToolContext(new EditorToolContextRegistration(
                "selection.context", "Selection settings",
                List.of("selection.box", "selection.lasso", "selection.attribute", "selection.move",
                        "selection.rotate", "selection.duplicate", "selection.replace"), 0,
                () -> ignored -> settings.settings()));
    }

    private void register(EditorPluginRegistry registry, String id, String label,
                           java.util.function.Supplier<? extends com.rspsi.editor.tool.EditorTool> factory) {
        registry.registerTool(id, label, "Selection / transforms", () -> {
            var tool = factory.get();
            settings.configure(id, tool);
            return tool;
        });
    }
}
