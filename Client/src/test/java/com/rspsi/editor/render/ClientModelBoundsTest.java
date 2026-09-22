package com.rspsi.editor.render;

import com.rspsi.editor.model.ObjectCategory;
import com.rspsi.editor.model.TileCoordinate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientModelBoundsTest {
    private static final List<ModelVertex> ASYMMETRIC = List.of(
            vertex(-10, -20, -30),
            vertex(50, 40, 70),
            vertex(20, 10, -5));

    @Test
    void matchesClientBoundsCylinderMath() {
        ClientModelBounds bounds = ClientModelBounds.calculate(ASYMMETRIC, 0, false);

        assertTrue(bounds.present());
        assertEquals(20, bounds.height());
        assertEquals(40, bounds.bottomY());
        assertEquals(87, bounds.xzRadius());
        assertEquals(90, bounds.radius());
        assertEquals(186, bounds.diameter());
    }

    @Test
    void matchesClientOrientationSpecificAabbAtCardinalAngles() {
        assertAabb(0, 20, 10, 20, 32, 30, 50);
        assertAabb(512, 19, 10, -20, 50, 30, 32);
        assertAabb(1024, -20, 10, -20, 32, 30, 50);
        assertAabb(1536, -20, 10, 19, 50, 30, 32);
    }

    @Test
    void matchesClientAabbAtShapeElevenDiagonalOrientation() {
        ClientModelBounds.Aabb aabb =
                ClientModelBounds.calculate(ASYMMETRIC, 256, false).drawAabb();

        assertEquals(256, aabb.orientation());
        assertEquals(27, aabb.xMid());
        assertEquals(10, aabb.yMid());
        assertEquals(-2, aabb.zMid());
        assertEquals(57, aabb.xMidOffset());
        assertEquals(30, aabb.yMidOffset());
        assertEquals(32, aabb.zMidOffset());
    }

    @Test
    void packetAnchorRebaseDoesNotTranslateModelLocalBounds() {
        ClientModelBounds bounds = ClientModelBounds.calculate(ASYMMETRIC, 0, false);
        ModelRenderPacket packet = new ModelRenderPacket(
                new TileCoordinate(0, 1, 1), 42, ObjectCategory.GROUND,
                List.of(vertex(0, 0, 0)), List.of(), List.of(), -1,
                0, 0, 0, 0, 0, 0, false, false)
                .withClientModelBounds(bounds);

        ModelRenderPacket world = packet.withAnchor(new TileCoordinate(0, 3200, 6400));

        assertEquals(List.of(bounds), world.clientRenderableBounds());
    }

    @Test
    void appliesClientMinimumHorizontalExtentAndSingleTilePadding() {
        List<ModelVertex> tiny = List.of(vertex(1, -2, 1), vertex(2, 3, 2));

        ClientModelBounds normal = ClientModelBounds.calculate(tiny, 0, false);
        ClientModelBounds singleTile = ClientModelBounds.calculate(tiny, 0, true);

        assertEquals(32, normal.drawAabb().xMidOffset());
        assertEquals(32, normal.drawAabb().zMidOffset());
        assertEquals(40, singleTile.drawAabb().xMidOffset());
        assertEquals(40, singleTile.drawAabb().zMidOffset());
    }

    private static void assertAabb(int orientation,
                                   int xMid, int yMid, int zMid,
                                   int xOffset, int yOffset, int zOffset) {
        ClientModelBounds.Aabb aabb =
                ClientModelBounds.calculate(ASYMMETRIC, orientation, false).drawAabb();
        assertEquals(orientation, aabb.orientation());
        assertEquals(xMid, aabb.xMid());
        assertEquals(yMid, aabb.yMid());
        assertEquals(zMid, aabb.zMid());
        assertEquals(xOffset, aabb.xMidOffset());
        assertEquals(yOffset, aabb.yMidOffset());
        assertEquals(zOffset, aabb.zMidOffset());
    }

    private static ModelVertex vertex(int x, int y, int z) {
        return new ModelVertex(x, y, z, 0, 0, 0, 0, 0.0f, 0.0f);
    }
}
