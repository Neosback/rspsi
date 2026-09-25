package com.rspsi.editor.plugin;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.core.CoreEditorModule;
import com.rspsi.editor.core.CoreEditorModules;
import com.rspsi.editor.settings.SettingsStore;
import com.rspsi.editor.settings.EditorSettingKeys;
import com.rspsi.editor.plugin.services.PluginServices;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Initializes and owns neutral editor plugins against one session and registry.
 *
 * <p>The host also owns plugin lifecycle. Closing it calls plugin shutdown in
 * reverse initialization order and removes each plugin's registered
 * contributions, allowing a JavaFX or Dear ImGui frontend to rebuild its
 * projection without retaining stale tools or panels.</p>
 */
public final class EditorPluginHost implements AutoCloseable {
    private final List<EditorPlugin> plugins;
    private final List<String> coreModuleIds;
    private final EditorPluginRegistry registry;
    private final EditorPluginContext context;
    private final List<LoadedPlugin> loadedPlugins;
    private final AtomicBoolean closed = new AtomicBoolean();

    private EditorPluginHost(List<LoadedPlugin> loadedPlugins,
                             List<String> coreModuleIds,
                             EditorPluginRegistry registry,
                             EditorPluginContext context) {
        this.loadedPlugins = new ArrayList<>(loadedPlugins);
        this.plugins = new ArrayList<>(this.loadedPlugins.stream().map(LoadedPlugin::plugin).toList());
        this.coreModuleIds = List.copyOf(coreModuleIds);
        this.registry = Objects.requireNonNull(registry, "registry");
        this.context = Objects.requireNonNull(context, "context");
    }

    public List<EditorPlugin> plugins() {
        return List.copyOf(plugins);
    }

    /** Always-on application modules installed before optional extensions. */
    public List<String> coreModuleIds() {
        return coreModuleIds;
    }

    public EditorPluginRegistry registry() {
        return registry;
    }

    public EditorPluginContext context() {
        return context;
    }

    public static EditorPluginHost initialize(
            Iterable<? extends EditorPlugin> plugins,
            EditorSession session,
            AssetRepository assets) {
        return initialize(List.of(), plugins, session, assets, Optional.empty());
    }

    public static EditorPluginHost initializeWithCoreModules(
            Iterable<? extends CoreEditorModule> coreModules,
            Iterable<? extends EditorPlugin> plugins,
            EditorSession session,
            AssetRepository assets) {
        return initialize(coreModules, plugins, session, assets, Optional.empty());
    }

    public static EditorPluginHost initialize(
            Iterable<? extends EditorPlugin> plugins,
            EditorSession session,
            AssetRepository assets,
            EditorSceneAccess scene) {
        return initialize(List.of(), plugins, session, assets, Optional.of(scene));
    }

    /** Initializes plugins with application-owned neutral services. */
    public static EditorPluginHost initialize(
            Iterable<? extends EditorPlugin> plugins,
            EditorSession session,
            AssetRepository assets,
            EditorSceneAccess scene,
            SettingsStore settings,
            EditorTaskService tasks,
            EditorNotificationService notifications) {
        return initialize(List.of(), plugins, session, assets, Optional.ofNullable(scene),
                settings, tasks, notifications, null, null, null, null, null, null, null);
    }

    /**
     * Initializes the application runtime with always-on core modules plus
     * optional external extensions. Core modules are not plugin candidates:
     * they cannot be disabled independently and are not exposed as installed
     * JAR plugins, but both paths register into this same host/registry.
     */
    public static EditorPluginHost initializeWithCoreModules(
            Iterable<? extends CoreEditorModule> coreModules,
            Iterable<? extends EditorPlugin> plugins,
            EditorSession session,
            AssetRepository assets,
            EditorSceneAccess scene,
            SettingsStore settings,
            EditorTaskService tasks,
            EditorNotificationService notifications,
            com.rspsi.editor.knowledge.WorldKnowledgeService knowledge,
            com.rspsi.editor.generation.GeneratorService generators,
            com.rspsi.editor.symbols.SymbolService symbols,
            com.rspsi.editor.integration.reference.ReferenceService references,
            com.rspsi.editor.integration.npc.NpcSpawnService spawns,
            com.rspsi.editor.simulation.SimulationEngine simulation,
            com.rspsi.editor.integration.ServerIntegrationService integrations) {
        return initialize(coreModules, plugins, session, assets, Optional.ofNullable(scene),
                settings, tasks, notifications, knowledge, generators,
                symbols, references, spawns, simulation, integrations);
    }

