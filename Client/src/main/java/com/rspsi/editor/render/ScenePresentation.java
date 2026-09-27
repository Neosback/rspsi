package com.rspsi.editor.render;

import com.rspsi.cache.definition.ObjectVarState;

import java.util.Objects;

/**
 * What a scene build is for, and whose game state it shows.
 *
 * <p>{@link #PARITY} reproduces exactly what the client submits for a fresh
 * account and is what verifiers, audits and exports use. {@link #EDITOR}
 * adds editor-only presentation (translucent ghosts for placed locs that draw
 * nothing). {@link #withVarState} resolves multilocs against another player's
 * vars, e.g. the Studio's simulated player.</p>
 */
public record ScenePresentation(boolean editorGhosts, ObjectVarState varState, boolean objectAnimations) {
    public static final ScenePresentation PARITY =
            new ScenePresentation(false, ObjectVarState.freshAccount(), true);
    public static final ScenePresentation EDITOR =
            new ScenePresentation(true, ObjectVarState.freshAccount(), true);

    public ScenePresentation(boolean editorGhosts, ObjectVarState varState) {
        this(editorGhosts, varState, true);
    }

    public ScenePresentation {
        Objects.requireNonNull(varState, "varState");
    }

    public ScenePresentation withVarState(ObjectVarState state) {
        return new ScenePresentation(editorGhosts, state, objectAnimations);
    }

    public ScenePresentation withObjectAnimations(boolean enabled) {
        return new ScenePresentation(editorGhosts, varState, enabled);
    }
}
