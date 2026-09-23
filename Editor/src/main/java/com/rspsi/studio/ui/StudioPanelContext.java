package com.rspsi.studio.ui;

import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.integration.ServerIntegrationService;
import com.rspsi.editor.integration.npc.NpcSpawnService;
import com.rspsi.editor.integration.reference.ReferenceService;
import com.rspsi.editor.plugin.EditorPluginLifecycleManager;
import com.rspsi.editor.settings.SettingsStore;
import com.rspsi.editor.simulation.SimulationEngine;
import com.rspsi.editor.symbols.SymbolService;
import com.rspsi.editor.tool.EditorToolController;
import com.rspsi.studio.NativeSceneViewport;
import com.rspsi.studio.brush.StudioBrushManager;
import com.rspsi.studio.ui.hud.ViewportHudManager;

import java.util.function.Consumer;

/**
 * Execution context provided to all Studio panels when rendering their native UI.
 */
public record StudioPanelContext(
        LoadedOsrsCacheSession cache,
        SettingsStore settings,
        EditorSession session,
        EditorPluginLifecycleManager pluginLifecycle,
        NativeSceneViewport viewport,
        SimulationEngine simulation,
        SymbolService symbols,
        ReferenceService references,
        NpcSpawnService spawns,
        ServerIntegrationService integrations,
        Consumer<String> activateTool,
        String activeToolId,
        EditorToolController toolController,
        com.rspsi.studio.plugin.StudioPluginManager studioPlugins,
        StudioBrushManager brushes,
        ViewportHudManager huds,
        Consumer<LoadedOsrsCacheSession> persistDefinitionPublication) {

    public StudioPanelContext {
        persistDefinitionPublication = persistDefinitionPublication == null
                ? ignored -> { }
                : persistDefinitionPublication;
    }

    /** Compatibility constructor retaining the pre-provenance canonical shape. */
    public StudioPanelContext(
            LoadedOsrsCacheSession cache,
            SettingsStore settings,
            EditorSession session,
            EditorPluginLifecycleManager pluginLifecycle,
            NativeSceneViewport viewport,
            SimulationEngine simulation,
            SymbolService symbols,
            ReferenceService references,
            NpcSpawnService spawns,
            ServerIntegrationService integrations,
            Consumer<String> activateTool,
            String activeToolId,
            EditorToolController toolController,
            com.rspsi.studio.plugin.StudioPluginManager studioPlugins,
            StudioBrushManager brushes,
            ViewportHudManager huds) {
        this(cache, settings, session, pluginLifecycle, viewport, simulation, symbols,
                references, spawns, integrations, activateTool, activeToolId,
                toolController, studioPlugins, brushes, huds, ignored -> { });
    }

    /** Compatibility constructor for tests and transitional callers. */
    public StudioPanelContext(
            LoadedOsrsCacheSession cache,
            SettingsStore settings,
            EditorSession session,
            EditorPluginLifecycleManager pluginLifecycle,
            NativeSceneViewport viewport,
            SimulationEngine simulation,
            SymbolService symbols,
            ReferenceService references,
            NpcSpawnService spawns,
            ServerIntegrationService integrations,
            Consumer<String> activateTool,
            String activeToolId,
            EditorToolController toolController,
            com.rspsi.studio.plugin.StudioPluginManager studioPlugins,
            StudioBrushManager brushes) {
        this(cache, settings, session, pluginLifecycle, viewport, simulation, symbols,
                references, spawns, integrations, activateTool, activeToolId,
                toolController, studioPlugins, brushes, new ViewportHudManager(),
                ignored -> { });
    }
}
