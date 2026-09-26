package com.rspsi.editor.selection;

/** A terrain corner/vertex selection; corner is 0=SW, 1=SE, 2=NE, 3=NW. */
public record VertexSelection(int plane, int x, int y, int corner) implements Selection {
    public VertexSelection {
        if (plane < 0 || x < 0 || y < 0 || corner < 0 || corner > 3) {
            throw new IllegalArgumentException("Invalid terrain vertex selection");
        }
    }
}
