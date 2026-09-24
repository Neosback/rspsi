package com.rspsi.api.worldmap;

import com.rspsi.api.Point;
import com.rspsi.api.coords.WorldPoint;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.render.CameraState;
import com.rspsi.editor.render.NavigationService;

import java.util.Objects;

/**
 * {@link WorldMap} over the Studio camera navigator: the map is centred on
 * the camera's world tile (what the minimap radar shows), and a position
 * target is a navigator jump, so it lands in Back/Forward history.
 */
public final class NavigatorWorldMap implements WorldMap {
    /** Minimap radar scale in pixels per tile (MinimapHudOverlay). */
    public static final float RADAR_PIXELS_PER_TILE = 2.6f;

    private final NavigationService navigation;

    public NavigatorWorldMap(NavigationService navigation) {
        this.navigation = Objects.requireNonNull(navigation, "navigation");
    }

    @Override
    public Point getWorldMapPosition() {
        CameraState camera = navigation.viewport().camera();
        return new Point((int) Math.floor(camera.x() / 128.0f), (int) Math.floor(camera.z() / 128.0f));
    }

    @Override
    public float getWorldMapZoom() {
        return RADAR_PIXELS_PER_TILE;
    }

    @Override
    public void setWorldMapPositionTarget(WorldPoint worldPoint) {
        Objects.requireNonNull(worldPoint, "worldPoint");
        navigation.jumpTo(new WorldTile(worldPoint.plane(), worldPoint.x(), worldPoint.y()));
    }
}
