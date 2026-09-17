package com.rspsi.editor.collision;

import com.rspsi.editor.model.TileCoordinate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoutePreviewServiceTest {
    @Test
    void returnsNeutralRouteResult() {
        CollisionMap map = new CollisionMap(5, 1, 1);
        TileCoordinate start = new TileCoordinate(0, 0, 0);
        TileCoordinate target = new TileCoordinate(0, 4, 0);

        RoutePreview preview = RoutePreviewService.evaluate(map, RoutePreviewMode.ROUTE,
                start, target, 100, 1, false);

        assertTrue(preview.successful());
        assertEquals(start, preview.path().get(0));
        assertEquals(target, preview.path().get(preview.path().size() - 1));
        assertTrue(preview.message().contains("5"));
    }

    @Test
    void lineOfSightKeepsTraceWhenBlocked() {
        CollisionMap map = new CollisionMap(3, 1, 1);
        map.add(new TileCoordinate(0, 1, 0), CollisionFlag.WALL_WEST_PROJECTILE);
        TileCoordinate start = new TileCoordinate(0, 0, 0);
        TileCoordinate target = new TileCoordinate(0, 2, 0);

        RoutePreview preview = RoutePreviewService.evaluate(map, RoutePreviewMode.LINE_OF_SIGHT,
                start, target, 100, 1, false);

        assertFalse(preview.successful());
        assertEquals(3, preview.path().size());
        assertEquals("Line of sight is blocked", preview.message());
    }

    @Test
    void lineOfSightUsesActorAndTargetFootprints() {
        CollisionMap map = new CollisionMap(6, 2, 1);
        TileCoordinate start = new TileCoordinate(0, 0, 0);
        TileCoordinate target = new TileCoordinate(0, 4, 0);
        map.add(new TileCoordinate(0, 3, 0), CollisionFlag.WALL_WEST_PROJECTILE);

        RoutePreview blocked = RoutePreviewService.evaluate(map,
                RoutePreviewMode.LINE_OF_SIGHT, start, target, 2, 1, 100, 2, false);
        assertFalse(blocked.successful());

        map.remove(new TileCoordinate(0, 3, 0), CollisionFlag.WALL_WEST_PROJECTILE);
        RoutePreview clear = RoutePreviewService.evaluate(map,
                RoutePreviewMode.LINE_OF_SIGHT, start, target, 2, 1, 100, 2, false);
        assertTrue(clear.successful());
    }

    @Test
    void reachUsesTargetFootprint() {
        CollisionMap map = new CollisionMap(5, 3, 1);
        TileCoordinate start = new TileCoordinate(0, 0, 1);
        TileCoordinate target = new TileCoordinate(0, 3, 1);

        RoutePreview preview = RoutePreviewService.evaluate(map, RoutePreviewMode.REACH,
                start, target, 2, 1, 100, 1, false);

        assertTrue(preview.successful());
        assertEquals(start, preview.path().get(0));
        TileCoordinate last = preview.path().get(preview.path().size() - 1);
        assertFalse(last.x() >= 3 && last.x() < 5 && last.y() == 1,
                "reach preview should stop at the footprint border");
    }

    @Test
    void rejectsInvalidSceneCoordinatesWithoutThrowing() {
        CollisionMap map = new CollisionMap(2, 2, 1);
        RoutePreview preview = RoutePreviewService.evaluate(map, RoutePreviewMode.ROUTE,
                new TileCoordinate(0, 2, 0), new TileCoordinate(0, 1, 1), 20, 1, false);

        assertFalse(preview.successful());
        assertTrue(preview.message().contains("outside"));
    }
}
