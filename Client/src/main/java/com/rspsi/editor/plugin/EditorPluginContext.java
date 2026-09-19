package com.rspsi.editor.plugin;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.generation.GeneratorService;
import com.rspsi.editor.knowledge.WorldKnowledgeService;
import com.rspsi.editor.settings.EditorSettingKeys;
import com.rspsi.editor.settings.SettingsService;
import com.rspsi.editor.settings.SettingsStore;

import java.util.Objects;
import java.util.Optional;

/**
 * Universal runtime context provided to first-party and third-party editor plugins.
 *
 * <p>Implements {@link PluginContext} to expose neutral, stable service handles.</p>
 */
public record EditorPluginContext(
        EditorSession session,
        AssetRepository assets,
        EditorPluginRegistry registry,
        Optional<EditorSceneAccess> scene,
        SettingsStore settings,
        SettingsService settingsService,
        EditorTaskService tasks,
        EditorNotificationService notifications,
        EditorPluginResources resources,
        WorldKnowledgeService knowledge,
        GeneratorService generators,
        com.rspsi.editor.symbols.SymbolService symbols,
        com.rspsi.editor.integration.reference.ReferenceService references,
        com.rspsi.editor.integration.npc.NpcSpawnService spawns,
        com.rspsi.editor.simulation.SimulationEngine simulation,
        com.rspsi.editor.integration.ServerIntegrationService integrations) implements PluginContext {

    public EditorPluginContext(
            EditorSession session,
            AssetRepository assets,
            EditorPluginRegistry registry,
            Optional<EditorSceneAccess> scene,
            SettingsStore settings,
            SettingsService settingsService,
            EditorTaskService tasks,
            EditorNotificationService notifications,
            EditorPluginResources resources,
            WorldKnowledgeService knowledge,
            GeneratorService generators) {
        this(session, assets, registry, scene, settings, settingsService, tasks, notifications, resources,
                knowledge, generators, null, null, null, null, null);
    }

    public EditorPluginContext(EditorSession session, AssetRepository assets) {
        this(session, assets, new EditorPluginRegistry(), Optional.empty(),
                defaultSettings(), null, new EditorTaskService(), new EditorNotificationService(),
                new EditorPluginResources(), null, null);
    }

    public EditorPluginContext(EditorSession session, AssetRepository assets,
                               EditorPluginRegistry registry) {
        this(session, assets, registry, Optional.empty(),
                defaultSettings(), null, new EditorTaskService(), new EditorNotificationService(),
                new EditorPluginResources(), null, null);
    }

    public EditorPluginContext(EditorSession session, AssetRepository assets,
                               EditorPluginRegistry registry,
                               Optional<EditorSceneAccess> scene) {
        this(session, assets, registry, scene,
                defaultSettings(), null, new EditorTaskService(), new EditorNotificationService(),
                new EditorPluginResources(), null, null);
    }

    /** Compatibility constructor for callers that already own resources. */
    public EditorPluginContext(EditorSession session, AssetRepository assets,
                               EditorPluginRegistry registry,
                               Optional<EditorSceneAccess> scene,
                               EditorPluginResources resources) {
        this(session, assets, registry, scene,
                defaultSettings(), null, new EditorTaskService(), new EditorNotificationService(),
                resources, null, null);
    }

    /** Compatibility constructor for callers that already own a settings store. */
    public EditorPluginContext(EditorSession session, AssetRepository assets,
                               EditorPluginRegistry registry,
                               Optional<EditorSceneAccess> scene,
                               SettingsStore settings,
                               EditorTaskService tasks,
                               EditorNotificationService notifications,
                               EditorPluginResources resources) {
        this(session, assets, registry, scene, settings, null, tasks, notifications, resources, null, null);
    }

    public EditorPluginContext(EditorSession session, AssetRepository assets,
                               EditorPluginRegistry registry,
                               Optional<EditorSceneAccess> scene,
                               SettingsStore settings,
                               SettingsService settingsService,
                               EditorTaskService tasks,
                               EditorNotificationService notifications,
                               EditorPluginResources resources) {
        this(session, assets, registry, scene, settings, settingsService, tasks, notifications, resources, null, null);
    }

    public EditorPluginContext {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(assets, "assets");
        Objects.requireNonNull(registry, "registry");
        scene = scene == null ? Optional.empty() : scene;
        Objects.requireNonNull(settings, "settings");
        settingsService = settingsService != null ? settingsService : new SettingsService(settings);
        Objects.requireNonNull(tasks, "tasks");
        Objects.requireNonNull(notifications, "notifications");
        Objects.requireNonNull(resources, "resources");
        knowledge = knowledge != null ? knowledge : new WorldKnowledgeService(session);
        generators = generators != null ? generators : new GeneratorService();
        symbols = symbols != null ? symbols : new com.rspsi.editor.symbols.SymbolService();
        references = references != null ? references : new com.rspsi.editor.integration.reference.ReferenceService();
        spawns = spawns != null ? spawns : new com.rspsi.editor.integration.npc.NpcSpawnService();
        simulation = simulation != null ? simulation : new com.rspsi.editor.simulation.SimulationEngine();
        integrations = integrations != null ? integrations : new com.rspsi.editor.integration.ServerIntegrationService(symbols, references, spawns);
    }

    @Override
    public ContributionOwner owner() {
        return ContributionOwner.SYSTEM;
    }

    /** Tracks a plugin-owned resource for automatic host cleanup. */
    @Override
    public <T extends AutoCloseable> T track(T resource) {
        return resources.track(resource);
    }

    /** Returns a fluent, user-friendly API helper bound to the specified plugin. */
    public PluginApi api(EditorPlugin plugin) {
        return new PluginApi(this, plugin);
    }

    private static SettingsStore defaultSettings() {
        return new SettingsStore(EditorSettingKeys.registry());
    }
}
