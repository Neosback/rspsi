package com.rspsi.api;

/** Two-dimensional integer point, as {@code net.runelite.api.Point}. */
public record Point(int x, int y) {
    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}
