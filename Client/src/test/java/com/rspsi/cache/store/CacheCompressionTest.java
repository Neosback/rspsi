package com.rspsi.cache.store;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CacheCompressionTest {

    @Test
    void gzipRoundTripPreservesCacheBytes() {
        byte[] source = "RSPSi OSRS cache boundary".getBytes(StandardCharsets.UTF_8);

        byte[] compressed = CacheCompression.gzip(source);

        assertArrayEquals(source, CacheCompression.gunzip(compressed));
    }

    @Test
    void malformedOrMissingArchivesUseOptionalResourceFallback() {
        assertNull(CacheCompression.gunzip(null));
        assertNull(CacheCompression.gunzip(new byte[]{0x01, 0x02, 0x03}));
        assertNull(CacheCompression.gzip(null));
    }
}
