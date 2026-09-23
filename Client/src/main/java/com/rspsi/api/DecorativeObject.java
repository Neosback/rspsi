package com.rspsi.api;

/** Wall decoration loc (shapes 4-8), as {@code net.runelite.api.DecorativeObject}. */
public interface DecorativeObject extends TileObject {
    /** Local-unit X displacement of the primary renderable from the tile. */
    int getXOffset();

    /** Local-unit Y displacement of the primary renderable from the tile. */
    int getYOffset();
}
