package com.rspsi.editor.plugin;

import com.rspsi.editor.plugin.ui.ToolUiContent;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Frontend-neutral presentation and capability contract for one map-editing tool.
 *
 * <p>This descriptor is shared by built-in and installed extensions. It describes
 * what the tool does and which host-owned Studio surfaces should project it.
 * It deliberately contains no Dear ImGui, GLFW, OpenGL, or native Studio types.</p>
 */
public record ToolUiDescriptor(
        Set<ToolSurface> surfaces,
        BrushUiMode brushUiMode,
        Set<ToolCapability> capabilities,
        boolean hasContextDrawerContent,
        ToolUiContent content) {

    /** Host-owned activation surfaces on which a tool may appear. */
    public enum ToolSurface {
        BOTTOM_BAR,
        FLOATING_TOOLBAR
    }

    /** Declares where brush controls live for the active tool. */
    public enum BrushUiMode {
        /** The tool does not use shared brush controls. */
        NONE,
        /** Studio shows the shared Brush Rail / Brush Settings while the tool is active. */
        SHARED_SETTINGS,
        /** The tool owns specialized brush controls in its own context UI. */
        TOOL_OWNED
    }

    /**
     * Semantic capabilities advertised by a map tool.
     *
     * <p>Capabilities describe behavior. They are not a first-party versus
     * third-party privilege tier.</p>
     */
    public enum ToolCapability {
        TILE_TARGET,
        OBJECT_TARGET,
        VERTEX_TARGET,
        AREA_TARGET,
        PATH_TARGET,
        FRAGMENT_TARGET,

        BRUSH_FOOTPRINT,
        BRUSH_FALLOFF,
        BRUSH_STRENGTH,
        STAMP,
        SCATTER,

        WORLD_READ,
        WORLD_EDIT,
        PREVIEW,

        CONTEXT_DRAWER,
        QUICK_PALETTE,
        SELECTION_INSPECTOR,
        HUD,
        SCENE_OVERLAY,

        POINTER_CAPTURE,
        SCROLL_INPUT,
        KEY_INPUT,
        CANCELABLE_INTERACTION
    }

    public ToolUiDescriptor {
        surfaces = surfaces == null || surfaces.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(surfaces));
        brushUiMode = Objects.requireNonNullElse(brushUiMode, BrushUiMode.NONE);
        content = content == null ? ToolUiContent.empty() : content;

        EnumSet<ToolCapability> normalizedCapabilities =
                capabilities == null || capabilities.isEmpty()
                        ? EnumSet.noneOf(ToolCapability.class)
                        : EnumSet.copyOf(capabilities);

        if (content.hasContextDrawer()) {
            hasContextDrawerContent = true;
            normalizedCapabilities.add(ToolCapability.CONTEXT_DRAWER);
        }
        if (content.hasQuickPalette()) {
            normalizedCapabilities.add(ToolCapability.QUICK_PALETTE);
        }
        if (content.hasInspector()) {
            normalizedCapabilities.add(ToolCapability.SELECTION_INSPECTOR);
        }
        if (hasContextDrawerContent) {
            normalizedCapabilities.add(ToolCapability.CONTEXT_DRAWER);
        }

        capabilities = normalizedCapabilities.isEmpty()
                ? Set.of()
                : Set.copyOf(normalizedCapabilities);

        if (brushUiMode == BrushUiMode.SHARED_SETTINGS
                && !capabilities.contains(ToolCapability.BRUSH_FOOTPRINT)) {
            throw new IllegalArgumentException(
                    "Shared brush settings require BRUSH_FOOTPRINT capability");
        }
    }

    /** Compatibility constructor retaining the original metadata-only descriptor shape. */
    public ToolUiDescriptor(
            Set<ToolSurface> surfaces,
            BrushUiMode brushUiMode,
            Set<ToolCapability> capabilities,
            boolean hasContextDrawerContent) {
        this(surfaces, brushUiMode, capabilities, hasContextDrawerContent, ToolUiContent.empty());
    }

    /**
     * Compatibility defaults for legacy engine-tool registrations.
     *
     * <p>Legacy tools remain behavior-only until explicitly promoted through
     * PluginApi.mapTool(...), preventing internal sub-tools from unexpectedly
     * appearing in Studio chrome during migration.</p>
     */
    public static ToolUiDescriptor defaults() {
        return new ToolUiDescriptor(
                Set.of(), BrushUiMode.NONE, Set.of(), false, ToolUiContent.empty());
    }

    public boolean appearsOn(ToolSurface surface) {
        return surfaces.contains(Objects.requireNonNull(surface, "surface"));
    }

    public boolean has(ToolCapability capability) {
        return capabilities.contains(Objects.requireNonNull(capability, "capability"));
    }

    public boolean usesSharedBrushSettings() {
        return brushUiMode == BrushUiMode.SHARED_SETTINGS;
    }
}
