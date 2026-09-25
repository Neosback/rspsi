package com.rspsi.cache.store;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheStoreFactoryTest {

    @Test
    void modernOsrsFactoryExposesOpenRuneAndLegacyIsExplicitlyNamed() {
        var methods = Arrays.stream(CacheStoreFactory.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(methods.contains("openOsrs"));
        assertTrue(methods.contains("openRuneWritable"));
        assertTrue(methods.contains("legacy"));
        assertFalse(methods.contains("openRuneWithDispleeOutput"),
                "modern OSRS must not regain a parallel Displee output path");
    }
}
