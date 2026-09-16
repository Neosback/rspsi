package com.rspsi.editor.ui;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** UI-neutral placement constraints for one editor panel. */
public record PanelDescriptor(
        String id,
        DockRegion preferredRegion,
        Set<DockRegion> allowedRegions,
        int minimumWidth,
        int minimumHeight
) {
    public PanelDescriptor {
        id = Objects.requireNonNull(id, "id").trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Panel id cannot be empty");
        }
        preferredRegion = Objects.requireNonNull(preferredRegion, "preferredRegion");
        allowedRegions = allowedRegions == null || allowedRegions.isEmpty()
                ? EnumSet.of(preferredRegion)
                : EnumSet.copyOf(allowedRegions);
        if (!allowedRegions.contains(preferredRegion)) {
            throw new IllegalArgumentException("Preferred region must be allowed");
        }
        if (minimumWidth < 0 || minimumHeight < 0) {
            throw new IllegalArgumentException("Panel minimum dimensions cannot be negative");
        }
        allowedRegions = Set.copyOf(allowedRegions);
    }

    public boolean allows(DockRegion region) {
        return allowedRegions.contains(Objects.requireNonNull(region, "region"));
    }
}
