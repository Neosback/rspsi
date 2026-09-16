package com.rspsi.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CacheStoreCapabilitiesTest {
    @Test
    void legacyThreeArgumentConstructorKeepsSafeDefaults() {
        assertEquals(CacheWriteMode.READ_ONLY,
                new CacheStoreCapabilities(false, true, false).writeMode());
        assertEquals(CacheWriteMode.DIRECT,
                new CacheStoreCapabilities(true, false, true).writeMode());
    }

    @Test
    void rejectsContradictoryWriteModes() {
        assertThrows(IllegalArgumentException.class,
                () -> new CacheStoreCapabilities(false, true, false, CacheWriteMode.STAGED));
        assertThrows(IllegalArgumentException.class,
                () -> new CacheStoreCapabilities(true, true, true, CacheWriteMode.READ_ONLY));
    }
}
