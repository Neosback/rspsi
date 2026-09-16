package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldDocument;

import java.util.Objects;

/** Neutral scene input; renderers observe a document through this snapshot. */
public record RenderScene(WorldDocument document) {
    public RenderScene {
        Objects.requireNonNull(document, "document");
    }
}
