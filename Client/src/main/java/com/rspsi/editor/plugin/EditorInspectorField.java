package com.rspsi.editor.plugin;

import java.util.Objects;

/** Immutable, display-neutral value emitted by a plugin inspector. */
public record EditorInspectorField(String id, String label, String value) {
    public EditorInspectorField {
        id = text(id, "inspector field id");
        label = text(label, "inspector field label");
        value = Objects.requireNonNull(value, "inspector field value");
    }

    private static String text(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return normalized;
    }
}
