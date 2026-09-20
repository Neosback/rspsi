package com.rspsi.editor.plugin;

import com.rspsi.editor.ui.DockRegion;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Neutral metadata for one panel contribution registered by a plugin.
 *
 * <p>Frontends use this to place the panel in a default slot (such as the
 * right sidebar vertical icon rail or the bottom drawer) while allowing user
 * overrides to reassign or reorder it.</p>
 */
public record EditorPanelRegistration(
        String id,
        String title,
        String icon,
        DockRegion preferredRegion,
        Set<DockRegion> allowedRegions,
        int order,
        boolean defaultVisible) {

    public EditorPanelRegistration {
        id = requireText(id, "panel id");
        title = requireText(title, "panel title");
        preferredRegion = Objects.requireNonNull(preferredRegion, "preferredRegion");
        allowedRegions = allowedRegions == null || allowedRegions.isEmpty()
                ? EnumSet.of(preferredRegion)
                : EnumSet.copyOf(allowedRegions);
        if (!allowedRegions.contains(preferredRegion)) {
            throw new IllegalArgumentException("Preferred region must be in allowed regions");
        }
        allowedRegions = Set.copyOf(allowedRegions);
    }

    public EditorPanelRegistration(String id, String title, String icon,
                                   DockRegion preferredRegion, int order) {
        this(id, title, icon, preferredRegion, EnumSet.of(DockRegion.RIGHT, DockRegion.BOTTOM), order, true);
    }

    public boolean allows(DockRegion region) {
        return allowedRegions.contains(Objects.requireNonNull(region, "region"));
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be empty");
        }
        return normalized;
    }
}
