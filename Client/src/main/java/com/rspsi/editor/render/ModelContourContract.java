package com.rspsi.editor.render;

import java.util.List;
import java.util.Objects;

/**
 * First-class contour-ground contract retained with a rendered model packet.
 *
 * <p>The raw type/parameter preserve the neutral cache semantics while
 * {@link Mode} classifies the behavior actually used by the builder. When
 * contouring creates a warped model, {@code unskewedVertexY} is aligned 1:1
 * with the packet vertex stream and reconstructs RuneLite's HILLSKEW
 * {@code Model#getUnskewedModel()} relationship without duplicating X/Z,
 * faces, materials, or UVs.</p>
 */
public record ModelContourContract(
        boolean present,
        int type,
        int parameter,
        Mode mode,
        int placementHeight,
        boolean applied,
        List<Integer> unskewedVertexY
) {
    public enum Mode {
        NONE,
        FULL,
        PARTIAL,
        CLAMPED_DELTA,
        ABOVE_PLANE_OFFSET,
        ABOVE_PLANE_BLEND,
        UNKNOWN_FULL
    }

    private static final ModelContourContract NONE =
            new ModelContourContract(false, -1, 0, Mode.NONE, 0, false, List.of());

    public ModelContourContract {
        mode = Objects.requireNonNull(mode, "mode");
        unskewedVertexY = List.copyOf(Objects.requireNonNull(
                unskewedVertexY, "unskewedVertexY"));
        if (!present) {
            if (type != -1 || parameter != 0 || mode != Mode.NONE
                    || applied || !unskewedVertexY.isEmpty()) {
                throw new IllegalArgumentException("Absent contour contract must be neutral");
            }
        } else {
            if (type < 0 || mode == Mode.NONE) {
                throw new IllegalArgumentException("Present contour contract requires a contour mode");
            }
            if (applied && unskewedVertexY.isEmpty()) {
                throw new IllegalArgumentException("Applied contour must retain unskewed vertex Y values");
            }
            if (!applied && !unskewedVertexY.isEmpty()) {
                throw new IllegalArgumentException("Skipped contour cannot retain an unskewed copy");
            }
        }
    }

    public static ModelContourContract none() {
        return NONE;
    }

    public static ModelContourContract of(int type, int parameter, int placementHeight,
                                          boolean applied, List<Integer> unskewedVertexY) {
        if (type < 0) return none();
        return new ModelContourContract(true, type, parameter, classify(type, parameter),
                placementHeight, applied,
                applied ? List.copyOf(unskewedVertexY) : List.of());
    }

    public static Mode classify(int type, int parameter) {
        if (type < 0) return Mode.NONE;
        if ((type == 1 || type == 2) && parameter > 0) return Mode.PARTIAL;
        if (type == 1 || type == 2) return Mode.FULL;
        if (type == 3) return Mode.CLAMPED_DELTA;
        if (type == 4) return Mode.ABOVE_PLANE_OFFSET;
        if (type == 5) return Mode.ABOVE_PLANE_BLEND;
        return Mode.UNKNOWN_FULL;
    }

    public boolean hasUnskewedModel() {
        return applied;
    }

    public int unskewedY(int vertexIndex) {
        if (!applied) {
            throw new IllegalStateException("Contour did not create an unskewed model relationship");
        }
        return unskewedVertexY.get(vertexIndex);
    }

    public void validateVertexCount(int vertexCount) {
        if (applied && unskewedVertexY.size() != vertexCount) {
            throw new IllegalArgumentException(
                    "Unskewed contour vertex count must match rendered vertices");
        }
    }
}
