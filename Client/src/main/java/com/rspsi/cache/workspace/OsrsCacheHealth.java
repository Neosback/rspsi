package com.rspsi.cache.workspace;

import com.rspsi.cache.OsrsCacheIndexLayout;
import com.rspsi.cache.OsrsCacheMetadata;
import com.rspsi.cache.store.CacheStoreFactory;
import com.rspsi.cache.store.OpenRuneCacheStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Lightweight FileStore/cache health snapshot used by the project home.
 *
 * <p>This deliberately does not construct a {@link LoadedOsrsCacheSession}, load definition
 * providers, decode sprites, or run the broad cache census. It verifies that FileStore can open
 * the selected cache, that the required OSRS config/map indices exist, and records enough identity
 * information for Studio to decide whether workspaces may be opened.</p>
 */
public record OsrsCacheHealth(
        Path path,
        int revision,
        String backendName,
        String fingerprint,
        int configArchiveCount,
        int mapArchiveCount) {

    public OsrsCacheHealth {
        path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        if (revision <= 0) throw new IllegalArgumentException("revision must be positive");
        backendName = Objects.requireNonNull(backendName, "backendName");
        fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        if (configArchiveCount <= 0) {
            throw new IllegalArgumentException("OSRS config index is empty");
        }
        if (mapArchiveCount <= 0) {
            throw new IllegalArgumentException("OSRS map index is empty");
        }
    }

    public static OsrsCacheHealth inspect(Path path) {
        Path normalized = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            throw new IllegalArgumentException("Cache directory does not exist: " + normalized);
        }

        int revision = OpenRuneCacheStore.detectRevision(normalized);
        try (OpenRuneCacheStore store = CacheStoreFactory.openOsrs(normalized)) {
            int configArchiveCount = store.archiveIds(OsrsCacheIndexLayout.CONFIGS).length;
            int mapArchiveCount = store.archiveIds(OsrsCacheIndexLayout.MAPS).length;
            if (configArchiveCount == 0 || mapArchiveCount == 0) {
                throw new IllegalArgumentException(
                        "Selected cache is missing required OSRS config or map archives: "
                                + normalized);
            }
            OsrsCacheMetadata identity = store.metadata(revision).orElseThrow(
                    () -> new IllegalArgumentException(
                            "Selected cache did not expose a stable FileStore identity"));
            return new OsrsCacheHealth(
                    normalized,
                    revision,
                    store.backendName(),
                    identity.fingerprint(),
                    configArchiveCount,
                    mapArchiveCount);
        }
    }
}
