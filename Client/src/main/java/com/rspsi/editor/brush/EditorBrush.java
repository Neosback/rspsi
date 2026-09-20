package com.rspsi.editor.brush;

import java.util.Set;

/**
 * Frontend-neutral spatial brush contract.
 *
 * <p>Brushes describe selection weight only. Tools decide what operation to
 * apply to the sampled footprint, which keeps UI and editor mutations out of
 * the brush SPI.</p>
 */
public interface EditorBrush {
    String id();

    String name();

    default String description() {
        return "";
    }

    Set<BrushCapability> capabilities();

    /**
     * Returns a normalized sample weight for a tile offset from the brush
     * center. Zero means outside the footprint.
     */
    double weight(int dx, int dy, int radius);

    default boolean contains(int dx, int dy, int radius) {
        return weight(dx, dy, radius) > 0.0;
    }
}
