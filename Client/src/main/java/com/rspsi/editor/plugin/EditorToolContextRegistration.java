package com.rspsi.editor.plugin;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Metadata and factory for one feature-owned tool-context contribution. */
public record EditorToolContextRegistration(
        String id,
        String label,
        List<String> toolIds,
        int order,
        Supplier<? extends EditorToolContextContribution> factory) {
    public EditorToolContextRegistration {
        id = text(id, "tool context id");
        label = text(label, "tool context label");
        toolIds = List.copyOf(toolIds == null ? List.of() : toolIds);
        if (toolIds.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Tool context tool IDs cannot be blank");
        }
        Objects.requireNonNull(factory, "tool context factory");
    }

    public boolean supports(String toolId) {
        return toolIds.isEmpty() || toolIds.contains(Objects.requireNonNull(toolId, "tool id"));
    }

    private static String text(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return normalized;
    }
}
