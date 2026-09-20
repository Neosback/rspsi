package com.rspsi.editor.brush;

/** Neutral contract for editor tools that consume a shared spatial brush. */
public interface BrushAwareTool {
    EditorBrush brush();

    void setBrush(EditorBrush brush);

    int brushRadius();

    void setBrushRadius(int radius);
}
