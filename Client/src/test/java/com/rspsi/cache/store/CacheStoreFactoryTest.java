package com.rspsi.cache.store;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CacheStoreFactoryTest {
    @Test
    void refusesToUseTheSourceCacheAsTheOutputCache() {
        Path path = Path.of("/tmp/rspsi-cache");

        assertThrows(IllegalArgumentException.class,
                () -> CacheStoreFactory.openRuneWithDispleeOutput(path, path));
    }
}
