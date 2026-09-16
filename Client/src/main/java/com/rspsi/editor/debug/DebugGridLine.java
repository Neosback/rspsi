package com.rspsi.editor.debug;

/** A world-space grid line independent of camera, JavaFX, or OpenGL types. */
public record DebugGridLine(DebugGridLevel level, int startX, int startY, int endX, int endY) {
    public DebugGridLine {
        if (level == null) {
            throw new NullPointerException("level");
        }
    }
}
