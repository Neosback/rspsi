package com.rspsi.editor.debug;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Immutable selection of debug views for one viewport/workspace. */
public record DebugOverlaySettings(Set<DebugOverlayMode> modes) {
    public DebugOverlaySettings {
        Objects.requireNonNull(modes, "modes");
        modes = Set.copyOf(EnumSet.copyOf(modes.isEmpty()
                ? EnumSet.noneOf(DebugOverlayMode.class)
                : EnumSet.copyOf(modes)));
    }

    public static DebugOverlaySettings none() {
        return new DebugOverlaySettings(Set.of());
    }

    public static DebugOverlaySettings of(DebugOverlayMode... modes) {
        Objects.requireNonNull(modes, "modes");
        EnumSet<DebugOverlayMode> enabled = EnumSet.noneOf(DebugOverlayMode.class);
        for (DebugOverlayMode mode : modes) {
            enabled.add(Objects.requireNonNull(mode, "mode"));
        }
        return new DebugOverlaySettings(enabled);
    }

    public boolean enabled(DebugOverlayMode mode) {
        return modes.contains(Objects.requireNonNull(mode, "mode"));
    }
}
