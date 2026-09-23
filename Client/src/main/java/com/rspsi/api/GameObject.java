package com.rspsi.api;

/** Interactable/scenery loc (shapes 9-11 and roof/other game layers), as {@code net.runelite.api.GameObject}. */
public interface GameObject extends TileObject {
    /** Footprint width in tiles, after rotation. */
    int sizeX();

    /** Footprint length in tiles, after rotation. */
    int sizeY();

    Point getSceneMinLocation();

    Point getSceneMaxLocation();

    /**
     * Model orientation in JAU: 256 for diagonal shape 11 and 0 otherwise,
     * as the client map loader passes it ({@code runescape-client/FriendSystem}).
     */
    default int getModelOrientation() {
        return getType() == 11 ? 256 : 0;
    }

    /** {@code rotation * 512 + modelOrientation}, as {@code RSGameObjectMixin.getOrientation}. */
    default int getOrientation() {
        return getRotation() * 512 + getModelOrientation();
    }
}
