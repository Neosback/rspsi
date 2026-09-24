package com.rspsi.api.worldmap;

import com.rspsi.api.Point;
import com.rspsi.api.coords.WorldPoint;

/**
 * The map navigator, as {@code net.runelite.api.worldmap.WorldMap}. In
 * Studio this is the minimap radar: its position is the world tile the
 * camera is centred on, and a position target moves the camera there.
 */
public interface WorldMap {
    /** World tile at the centre of the map. */
    Point getWorldMapPosition();

    /** Pixels per tile. */
    float getWorldMapZoom();

    /** Moves the map (and the Studio camera) to centre on this tile. */
    void setWorldMapPositionTarget(WorldPoint worldPoint);
}
