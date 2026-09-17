package com.rspsi.editor.plugin;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.assets.AssetRepository;

import java.util.Objects;
import java.util.Optional;

/** Neutral services available to a first-party editor plugin. */
public record EditorPluginContext(
        EditorSession session,
        AssetRepository assets,
        EditorPluginRegistry registry,
        Optional<EditorSceneAccess> scene,
        EditorPluginResources resources) {
    public EditorPluginContext(EditorSession session, AssetRepository assets) {
        this(session, assets, new EditorPluginRegistry(), Optional.empty(), new EditorPluginResources());
    }

    public EditorPluginContext(EditorSession session, AssetRepository assets,
                               EditorPluginRegistry registry) {
        this(session, assets, registry, Optional.empty(), new EditorPluginResources());
    }

    public EditorPluginContext(EditorSession session, AssetRepository assets,
                               EditorPluginRegistry registry,
                               Optional<EditorSceneAccess> scene) {
        this(session, assets, registry, scene, new EditorPluginResources());
    }

    public EditorPluginContext {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(assets, "assets");
        Objects.requireNonNull(registry, "registry");
        scene = scene == null ? Optional.empty() : scene;
        Objects.requireNonNull(resources, "resources");
    }

    /** Tracks a plugin-owned resource for automatic host cleanup. */
    public <T extends AutoCloseable> T track(T resource) {
        return resources.track(resource);
    }
}
