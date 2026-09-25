package com.rspsi.editor.plugin.ui;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Frontend-neutral UI surfaces owned by one map tool.
 *
 * <p>Each supplier is evaluated by the host when its surface is rendered so
 * extension UI can reflect current tool/session state without retaining native
 * widget objects.</p>
 */
public record ToolUiContent(
        Supplier<? extends EditorUiNode> contextDrawer,
        Supplier<? extends EditorUiNode> quickPalette,
        Supplier<? extends EditorUiNode> inspector) {

    public static ToolUiContent empty() {
        return new ToolUiContent(null, null, null);
    }

    public Optional<EditorUiNode> contextDrawerNode() {
        return resolve(contextDrawer);
    }

    public Optional<EditorUiNode> quickPaletteNode() {
        return resolve(quickPalette);
    }

    public Optional<EditorUiNode> inspectorNode() {
        return resolve(inspector);
    }

    public boolean hasContextDrawer() {
        return contextDrawer != null;
    }

    public boolean hasQuickPalette() {
        return quickPalette != null;
    }

    public boolean hasInspector() {
        return inspector != null;
    }

    private static Optional<EditorUiNode> resolve(Supplier<? extends EditorUiNode> supplier) {
        return supplier == null ? Optional.empty() : Optional.ofNullable(supplier.get());
    }
}
