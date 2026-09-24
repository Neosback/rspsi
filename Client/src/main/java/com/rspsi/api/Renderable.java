package com.rspsi.api;

/** Something the scene can draw, as {@code net.runelite.api.Renderable}. */
public interface Renderable {
    /** The lit model, or {@code null} when there is nothing to draw. */
    Model getModel();

    /** Height of the model above its base ({@code -min Y}). */
    int getModelHeight();
}
