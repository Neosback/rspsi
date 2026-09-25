package com.rspsi.cache.workspace;

import com.rspsi.cache.OsrsCacheMetadata;
import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.map.OsrsProjectSessionLoader;
import com.rspsi.cache.map.OsrsRevisionFeatures;
import com.rspsi.cache.map.OsrsRevisionProfile;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.io.SessionAutosaveCoordinator;
import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginHost;
import com.rspsi.editor.plugin.EditorSceneAccess;
import com.rspsi.project.ProjectMetadata;
import com.rspsi.server.ServerAdapter;
import com.rspsi.server.ServerConnection;
import com.rspsi.server.ServerProjectInspection;

import java.nio.file.Path;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * The selectable OSRS product bundle.
 *
 * <p>The bundle is the composition root for revision-aware cache access,
 * neutral definitions, project/session creation, and feature-plugin startup.
 * Feature plugins receive only the resulting neutral editor contracts; they
 * never open a cache or depend on a server checkout.</p>
 */
public final class OsrsBundle implements AutoCloseable {
    public static final String ID = "osrs";

    private final OsrsStudioProject project;
    private final OsrsRevisionProfile revisionProfile;
    private ServerAdapter serverAdapter;
    private ServerProjectInspection serverInspection;

    private OsrsBundle(OsrsStudioProject project, OsrsRevisionProfile revisionProfile,
                       ServerAdapter serverAdapter) {
        this.project = Objects.requireNonNull(project, "project");
        this.revisionProfile = Objects.requireNonNull(revisionProfile, "revisionProfile");
        this.serverAdapter = serverAdapter;
    }

    /** Opens the selected cache through the modern OSRS/FileStore path. */
    public static OsrsBundle openReadOnly(Path cachePath, ProjectMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        OsrsStudioProject project = OsrsStudioProject.openReadOnly(cachePath, metadata);
        try {
            OsrsCacheMetadata identity = project.cacheIdentity().orElseThrow(
                    () -> new IllegalArgumentException("Selected OSRS cache has no identity"));
            if (identity.revision() != metadata.cacheRevision()) {
                throw new IllegalArgumentException("Selected cache revision " + identity.revision()
                        + " does not match project revision " + metadata.cacheRevision());
            }
            return new OsrsBundle(project, OsrsRevisionProfile.forRevision(identity.revision()), null);
        } catch (RuntimeException failure) {
            project.close();
            throw failure;
        }
    }

    /** Opens a bundle from cache identity without requiring a pre-existing project file. */
    public static OsrsBundle openReadOnly(Path cachePath, int expectedRevision) {
        if (expectedRevision <= 0) {
            throw new IllegalArgumentException("expectedRevision must be positive");
        }
        try (com.rspsi.cache.store.OpenRuneCacheStore store =
                     com.rspsi.cache.store.CacheStoreFactory.openOsrs(cachePath)) {
            OsrsCacheMetadata identity = store.metadata(expectedRevision).orElseThrow(
                    () -> new IllegalArgumentException("Selected OSRS cache has no identity"));
            return openReadOnly(cachePath, ProjectMetadata.forCache(identity));
        }
    }

    /** Opens a read-only OSRS bundle using the revision advertised by the cache. */
    public static OsrsBundle openReadOnly(Path cachePath) {
        return openReadOnly(cachePath,
                com.rspsi.cache.store.OpenRuneCacheStore.detectRevision(cachePath));
    }

    /** Opens an explicit writable OpenRune output while keeping the OSRS bundle boundary. */
    public static OsrsBundle openWithOpenRuneOutput(Path basePath, Path outputPath,
                                                     ProjectMetadata metadata) {
        OsrsStudioProject project = OsrsStudioProject.openWithOpenRuneOutput(
                basePath, outputPath, metadata);
        return fromProject(project, metadata);
    }

    private static OsrsBundle fromProject(OsrsStudioProject project, ProjectMetadata metadata) {
        try {
            int revision = project.cacheIdentity().map(OsrsCacheMetadata::revision)
                    .orElse(metadata.cacheRevision());
            return new OsrsBundle(project, OsrsRevisionProfile.forRevision(revision), null);
        } catch (RuntimeException failure) {
            project.close();
            throw failure;
        }
    }

    /** Attaches optional server/project integration without changing cache ownership. */
    public OsrsBundle withServerAdapter(ServerAdapter adapter) {
        this.serverAdapter = Objects.requireNonNull(adapter, "adapter");
        this.serverInspection = null;
        return this;
    }

    /** Inspects an explicitly selected server checkout through the attached adapter. */
    public OsrsBundle withServerConnection(ServerConnection connection) {
        if (serverAdapter == null) {
            throw new IllegalStateException("Attach a server adapter before selecting a connection");
        }
        Objects.requireNonNull(connection, "connection");
        if (!serverAdapter.id().equals(connection.adapterId())) {
            throw new IllegalArgumentException("Connection adapter '" + connection.adapterId()
                    + "' does not match '" + serverAdapter.id() + "'");
        }
        serverInspection = serverAdapter.inspect(connection);
        return this;
    }

    public String id() { return ID; }

    public OsrsStudioProject project() { return project; }

    public OsrsRevisionProfile revisionProfile() { return revisionProfile; }

    public OsrsRevisionFeatures revisionFeatures() {
        return OsrsRevisionFeatures.forRevision(revisionProfile.revision());
    }

    public DefinitionProvider definitions() { return project.definitions(); }

    public AssetRepository assets() { return project.assets(); }

    public String backendName() { return project.backendName(); }

    public int mapCount() { return project.maps().index().size(); }

    public CacheDecoderSummary decoderSummary() { return project.decoderSummary(); }

    public Optional<ServerAdapter> serverAdapter() { return Optional.ofNullable(serverAdapter); }

    public Optional<ServerProjectInspection> serverInspection() {
        return Optional.ofNullable(serverInspection);
    }

    public OsrsProjectSessionLoader.OpenedProject openRegion(int regionX, int regionY) {
        return project.openRegion(regionX, regionY);
    }

    public com.rspsi.editor.model.WorldRegionWindow openWindowAround(int regionX, int regionY,
                                                                       int radius) {
        return project.openWindowAround(regionX, regionY, radius);
    }

    public SessionAutosaveCoordinator attachAutosave(com.rspsi.project.ProjectLayout layout,
                                                       EditorSession session) throws IOException {
        return project.attachAutosave(layout, session);
    }

    /**
     * Starts feature plugins only after the cache, definitions, and session
     * have been created. The returned host owns plugin lifecycle and resources.
     */
    public EditorPluginHost startFeatures(Iterable<? extends EditorPlugin> plugins,
                                          EditorSession session,
                                          EditorSceneAccess scene) {
        return EditorPluginHost.initialize(plugins, session, assets(), scene);
    }

    @Override
    public void close() {
        project.close();
    }
}
