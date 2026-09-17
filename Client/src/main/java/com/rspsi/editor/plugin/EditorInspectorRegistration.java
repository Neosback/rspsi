package com.rspsi.editor.plugin;

import java.util.Objects;
import java.util.function.Supplier;

/** Stable metadata and factory for one plugin inspector contribution. */
public record EditorInspectorRegistration(
        String id,
        String label,
        String category,
        Supplier<? extends EditorInspector> factory) {
    public EditorInspectorRegistration {
        id = text(id, "inspector id");
        label = text(label, "inspector label");
        category = text(category, "inspector category");
        Objects.requireNonNull(factory, "inspector factory");
    }

    private static String text(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return normalized;
    }
}
