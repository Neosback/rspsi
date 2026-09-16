package com.rspsi.editor.model;

/** Backend-neutral object placement used by editor operations. */
public record WorldObject(int id, int type, int rotation, int plane, int x, int y) {
    public WorldObject {
        if (id < 0 || type < 0 || plane < 0 || x < 0 || y < 0) {
            throw new IllegalArgumentException("World object values cannot be negative");
        }
        if (rotation < 0 || rotation > 3) {
            throw new IllegalArgumentException("Object rotation must be between 0 and 3");
        }
    }
}