    /** Initializes plugins with full studio runtime services. */
    public static EditorPluginHost initialize(
            Iterable<? extends EditorPlugin> plugins,
            EditorSession session,
            AssetRepository assets,
            EditorSceneAccess scene,
            SettingsStore settings,
            EditorTaskService tasks,
            EditorNotificationService notifications,
            com.rspsi.editor.knowledge.WorldKnowledgeService knowledge,
            com.rspsi.editor.generation.GeneratorService generators,
            com.rspsi.editor.symbols.SymbolService symbols,
            com.rspsi.editor.integration.reference.ReferenceService references,
            com.rspsi.editor.integration.npc.NpcSpawnService spawns,
            com.rspsi.editor.simulation.SimulationEngine simulation,
            com.rspsi.editor.integration.ServerIntegrationService integrations) {
        return initialize(List.of(), plugins, session, assets, Optional.ofNullable(scene),
                settings, tasks, notifications, knowledge, generators,
                symbols, references, spawns, simulation, integrations);
    }

    private static EditorPluginHost initialize(
            Iterable<? extends CoreEditorModule> coreModules,
            Iterable<? extends EditorPlugin> plugins,
            EditorSession session,
            AssetRepository assets,
            Optional<EditorSceneAccess> scene) {
        return initialize(coreModules, plugins, session, assets, scene,
                new SettingsStore(EditorSettingKeys.registry()),
                new EditorTaskService(), new EditorNotificationService(),
                null, null, null, null, null, null, null);
    }

    private static EditorPluginHost initialize(
            Iterable<? extends CoreEditorModule> coreModules,
            Iterable<? extends EditorPlugin> plugins,
            EditorSession session,
            AssetRepository assets,
            Optional<EditorSceneAccess> scene,
            SettingsStore settings,
            EditorTaskService tasks,
            EditorNotificationService notifications,
            com.rspsi.editor.knowledge.WorldKnowledgeService knowledge,
            com.rspsi.editor.generation.GeneratorService generators,
            com.rspsi.editor.symbols.SymbolService symbols,
            com.rspsi.editor.integration.reference.ReferenceService references,
            com.rspsi.editor.integration.npc.NpcSpawnService spawns,
            com.rspsi.editor.simulation.SimulationEngine simulation,
            com.rspsi.editor.integration.ServerIntegrationService integrations) {
        Objects.requireNonNull(coreModules, "coreModules");
        Objects.requireNonNull(plugins, "plugins");
        EditorPluginRegistry registry = new EditorPluginRegistry();
        EditorPluginResources resources = new EditorPluginResources();
        com.rspsi.editor.settings.SettingsService settingsService = settings != null
                ? new com.rspsi.editor.settings.SettingsService(settings) : null;
        EditorPluginContext context = new EditorPluginContext(session, assets, registry, scene,
                settings, settingsService, tasks, notifications, resources,
                knowledge, generators, symbols, references, spawns, simulation, integrations);
        List<LoadedPlugin> initialized = new ArrayList<>();
        Set<String> pluginIds = new HashSet<>();
        List<CoreEditorModule> coreSnapshot = new ArrayList<>();
        try {
            for (CoreEditorModule module : coreModules) {
                coreSnapshot.add(Objects.requireNonNull(module, "core module"));
            }
            CoreEditorModules.install(coreSnapshot, context);

            List<EditorPlugin> discoveredPlugins = new ArrayList<>();
            for (EditorPlugin plugin : plugins) {
                discoveredPlugins.add(Objects.requireNonNull(plugin, "plugin"));
            }
            Set<String> availableCoreIds = coreSnapshot.stream()
                    .map(CoreEditorModule::id)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            for (EditorPlugin plugin : discoveredPlugins) {
                String pluginId = requireId(plugin.id());
                if (availableCoreIds.contains(pluginId)) {
                    throw new IllegalArgumentException(
                            "External plugin id conflicts with core editor module: " + pluginId);
                }
            }
            List<EditorPlugin> orderedPlugins = orderPlugins(discoveredPlugins, availableCoreIds);
            for (EditorPlugin checked : orderedPlugins) {
                String pluginId = requireId(checked.id());
                if (!pluginIds.add(pluginId)) {
                    throw new IllegalArgumentException("Duplicate editor plugin: " + pluginId);
                }
                ContributionSet before = ContributionSet.capture(registry);
                try {
                    checked.initialize(context);
                } catch (RuntimeException | Error failure) {
                    removeContributions(registry, ContributionSet.capture(registry).difference(before));
                    throw failure;
                }
                ContributionSet contributions = ContributionSet.capture(registry).difference(before);
                initialized.add(new LoadedPlugin(checked, contributions));
            }
            registry.validateReferences();
            List<String> installedCoreIds = coreSnapshot.stream()
                    .sorted(Comparator.comparingInt(CoreEditorModule::order)
                            .thenComparing(CoreEditorModule::id))
                    .map(CoreEditorModule::id)
                    .toList();
            return new EditorPluginHost(initialized, installedCoreIds, registry, context);
        } catch (RuntimeException | Error failure) {
            shutdownReverse(initialized, registry, context, failure);
            closeResources(resources, failure);
            releasePluginServices(registry, failure);
            registry.close();
            throw failure;
        }
    }

