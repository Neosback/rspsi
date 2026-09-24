package com.rspsi.cache.map;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.WorldRegionSessionWindow;
import com.rspsi.editor.model.WorldRegion;
import com.rspsi.editor.model.WorldRegionWindow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds a canonical editor session from one OSRS region.
 *
 * <p>The loader is the small composition seam between cache/map services and
 * the editor core: the returned session owns editing state and persistence,
 * while region coordinates and format-specific save behavior remain here at
 * the cache boundary.</p>
 */
public final class OsrsSessionLoader {
    private final OsrsMapService maps;
    private final OsrsRegionSaveCoordinator saves;

    public OsrsSessionLoader(OsrsMapService maps) {
        this.maps = Objects.requireNonNull(maps, "maps");
        this.saves = new OsrsRegionSaveCoordinator(maps);
    }

    /** Loads an indexed region and returns a clean, save-capable session. */
    public LoadedRegion load(int regionX, int regionY) {
        return load(regionX, regionY, true);
    }

    /** Loads an indexed region without attaching persistence. */
    public LoadedRegion loadReadOnly(int regionX, int regionY) {
        return load(regionX, regionY, false);
    }

    /** Loads a bounded world window with one canonical editor session per present region. */
    public LoadedWindow loadWindow(int minRegionX, int minRegionY,
                                   int regionWidth, int regionHeight) {
        return loadWindow(minRegionX, minRegionY, regionWidth, regionHeight, true);
    }

    /** Loads a bounded inspect-only world window while retaining missing-region holes. */
    public LoadedWindow loadWindowReadOnly(int minRegionX, int minRegionY,
                                           int regionWidth, int regionHeight) {
        return loadWindow(minRegionX, minRegionY, regionWidth, regionHeight, false);
    }

    private LoadedRegion load(int regionX, int regionY, boolean saveable) {
        WorldRegion region = maps.loadRegion(regionX, regionY)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Region is not present in the OSRS map index: " + regionX + "," + regionY));
        EditorSession session = saveable
                ? new EditorSession(region.document(), region.window(),
                        current -> saves.save(current, regionX, regionY))
                : EditorSession.readOnly(region.document(), region.window());
        session.markSaved();
        return new LoadedRegion(regionX, regionY, session);
    }

    private LoadedWindow loadWindow(int minRegionX, int minRegionY,
                                    int regionWidth, int regionHeight,
                                    boolean saveable) {
        WorldRegionWindow window =
                maps.loadWindow(minRegionX, minRegionY, regionWidth, regionHeight);
        Map<Integer, EditorSession> regionSessions = new LinkedHashMap<>();

        for (WorldRegion region : window.regions().values()) {
            EditorSession session = saveable
                    ? new EditorSession(region.document(), region.window(),
                            current -> saves.save(current, region.regionX(), region.regionY()))
                    : EditorSession.readOnly(region.document(), region.window());
            session.markSaved();
            regionSessions.put(region.regionId(), session);
        }

        WorldRegionSessionWindow authoringWindow = new WorldRegionSessionWindow(
                window,
                regionSessions,
                saveable ? current -> {
                    List<OsrsRegionSaveCoordinator.RegionSaveRequest> requests =
                            new ArrayList<>();
                    for (int regionId : current.dirtyRegionIds()) {
                        EditorSession session = current.session(regionId).orElseThrow();
                        WorldRegion region = current.window().regions().get(regionId);
                        requests.add(new OsrsRegionSaveCoordinator.RegionSaveRequest(
                                session, region.regionX(), region.regionY()));
                    }
                    if (!requests.isEmpty()) {
                        saves.saveAll(requests);
                    }
                } : null);
        return new LoadedWindow(minRegionX, minRegionY, regionWidth, regionHeight,
                authoringWindow);
    }

    public record LoadedWindow(
            int minRegionX,
            int minRegionY,
            int regionWidth,
            int regionHeight,
            WorldRegionSessionWindow sessions
    ) {
        public LoadedWindow {
            if (minRegionX < 0 || minRegionY < 0 || regionWidth <= 0 || regionHeight <= 0
                    || minRegionX + regionWidth > 256
                    || minRegionY + regionHeight > 256) {
                throw new IllegalArgumentException("Invalid OSRS region window");
            }
            sessions = Objects.requireNonNull(sessions, "sessions");
        }

        public WorldRegionWindow window() {
            return sessions.window();
        }
    }

    public record LoadedRegion(int regionX, int regionY, EditorSession session) {
        public LoadedRegion {
            if (regionX < 0 || regionX > 255 || regionY < 0 || regionY > 255) {
                throw new IllegalArgumentException("OSRS region coordinates must be in [0, 255]");
            }
            session = Objects.requireNonNull(session, "session");
        }

        public int regionId() {
            return (regionX << 8) | regionY;
        }
    }
}
