package com.rspsi.editor.render;

/**
 * Scene-placement translation for one client renderable whose
 * {@link ClientModelBounds} remain model-local.
 */
public record ClientRenderablePlacement(int offsetX, int offsetZ) {
    private static final ClientRenderablePlacement NONE = new ClientRenderablePlacement(0, 0);

    public static ClientRenderablePlacement none() {
        return NONE;
    }
}
