package com.rspsi.editor.plugin;

import java.util.Objects;
import java.util.function.Supplier;

/** Metadata and factory for one plugin-owned status contribution. */
public record EditorStatusRegistration(
        String id,
        String label,
        int order,
        Supplier<? extends EditorStatusContribution> factory) {
    public EditorStatusRegistration {
        id = text(id, "status contribution id");
        label = text(label, "status contribution label");
        Objects.requireNonNull(factory, "status contribution factory");
    }

    private static String text(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return normalized;
    }
}
