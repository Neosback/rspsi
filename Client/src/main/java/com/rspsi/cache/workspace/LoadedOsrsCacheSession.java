package com.rspsi.cache.workspace;

import com.rspsi.cache.OsrsCacheMetadata;
import com.rspsi.cache.map.OsrsProjectSessionLoader;

import java.nio.file.Path;
import java.util.Objects;

/** A validated, read-only OpenRune cache prepared for workspace consumers. */
public final class LoadedOsrsCacheSession implements AutoCloseable {
    private final Path path;
    private final OsrsBundle bundle;
    private final OsrsCacheMetadata identity;
    private final String backendName;
    private final int mapCount;

    private LoadedOsrsCacheSession(Path path, OsrsBundle bundle) {
        this.path = Objects.requireNonNull(path, "path");
        this.bundle = Objects.requireNonNull(bundle, "bundle");
        this.identity = bundle.project().cacheIdentity().orElseThrow(
                () -> new IllegalArgumentException("Selected cache has no identity"));
        this.backendName = bundle.backendName();
        this.mapCount = bundle.mapCount();
    }

    public static LoadedOsrsCacheSession open(Path path) {
        Objects.requireNonNull(path, "path");
        Path normalized = path.toAbsolutePath().normalize();
        if (!java.nio.file.Files.isDirectory(normalized)) {
            throw new IllegalArgumentException("Cache directory does not exist: " + normalized);
        }
        OsrsBundle bundle = OsrsBundle.openReadOnly(normalized);
        try {
            return new LoadedOsrsCacheSession(normalized, bundle);
        } catch (RuntimeException failure) {
            bundle.close();
            throw failure;
        }
    }

    public Path path() { return path; }

    public OsrsBundle bundle() { return bundle; }

    public OsrsCacheMetadata identity() { return identity; }

    public String backendName() { return backendName; }

    public int mapCount() { return mapCount; }

    public OsrsProjectSessionLoader.OpenedProject openRegion(int regionX, int regionY) {
        return bundle.openRegion(regionX, regionY);
    }

    @Override
    public void close() {
        bundle.close();
    }
}
