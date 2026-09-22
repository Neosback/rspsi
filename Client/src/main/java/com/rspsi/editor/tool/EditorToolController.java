package com.rspsi.editor.tool;

import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.input.PointerEventSink;

import java.util.Objects;

/** Dispatches neutral pointer events to the active tool. */
public final class EditorToolController implements PointerEventSink {
    private EditorTool activeTool;

    public void activate(EditorTool tool, ToolContext context) {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(context, "context");
        if (activeTool != null) {
            activeTool.deactivate();
        }
        activeTool = tool;
        activeTool.activate(context);
    }

    public void deactivate() {
        if (activeTool != null) {
            activeTool.deactivate();
            activeTool = null;
        }
    }

    public EditorTool activeTool() {
        return activeTool;
    }

    @Override
    public void pointerDown(PointerEvent event) {
        if (activeTool != null) activeTool.pointerDown(event);
    }

    @Override
    public void pointerDrag(PointerEvent event) {
        if (activeTool != null) activeTool.pointerDrag(event);
    }

    @Override
    public void pointerUp(PointerEvent event) {
        if (activeTool != null) activeTool.pointerUp(event);
    }

    @Override
    public void pointerMove(PointerEvent event) {
        if (activeTool != null) activeTool.pointerMove(event);
    }
}
