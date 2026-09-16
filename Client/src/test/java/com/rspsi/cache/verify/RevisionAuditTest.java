package com.rspsi.cache.verify;

import com.rspsi.cache.CacheStoreCapabilities;
import com.rspsi.cache.OsrsCacheMetadata;
import com.rspsi.cache.map.MapIndexEntry;
import com.rspsi.cache.map.MapIndexTable;
import com.rspsi.cache.store.CacheStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevisionAuditTest {
    @Test
    void acceptsPackedModernLayoutWhenMetadataMatches() {
        MapIndexTable index = MapIndexTable.of(List.of(
                new MapIndexEntry(16, 33, 4129, 4129, "m16_33", "l16_33")));

        List<VerificationCheck> checks = RevisionAudit.audit(
                new FakeStore(new OsrsCacheMetadata(240, null, "fingerprint"), false), 240, index);

        assertEquals(VerificationCheck.Status.PASS, status(checks, "revision.metadata"));
        assertEquals(VerificationCheck.Status.PASS, status(checks, "revision.mapLayout"));
        assertEquals(VerificationCheck.Status.PASS, status(checks, "revision.codec"));
    }

    @Test
    void rejectsSplitNamedLayoutForModernProfile() {
        MapIndexTable index = MapIndexTable.of(List.of(
                new MapIndexEntry(50, 50, 10, 11, "m50_50", "l50_50")));

        List<VerificationCheck> checks = RevisionAudit.audit(
                new FakeStore(new OsrsCacheMetadata(240, null, "fingerprint"), true), 240, index);

        assertEquals(VerificationCheck.Status.FAIL, status(checks, "revision.mapLayout"));
        assertTrue(checks.stream().anyMatch(value -> value.status() == VerificationCheck.Status.FAIL));
    }

    private static VerificationCheck.Status status(List<VerificationCheck> checks, String id) {
        return checks.stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow().status();
    }

    private static final class FakeStore implements CacheStore {
        private final OsrsCacheMetadata metadata;
        private final boolean named;

        private FakeStore(OsrsCacheMetadata metadata, boolean named) {
            this.metadata = metadata;
            this.named = named;
        }

        @Override public byte[] read(int index, int archive, int file) { return new byte[0]; }
        @Override public void write(int index, int archive, int file, byte[] data) { }
        @Override public void flush() { }
        @Override public CacheStoreCapabilities capabilities() {
            return new CacheStoreCapabilities(false, named, false);
        }
        @Override public Optional<OsrsCacheMetadata> metadata(int revision) { return Optional.of(metadata); }
    }
}
