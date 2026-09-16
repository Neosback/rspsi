package com.rspsi.cache.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OsrsRevisionProfileTest {
    @Test
    void selectsNamedLayoutBeforeNumericPackingChange() {
        assertEquals(OsrsRevisionProfile.MapGroupLayout.NAMED,
                OsrsRevisionProfile.forRevision(236).mapGroupLayout());
    }

    @Test
    void selectsNumericLayoutAtOpenRunePackingChange() {
        assertEquals(OsrsRevisionProfile.MapGroupLayout.NUMERIC,
                OsrsRevisionProfile.forRevision(237).mapGroupLayout());
    }

    @Test
    void selectsLegacyTerrainBytesBeforeRevision209() {
        assertEquals(false, OsrsRevisionProfile.forRevision(208).newTerrainFormat());
    }

    @Test
    void selectsModernTerrainShortsAtRevision209() {
        assertEquals(true, OsrsRevisionProfile.forRevision(209).newTerrainFormat());
    }

    @Test
    void rejectsUnknownRevision() {
        assertThrows(IllegalArgumentException.class, () -> OsrsRevisionProfile.forRevision(0));
    }
}
