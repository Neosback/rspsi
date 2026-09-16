package com.rspsi.editor.input;

/** Immutable pointer event translated from JavaFX, GLFW, or another frontend. */
public record PointerEvent(
        float x,
        float y,
        PointerButton button,
        boolean shift,
        boolean ctrl,
        boolean alt
) {
    public PointerEvent {
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            throw new IllegalArgumentException("Pointer coordinates must be finite");
        }
        if (button == null) {
            throw new NullPointerException("button");
        }
    }
}
