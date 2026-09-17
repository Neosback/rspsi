package com.rspsi.editor.frontend;

import com.rspsi.editor.input.EditorInputRouter;
import com.rspsi.editor.input.EditorKeyEvent;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.plugin.EditorPluginContext;

import java.util.Objects;

/**
 * Minimal Dear ImGui host seam.
 *
 * <p>The native ImGui context and draw submission belong in the UI module. This
 * class proves the important foundation rule: the future host reads the same
 * plugin/session/scene projection and sends input through the same neutral
 * router. It intentionally contains no ImGui or graphics-library dependency.</p>
 */
public final class DearImGuiFrontendAdapter {
    private final EditorPluginContext context;
    private final EditorInputRouter input;

    public DearImGuiFrontendAdapter(EditorPluginContext context, EditorInputRouter input) {
        this.context = Objects.requireNonNull(context, "context");
        this.input = Objects.requireNonNull(input, "input");
    }

    public EditorPluginContext context() {
        return context;
    }

    public EditorFrontendFrame frame() {
        return EditorFrontendProjection.capture(context);
    }

    public boolean key(EditorKeyEvent event, boolean textInputFocused) {
        return input.key(event, textInputFocused);
    }

    public void pointerDown(PointerEvent event) {
        input.pointerDown(event);
    }

    public void pointerDrag(PointerEvent event) {
        input.pointerDrag(event);
    }

    public void pointerUp(PointerEvent event) {
        input.pointerUp(event);
    }
}
