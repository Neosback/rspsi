package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.editor.model.WorldDocument;

import java.util.Objects;

/** Revision-aware scene-resolver boundary backed by the current RSPSi builder. */
public final class OsrsSceneResolver implements SceneResolver {
    private final RenderSceneBuilder delegate;

    public OsrsSceneResolver() {
        this.delegate = new RenderSceneBuilder();
    }

    public OsrsSceneResolver(DefinitionProvider definitions) {
        this.delegate = new RenderSceneBuilder(Objects.requireNonNull(definitions, "definitions"));
    }

    @Override
    public RenderScene resolve(WorldDocument document) {
        return delegate.build(Objects.requireNonNull(document, "document"));
    }
}
