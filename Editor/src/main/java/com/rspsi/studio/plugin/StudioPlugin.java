package com.rspsi.studio.plugin;

import com.rspsi.studio.ui.StudioPanelContext;
import imgui.ImDrawList;

/**
 * Internal transitional projection API for built-in Studio presentation code.
 *
 * <p>Public/third-party extensions should implement
 * {@code com.rspsi.editor.plugin.EditorPlugin} and register neutral
 * contributions through {@code EditorPluginRegistry}. This interface may call
 * Dear ImGui directly and is therefore intentionally not the public plugin
 * boundary.</p>
 */
public interface StudioPlugin {

    /**
     * Unique identifier for this plugin (e.g. "com.example.cinematics").
     */
    String id();

    /**
     * Human-readable display name for this plugin.
     */
    String name();

    /**
     * Optional description of the plugin's functionality.
     */
    default String description() {
        return "";
    }

    /**
     * Semantic version string (e.g. "1.0.0").
     */
    default String version() {
        return "1.0.0";
    }

    /**
     * Author or contributor name.
     */
    default String author() {
        return "OpenRune";
    }

    /**
     * OpenGL texture ID for the plugin's custom PNG icon (or 0 if vector/font fallback).
     */
    default int iconTextureId() {
        return 0;
    }

    /**
     * Google Fonts Material Icon glyph string or name (e.g. StudioIcons.BRUSH or "brush").
     */
    default String icon() {
        return com.rspsi.studio.theme.StudioIcons.OBJECT;
    }

    /**
     * Invoked when the plugin is activated or loaded.
     */
    default void onEnable() {}

    /**
     * Invoked when the plugin is disabled or unloaded.
     */
    default void onDisable() {}

    /**
     * Renders directly onto the 3D viewport canvas via ImDrawList
     * (e.g. tile markers, selection marquees, collision heatmaps).
     */
    default void renderOverlay(ImDrawList drawList, StudioPanelContext context) {}

    /**
     * Renders floating transparent HUD windows over the viewport
     * (e.g. compass, telemetry pill badge, coordinates HUD).
     */
    default void renderHUD(StudioPanelContext context) {}

    /**
     * Renders inside the right sidebar inspector tabs.
     */
    default void renderSidePanel(StudioPanelContext context) {}

    /**
     * Renders context-sensitive parameters inside the bottom drawer when this tool is active.
     */
    default void renderToolShelf(StudioPanelContext context) {}

    /**
     * Renders interactive configuration UI (checkboxes, sliders, inputs) for this plugin
     * in the Plugin Settings Inspector panel.
     */
    default void renderSettings(StudioPanelContext context) {}

    /**
     * Whether this plugin provides configurable settings in the settings inspector.
     */
    default boolean isConfigurable() {
        return true;
    }
}
