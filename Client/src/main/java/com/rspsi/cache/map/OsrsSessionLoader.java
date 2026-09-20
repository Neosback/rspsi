package com.rspsi.cache.map;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.model.WorldRegion;

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
