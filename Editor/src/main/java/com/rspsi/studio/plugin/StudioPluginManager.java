package com.rspsi.studio.plugin;

import com.rspsi.editor.plugin.EditorPluginRegistry;
import com.rspsi.editor.plugin.EditorToolRegistration;
import com.rspsi.editor.plugin.ToolUiDescriptor;
import com.rspsi.studio.theme.StudioIcons;
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
    private EditorPluginRegistry editorPluginRegistry;

    public StudioPluginManager() {
        discoverPlugins();
    }

    /**
     * Binds the neutral editor registry whose map tools should be projected into
     * native Studio chrome. Built-in native projections temporarily win on ID
     * collisions while they migrate onto the shared SDK.
     */
    public synchronized void bindEditorPluginRegistry(EditorPluginRegistry registry) {
        this.editorPluginRegistry = registry;
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
        Set<StudioToolPlugin.ToolSurface> value = surfaces == null || surfaces.isEmpty()
                ? EnumSet.noneOf(StudioToolPlugin.ToolSurface.class)
                : EnumSet.copyOf(surfaces);
        surfaceOverrides.put(toolPluginId, value);
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
     * Unified tool view consumed by native Studio chrome.
     *
     * <p>Native built-ins and neutral extension tools are peers here. During
     * migration, a native projection wins when it represents the same engine
     * tool ID so buttons are not duplicated.</p>
     */
    public synchronized List<StudioToolView> toolViews() {
        List<StudioToolView> views = new ArrayList<>();
        java.util.Set<String> representedToolIds = new java.util.LinkedHashSet<>();

        for (StudioToolPlugin tool : toolPlugins()) {
            views.add(StudioToolView.fromNative(tool, effectiveSurfaces(tool)));
            representedToolIds.addAll(tool.toolIds());
            representedToolIds.add(tool.id());
        }

        if (editorPluginRegistry != null) {
            for (EditorToolRegistration registration : editorPluginRegistry.toolRegistrations()) {
                if (representedToolIds.contains(registration.id())) continue;
                views.add(StudioToolView.fromNeutral(registration));
            }
        }

        views.sort(java.util.Comparator.comparingInt(StudioToolView::railPriority)
                .thenComparing(StudioToolView::id));
        return Collections.unmodifiableList(views);
    }

    public synchronized Optional<StudioToolView> toolView(String toolId) {
        if (toolId == null) return Optional.empty();
        return toolViews().stream()
                .filter(tool -> tool.toolIds().contains(toolId) || tool.id().equals(toolId))
                .findFirst();
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

    public synchronized StudioToolPlugin.BrushUiMode brushUiMode(String toolId) {
        return toolView(toolId)
                .map(StudioToolView::brushUiMode)
                .orElse(StudioToolPlugin.BrushUiMode.NONE);
    }

    public synchronized boolean usesSharedBrushSettings(String toolId) {
        return brushUiMode(toolId) == StudioToolPlugin.BrushUiMode.SHARED_SETTINGS;
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

    /**
     * Internal native projection of the shared tool descriptor.
     * It contains presentation metadata only; engine behavior remains the
     * neutral EditorTool registered in EditorPluginRegistry.
     */
    public record StudioToolView(
            String id,
            String toolId,
            Set<String> toolIds,
            String name,
            String icon,
            String shortcut,
            int railPriority,
            Set<StudioToolPlugin.ToolSurface> surfaces,
            StudioToolPlugin.BrushUiMode brushUiMode,
            boolean hasContextDrawerContent,
            StudioToolPlugin nativePlugin) {

        private static StudioToolView fromNative(
                StudioToolPlugin tool,
                Set<StudioToolPlugin.ToolSurface> surfaces) {
            return new StudioToolView(
                    tool.id(),
                    tool.toolId(),
                    Set.copyOf(tool.toolIds()),
                    tool.name(),
                    tool.icon(),
                    tool.shortcut(),
                    tool.railPriority(),
                    Set.copyOf(surfaces),
                    tool.brushUiMode(),
                    tool.hasContextDrawerContent(),
                    tool);
        }

        private static StudioToolView fromNeutral(EditorToolRegistration registration) {
            ToolUiDescriptor ui = registration.ui();
            java.util.EnumSet<StudioToolPlugin.ToolSurface> surfaces =
                    java.util.EnumSet.noneOf(StudioToolPlugin.ToolSurface.class);
            if (ui.appearsOn(ToolUiDescriptor.ToolSurface.BOTTOM_BAR)) {
                surfaces.add(StudioToolPlugin.ToolSurface.BOTTOM_BAR);
            }
            if (ui.appearsOn(ToolUiDescriptor.ToolSurface.FLOATING_TOOLBAR)) {
                surfaces.add(StudioToolPlugin.ToolSurface.FLOATING_TOOLBAR);
            }

            StudioToolPlugin.BrushUiMode brushUi = switch (ui.brushUiMode()) {
                case NONE -> StudioToolPlugin.BrushUiMode.NONE;
                case SHARED_SETTINGS -> StudioToolPlugin.BrushUiMode.SHARED_SETTINGS;
                case TOOL_OWNED -> StudioToolPlugin.BrushUiMode.TOOL_OWNED;
            };

            String icon = registration.icon() == null || registration.icon().isBlank()
                    ? StudioIcons.OBJECT
                    : StudioIcons.byName(registration.icon(), registration.icon());
            String shortcut = registration.shortcut() == null ? "" : registration.shortcut();

            return new StudioToolView(
                    registration.id(),
                    registration.id(),
                    Set.of(registration.id()),
                    registration.label(),
                    icon,
                    shortcut,
                    registration.order(),
                    Set.copyOf(surfaces),
                    brushUi,
                    ui.hasContextDrawerContent(),
                    null);
        }

        public boolean isNativeProjection() {
            return nativePlugin != null;
        }
    }
}
