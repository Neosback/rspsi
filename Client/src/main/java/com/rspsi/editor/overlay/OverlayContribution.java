package com.rspsi.editor.overlay;

import java.util.Objects;
import java.util.function.Supplier;

/** Metadata plus content supplier for one declarative viewport overlay. */
public record OverlayContribution(
        String id,
        String label,
        OverlayPosition position,
        OverlayLayer layer,
        int priority,
        boolean movable,
        boolean enabledByDefault,
        float preferredWidth,
        float defaultOpacity,
        Supplier<? extends OverlayComponent> content) {

    /** Compatibility constructor for pre-opacity contributions. */
    public OverlayContribution(
            String id,
            String label,
            OverlayPosition position,
            OverlayLayer layer,
            int priority,
            boolean movable,
            boolean enabledByDefault,
            float preferredWidth,
            Supplier<? extends OverlayComponent> content) {
        this(id, label, position, layer, priority, movable, enabledByDefault,
                preferredWidth, 0.86f, content);
    }

    public OverlayContribution {
        id = text(id, "overlay id");
        label = text(label, "overlay label");
        position = Objects.requireNonNull(position, "position");
        layer = Objects.requireNonNull(layer, "layer");
        if (!Float.isFinite(preferredWidth) || preferredWidth <= 0.0f) {
            throw new IllegalArgumentException("preferredWidth must be positive and finite");
        }
        if (!Float.isFinite(defaultOpacity)) {
            throw new IllegalArgumentException("defaultOpacity must be finite");
        }
        defaultOpacity = Math.max(0.15f, Math.min(1.0f, defaultOpacity));
        Objects.requireNonNull(content, "content");
    }

    public OverlayComponent snapshot() {
        return Objects.requireNonNull(content.get(), "overlay content");
    }

    private static String text(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return normalized;
    }
}
