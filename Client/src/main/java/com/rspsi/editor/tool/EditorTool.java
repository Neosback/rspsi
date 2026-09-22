package com.rspsi.editor.tool;

import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.render.OverlayDraw;

/** Small lifecycle contract shared by JavaFX and future ImGui frontends. */
public interface EditorTool {
    String id();

    void activate(ToolContext context);

    void deactivate();

    void pointerDown(PointerEvent event);

    void pointerDrag(PointerEvent event);

    void pointerUp(PointerEvent event);

    default void pointerMove(PointerEvent event) {
    }

    default void renderOverlay(OverlayDraw draw) {
    }

    default ToolInspector inspector() {
        return ListToolInspector.EMPTY;
    }

    final class ListToolInspector {
        private ListToolInspector() {
        }

        private static final ToolInspector EMPTY = java.util.List::of;
    }
}
