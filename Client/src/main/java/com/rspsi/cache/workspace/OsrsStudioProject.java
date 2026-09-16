package com.rspsi.cache.workspace;

import com.rspsi.cache.CacheStoreCapabilities;
import com.rspsi.cache.OsrsCacheMetadata;
import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.map.OsrsMapService;
import com.rspsi.cache.map.OsrsProjectSessionLoader;
import com.rspsi.cache.store.CacheStore;
import com.rspsi.cache.store.CacheStoreFactory;
import com.rspsi.cache.store.OpenRuneCacheStore;
import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.assets.DefinitionAssetRepository;
import com.rspsi.project.ProjectMetadata;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Small OSRS project composition root for the editor.
 *
 * <p>This class is the lifecycle seam between an OSRS cache and a frontend.
 * It owns cache/map/definition services and exposes only RSPSi contracts to
 * callers. The read-only OpenRune base may be paired with a distinct staged
 * output cache; the source cache is never used as the output target.</p>
 */
public final class OsrsStudioProject implements AutoCloseable {
    private final CacheStore store;
    private final AutoCloseable definitionStore;
    private final ProjectMetadata project;
    private final OsrsMapService maps;
    private final OsrsProjectSessionLoader sessions;
    private final DefinitionProvider definitions;
    private final AssetRepository assets;
    private boolean closed;

    /**
     * Creates a project over already-created neutral services. This overload
     * is useful for tests and for future backends that are not OpenRune.
     */
    public OsrsStudioProject(CacheStore store, DefinitionProvider definitions,
                             ProjectMetadata project) {
        this(store, new OsrsMapService(store, project.cacheRevision()), definitions,
                new DefinitionAssetRepository(definitions), project);
    }

    /** Creates a project with an explicitly supplied neutral asset repository. */
    public OsrsStudioProject(CacheStore store, DefinitionProvider definitions,
                             AssetRepository assets, ProjectMetadata project) {
        this(store, new OsrsMapService(store, project.cacheRevision()), definitions, assets, project);
    }

    /** Creates a project over an explicitly configured neutral map service. */
    public OsrsStudioProject(CacheStore store, OsrsMapService maps,
                             DefinitionProvider definitions, ProjectMetadata project) {
        this(store, maps, definitions, new DefinitionAssetRepository(definitions), project);
    }

    /** Creates a project over an explicitly configured neutral map service. */
    public OsrsStudioProject(CacheStore store, OsrsMapService maps,
                             DefinitionProvider definitions, AssetRepository assets,
                             ProjectMetadata project) {
        this(store, null, maps, definitions, assets, project);
    }

    private OsrsStudioProject(CacheStore store, AutoCloseable definitionStore,
                              OsrsMapService maps, DefinitionProvider definitions, AssetRepository assets,
                              ProjectMetadata project) {
        this.store = Objects.requireNonNull(store, "store");
        this.definitionStore = definitionStore;
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.project = Objects.requireNonNull(project, "project");
        this.maps = Objects.requireNonNull(maps, "maps");
        this.sessions = new OsrsProjectSessionLoader(store, maps, project);
    }

    private OsrsStudioProject(CacheStore store, AutoCloseable definitionStore,
                              DefinitionProvider definitions, AssetRepository assets,
                              ProjectMetadata project) {
        this(store, definitionStore, new OsrsMapService(store, project.cacheRevision()),
                definitions, assets, project);
    }

    /** Opens an OpenRune-backed read-only project over an existing cache. */
    public static OsrsStudioProject openReadOnly(Path cachePath, ProjectMetadata project) {
        Objects.requireNonNull(cachePath, "cachePath");
        Objects.requireNonNull(project, "project");
        OpenRuneCacheStore base = OpenRuneCacheStore.open(cachePath);
        try {
            DefinitionProvider definitions = base.definitionProvider(project.cacheRevision());
            AssetRepository assets = new DefinitionAssetRepository(definitions,
                    base.symbolicNameProvider());
            return new OsrsStudioProject(base, base,
                    new OsrsMapService(base, project.cacheRevision()),
                    definitions, assets, project);
        } catch (RuntimeException exception) {
            base.close();
            throw exception;
        }
    }

    /**
     * Opens an OpenRune source cache and a distinct Displee output cache.
     * OpenRune remains the source/definition reader until a writable OpenRune
     * packer has passed the parity gate.
     */
    public static OsrsStudioProject openWithDispleeOutput(Path basePath, Path outputPath,
                                                           ProjectMetadata project) {
        Objects.requireNonNull(basePath, "basePath");
        Objects.requireNonNull(outputPath, "outputPath");
        Objects.requireNonNull(project, "project");
        OpenRuneCacheStore definitionsBase = OpenRuneCacheStore.open(basePath);
        CacheStore outputStore = null;
        try {
            outputStore = CacheStoreFactory.openRuneWithDispleeOutput(basePath, outputPath);
            DefinitionProvider definitions = definitionsBase.definitionProvider(project.cacheRevision());
            AssetRepository assets = new DefinitionAssetRepository(definitions,
                    definitionsBase.symbolicNameProvider());
            return new OsrsStudioProject(outputStore, definitionsBase,
                    new OsrsMapService(outputStore, project.cacheRevision()),
                    definitions, assets, project);
        } catch (RuntimeException exception) {
            closeQuietly(outputStore);
            definitionsBase.close();
            throw exception;
        }
    }

    public ProjectMetadata project() {
        return project;
    }

    public CacheStoreCapabilities capabilities() {
        return store.capabilities();
    }

    public Optional<OsrsCacheMetadata> cacheIdentity() {
        return store.metadata(project.cacheRevision());
    }

    public DefinitionProvider definitions() {
        return definitions;
    }

    public AssetRepository assets() {
        return assets;
    }

    public OsrsMapService maps() {
        return maps;
    }

    /** Opens one 64x64 OSRS region into a canonical editor session. */
    public OsrsProjectSessionLoader.OpenedProject openRegion(int regionX, int regionY) {
        ensureOpen();
        return sessions.load(regionX, regionY);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        RuntimeException failure = null;
        try {
            store.close();
        } catch (RuntimeException exception) {
            failure = exception;
        }
        if (definitionStore != null && definitionStore != store) {
            try {
                definitionStore.close();
            } catch (Exception exception) {
                if (failure == null) {
                    failure = exception instanceof RuntimeException runtime
                            ? runtime
                            : new IllegalStateException("Failed to close OSRS definition cache", exception);
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) throw failure;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("OSRS project is closed");
    }

    private static void closeQuietly(AutoCloseable resource) {
        if (resource == null) return;
        try {
            resource.close();
        } catch (Exception ignored) {
            // Preserve the original construction failure.
        }
    }
}
