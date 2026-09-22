package com.rspsi.cache.store;

import dev.openrune.filesystem.Cache;
import dev.openrune.cache.CacheDelegate;
import com.rspsi.cache.CacheStoreCapabilities;
import com.rspsi.cache.CacheWriteMode;
import com.rspsi.cache.OsrsCacheMetadata;
import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.assets.DefinitionAssetRepository;
import com.rspsi.editor.assets.SymbolicNameProvider;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/**
 * OpenRune FileStore compatibility adapter.
 *
 * The normal {@link #open(Path)} path intentionally supports reads only. The
 * OpenRune file-backed implementation is read-only; direct writes require the
 * explicit {@link #openWritable(Path)} output-cache path.
 */
public final class OpenRuneCacheStore implements CacheStore {

    /** Keep the runtime identity visible in diagnostics alongside the Gradle pin. */
    public static final String FILESTORE_VERSION = "3.0.2";
    /**
     * FileStore intentionally exposes bytes and archive structure, not the
     * game build number. This is the audited decoder profile used by the
     * current OpenRune editor cache fixtures; callers can override it with
     * RSPSI_OSRS_REVISION when working with another supported profile.
     */
    public static final int DEFAULT_OSRS_REVISION = 240;

    private final Cache cache;
    private final boolean writable;

    OpenRuneCacheStore(Cache cache) {
        this(cache, false);
    }

