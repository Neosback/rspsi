package com.rspsi.editor.plugin;

import java.util.Objects;
import java.util.function.Supplier;

/** Stable metadata and factory for one plugin validation contribution. */
public record EditorValidatorRegistration(
        String id,
        String label,
        String category,
        Supplier<? extends EditorValidator> factory) {
    public EditorValidatorRegistration {
        id = text(id, "validator id");
        label = text(label, "validator label");
        category = text(category, "validator category");
        Objects.requireNonNull(factory, "validator factory");
    }

    private static String text(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return normalized;
    }
}
