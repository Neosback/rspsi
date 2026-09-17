package com.rspsi.editor.plugin;

import com.rspsi.editor.EditorCommand;

import java.util.Objects;
import java.util.function.Supplier;

/** Stable metadata and factory for one plugin-owned command contribution. */
public record EditorCommandRegistration(
        String id,
        String label,
        String category,
        Supplier<? extends EditorCommand> factory) {
    public EditorCommandRegistration {
        id = text(id, "command id");
        label = text(label, "command label");
        category = text(category, "command category");
        Objects.requireNonNull(factory, "command factory");
    }

    private static String text(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return normalized;
    }
}
