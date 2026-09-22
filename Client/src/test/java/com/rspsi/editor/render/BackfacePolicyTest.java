package com.rspsi.editor.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    /** Exact projected-edge expression used by RuneLite-melxin Model.draw0. */
    private static float clientEdge(float ax, float ay, float bx, float by, float cx, float cy) {
        return (ax - bx) * (cy - by) - (ay - by) * (cx - bx);
    }

    /** Conventional signed area used to classify CCW (>0) versus CW (<0). */
    private static float conventionalArea(float ax, float ay, float bx, float by, float cx, float cy) {
        return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    }
}
