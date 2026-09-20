package com.rspsi.editor.plugin;

import java.util.Objects;
import java.util.function.Supplier;

/** Stable metadata and factory for one plugin scene-overlay contribution. */
public record EditorOverlayRegistration(
        String id,
        String label,
        String category,
        boolean enabledByDefault,
        Supplier<? extends EditorSceneOverlay> factory) {

    public EditorOverlayRegistration(String id, String label, String category,
                                    Supplier<? extends EditorSceneOverlay> factory) {
        this(id, label, category, false, factory);
    }

    public EditorOverlayRegistration {
        id = text(id, "overlay id");
        label = text(label, "overlay label");
        category = text(category, "overlay category");
        Objects.requireNonNull(factory, "overlay factory");
    }

    private static String text(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return normalized;
    }
}
