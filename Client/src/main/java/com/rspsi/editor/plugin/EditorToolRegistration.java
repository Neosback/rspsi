package com.rspsi.editor.plugin;

import com.rspsi.editor.tool.EditorTool;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Stable metadata for one tool contribution. The registry key is the
 * contribution identity; it may intentionally differ from the tool's
 * intrinsic implementation id when a plugin exposes configured variants.
 */
public record EditorToolRegistration(
        String id,
        String label,
        String category,
        String toolGroup,
        String icon,
        String shortcut,
        int order,
        ToolUiDescriptor ui,
        Supplier<? extends EditorTool> factory) {

    public EditorToolRegistration {
        id = requireText(id, "tool id");
        label = requireText(label, "tool label");
        category = requireText(category, "tool category");
        toolGroup = toolGroup != null && !toolGroup.isBlank() ? toolGroup.trim() : category;
        ui = ui == null ? ToolUiDescriptor.defaults() : ui;
        Objects.requireNonNull(factory, "tool factory");
    }

    /** Compatibility constructor retaining the pre-tool-descriptor registration shape. */
    public EditorToolRegistration(
            String id,
            String label,
            String category,
            String toolGroup,
            String icon,
            String shortcut,
            int order,
            Supplier<? extends EditorTool> factory) {
        this(id, label, category, toolGroup, icon, shortcut, order,
                ToolUiDescriptor.defaults(), factory);
    }

    public EditorToolRegistration(
            String id,
            String label,
            String category,
            Supplier<? extends EditorTool> factory) {
        this(id, label, category, category, null, null, 0,
                ToolUiDescriptor.defaults(), factory);
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be empty");
        }
        return normalized;
    }
}
