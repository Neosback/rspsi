package com.rspsi.editor.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackfacePolicyTest {
    @Test
    void clientDraw0VisibleFaceMapsToCounterClockwiseNativeWindowWinding() {
        // Exact Model.draw0 edge expression. A(0,0), B(0,1), C(1,0)
        // produces +1, so field3034 (culled) is false and the client draws it.
        float clientEdge = clientEdge(0, 0, 0, 1, 1, 0);
        assertEquals(1.0f, clientEdge);
        assertTrue(BackfacePolicy.isFrontFacingSoftware(clientEdge));

        // Client Y-down -> OpenGL Y-up. Conventional native signed area is
        // positive for the same displayed triangle, therefore GL_CCW.
        float nativeArea = conventionalArea(0, 1, 0, 0, 1, 1);
        assertEquals(1.0f, nativeArea);
        assertEquals(BackfacePolicy.NativeWinding.COUNTER_CLOCKWISE,
                BackfacePolicy.nativeWinding());
    }

    @Test
    void clientDraw0CulledFaceMapsToClockwiseNativeWindowWinding() {
        float clientEdge = clientEdge(0, 0, 1, 0, 0, 1);

        assertEquals(-1.0f, clientEdge);
        assertTrue(!BackfacePolicy.isFrontFacingSoftware(clientEdge));
        float nativeArea = conventionalArea(0, 1, 1, 1, 0, 0);
        assertTrue(nativeArea < 0.0f);
    }

    @Test
    void degenerateTrianglesRemainRejected() {
        assertTrue(!BackfacePolicy.isFrontFacingSoftware(0.0f));
        assertTrue(!BackfacePolicy.isFrontFacingSoftware(0.00001f));
    }

    @Test
    void clientFrontIsTheNativeDefaultAndTerrainRemainsTwoSided() {
        assertEquals(BackfacePolicy.NativeCullingMode.CLIENT_FRONT,
                BackfacePolicy.defaultMode());

        assertFalse(BackfacePolicy.cullsLayer(
                SceneLayer.Kind.TERRAIN, BackfacePolicy.NativeCullingMode.CLIENT_FRONT));
        assertTrue(BackfacePolicy.cullsLayer(
                SceneLayer.Kind.WALL, BackfacePolicy.NativeCullingMode.CLIENT_FRONT));
        assertTrue(BackfacePolicy.cullsLayer(
                SceneLayer.Kind.WALL_DECORATION, BackfacePolicy.NativeCullingMode.CLIENT_FRONT));
        assertTrue(BackfacePolicy.cullsLayer(
                SceneLayer.Kind.GROUND_OBJECT, BackfacePolicy.NativeCullingMode.CLIENT_FRONT));
        assertTrue(BackfacePolicy.cullsLayer(
                SceneLayer.Kind.GROUND_DECORATION, BackfacePolicy.NativeCullingMode.CLIENT_FRONT));

        for (SceneLayer.Kind layer : SceneLayer.Kind.values()) {
            assertFalse(BackfacePolicy.cullsLayer(
                    layer, BackfacePolicy.NativeCullingMode.TWO_SIDED));
        }
    }

    @Test
    void reversedDebugFlipsOnlyTheNativeWinding() {
        assertEquals(BackfacePolicy.NativeWinding.COUNTER_CLOCKWISE,
                BackfacePolicy.nativeWinding(BackfacePolicy.NativeCullingMode.CLIENT_FRONT));
        assertEquals(BackfacePolicy.NativeWinding.CLOCKWISE,
                BackfacePolicy.nativeWinding(BackfacePolicy.NativeCullingMode.REVERSED_DEBUG));

        float clientEdge = clientEdge(0, 0, 0, 2, 3, 0);
        assertTrue(BackfacePolicy.isFrontFacingSoftware(clientEdge));
    }

    @Test
    void quarterTurnRotationsPreserveClientFrontWinding() {
        float ax = 0.0f, ay = 0.0f;
        float bx = 0.0f, by = 2.0f;
        float cx = 3.0f, cy = 0.0f;

        for (int rotation = 0; rotation < 4; rotation++) {
            float clientEdge = clientEdge(ax, ay, bx, by, cx, cy);
            assertTrue(BackfacePolicy.isFrontFacingSoftware(clientEdge),
                    "quarter-turn rotation " + rotation + " inverted client facing");

            float nativeArea = conventionalArea(ax, -ay, bx, -by, cx, -cy);
            assertTrue(nativeArea > 0.0f,
                    "quarter-turn rotation " + rotation + " inverted native CCW winding");

            float nextAx = -ay, nextAy = ax;
            float nextBx = -by, nextBy = bx;
            float nextCx = -cy, nextCy = cx;
            ax = nextAx;
            ay = nextAy;
            bx = nextBx;
            by = nextBy;
            cx = nextCx;
            cy = nextCy;
        }
    }

    /** Exact projected-edge expression used by RuneLite-melxin Model.draw0. */
    private static float clientEdge(float ax, float ay, float bx, float by, float cx, float cy) {
        return (ax - bx) * (cy - by) - (ay - by) * (cx - bx);
    }

    /** Conventional signed area used to classify CCW (>0) versus CW (<0). */
    private static float conventionalArea(float ax, float ay, float bx, float by, float cx, float cy) {
        return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    }
}
