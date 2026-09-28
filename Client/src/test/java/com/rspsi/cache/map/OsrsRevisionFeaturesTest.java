package com.rspsi.cache.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsrsRevisionFeaturesTest {
    @Test
    void centralizesNamedAndNumericMapGroupTransitions() {
        assertEquals(OsrsRevisionProfile.MapGroupLayout.NAMED,
                OsrsRevisionFeatures.forRevision(236).mapGroupLayout());
        assertTrue(OsrsRevisionFeatures.forRevision(237).usesNumericMapGroups());
    }

    @Test
    void rejectsInvalidRevision() {
        assertThrows(IllegalArgumentException.class,
                () -> OsrsRevisionFeatures.forRevision(0));
    }
}
