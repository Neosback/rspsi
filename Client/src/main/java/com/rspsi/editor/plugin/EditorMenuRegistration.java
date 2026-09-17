package com.rspsi.editor.plugin;

import java.util.List;
import java.util.Objects;

/** Shell-owned menu entry that invokes a registered neutral command by ID. */
public record EditorMenuRegistration(
        String id,
        List<String> path,
        String label,
        String commandId,
        int order) {
    public EditorMenuRegistration {
        id = text(id, "menu contribution id");
        path = List.copyOf(path == null ? List.of() : path);
        if (path.isEmpty() || path.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Menu path cannot be empty");
        }
        label = text(label, "menu label");
        commandId = text(commandId, "menu command id");
    }

    private static String text(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return normalized;
    }
}
