package com.rspsi.cache.map;

import com.rspsi.cache.OsrsCacheMetadata;
import com.rspsi.cache.CacheStoreCapabilities;
import com.rspsi.cache.CacheWriteMode;
import com.rspsi.cache.store.CacheStore;
import com.rspsi.editor.model.WorldRegion;
import com.rspsi.project.ProjectCompatibility;
import com.rspsi.project.ProjectMetadata;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayList;

/**
 * Opens an OSRS project against a selected cache with an explicit identity
 * decision. Mismatched or unidentified caches remain inspectable but produce
 * read-only sessions until migration support exists.
 */
public final class OsrsProjectSessionLoader {
    private final CacheStore store;
    private final CacheStore identityStore;
    private final OsrsSessionLoader sessions;
    private final ProjectMetadata project;
    private final int revision;

    public OsrsProjectSessionLoader(CacheStore store, OsrsMapService maps,
                                    ProjectMetadata project) {
        this(store, store, maps, project);
    }

    /**
     * Creates a loader with a separate identity source for staged/direct
     * output projects. Map reads and writes use {@code store}; compatibility
     * is assessed against the stable source cache represented by
     * {@code identityStore}.
     */
    public OsrsProjectSessionLoader(CacheStore store, CacheStore identityStore,
                                    OsrsMapService maps, ProjectMetadata project) {
        this.store = Objects.requireNonNull(store, "store");
        this.identityStore = Objects.requireNonNull(identityStore, "identityStore");
        this.sessions = new OsrsSessionLoader(Objects.requireNonNull(maps, "maps"));
        this.project = Objects.requireNonNull(project, "project");
        this.revision = project.cacheRevision();
    }

    public OpenedProject load(int regionX, int regionY) {
        CompatibilityState state = compatibilityState();
        OsrsSessionLoader.LoadedRegion region = state.compatibility().readOnly()
                ? sessions.loadReadOnly(regionX, regionY)
                : sessions.load(regionX, regionY);
        return new OpenedProject(project, state.cache(), state.compatibility(),
                store.capabilities(), region);
    }

    /**
     * Opens a bounded multi-region authoring window using the same project/cache
     * compatibility decision as the single-region loader.
     */
    public OpenedWorldWindow loadWindow(int minRegionX, int minRegionY,
                                        int regionWidth, int regionHeight) {
        CompatibilityState state = compatibilityState();
        OsrsSessionLoader.LoadedWindow window = state.compatibility().readOnly()
                ? sessions.loadWindowReadOnly(minRegionX, minRegionY, regionWidth, regionHeight)
                : sessions.loadWindow(minRegionX, minRegionY, regionWidth, regionHeight);
        return new OpenedWorldWindow(project, state.cache(), state.compatibility(),
                store.capabilities(), window);
    }

    private CompatibilityState compatibilityState() {
        Optional<OsrsCacheMetadata> cache = identityStore.metadata(revision);
        ProjectCompatibility compatibility = cache
                .map(value -> ProjectCompatibility.assess(project, value))
                .orElseGet(() -> new ProjectCompatibility(true,
                        List.of("cache identity is unavailable")));
        if (!store.capabilities().writable()) {
            ArrayList<String> issues = new ArrayList<>(compatibility.issues());
            issues.add("cache backend is read-only");
            compatibility = new ProjectCompatibility(true, issues);
        }
        return new CompatibilityState(cache, compatibility);
    }

    private record CompatibilityState(
            Optional<OsrsCacheMetadata> cache,
            ProjectCompatibility compatibility
    ) {
        private CompatibilityState {
            cache = Objects.requireNonNull(cache, "cache");
            compatibility = Objects.requireNonNull(compatibility, "compatibility");
        }
    }

    public record OpenedWorldWindow(
            ProjectMetadata project,
            Optional<OsrsCacheMetadata> cache,
            ProjectCompatibility compatibility,
            CacheStoreCapabilities capabilities,
            OsrsSessionLoader.LoadedWindow window
    ) {
        public OpenedWorldWindow {
            project = Objects.requireNonNull(project, "project");
            cache = Objects.requireNonNull(cache, "cache");
            compatibility = Objects.requireNonNull(compatibility, "compatibility");
            capabilities = Objects.requireNonNull(capabilities, "capabilities");
            window = Objects.requireNonNull(window, "window");
        }

        public boolean readOnly() {
            return compatibility.readOnly();
        }

        public CacheWriteMode writeMode() {
            return readOnly() ? CacheWriteMode.READ_ONLY : capabilities.writeMode();
        }
    }

    public record OpenedProject(
            ProjectMetadata project,
            Optional<OsrsCacheMetadata> cache,
            ProjectCompatibility compatibility,
            CacheStoreCapabilities capabilities,
            OsrsSessionLoader.LoadedRegion region
    ) {
        public OpenedProject {
            project = Objects.requireNonNull(project, "project");
            cache = Objects.requireNonNull(cache, "cache");
            compatibility = Objects.requireNonNull(compatibility, "compatibility");
            capabilities = Objects.requireNonNull(capabilities, "capabilities");
            region = Objects.requireNonNull(region, "region");
        }

        public WorldRegion worldRegion() {
            return new WorldRegion(region.regionX(), region.regionY(), region.session().world());
        }

        public boolean readOnly() {
            return compatibility.readOnly();
        }

        /** Explains whether an editable session writes directly or via staging. */
        public CacheWriteMode writeMode() {
            return readOnly() ? CacheWriteMode.READ_ONLY : capabilities.writeMode();
        }
    }
}
