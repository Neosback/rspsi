package com.rspsi.api.worldmap;

import com.rspsi.api.SpritePixels;

/**
 * A map element (map function icon such as a bank or shop), as
 * {@code net.runelite.api.worldmap.MapElementConfig}.
 */
public interface MapElementConfig {
    /** Studio extra: the element id. */
    int getId();

    /** The icon sprite, or {@code null} when the cache has none. */
    SpritePixels getMapIcon(boolean unused);

    /** Content category (opcode 19), or -1. */
    int getCategory();

    /** Studio extra: label text, may be empty. */
    String getName();

    /** Studio extra: sprite group of the icon, or -1. */
    int getSpriteId();

    /** Studio extra: shown on the minimap. */
    boolean isMinimapVisible();

    /** Studio extra: shown on the world map. */
    boolean isWorldMapVisible();
}
