package com.rspsi.editor.input;

/** Frontend-neutral destination for pointer lifecycle events. */
public interface PointerEventSink {
    void pointerDown(PointerEvent event);

    void pointerDrag(PointerEvent event);

    void pointerUp(PointerEvent event);
}
