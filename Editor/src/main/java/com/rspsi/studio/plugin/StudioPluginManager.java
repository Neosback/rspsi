package com.rspsi.studio.plugin;

import com.rspsi.studio.ui.StudioPanel;
import com.rspsi.studio.ui.StudioPanelContext;
import imgui.ImDrawList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Central registry and lifecycle manager for {@link StudioPlugin}s.
 * Supports auto-discovery via {@link ServiceLoader} and explicit registration.
 */
public final class StudioPluginManager {

    private static final Logger log = LoggerFactory.getLogger(StudioPluginManager.class);

    private final Map<String, StudioPlugin> plugins = new LinkedHashMap<>();
    private final Map<String, Boolean> enabledStates = new LinkedHashMap<>();
    private final Map<String, Set<StudioToolPlugin.ToolSurface>> surfaceOverrides = new LinkedHashMap<>();
    private Consumer<StudioPanel> ownedPanelSink;

    public StudioPluginManager() {
        discoverPlugins();
    }

    /**
     * Called once, right after construction, by whoever owns the right-sidebar's
     * {@code StudioPanelManager} so any tool plugin's {@link StudioToolPlugin#ownedPanel()} gets
     * registered there too - mirrors RuneLite's {@code NavigationButton.panel}: a tool's panel
     * is a permanent sidebar entry, not something conjured only while the tool is active.
     */
    public void setOwnedPanelSink(Consumer<StudioPanel> sink) {
        this.ownedPanelSink = sink;
        if (sink != null) {
            for (StudioPlugin plugin : plugins.values()) {
                if (plugin instanceof StudioToolPlugin tool) {
                    tool.ownedPanel().ifPresent(sink);
                }
            }
        }
    }

    /**
     * The surfaces this tool's button actually appears on right now: a user/Plugin-Manager
     * override if one has been set, otherwise the tool's own declared default.
     */
    public synchronized Set<StudioToolPlugin.ToolSurface> effectiveSurfaces(StudioToolPlugin tool) {
        return surfaceOverrides.getOrDefault(tool.id(), tool.surfaces());
    }

    public synchronized void setSurfaceOverride(String toolPluginId, Set<StudioToolPlugin.ToolSurface> surfaces) {
        surfaceOverrides.put(toolPluginId, EnumSet.copyOf(surfaces));
    }

    public synchronized void resetSurfaceOverride(String toolPluginId) {
        surfaceOverrides.remove(toolPluginId);
    }

    public synchronized boolean hasSurfaceOverride(String toolPluginId) {
        return surfaceOverrides.containsKey(toolPluginId);
    }

    /**
     * Registers internal Studio projections only. Third-party discovery lives
     * exclusively on the neutral EditorPlugin boundary in Client.
     */
    public void discoverPlugins() {
        // Core built-in Studio modal tool projections.
        register(new com.rspsi.studio.plugin.builtin.tool.SingleSelectToolPlugin());
        register(new com.rspsi.studio.plugin.builtin.tool.MultiSelectToolPlugin());
        register(new com.rspsi.studio.plugin.builtin.tool.TilePainterToolPlugin());
        register(new com.rspsi.studio.plugin.builtin.tool.HeightSculptorToolPlugin());
        register(new com.rspsi.studio.plugin.builtin.tool.PathToolPlugin());
        register(new com.rspsi.studio.plugin.builtin.tool.ObjectPlacementToolPlugin());
        register(new com.rspsi.studio.ui.hud.BrushSettingsHud());
        // Object selection buttons are nested inside SelectionOverlayPlugin - they exist
        // only to feed the selection that plugin then highlights, so they're registered
        // and maintained together instead of as separate top-level plugin files.
        register(new com.rspsi.studio.ui.SelectionOverlayPlugin());
        register(new com.rspsi.studio.ui.SelectionOverlayPlugin.SingleObjectSelectToolPlugin());
        register(new com.rspsi.studio.ui.SelectionOverlayPlugin.MultiObjectSelectToolPlugin());
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
        if (plugin instanceof StudioToolPlugin tool && ownedPanelSink != null) {
            tool.ownedPanel().ifPresent(ownedPanelSink);
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
                if (toolPlugin.toolIds().contains(toolId) || toolId.equals(toolPlugin.id())) {
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
        List<StudioPlugin> ordered = new ArrayList<>(enabledPlugins());
        if (context != null && context.huds() != null) {
            ordered.sort(java.util.Comparator.comparingInt(plugin -> context.huds().priority(plugin.id())));
        }
        for (StudioPlugin p : ordered) {
            try {
                p.renderHUD(context);
            } catch (Exception ex) {
                log.error("Plugin {} HUD error: {}", p.id(), ex.getMessage());
            }
        }
    }

    /**
     * Dispatches floating interactive window rendering to all active plugins.
     */
    public void renderFloating(StudioPanelContext context) {
        for (StudioPlugin p : enabledPlugins()) {
            try {
                p.renderFloating(context);
            } catch (Exception ex) {
                log.error("Plugin {} floating error: {}", p.id(), ex.getMessage(), ex);
            }
        }
    }
}
