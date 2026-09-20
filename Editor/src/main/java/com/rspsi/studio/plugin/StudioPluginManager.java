package com.rspsi.studio.plugin;

import com.rspsi.studio.ui.StudioPanelContext;
import imgui.ImDrawList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Central registry and lifecycle manager for {@link StudioPlugin}s.
 * Supports auto-discovery via {@link ServiceLoader} and explicit registration.
 */
public final class StudioPluginManager {

    private static final Logger log = LoggerFactory.getLogger(StudioPluginManager.class);

    private final Map<String, StudioPlugin> plugins = new LinkedHashMap<>();
    private final Map<String, Boolean> enabledStates = new LinkedHashMap<>();

    public StudioPluginManager() {
        discoverPlugins();
    }

    /**
     * Auto-discovers and registers plugins via java.util.ServiceLoader.
     */
    public void discoverPlugins() {
        // Core built-in Studio modal tool plugins
        register(new com.rspsi.studio.plugin.builtin.tool.TileSelectionToolPlugin());
        register(new com.rspsi.studio.plugin.builtin.tool.TilePainterToolPlugin());
        register(new com.rspsi.studio.plugin.builtin.tool.HeightSculptorToolPlugin());
        register(new com.rspsi.studio.plugin.builtin.tool.PathToolPlugin());
        register(new com.rspsi.studio.plugin.builtin.tool.ObjectPlacementToolPlugin());

        try {
            ServiceLoader<StudioPlugin> loader = ServiceLoader.load(StudioPlugin.class);
            for (StudioPlugin plugin : loader) {
                register(plugin);
            }
        } catch (Exception ex) {
            log.warn("Failed scanning ServiceLoader for StudioPlugins: {}", ex.getMessage());
        }
    }

    public synchronized void register(StudioPlugin plugin) {
        if (plugin == null || plugin.id() == null) return;
        plugins.put(plugin.id(), plugin);
        enabledStates.putIfAbsent(plugin.id(), true);
        try {
            plugin.onEnable();
            log.info("Registered StudioPlugin: {} [{}]", plugin.name(), plugin.id());
        } catch (Exception ex) {
            log.error("Error enabling plugin {}: {}", plugin.id(), ex.getMessage(), ex);
        }
    }

    public synchronized void unregister(String pluginId) {
        StudioPlugin removed = plugins.remove(pluginId);
        if (removed != null) {
            try {
                removed.onDisable();
            } catch (Exception ex) {
                log.error("Error disabling plugin {}: {}", pluginId, ex.getMessage(), ex);
            }
        }
    }

    public synchronized boolean isEnabled(String pluginId) {
        return enabledStates.getOrDefault(pluginId, false);
    }

    public synchronized void setEnabled(String pluginId, boolean enabled) {
        StudioPlugin plugin = plugins.get(pluginId);
        if (plugin == null) return;
        boolean current = isEnabled(pluginId);
        if (current == enabled) return;

        enabledStates.put(pluginId, enabled);
        try {
            if (enabled) {
                plugin.onEnable();
            } else {
                plugin.onDisable();
            }
        } catch (Exception ex) {
            log.error("Error toggling plugin {}: {}", pluginId, ex.getMessage(), ex);
        }
    }

    public synchronized Optional<StudioPlugin> plugin(String pluginId) {
        return Optional.ofNullable(plugins.get(pluginId));
    }

    public synchronized List<StudioPlugin> allPlugins() {
        return List.copyOf(plugins.values());
    }

    public synchronized List<StudioPlugin> enabledPlugins() {
        List<StudioPlugin> active = new ArrayList<>();
        for (var entry : plugins.entrySet()) {
            if (enabledStates.getOrDefault(entry.getKey(), false)) {
                active.add(entry.getValue());
            }
        }
        return Collections.unmodifiableList(active);
    }

    /**
     * Returns all registered active {@link StudioToolPlugin}s sorted by rail priority.
     */
    public synchronized List<StudioToolPlugin> toolPlugins() {
        List<StudioToolPlugin> tools = new ArrayList<>();
        for (StudioPlugin plugin : plugins.values()) {
            if (plugin instanceof StudioToolPlugin toolPlugin && isEnabled(plugin.id())) {
                tools.add(toolPlugin);
            }
        }
        tools.sort(java.util.Comparator.comparingInt(StudioToolPlugin::railPriority));
        return Collections.unmodifiableList(tools);
    }

    /**
     * Resolves a tool plugin by its engine tool ID or plugin ID.
     */
    public synchronized Optional<StudioToolPlugin> toolPlugin(String toolId) {
        if (toolId == null) return Optional.empty();
        for (StudioPlugin plugin : plugins.values()) {
            if (plugin instanceof StudioToolPlugin toolPlugin) {
                if (toolId.equals(toolPlugin.toolId()) || toolId.equals(toolPlugin.id())) {
                    return Optional.of(toolPlugin);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Dispatches viewport overlay rendering to all active plugins.
     */
    public void renderOverlays(ImDrawList drawList, StudioPanelContext context) {
        for (StudioPlugin p : enabledPlugins()) {
            try {
                p.renderOverlay(drawList, context);
            } catch (Exception ex) {
                log.error("Plugin {} overlay error: {}", p.id(), ex.getMessage());
            }
        }
    }

    /**
     * Dispatches floating HUD rendering to all active plugins.
     */
    public void renderHUDs(StudioPanelContext context) {
        for (StudioPlugin p : enabledPlugins()) {
            try {
                p.renderHUD(context);
            } catch (Exception ex) {
                log.error("Plugin {} HUD error: {}", p.id(), ex.getMessage());
            }
        }
    }
}
