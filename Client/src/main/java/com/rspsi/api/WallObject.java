package com.rspsi.api;

/** Boundary loc (shapes 0-3), as {@code net.runelite.api.WallObject}. */
public interface WallObject extends TileObject {
    /** 1/2/4/8 = west/north/east/south; 16/32/64/128 = NW/NE/SE/SW for diagonal walls. */
    int getOrientationA();

    /** Second wall of an L-corner (shape 2), otherwise 0. */
    int getOrientationB();
}
