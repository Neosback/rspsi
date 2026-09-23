package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;

import java.util.Objects;

/**
 * Compiles a packed instance-template scene through the ordinary neutral
 * render pipeline after materialization.
 */
public final class InstanceRenderSceneBuilder {
    private final InstanceSceneMaterializer materializer;
    private final RenderSceneBuilder scenes;

    public InstanceRenderSceneBuilder(DefinitionProvider definitions) {
        DefinitionProvider source = Objects.requireNonNull(definitions, "definitions");
        this.materializer = new InstanceSceneMaterializer(source);
        this.scenes = new RenderSceneBuilder(source);
    }

    public RenderScene build(SceneWindow window) {
        return build(window, 0);
    }

    public RenderScene build(SceneWindow window, int clientCycle) {
        if (clientCycle < 0) {
            throw new IllegalArgumentException("Client cycle cannot be negative");
        }
        return scenes.build(materializer.materialize(window), clientCycle);
    }
}
