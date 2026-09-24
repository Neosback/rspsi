package com.rspsi.api.model;

/** One model vertex, as {@code net.runelite.api.model.Vertex}. Y is negative-up. */
public record Vertex(int x, int y, int z) {
    public int getX() { return x; }

    public int getY() { return y; }

    public int getZ() { return z; }
}
