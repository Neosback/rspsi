package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DirtyRegionInteropTest {

    @Test
    void remainsJvmRecordAndPreservesComponentAccessors() {
        DirtyRegion dirty = new DirtyRegion(2, 10, 11, true, false, true, false, true);

        assertTrue(DirtyRegion.class.isRecord());
        assertEquals(2, dirty.plane());
        assertEquals(10, dirty.chunkX());
        assertEquals(11, dirty.chunkY());
        assertTrue(dirty.terrain());
        assertFalse(dirty.objects());
        assertTrue(dirty.collision());
        assertFalse(dirty.minimap());
        assertTrue(dirty.render());
        assertEquals(new DirtyRegion(2, 10, 11, true, false, true, false, true), dirty);
        assertTrue(dirty.toString().startsWith("DirtyRegion["));
    }

    @Test
    void compatibilityConstructorPreservesAllPlaneSentinel() {
        DirtyRegion dirty = new DirtyRegion(4, 5, true, true, false, false, true);

        assertEquals(-1, dirty.plane());
        assertEquals(4, dirty.chunkX());
        assertEquals(5, dirty.chunkY());
    }

    @Test
    @SuppressWarnings("deprecation")
    void forTilePreservesPlaneAndEightByEightChunkMapping() {
        DirtyRegion dirty = DirtyRegion.forTile(new TileCoordinate(3, 23, 31));

        assertEquals(3, dirty.plane());
        assertEquals(2, dirty.chunkX());
        assertEquals(3, dirty.chunkY());
        assertTrue(dirty.terrain());
        assertTrue(dirty.objects());
        assertTrue(dirty.collision());
        assertTrue(dirty.minimap());
        assertTrue(dirty.render());
        assertThrows(NullPointerException.class, () -> DirtyRegion.forTile(null));
    }

    @Test
    void mergeOrsInvalidationReasonsWithoutChangingIdentity() {
        DirtyRegion left = new DirtyRegion(1, 8, 9, true, false, false, true, false);
        DirtyRegion right = new DirtyRegion(1, 8, 9, false, true, true, false, true);

        DirtyRegion merged = left.merge(right);

        assertEquals(1, merged.plane());
        assertEquals(8, merged.chunkX());
        assertEquals(9, merged.chunkY());
        assertTrue(merged.terrain());
        assertTrue(merged.objects());
        assertTrue(merged.collision());
        assertTrue(merged.minimap());
        assertTrue(merged.render());
    }

    @Test
    void mergeRejectsDifferentIdentityAndNull() {
        DirtyRegion dirty = new DirtyRegion(1, 8, 9, true, false, false, false, false);

        assertThrows(NullPointerException.class, () -> dirty.merge(null));
        assertThrows(IllegalArgumentException.class,
                () -> dirty.merge(new DirtyRegion(2, 8, 9, false, true, false, false, false)));
        assertThrows(IllegalArgumentException.class,
                () -> dirty.merge(new DirtyRegion(1, 7, 9, false, true, false, false, false)));
        assertThrows(IllegalArgumentException.class,
                () -> dirty.merge(new DirtyRegion(1, 8, 10, false, true, false, false, false)));
    }

    @Test
    void preservesValidationIncludingMinusOnePlaneSentinel() {
        assertDoesNotThrow(() -> new DirtyRegion(-1, 0, 0, false, false, false, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> new DirtyRegion(-2, 0, 0, false, false, false, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> new DirtyRegion(0, -1, 0, false, false, false, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> new DirtyRegion(0, 0, -1, false, false, false, false, false));
    }
}
