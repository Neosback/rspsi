package com.rspsi.api;

/** Decoded sprite, as {@code net.runelite.api.SpritePixels}. */
public interface SpritePixels {
    int getWidth();

    int getHeight();

    /** Row-major RGB pixels; 0 is transparent, as in the client. */
    int[] getPixels();
}