    /** Idempotently releases plugins and removes their registry contributions. */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        Throwable failure = null;
        for (int index = loadedPlugins.size() - 1; index >= 0; index--) {
            LoadedPlugin loaded = loadedPlugins.get(index);
            try {
                loaded.plugin().shutdown(context);
            } catch (RuntimeException | Error error) {
                failure = appendFailure(failure, error);
            } finally {
                ContributionOwner owner = ContributionOwner.plugin(loaded.plugin().id());
                context.knowledge().unregisterAll(owner);
                context.generators().unregisterAll(owner);
                removeContributions(registry, loaded.contributions());
            }
        }
        loadedPlugins.clear();
        plugins.clear();
        closeResources(context.resources(), failure);
        failure = releasePluginServices(registry, failure);
        registry.close();
        if (failure != null) {
            throw new IllegalStateException("One or more editor plugins failed to shut down", failure);
        }
    }

    private static void shutdownReverse(List<LoadedPlugin> loadedPlugins,
                                        EditorPluginRegistry registry,
                                        EditorPluginContext context,
                                        Throwable originalFailure) {
        for (int index = loadedPlugins.size() - 1; index >= 0; index--) {
            LoadedPlugin loaded = loadedPlugins.get(index);
            try {
                loaded.plugin().shutdown(context);
            } catch (RuntimeException | Error error) {
                originalFailure.addSuppressed(error);
            } finally {
                ContributionOwner owner = ContributionOwner.plugin(loaded.plugin().id());
                context.knowledge().unregisterAll(owner);
                context.generators().unregisterAll(owner);
                removeContributions(registry, loaded.contributions());
            }
        }
    }

    private static void closeResources(EditorPluginResources resources, Throwable failure) {
        try {
            resources.close();
        } catch (RuntimeException | Error resourceFailure) {
            if (failure != null) failure.addSuppressed(resourceFailure);
            else throw resourceFailure;
        }
    }

    private static Throwable releasePluginServices(
            EditorPluginRegistry registry, Throwable failure) {
        try {
            PluginServices.release(registry);
            return failure;
        } catch (RuntimeException | Error serviceFailure) {
            return appendFailure(failure, serviceFailure);
        }
    }

    private static Throwable appendFailure(Throwable current, Throwable next) {
        if (current == null) return next;
        current.addSuppressed(next);
        return current;
    }

    private static void removeContributions(EditorPluginRegistry registry,
                                             ContributionSet contributions) {
        contributions.tools().forEach(registry::removeTool);
        contributions.commands().forEach(registry::removeCommand);
        contributions.toolContexts().forEach(registry::removeToolContext);
        contributions.assetProviders().forEach(registry::removeAssetProvider);
        contributions.statuses().forEach(registry::removeStatus);
        contributions.menus().forEach(registry::removeMenu);
        contributions.overlays().forEach(registry::removeOverlay);
        contributions.inspectors().forEach(registry::removeInspector);
        contributions.validators().forEach(registry::removeValidator);
        contributions.shortcuts().forEach(registry::removeShortcut);
        contributions.uiSurfaces().forEach(registry::removeUiSurface);
        contributions.panels().forEach(id -> {
            registry.removePanel(id);
            registry.removePanelRegistration(id);
        });
        contributions.workspaces().forEach(registry::removeWorkspace);
    }

    private static String requireId(String id) {
        String value = Objects.requireNonNull(id, "plugin id").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Plugin id cannot be empty");
        }
        return value;
    }

    private static List<EditorPlugin> orderPlugins(
            List<EditorPlugin> plugins,
            Set<String> satisfiedExternalDependencies) {
        Set<String> satisfied = satisfiedExternalDependencies == null
                ? Set.of()
                : Set.copyOf(satisfiedExternalDependencies);
        Map<String, EditorPlugin> byId = new java.util.LinkedHashMap<>();
        Map<String, EditorPluginDescriptor> descriptors = new java.util.LinkedHashMap<>();
        for (EditorPlugin plugin : plugins) {
            String id = requireId(plugin.id());
            if (byId.putIfAbsent(id, plugin) != null) {
                throw new IllegalArgumentException("Duplicate editor plugin: " + id);
            }
            EditorPluginDescriptor descriptor = Objects.requireNonNull(plugin.descriptor(),
                    "plugin descriptor");
            if (!id.equals(descriptor.id())) {
                throw new IllegalArgumentException("Plugin descriptor ID does not match plugin ID: " + id);
            }
            if (descriptor.apiVersion() != EditorPluginApi.CURRENT_VERSION) {
                throw new IllegalArgumentException("Plugin " + id + " requires editor plugin API "
                        + descriptor.apiVersion() + ", but this editor provides API "
                        + EditorPluginApi.CURRENT_VERSION);
            }
            descriptors.put(id, descriptor);
        }

        Map<String, Integer> remaining = new java.util.LinkedHashMap<>();
        Map<String, List<String>> dependents = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, EditorPluginDescriptor> entry : descriptors.entrySet()) {
            List<String> dependencies = entry.getValue().dependencies();
            int unresolvedCount = 0;
            for (String dependency : dependencies) {
                if (satisfied.contains(dependency)) {
                    continue;
                }
                if (!byId.containsKey(dependency)) {
                    throw new IllegalArgumentException("Plugin " + entry.getKey()
                            + " depends on missing plugin or core module: " + dependency);
                }
                unresolvedCount++;
                dependents.computeIfAbsent(dependency, ignored -> new ArrayList<>())
                        .add(entry.getKey());
            }
            remaining.put(entry.getKey(), unresolvedCount);
        }

        Comparator<EditorPlugin> stableOrder = Comparator.comparingInt(EditorPlugin::loadOrder)
                .thenComparing(plugin -> String.valueOf(plugin.id()));
        List<EditorPlugin> ready = byId.values().stream()
                .filter(plugin -> remaining.get(plugin.id()) == 0)
                .sorted(stableOrder)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<EditorPlugin> ordered = new ArrayList<>(plugins.size());
        while (!ready.isEmpty()) {
            EditorPlugin plugin = ready.remove(0);
            ordered.add(plugin);
            for (String dependent : dependents.getOrDefault(plugin.id(), List.of())) {
                int count = remaining.merge(dependent, -1, Integer::sum);
                if (count == 0) {
                    ready.add(byId.get(dependent));
                    ready.sort(stableOrder);
                }
            }
        }
        if (ordered.size() != plugins.size()) {
            throw new IllegalArgumentException("Editor plugin dependency cycle detected");
        }
        return List.copyOf(ordered);
    }

    private record LoadedPlugin(EditorPlugin plugin, ContributionSet contributions) {
    }

    private record ContributionSet(
            List<String> tools,
            List<String> commands,
            List<String> toolContexts,
            List<String> assetProviders,
            List<String> statuses,
            List<String> menus,
            List<String> overlays,
            List<String> inspectors,
            List<String> validators,
            List<String> shortcuts,
            List<String> uiSurfaces,
            List<String> panels,
            List<String> workspaces) {
        private static ContributionSet capture(EditorPluginRegistry registry) {
            return new ContributionSet(
                    registry.toolIds(),
                    registry.commandRegistrations().stream().map(EditorCommandRegistration::id).toList(),
                    registry.toolContextRegistrations().stream().map(EditorToolContextRegistration::id).toList(),
                    registry.assetProviderRegistrations().stream().map(EditorAssetProviderRegistration::id).toList(),
                    registry.statusRegistrations().stream().map(EditorStatusRegistration::id).toList(),
                    registry.menuRegistrations().stream().map(EditorMenuRegistration::id).toList(),
                    registry.overlayRegistrations().stream().map(EditorOverlayRegistration::id).toList(),
                    registry.inspectorRegistrations().stream().map(EditorInspectorRegistration::id).toList(),
                    registry.validatorRegistrations().stream().map(EditorValidatorRegistration::id).toList(),
                    registry.shortcutRegistrations().stream().map(EditorShortcutRegistration::id).toList(),
                    registry.uiSurfaceContributions().stream().map(com.rspsi.editor.plugin.ui.UiSurfaceContribution::id).toList(),
                    java.util.stream.Stream.concat(
                            registry.panels().stream().map(com.rspsi.editor.ui.PanelDescriptor::id),
                            registry.panelRegistrations().stream().map(EditorPanelRegistration::id)
                    ).toList(),
                    registry.workspaces().stream().map(com.rspsi.editor.ui.WorkspaceDefinition::id).toList());
        }

        private ContributionSet difference(ContributionSet before) {
            return new ContributionSet(
                    difference(tools, before.tools()),
                    difference(commands, before.commands()),
                    difference(toolContexts, before.toolContexts()),
                    difference(assetProviders, before.assetProviders()),
                    difference(statuses, before.statuses()),
                    difference(menus, before.menus()),
                    difference(overlays, before.overlays()),
                    difference(inspectors, before.inspectors()),
                    difference(validators, before.validators()),
                    difference(shortcuts, before.shortcuts()),
                    difference(uiSurfaces, before.uiSurfaces()),
                    difference(panels, before.panels()),
                    difference(workspaces, before.workspaces()));
        }

        private static List<String> difference(List<String> after, List<String> before) {
            return after.stream().filter(id -> !before.contains(id)).toList();
        }
    }
}
