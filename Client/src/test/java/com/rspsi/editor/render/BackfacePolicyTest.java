package com.rspsi.editor.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackfacePolicyTest {
    @Test
    void concreteClientFrontTriangleMapsToClockwiseNativeWindowWinding() {
        // Client screen coordinates use Y down. For A(0,0), B(1,0), C(0,1)
        // Model.draw0's edge expression is -1, so the client accepts it.
        float clientEdge = clientEdge(0, 0, 1, 0, 0, 1);
        assertTrue(BackfacePolicy.isFrontFacingSoftware(clientEdge));
        assertEquals(-1.0f, clientEdge);

        // Mapping the same displayed points into a Y-up OpenGL window gives
        // A(0,1), B(1,1), C(0,0). Conventional signed area is -1: clockwise.
        float nativeArea = conventionalArea(0, 1, 1, 1, 0, 0);
        assertEquals(clientEdge, nativeArea);
        assertEquals(nativeArea, BackfacePolicy.nativeWindowArea(clientEdge));
        assertEquals(BackfacePolicy.NativeWinding.CLOCKWISE,
                BackfacePolicy.nativeWinding());
    }

    @Test
    void clientBackFaceMapsToCounterClockwiseNativeWindowWinding() {
        float clientEdge = clientEdge(0, 0, 0, 1, 1, 0);

        assertTrue(!BackfacePolicy.isFrontFacingSoftware(clientEdge));
        assertTrue(BackfacePolicy.nativeWindowArea(clientEdge) > 0.0f);
    }

    @Test
    void degenerateTrianglesRemainRejected() {
        assertTrue(!BackfacePolicy.isFrontFacingSoftware(0.0f));
        assertTrue(!BackfacePolicy.isFrontFacingSoftware(-0.00001f));
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
