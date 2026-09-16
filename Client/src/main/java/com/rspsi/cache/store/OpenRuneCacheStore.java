package com.rspsi.cache.store;

import dev.openrune.filesystem.Cache;
import com.rspsi.cache.CacheStoreCapabilities;
import com.rspsi.cache.OsrsCacheMetadata;
import com.rspsi.cache.definition.DefinitionProvider;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * OpenRune FileStore compatibility adapter.
 *
 * The first spike intentionally supports reads only. The OpenRune file-backed
 * implementation is read-only, so silently pretending that writes succeeded
 * would risk corrupting edited maps.
 */
public final class OpenRuneCacheStore implements CacheStore {

    private final Cache cache;

    OpenRuneCacheStore(Cache cache) {
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    public static OpenRuneCacheStore open(Path path) {
        Objects.requireNonNull(path, "path");
        return new OpenRuneCacheStore(Cache.Companion.load(path));
    }

    /** Loads OpenRune definitions and immediately reduces them to RSPSi views. */
    public DefinitionProvider definitionProvider(int revision) {
        return OpenRuneDefinitionProvider.load(cache, revision);
    }

    /** Returns a stable identity derived from the cache's reference-table versions. */
    public OsrsCacheMetadata metadata(int revision) {
        if (revision <= 0) {
            throw new IllegalArgumentException("OSRS cache revision must be positive");
        }
        return new OsrsCacheMetadata(revision, null, fingerprint(cache.getVersionTable()));
    }

    @Override
    public byte[] read(int index, int archive, int file) {
        return cache.data(index, archive, file, null);
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
    public void write(int index, int archive, int file, byte[] data) {
        throw new UnsupportedOperationException(
                "OpenRune compatibility store is read-only until writable packing is validated");
    }

    @Override
    public void flush() {
        // No writes are accepted by this read-only spike.
    }

    @Override
    public CacheStoreCapabilities capabilities() {
        return new CacheStoreCapabilities(false, true, false);
    }

    @Override
    public void close() {
        cache.close();
    }

    private static String fingerprint(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes.clone());
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format("%02x", value & 0xFF));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide SHA-256", exception);
        }
    }
}
