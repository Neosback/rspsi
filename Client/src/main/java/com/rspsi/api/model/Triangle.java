package com.rspsi.api.model;

/** One face as its three vertices, as {@code net.runelite.api.model.Triangle}. */
public record Triangle(Vertex a, Vertex b, Vertex c) {
    public Vertex getA() { return a; }

    public Vertex getB() { return b; }

    public Vertex getC() { return c; }
}
