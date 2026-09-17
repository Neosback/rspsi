package com.rspsi.editor.plugin;

import java.util.Objects;
import java.util.function.Supplier;

/** Stable metadata and factory for one plugin keyboard shortcut. */
public record EditorShortcutRegistration(
        String id,
        String label,
        String key,
        boolean shift,
        boolean ctrl,
        boolean alt,
        boolean meta,
        Supplier<? extends EditorShortcut> factory) {
    public EditorShortcutRegistration {
        id = text(id, "shortcut id");
        label = text(label, "shortcut label");
        key = text(key, "shortcut key");
        Objects.requireNonNull(factory, "shortcut factory");
    }

    public boolean matches(com.rspsi.editor.input.EditorKeyEvent event) {
        Objects.requireNonNull(event, "event");
        return event.pressed() && key.equalsIgnoreCase(event.key())
                && shift == event.shift() && ctrl == event.ctrl()
                && alt == event.alt() && meta == event.meta();
    }

    private static String text(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return normalized;
    }
}
