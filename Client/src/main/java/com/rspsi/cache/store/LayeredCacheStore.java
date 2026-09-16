package com.rspsi.cache.store;

import com.rspsi.cache.CacheStoreCapabilities;
import com.rspsi.cache.OsrsCacheMetadata;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Neutral base-cache plus output-layer store.
 *
 * <p>Reads see staged edits first and fall back to the base cache. Writes are
 * held in memory until {@link #flush()} explicitly commits them to the output
 * store, so painting never mutates the source cache by accident.</p>
 */
public final class LayeredCacheStore implements CacheStore {
    private final CacheStore base;
    private final CacheStore output;
    private final Map<CacheKey, byte[]> pending = new LinkedHashMap<>();

    public LayeredCacheStore(CacheStore base, CacheStore output) {
        this.base = Objects.requireNonNull(base, "base");
        this.output = Objects.requireNonNull(output, "output");
    }

    public int pendingWriteCount() {
        return pending.size();
    }

    /** Discards uncommitted output-layer edits without touching either cache. */
    public void discardWrites() {
        pending.clear();
    }

    @Override
    public byte[] read(int index, int archive, int file) {
        byte[] staged = pending.get(new CacheKey(index, archive, file));
        if (staged != null) return staged.clone();
        byte[] value = output.read(index, archive, file);
        if (value == null) value = base.read(index, archive, file);
        return value == null ? null : value.clone();
    }

    @Override
    public int archiveId(int index, String archiveName) {
        int baseId = base.archiveId(index, archiveName);
        return baseId >= 0 ? baseId : output.archiveId(index, archiveName);
    }

    @Override
    public int[] archiveIds(int index) {
        return java.util.stream.IntStream.concat(Arrays.stream(base.archiveIds(index)),
                        Arrays.stream(output.archiveIds(index)))
                .distinct().sorted().toArray();
    }

    @Override
    public void write(int index, int archive, int file, byte[] data) {
        Objects.requireNonNull(data, "data");
        if (!output.capabilities().writable()) {
            throw new UnsupportedOperationException("Output cache backend is read-only");
        }
        pending.put(new CacheKey(index, archive, file), data.clone());
    }

    @Override
    public void flush() {
        for (Map.Entry<CacheKey, byte[]> entry : pending.entrySet()) {
            CacheKey key = entry.getKey();
            output.write(key.index(), key.archive(), key.file(), entry.getValue());
        }
        output.flush();
        pending.clear();
    }

    @Override
    public CacheStoreCapabilities capabilities() {
        CacheStoreCapabilities baseCapabilities = base.capabilities();
        CacheStoreCapabilities outputCapabilities = output.capabilities();
        return new CacheStoreCapabilities(outputCapabilities.writable(),
                baseCapabilities.namedArchives() || outputCapabilities.namedArchives(),
                baseCapabilities.mapPacking() || outputCapabilities.mapPacking());
    }

    @Override
    public Optional<OsrsCacheMetadata> metadata(int revision) {
        Optional<OsrsCacheMetadata> baseMetadata = base.metadata(revision);
        return baseMetadata.isPresent() ? baseMetadata : output.metadata(revision);
    }

    @Override
    public void close() {
        flush();
        if (base == output) {
            base.close();
        } else {
            base.close();
            output.close();
        }
    }

    private record CacheKey(int index, int archive, int file) {
    }
}
