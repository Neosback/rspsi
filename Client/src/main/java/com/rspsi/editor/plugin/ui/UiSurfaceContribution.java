package com.rspsi.editor.plugin.ui;

import com.rspsi.editor.ui.DockRegion;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Neutral declaration of one managed UI surface contributed by an EditorPlugin. */
public record UiSurfaceContribution(
        String id,
        String title,
        String icon,
        SurfaceType type,
        DockRegion preferredRegion,
        Set<DockRegion> allowedRegions,
        SizeClass sizeClass,
        boolean movable,
        boolean closable,
        String associatedToolId,
        int priority) {

    public enum SurfaceType { TOOL_PANEL, UTILITY_PANEL, VIEWPORT_HUD, INSPECTOR_SECTION, BOTTOM_CONTEXT }
    public enum SizeClass { COMPACT, STANDARD, EXPANDED }

    public UiSurfaceContribution {
        id = requireText(id, "id");
        title = requireText(title, "title");
        icon = icon == null ? "" : icon;
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(preferredRegion, "preferredRegion");
        allowedRegions = allowedRegions == null || allowedRegions.isEmpty()
                ? EnumSet.of(preferredRegion) : EnumSet.copyOf(allowedRegions);
        if (!allowedRegions.contains(preferredRegion)) {
            throw new IllegalArgumentException("Preferred region must be allowed");
        }
        allowedRegions = Set.copyOf(allowedRegions);
        sizeClass = sizeClass == null ? SizeClass.STANDARD : sizeClass;
        associatedToolId = associatedToolId == null ? "" : associatedToolId.trim();
    }

    private static String requireText(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return result;
    }
}
