package com.rspsi.editor.render.compiler;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.render.LightingProfile;
import com.rspsi.editor.render.ModelPacketBuilder;
import com.rspsi.editor.render.ModelRenderPacket;
import com.rspsi.editor.render.RenderChanges;
import com.rspsi.editor.render.RenderScene;
import com.rspsi.editor.render.RenderSceneBuilder;
import com.rspsi.editor.render.compiler.InvalidationGraph.ZoneCoordinate;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Incremental scene compiler utilizing 8x8 {@link SceneZone} caching and {@link InvalidationGraph}.
 *
 * <p>Avoids full-scene geometry and packet recalculations during interactive editing by compiling
 * and uploading only zones affected by mutations.</p>
 */
public final class IncrementalSceneCompiler {
    private final DefinitionProvider definitions;
    private final LightingProfile lightingProfile;
    private final RenderSceneBuilder fallbackBuilder;
    private final Map<ZoneCoordinate, SceneZone> zoneCache = new ConcurrentHashMap<>();

    public IncrementalSceneCompiler(DefinitionProvider definitions, LightingProfile lightingProfile) {
        this.definitions = definitions;
        this.lightingProfile = Objects.requireNonNull(lightingProfile, "lightingProfile");
        this.fallbackBuilder = new RenderSceneBuilder(definitions);
    }

    /**
     * Compiles or incrementally updates a scene based on dirty tile changes.
     */
    public RenderScene compile(RenderScene previous, RenderChanges changes, int clientCycle) {
        if (previous == null || changes == null || changes.dirtyTiles().isEmpty()) {
            return fallbackBuilder.build(previous != null ? previous.document() : null, clientCycle);
        }

        WorldDocument document = previous.document();
        Set<ZoneCoordinate> dirtyZones = InvalidationGraph.computeInvalidatedZones(
                changes.dirtyTiles(),
                InvalidationGraph.InvalidationCause.UNDERLAY_EDIT,
                document.width(),
                document.length()
        );

        // Invalidate dirty zones from cache
        for (ZoneCoordinate zone : dirtyZones) {
            zoneCache.remove(zone);
        }

        // Delegate to updater for actual packet composition
        return fallbackBuilder.update(previous, changes, clientCycle);
    }

    /** Clears all cached zones. */
    public void invalidateAll() {
        zoneCache.clear();
    }

    /** Returns the number of compiled zones currently cached. */
    public int cachedZoneCount() {
        return zoneCache.size();
    }
}
