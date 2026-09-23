package com.rspsi.api;

/**
 * Simple four-colour tile paint, as {@code net.runelite.api.SceneTilePaint}.
 *
 * <p>The client builds a paint for an underlay-only tile and for a full-square
 * (shape 0) overlay; every other overlay shape becomes a {@link SceneTileModel}
 * ({@code runescape-client/Scene.addTile}). Corner colours are the lit packed
 * HSL values the client stores, not raw floor-definition colours.</p>
 */
public interface SceneTilePaint {
    int getSwColor();

    int getSeColor();

    int getNeColor();

    int getNwColor();

    /** Texture id, or -1 when the tile draws the four-colour gradient. */
    int getTexture();

    /** True when all four corner heights are equal (always false for underlay paint, as in the client). */
    boolean isFlat();

    /** Studio extra: packed HSL the minimap uses for this tile, or a negative sentinel. */
    int getMinimapHsl();
}
