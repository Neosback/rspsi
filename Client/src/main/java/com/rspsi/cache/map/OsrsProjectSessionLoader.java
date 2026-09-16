package com.rspsi.cache.map;

import com.rspsi.cache.OsrsCacheMetadata;
import com.rspsi.cache.store.CacheStore;
import com.rspsi.editor.model.WorldRegion;
import com.rspsi.project.ProjectCompatibility;
import com.rspsi.project.ProjectMetadata;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Opens an OSRS project against a selected cache with an explicit identity
 * decision. Mismatched or unidentified caches remain inspectable but produce
 * read-only sessions until migration support exists.
 */
public final class OsrsProjectSessionLoader {
    private final CacheStore store;
    private final OsrsSessionLoader sessions;
    private final ProjectMetadata project;
    private final int revision;

    public OsrsProjectSessionLoader(CacheStore store, OsrsMapService maps,
                                    ProjectMetadata project) {
        this.store = Objects.requireNonNull(store, "store");
        this.sessions = new OsrsSessionLoader(Objects.requireNonNull(maps, "maps"));
        this.project = Objects.requireNonNull(project, "project");
        this.revision = project.cacheRevision();
    }

    public OpenedProject load(int regionX, int regionY) {
        Optional<OsrsCacheMetadata> cache = store.metadata(revision);
        ProjectCompatibility compatibility = cache
                .map(value -> ProjectCompatibility.assess(project, value))
                .orElseGet(() -> new ProjectCompatibility(true,
                        List.of("cache identity is unavailable")));
        OsrsSessionLoader.LoadedRegion region = compatibility.readOnly()
                ? sessions.loadReadOnly(regionX, regionY)
                : sessions.load(regionX, regionY);
        return new OpenedProject(project, cache, compatibility, region);
    }

    public record OpenedProject(
            ProjectMetadata project,
            Optional<OsrsCacheMetadata> cache,
            ProjectCompatibility compatibility,
            OsrsSessionLoader.LoadedRegion region
    ) {
        public OpenedProject {
            project = Objects.requireNonNull(project, "project");
            cache = Objects.requireNonNull(cache, "cache");
            compatibility = Objects.requireNonNull(compatibility, "compatibility");
            region = Objects.requireNonNull(region, "region");
        }

        public WorldRegion worldRegion() {
            return new WorldRegion(region.regionX(), region.regionY(), region.session().world());
        }

        public boolean readOnly() {
            return compatibility.readOnly();
        }
    }
}
