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
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.io.SessionAutosaveCoordinator;
import com.rspsi.editor.model.WorldRegionWindow;
import com.rspsi.project.ProjectMetadata;
import com.rspsi.project.ProjectLayout;

import java.io.IOException;
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
    private final CacheStore identityStore;
    private final AutoCloseable definitionStore;
    private final ProjectMetadata project;
    private final OsrsMapService maps;
    private final OsrsProjectSessionLoader sessions;
    private final DefinitionProvider definitions;
    private final AssetRepository assets;
    private final CacheDecoderSummary decoderSummary;
    private boolean closed;

    /**
     * Captures the selected cache identity into a new project layout. This
     * does not copy or modify cache data.
     */
    public static ProjectMetadata initializeProject(ProjectLayout layout, Path cachePath,
                                                    int revision) throws IOException {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(cachePath, "cachePath");
        if (revision <= 0) throw new IllegalArgumentException("OSRS revision must be positive");
        try (OpenRuneCacheStore cache = CacheStoreFactory.openOsrs(cachePath)) {
            OsrsCacheMetadata identity = cache.metadata(revision)
                    .orElseThrow(() -> new IOException("Selected cache did not expose an identity"));
            ProjectMetadata metadata = ProjectMetadata.forCache(identity);
            layout.initialize(metadata);
            return metadata;
        }
    }

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
        this(store, store, null, maps, definitions, assets, project);
    }

    private OsrsStudioProject(CacheStore store, AutoCloseable definitionStore,
                              OsrsMapService maps, DefinitionProvider definitions, AssetRepository assets,
                              ProjectMetadata project) {
        this(store, store, definitionStore, maps, definitions, assets, project);
    }

    private OsrsStudioProject(CacheStore store, CacheStore identityStore,
                              AutoCloseable definitionStore, OsrsMapService maps,
                              DefinitionProvider definitions, AssetRepository assets,
                              ProjectMetadata project) {
        this.store = Objects.requireNonNull(store, "store");
        this.identityStore = Objects.requireNonNull(identityStore, "identityStore");
        this.definitionStore = definitionStore;
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.project = Objects.requireNonNull(project, "project");
        this.maps = Objects.requireNonNull(maps, "maps");
        this.sessions = new OsrsProjectSessionLoader(store, identityStore, maps, project);
        this.decoderSummary = definitionStore instanceof CacheStore defStore
                ? defStore.decoderSummary(project.cacheRevision(), definitions)
                : store.decoderSummary(project.cacheRevision(), definitions);
    }

    private OsrsStudioProject(CacheStore store, AutoCloseable definitionStore,
                              DefinitionProvider definitions, AssetRepository assets,
                              ProjectMetadata project) {
        this(store, store, definitionStore, new OsrsMapService(store, project.cacheRevision()),
                definitions, assets, project);
    }

    /** Opens an OpenRune-backed read-only project over an existing cache. */
    public static OsrsStudioProject openReadOnly(Path cachePath, ProjectMetadata project) {
        Objects.requireNonNull(cachePath, "cachePath");
        Objects.requireNonNull(project, "project");
        OpenRuneCacheStore base = CacheStoreFactory.openOsrs(cachePath);
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

    /** Opens a read-only OSRS project from its persisted metadata file. */
    public static OsrsStudioProject openReadOnly(ProjectLayout layout, Path cachePath)
            throws IOException {
        Objects.requireNonNull(layout, "layout");
        return openReadOnly(cachePath, layout.readMetadata());
    }

    /**
     * Opens an OpenRune source cache with an explicit writable OpenRune output
     * cache. The two paths must differ; this method never turns the selected
     * source cache into an implicit edit target.
     */
    public static OsrsStudioProject openWithOpenRuneOutput(Path basePath, Path outputPath,
                                                            ProjectMetadata project) {
        Objects.requireNonNull(basePath, "basePath");
        Objects.requireNonNull(outputPath, "outputPath");
        Objects.requireNonNull(project, "project");
        if (basePath.toAbsolutePath().normalize().equals(outputPath.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("OSRS base and output cache paths must differ");
        }
        OpenRuneCacheStore definitionsBase = CacheStoreFactory.openOsrs(basePath);
        CacheStore outputStore = null;
        try {
            outputStore = CacheStoreFactory.openRuneWritable(outputPath);
            DefinitionProvider definitions = definitionsBase.definitionProvider(project.cacheRevision());
            AssetRepository assets = new DefinitionAssetRepository(definitions,
                    definitionsBase.symbolicNameProvider());
            return new OsrsStudioProject(outputStore, definitionsBase, definitionsBase,
                    new OsrsMapService(outputStore, project.cacheRevision()),
                    definitions, assets, project);
        } catch (RuntimeException exception) {
            closeQuietly(outputStore);
            definitionsBase.close();
            throw exception;
        }
    }

    /** Opens persisted project metadata with the explicit OpenRune output path. */
    public static OsrsStudioProject openWithOpenRuneOutput(ProjectLayout layout,
                                                            Path basePath, Path outputPath)
            throws IOException {
        Objects.requireNonNull(layout, "layout");
        return openWithOpenRuneOutput(basePath, outputPath, layout.readMetadata());
    }

    public ProjectMetadata project() {
        return project;
    }

    public CacheStoreCapabilities capabilities() {
        return store.capabilities();
    }

    public String backendName() {
        return store.backendName();
    }

    public Optional<OsrsCacheMetadata> cacheIdentity() {
        return identityStore.metadata(project.cacheRevision());
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

    public CacheDecoderSummary decoderSummary() {
        return decoderSummary;
    }

    /** Opens one 64x64 OSRS region into a canonical editor session. */
    public OsrsProjectSessionLoader.OpenedProject openRegion(int regionX, int regionY) {
        ensureOpen();
        return sessions.load(regionX, regionY);
    }

    /**
     * Attaches project-scoped recovery snapshots to a loaded editor session.
     * The metadata check prevents a coordinator from writing recovery data
     * under a different project identity by accident.
     */
    public SessionAutosaveCoordinator attachAutosave(ProjectLayout layout,
                                                       EditorSession session)
            throws IOException {
        ensureOpen();
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(session, "session");
        if (!project.equals(layout.readMetadata())) {
            throw new IOException("Project metadata does not match the opened OSRS project");
        }
        return new SessionAutosaveCoordinator(layout, project, session);
    }

    /**
     * Loads a bounded scene context around one or more regions. Missing
     * regions remain explicit holes, and shared terrain borders are stitched
     * before the window is handed to scene/render consumers.
     */
    public WorldRegionWindow openWindow(int minRegionX, int minRegionY,
                                        int regionWidth, int regionHeight) {
        ensureOpen();
        WorldRegionWindow window = maps.loadWindow(minRegionX, minRegionY,
                regionWidth, regionHeight);
        window.stitchSharedEdges();
        return window;
    }

    /** Loads a square context centered on a region, clamped to OSRS bounds. */
    public WorldRegionWindow openWindowAround(int centerRegionX, int centerRegionY,
                                              int radius) {
        if (centerRegionX < 0 || centerRegionX > 255
                || centerRegionY < 0 || centerRegionY > 255) {
            throw new IllegalArgumentException("OSRS region coordinates must be in [0, 255]");
        }
        if (radius < 0 || radius > 255) {
            throw new IllegalArgumentException("Window radius must be in [0, 255]");
        }
        int minX = Math.max(0, centerRegionX - radius);
        int minY = Math.max(0, centerRegionY - radius);
        int maxX = Math.min(255, centerRegionX + radius);
        int maxY = Math.min(255, centerRegionY + radius);
        return openWindow(minX, minY, maxX - minX + 1, maxY - minY + 1);
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
