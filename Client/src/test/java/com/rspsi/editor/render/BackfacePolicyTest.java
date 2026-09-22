package com.rspsi.editor.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackfacePolicyTest {
    @Test
    void concreteClientVisibleTriangleMapsToCounterClockwiseNativeWindowWinding() {
        // Model.draw0 stores edge <= 0 in field3034, then only draws faces
        // where !field3034. A real visible client face therefore has edge > 0.
        float clientEdge = clientEdge(0, 0, 0, 1, 1, 0);
        assertEquals(1.0f, clientEdge);
        assertTrue(BackfacePolicy.isFrontFacingSoftware(clientEdge));

        // Software/client screen Y grows downward. Flip the same displayed
        // points into OpenGL's Y-up window coordinates.
        float nativeArea = conventionalArea(0, 0, 0, -1, 1, 0);
        assertEquals(1.0f, nativeArea);
        assertEquals(nativeArea, BackfacePolicy.nativeWindowArea(clientEdge));
        assertEquals(BackfacePolicy.NativeWinding.COUNTER_CLOCKWISE,
                BackfacePolicy.nativeWinding());
    }

    @Test
    void clientCulledFaceMapsToClockwiseNativeWindowWinding() {
        float clientEdge = clientEdge(0, 0, 1, 0, 0, 1);

        assertEquals(-1.0f, clientEdge);
        assertTrue(!BackfacePolicy.isFrontFacingSoftware(clientEdge));
        assertTrue(BackfacePolicy.nativeWindowArea(clientEdge) < 0.0f);
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
