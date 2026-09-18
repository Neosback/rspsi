package com.rspsi.editor.plugin;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.settings.EditorSettingKeys;
import com.rspsi.editor.settings.SettingsStore;

import java.util.Objects;
import java.util.Optional;

/** Neutral services available to a first-party editor plugin. */
public record EditorPluginContext(
        EditorSession session,
        AssetRepository assets,
        EditorPluginRegistry registry,
        Optional<EditorSceneAccess> scene,
        SettingsStore settings,
        EditorTaskService tasks,
        EditorNotificationService notifications,
        EditorPluginResources resources) {
    public EditorPluginContext(EditorSession session, AssetRepository assets) {
        this(session, assets, new EditorPluginRegistry(), Optional.empty(),
                defaultSettings(), new EditorTaskService(), new EditorNotificationService(),
                new EditorPluginResources());
    }

    public EditorPluginContext(EditorSession session, AssetRepository assets,
                               EditorPluginRegistry registry) {
        this(session, assets, registry, Optional.empty(),
                defaultSettings(), new EditorTaskService(), new EditorNotificationService(),
                new EditorPluginResources());
    }

    public EditorPluginContext(EditorSession session, AssetRepository assets,
                               EditorPluginRegistry registry,
                               Optional<EditorSceneAccess> scene) {
        this(session, assets, registry, scene,
                defaultSettings(), new EditorTaskService(), new EditorNotificationService(),
                new EditorPluginResources());
    }

    /** Compatibility constructor for callers that already own resources. */
    public EditorPluginContext(EditorSession session, AssetRepository assets,
                               EditorPluginRegistry registry,
                               Optional<EditorSceneAccess> scene,
                               EditorPluginResources resources) {
        this(session, assets, registry, scene,
                defaultSettings(), new EditorTaskService(), new EditorNotificationService(), resources);
    }

    public EditorPluginContext {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(assets, "assets");
        Objects.requireNonNull(registry, "registry");
        scene = scene == null ? Optional.empty() : scene;
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(tasks, "tasks");
        Objects.requireNonNull(notifications, "notifications");
        Objects.requireNonNull(resources, "resources");
    }

    /** Tracks a plugin-owned resource for automatic host cleanup. */
    public <T extends AutoCloseable> T track(T resource) {
        return resources.track(resource);
    }

    private static SettingsStore defaultSettings() {
        return new SettingsStore(EditorSettingKeys.registry());
    }
}
