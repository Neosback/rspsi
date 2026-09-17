package com.rspsi.editor.input;

import java.util.Objects;

/** Immutable keyboard input translated from JavaFX, Dear ImGui, or another frontend. */
public record EditorKeyEvent(
        String key,
        boolean pressed,
        boolean repeat,
        boolean shift,
        boolean ctrl,
        boolean alt,
        boolean meta
) {
    public EditorKeyEvent {
        key = Objects.requireNonNull(key, "key").trim();
        if (key.isEmpty()) throw new IllegalArgumentException("key cannot be empty");
    }
}