    OpenRuneCacheStore(Cache cache, boolean writable) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.writable = writable;
    }

    public static OpenRuneCacheStore open(Path path) {
        Objects.requireNonNull(path, "path");
        return new OpenRuneCacheStore(Cache.Companion.load(path));
    }

    /**
     * Validates an OpenRune cache and selects the decoder profile before a
     * project is opened. Do not use Displee's {@code isOSRS()} heuristic here:
     * it is a legacy format classifier and rejects valid OpenRune-produced
     * caches with newer index layouts. FileStore itself is the authority for
     * opening and inspecting this cache.
     */
    public static int detectRevision(Path path) {
        Objects.requireNonNull(path, "path");
        Path normalized = path.toAbsolutePath().normalize();
        Cache cache = Cache.Companion.load(normalized);
        try {
            int[] indices = cache.indices();
            if (!contains(indices, com.rspsi.cache.OsrsCacheIndexLayout.CONFIGS)
                    || !contains(indices, com.rspsi.cache.OsrsCacheIndexLayout.MAPS)
                    || cache.archives(com.rspsi.cache.OsrsCacheIndexLayout.CONFIGS).length == 0
                    || cache.archives(com.rspsi.cache.OsrsCacheIndexLayout.MAPS).length == 0) {
                throw new IllegalArgumentException(
                        "Selected directory does not contain OpenRune OSRS config and map indices");
            }
            return configuredRevision();
        } finally {
            cache.close();
        }
    }

    private static boolean contains(int[] values, int expected) {
        for (int value : values) if (value == expected) return true;
        return false;
    }

    private static int configuredRevision() {
        String configured = System.getProperty("rspsi.osrs.revision");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("RSPSI_OSRS_REVISION");
        }
        if (configured != null && !configured.isBlank()) {
            try {
                int revision = Integer.parseInt(configured.trim());
                if (revision > 0) return revision;
            } catch (NumberFormatException ignored) {
                // Fall back to the audited OpenRune profile below.
            }
        }
        return DEFAULT_OSRS_REVISION;
    }

    /**
     * Opens a writable OpenRune FileStore facade over an explicit output
     * cache. The source/read-only {@link #open(Path)} path remains unchanged;
     * callers must opt into this method when they intentionally want direct
     * output-cache writes.
     */
    public static OpenRuneCacheStore openWritable(Path path) {
        Objects.requireNonNull(path, "path");
        return new OpenRuneCacheStore(new CacheDelegate(path.toString()), true);
    }

    /** Loads OpenRune definitions and immediately reduces them to RSPSi views. */
    public DefinitionProvider definitionProvider(int revision) {
        return OpenRuneDefinitionProvider.load(cache, revision);
    }

    /**
     * Returns an editor-neutral view over mappings loaded into OpenRune's
     * RSCM/GameVal provider. Mapping files are optional and are not loaded as
     * a side effect of opening a cache.
     */
    public SymbolicNameProvider symbolicNameProvider() {
        return symbolicNameProvider(configuredRevision());
    }

    /** Builds a cache-aware symbolic provider for the selected revision. */
    public SymbolicNameProvider symbolicNameProvider(int revision) {
        return new OpenRuneSymbolicNameProvider(cache, revision);
    }

    /** Builds the neutral asset-browser repository for this OpenRune cache. */
    public AssetRepository assetRepository(int revision) {
        return new DefinitionAssetRepository(definitionProvider(revision), symbolicNameProvider(revision));
    }

    /**
     * Returns a stable identity derived from canonical index IDs and CRCs.
     *
     * <p>OpenRune's read-only {@code FileCache} and writable {@code CacheDelegate}
     * expose different raw version-table encodings. Index IDs and reference
     * table CRCs are the common representation, so using them avoids falsely
     * treating the same cache as a different project when switching adapters.</p>
     */
    @Override
    public java.util.Optional<OsrsCacheMetadata> metadata(int revision) {
        if (revision <= 0) {
            throw new IllegalArgumentException("OSRS cache revision must be positive");
        }
        return java.util.Optional.of(new OsrsCacheMetadata(revision, null,
                fingerprint(cache)));
    }

    @Override
    public byte[] read(int index, int archive, int file) {
        byte[] data = cache.data(index, archive, file, null);
        return data == null ? null : data.clone();
    }

    @Override
    public int archiveId(int index, String archiveName) {
        Objects.requireNonNull(archiveName, "archiveName");
        return cache.archiveId(index, archiveName);
    }

    @Override
    public int[] archiveIds(int index) {
        return cache.archives(index).clone();
    }

    @Override
    public int[] fileIds(int index, int archive) {
        return cache.files(index, archive).clone();
    }

    @Override
    public void write(int index, int archive, int file, byte[] data) {
        Objects.requireNonNull(data, "data");
        if (!writable) {
            throw new UnsupportedOperationException(
                    "OpenRune compatibility store is read-only; use openWritable for an explicit output cache");
        }
        cache.write(index, archive, file, data.clone(), null);
    }

    @Override
    public void flush() {
        if (writable) {
            cache.update();
        }
    }

    @Override
    public CacheStoreCapabilities capabilities() {
        return writable
                ? new CacheStoreCapabilities(true, true, true, CacheWriteMode.DIRECT)
                : new CacheStoreCapabilities(false, true, false, CacheWriteMode.READ_ONLY);
    }

    @Override
    public String backendName() {
        return "OpenRune FileStore " + FILESTORE_VERSION
                + (writable ? " (CacheDelegate output)" : " (read-only)");
    }

    @Override
    public com.rspsi.cache.workspace.CacheDecoderSummary decoderSummary(int revision,
                                                                         DefinitionProvider definitions) {
        return OpenRuneCacheInspector.inspect(cache, revision, backendName(), definitions);
    }

    @Override
    public void close() {
        if (writable) {
            cache.update();
        }
        cache.close();
    }

    private static String fingerprint(Cache cache) {
        try {
            int[] indices = Arrays.stream(cache.indices())
                    .filter(index -> cache.archives(index).length > 0)
                    .toArray();
            Arrays.sort(indices);
            byte[] versionTable = cache.getVersionTable();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            ByteBuffer entry = ByteBuffer.allocate(Integer.BYTES * 2);
            for (int index : indices) {
                entry.clear();
                entry.putInt(index).putInt(canonicalCrc(cache, versionTable, index)).flip();
                digest.update(entry);
            }
            byte[] hash = digest.digest();
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte value : hash) result.append(String.format("%02x", value & 0xFF));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide SHA-256", exception);
        }
    }

    private static int canonicalCrc(Cache cache, byte[] versionTable, int index) {
        // FileCache exposes the raw reference-table bytes but its crc(index)
        // method is intentionally unimplemented. CacheDelegate exposes the
        // same value through its Displee delegate, so keep this conversion
        // local to the adapter and never leak either representation outward.
        int offset = 5 + index * 8;
        if (!(cache instanceof CacheDelegate)
                && index >= 0 && offset >= 5 && offset + Integer.BYTES <= versionTable.length) {
            return ByteBuffer.wrap(versionTable, offset, Integer.BYTES).getInt();
        }
        return cache.crc(index);
    }
}
