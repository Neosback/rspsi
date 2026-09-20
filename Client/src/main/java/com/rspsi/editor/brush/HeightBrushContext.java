package com.rspsi.editor.brush;

/**
 * Neutral parameters supplied to height-capable brushes.
 *
 * @param dx tile/vertex offset from the brush anchor
 * @param dy tile/vertex offset from the brush anchor
 * @param radius active brush radius
 * @param directionRadians directional heading for ramps/slopes
 * @param strength height strength/delta
 * @param terraceStep quantization step for terracing
 */
public record HeightBrushContext(
        int dx,
        int dy,
        int radius,
        double directionRadians,
        int strength,
        int terraceStep) {
}
