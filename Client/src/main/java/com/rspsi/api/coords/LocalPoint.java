package com.rspsi.api.coords;

import com.rspsi.api.Perspective;
import com.rspsi.api.WorldView;

import java.util.Objects;

/**
 * Scene-relative position in local units (128 per tile), as
 * {@code net.runelite.api.coords.LocalPoint}.
 */
public record LocalPoint(int x, int y) {
    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getSceneX() {
        return x >> Perspective.LOCAL_COORD_BITS;
    }

    public int getSceneY() {
        return y >> Perspective.LOCAL_COORD_BITS;
    }

    /** Centre of the given scene tile. */
    public static LocalPoint fromScene(int sceneX, int sceneY) {
        return new LocalPoint((sceneX << Perspective.LOCAL_COORD_BITS) + Perspective.LOCAL_HALF_TILE_SIZE,
                (sceneY << Perspective.LOCAL_COORD_BITS) + Perspective.LOCAL_HALF_TILE_SIZE);
    }

    /** Centre of the world tile, or {@code null} when it lies outside the view (RuneLite contract). */
    public static LocalPoint fromWorld(WorldView view, WorldPoint point) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(point, "point");
        int sceneX = point.x() - view.getBaseX();
        int sceneY = point.y() - view.getBaseY();
        if (sceneX < 0 || sceneY < 0 || sceneX >= view.getSizeX() || sceneY >= view.getSizeY()) {
            return null;
        }
        return fromScene(sceneX, sceneY);
    }
}
