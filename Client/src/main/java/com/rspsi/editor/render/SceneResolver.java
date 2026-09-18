package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldDocument;

/** Resolves authored editor data into immutable RuneScape scene semantics. */
public interface SceneResolver {
    RenderScene resolve(WorldDocument document);
}
