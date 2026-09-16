package com.rspsi.cache.store;

import com.rspsi.cache.CacheStoreCapabilities;
import com.rspsi.cache.CacheWriteMode;
import com.rspsi.cache.OsrsCacheMetadata;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LayeredCacheStoreTest {
    @Test
    void stagesWritesUntilExplicitFlushAndReadsTheStagedValue() {
        FakeStore base = new FakeStore(false);
        FakeStore output = new FakeStore(true);
        base.values.put(key(5, 100, 0), new byte[]{1});
        LayeredCacheStore store = new LayeredCacheStore(base, output);

        store.write(5, 100, 0, new byte[]{2});

        assertArrayEquals(new byte[]{2}, store.read(5, 100, 0));
        assertArrayEquals(new byte[]{1}, base.values.get(key(5, 100, 0)));
        assertNull(output.values.get(key(5, 100, 0)));
        assertEquals(1, store.pendingWriteCount());
        assertEquals(CacheWriteMode.STAGED, store.capabilities().writeMode());

        store.flush();

        assertArrayEquals(new byte[]{2}, output.values.get(key(5, 100, 0)));
        assertArrayEquals(new byte[]{2}, store.read(5, 100, 0));
        assertEquals(0, store.pendingWriteCount());
    }

    @Test
    void refusesStagingWhenTheOutputBackendIsReadOnly() {
        LayeredCacheStore store = new LayeredCacheStore(new FakeStore(false), new FakeStore(false));

        assertEquals(CacheWriteMode.READ_ONLY, store.capabilities().writeMode());
        assertThrows(UnsupportedOperationException.class,
                () -> store.write(5, 1, 0, new byte[]{7}));
        assertEquals(0, store.pendingWriteCount());
    }

    @Test
    void discardLeavesBothBackingStoresUnchanged() {
        FakeStore base = new FakeStore(false);
        FakeStore output = new FakeStore(true);
        LayeredCacheStore store = new LayeredCacheStore(base, output);
        store.write(5, 1, 0, new byte[]{7});
        store.discardWrites();

        assertNull(store.read(5, 1, 0));
        assertNull(output.values.get(key(5, 1, 0)));
    }

    @Test
    void preservesBaseCacheIdentityForProjectCompatibility() {
        OsrsCacheMetadata metadata = new OsrsCacheMetadata(240, 2, "cache-a");
        MetadataStore base = new MetadataStore(metadata);
        LayeredCacheStore store = new LayeredCacheStore(base, new MetadataStore(null));

        assertEquals(metadata, store.metadata(240).orElseThrow());
    }

    private static String key(int index, int archive, int file) {
        return index + ":" + archive + ":" + file;
    }

    private static final class FakeStore implements CacheStore {
        private final Map<String, byte[]> values = new HashMap<>();
        private final boolean writable;

        private FakeStore(boolean writable) { this.writable = writable; }

        @Override public byte[] read(int index, int archive, int file) { return values.get(key(index, archive, file)); }
        @Override public void write(int index, int archive, int file, byte[] data) {
            if (!writable) throw new UnsupportedOperationException("read-only");
            values.put(key(index, archive, file), data.clone());
        }
        @Override public void flush() { }
        @Override public CacheStoreCapabilities capabilities() {
            return new CacheStoreCapabilities(writable, true, writable);
        }
    }

    private static final class MetadataStore implements CacheStore {
        private final OsrsCacheMetadata metadata;

        private MetadataStore(OsrsCacheMetadata metadata) {
            this.metadata = metadata;
        }

        @Override public byte[] read(int index, int archive, int file) { return null; }
        @Override public void write(int index, int archive, int file, byte[] data) { }
        @Override public void flush() { }
        @Override public CacheStoreCapabilities capabilities() {
            return new CacheStoreCapabilities(false, false, false);
        }
        @Override public java.util.Optional<OsrsCacheMetadata> metadata(int revision) {
            return java.util.Optional.ofNullable(metadata);
        }
    }
}
