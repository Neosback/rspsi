package com.rspsi.editor.tool.spline;

/**
 * Autotiling edge styles for spline-generated paths.
 * Translates 4-bit neighbor connectivity masks into canonical OSRS overlay shapes and rotations.
 */
public enum SplineBrushStyle {
    SOLID("Solid (Full / Straight)", "Square tiles with straight-edge transitions"),
    WEDGE("Wedge (Chiseled)", "Diagonal 45-degree corner transitions"),
    SMOOTH("Smooth (Curved)", "Rounded curve transitions for organic paths and rivers"),
    RAMP("Incline Ramp", "Interpolates terrain heights uniformly along the spline path");

    private final String displayName;
    private final String description;
    private final int[] edgeTable = new int[16];

    SplineBrushStyle(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
        initEdgeTable();
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    /**
     * Unpacks the OSRS overlay shape ID (0..12) for the given 4-bit neighbor mask.
     */
    public int shape(int neighbourMask) {
        int packed = edgeTable[neighbourMask & 0xF];
        return (packed >> 4) & 0xF;
    }

    /**
     * Unpacks the OSRS overlay rotation (0..3) for the given 4-bit neighbor mask.
     */
    public int rotation(int neighbourMask) {
        int packed = edgeTable[neighbourMask & 0xF];
        return packed & 0x3;
    }

    public SplineBrushStyle next() {
        SplineBrushStyle[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    private static int packEdge(int shape, int rotation) {
        return ((shape & 0xF) << 4) | (rotation & 0x3);
    }

    private void initEdgeTable() {
        // Default all masks to full tile (shape 0, rotation 0)
        for (int i = 0; i < 16; i++) {
            edgeTable[i] = packEdge(0, 0);
        }

        switch (this) {
            case SOLID, RAMP -> {
                edgeTable[3] = packEdge(1, 2);
                edgeTable[9] = packEdge(1, 1);
                edgeTable[12] = packEdge(1, 0);
                edgeTable[6] = packEdge(1, 3);
                edgeTable[1] = packEdge(1, 0);
                edgeTable[2] = packEdge(1, 0);
                edgeTable[4] = packEdge(1, 3);
                edgeTable[8] = packEdge(1, 2);
            }
            case WEDGE -> {
                edgeTable[14] = packEdge(6, 1);
                edgeTable[13] = packEdge(6, 3);
                edgeTable[11] = packEdge(6, 0);
                edgeTable[7] = packEdge(6, 2);
                edgeTable[3] = packEdge(1, 2);
                edgeTable[9] = packEdge(1, 1);
                edgeTable[12] = packEdge(1, 0);
                edgeTable[6] = packEdge(1, 3);
                edgeTable[1] = packEdge(6, 3);
                edgeTable[2] = packEdge(6, 2);
                edgeTable[4] = packEdge(6, 1);
                edgeTable[8] = packEdge(6, 0);
            }
            case SMOOTH -> {
                edgeTable[14] = packEdge(6, 1);
                edgeTable[13] = packEdge(6, 3);
                edgeTable[11] = packEdge(6, 0);
                edgeTable[7] = packEdge(6, 2);
                edgeTable[3] = packEdge(11, 0);
                edgeTable[9] = packEdge(11, 3);
                edgeTable[12] = packEdge(11, 2);
                edgeTable[6] = packEdge(11, 1);
                edgeTable[1] = packEdge(11, 2);
                edgeTable[2] = packEdge(11, 2);
                edgeTable[4] = packEdge(11, 1);
                edgeTable[8] = packEdge(11, 0);
            }
        }
    }
}
