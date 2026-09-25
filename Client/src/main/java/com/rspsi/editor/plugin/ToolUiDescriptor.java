package com.rspsi.editor.plugin;

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
        boolean hasContextDrawerContent) {

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
                ? EnumSet.of(ToolSurface.BOTTOM_BAR, ToolSurface.FLOATING_TOOLBAR)
                : EnumSet.copyOf(surfaces);
        surfaces = Set.copyOf(surfaces);
        brushUiMode = Objects.requireNonNullElse(brushUiMode, BrushUiMode.NONE);
        capabilities = capabilities == null || capabilities.isEmpty()
                ? Set.of()
                : Set.copyOf(capabilities);

        if (brushUiMode == BrushUiMode.SHARED_SETTINGS
                && !capabilities.contains(ToolCapability.BRUSH_FOOTPRINT)) {
            throw new IllegalArgumentException(
                    "Shared brush settings require BRUSH_FOOTPRINT capability");
        }
        if (hasContextDrawerContent
                && !capabilities.contains(ToolCapability.CONTEXT_DRAWER)) {
            throw new IllegalArgumentException(
                    "Context drawer content requires CONTEXT_DRAWER capability");
        }
    }

    /** Standard modal map-tool defaults used by compatibility registrations. */
    public static ToolUiDescriptor defaults() {
        return new ToolUiDescriptor(
                EnumSet.of(ToolSurface.BOTTOM_BAR, ToolSurface.FLOATING_TOOLBAR),
                BrushUiMode.NONE,
                Set.of(),
                false);
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
