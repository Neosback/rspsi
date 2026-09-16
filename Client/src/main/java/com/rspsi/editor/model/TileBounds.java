package com.rspsi.editor.model;

/** Inclusive rectangular tile bounds used by fragments and selection tools. */
public record TileBounds(int minX, int minY, int maxX, int maxY) {
    public TileBounds {
        if (minX < 0 || minY < 0 || maxX < minX || maxY < minY) {
            throw new IllegalArgumentException("Invalid inclusive tile bounds");
        }
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int height() {
        return maxY - minY + 1;
    }

    public boolean contains(int x, int y) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }
}
