package com.rspsi.editor.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrawCallbackContractTest {
    @Test
    void mirrorsVendoredRuneLitePassIdsAndCapabilityBits() {
        assertEquals(0x1, DrawCallbackContract.GPU);
        assertEquals(0x2, DrawCallbackContract.HILLSKEW);
        assertEquals(0x4, DrawCallbackContract.NORMALS);
        assertEquals(0x8, DrawCallbackContract.NO_VERTEX_SNAPPING);
        assertEquals(0x10, DrawCallbackContract.ZBUF);
        assertEquals(0x20, DrawCallbackContract.ZBUF_ZONE_FRUSTUM_CHECK);
        assertEquals(0x40, DrawCallbackContract.UNLIT_FACE_COLORS);
        assertEquals(0, DrawCallbackContract.PASS_OPAQUE);
        assertEquals(1, DrawCallbackContract.PASS_ALPHA);
        assertEquals(3 << 7, DrawCallbackContract.renderThreads(3));
    }

    @Test
    void nativeNegotiationAdvertisesOnlyBackedCapabilities() {
        int requested = DrawCallbackContract.GPU
                | DrawCallbackContract.HILLSKEW
                | DrawCallbackContract.NORMALS
                | DrawCallbackContract.NO_VERTEX_SNAPPING
                | DrawCallbackContract.ZBUF
                | DrawCallbackContract.ZBUF_ZONE_FRUSTUM_CHECK
                | DrawCallbackContract.UNLIT_FACE_COLORS
                | DrawCallbackContract.renderThreads(4);

        DrawCallbackContract.Negotiation result =
                DrawCallbackContract.negotiate(requested);

        assertTrue(result.enabled(DrawCallbackContract.GPU));
        assertTrue(result.enabled(DrawCallbackContract.HILLSKEW));
        assertTrue(result.enabled(DrawCallbackContract.NORMALS));
        assertTrue(result.enabled(DrawCallbackContract.ZBUF));
        assertFalse(result.enabled(DrawCallbackContract.NO_VERTEX_SNAPPING));
        assertFalse(result.enabled(DrawCallbackContract.ZBUF_ZONE_FRUSTUM_CHECK));
        assertFalse(result.enabled(DrawCallbackContract.UNLIT_FACE_COLORS));
        assertFalse(result.fullySupported());

        assertEquals(DrawCallbackContract.GPU
                        | DrawCallbackContract.HILLSKEW
                        | DrawCallbackContract.NORMALS
                        | DrawCallbackContract.ZBUF,
                result.enabledFlags());
    }

    @Test
    void mapsOnlyTheTwoPassesDefinedByVendoredDrawCallbacks() {
        assertEquals(GpuDrawCommand.SubmissionPass.OPAQUE,
                DrawCallbackContract.submissionPass(DrawCallbackContract.PASS_OPAQUE));
        assertEquals(GpuDrawCommand.SubmissionPass.ALPHA,
                DrawCallbackContract.submissionPass(DrawCallbackContract.PASS_ALPHA));
        assertThrows(IllegalArgumentException.class,
                () -> DrawCallbackContract.submissionPass(2));
    }

    @Test
    void unknownCapabilityBitsFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> DrawCallbackContract.negotiate(1 << 20));
    }
}
