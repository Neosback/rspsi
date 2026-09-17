package com.rspsi.cache.map;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.model.WorldDocument;

import java.util.Objects;

/**
 * Coordinates one canonical region save without exposing cache-library types
 * to the editor session or UI.
 *
 * <p>Encoding happens completely before the first write. The map service owns
 * archive/file selection and backend capability checks; this coordinator owns
 * the editor-level rule that history is marked saved only after both payloads
 * have been accepted and flushed.</p>
 */
public final class OsrsRegionSaveCoordinator {
    private final MapService maps;

    public OsrsRegionSaveCoordinator(MapService maps) {
        this.maps = Objects.requireNonNull(maps, "maps");
    }

    public SaveResult save(EditorSession session, int regionX, int regionY) {
        Objects.requireNonNull(session, "session");
        if (!session.canEdit()) {
            throw new IllegalStateException("Cannot save a read-only editor session");
        }
        WorldDocument document = session.world();
        byte[] landscape = OsrsRegionEncoder.encodeTerrain(document, maps.newTerrainFormat());
        byte[] locations = OsrsRegionEncoder.encodeLocations(document);

        maps.writeLandscape(regionX, regionY, landscape);
        maps.writeLocations(regionX, regionY, locations);
        maps.flush();
        session.markSaved();
        return new SaveResult(regionX, regionY, landscape.length, locations.length,
                session.history().position());
    }

    public record SaveResult(int regionX, int regionY, int landscapeBytes,
                             int locationBytes, int savedHistoryPosition) {
        public SaveResult {
            if (regionX < 0 || regionY < 0 || landscapeBytes < 0 || locationBytes < 0
                    || savedHistoryPosition < 0) {
                throw new IllegalArgumentException("Invalid region save result");
            }
        }
    }
}
