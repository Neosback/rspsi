package com.rspsi.editor.input;

import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.tool.EditorToolController;

import java.util.Objects;

/**
 * Routes frontend-translated input through the neutral editor contracts.
 *
 * <p>JavaFX, Dear ImGui, and future frontends may disagree about how focus is
 * represented, but they must agree on this ordering: a focused text editor
 * keeps text-editing keys, otherwise registered editor shortcuts get the first
 * chance to handle a pressed key. Pointer events continue to flow to the
 * active tool controller.</p>
 */
public final class EditorInputRouter implements PointerEventSink {
    private final EditorPluginContext pluginContext;
    private final EditorToolController tools;

    public EditorInputRouter(EditorPluginContext pluginContext,
                             EditorToolController tools) {
        this.pluginContext = Objects.requireNonNull(pluginContext, "pluginContext");
        this.tools = Objects.requireNonNull(tools, "tools");
    }

    /** Dispatches a key when no frontend text editor currently owns focus. */
    public boolean key(EditorKeyEvent event) {
        return key(event, false);
    }

    /**
     * Dispatches one translated key event.
     *
     * @param textInputFocused true when a text field owns keyboard input;
     *                         global editor shortcuts are then skipped
     */
    public boolean key(EditorKeyEvent event, boolean textInputFocused) {
        Objects.requireNonNull(event, "event");
        if (textInputFocused || !event.pressed()) return false;
        return pluginContext.registry().dispatchShortcut(pluginContext, event);
    }

    public EditorToolController tools() {
        return tools;
    }

    @Override
    public void pointerDown(PointerEvent event) {
        tools.pointerDown(event);
    }

    @Override
    public void pointerDrag(PointerEvent event) {
        tools.pointerDrag(event);
    }

    @Override
    public void pointerUp(PointerEvent event) {
        tools.pointerUp(event);
    }
}
