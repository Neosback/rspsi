package com.rspsi.editor.render;

/**
 * What a scene build is for.
 *
 * <p>{@link #PARITY} reproduces exactly what the client submits and is what
 * verifiers, audits and exports use. {@link #EDITOR} adds editor-only
 * presentation on top, currently translucent ghosts for placed locs that
 * draw nothing, so every authored object stays visible and selectable.</p>
 */
public enum ScenePresentation {
    PARITY,
    EDITOR;

    public boolean editorGhosts() {
        return this == EDITOR;
    }
}
