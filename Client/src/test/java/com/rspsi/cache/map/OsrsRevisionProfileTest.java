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
    void rejectsUnknownRevision() {
        assertThrows(IllegalArgumentException.class, () -> OsrsRevisionProfile.forRevision(0));
    }
}
