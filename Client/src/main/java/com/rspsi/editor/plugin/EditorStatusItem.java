package com.rspsi.editor.plugin;

import java.util.Objects;

/** Immutable status-bar value contributed by a feature. */
public record EditorStatusItem(String id, String label, String value) {
    public EditorStatusItem {
        id = text(id, "status item id");
        label = text(label, "status item label");
        value = Objects.requireNonNull(value, "status item value");
    }

    private static String text(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return normalized;
    }
}
