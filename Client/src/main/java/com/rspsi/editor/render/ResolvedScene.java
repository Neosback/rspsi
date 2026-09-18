package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldDocument;

/** Stable semantic name for the immutable scene produced by a resolver. */
public interface ResolvedScene {
    WorldDocument document();
}
