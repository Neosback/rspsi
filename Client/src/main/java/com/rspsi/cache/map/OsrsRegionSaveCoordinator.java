package com.rspsi.cache.map;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.model.WorldDocument;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
        return saveAll(List.of(new RegionSaveRequest(session, regionX, regionY)))
                .regions().get(0);
    }

    /**
     * Saves several region sessions as one cache write batch. Every document
     * is encoded before the first write, and no session is marked saved until
     * all payloads have been flushed successfully.
     */
    public BatchSaveResult saveAll(List<RegionSaveRequest> requests) {
        Objects.requireNonNull(requests, "requests");
        if (requests.isEmpty()) {
            throw new IllegalArgumentException("At least one region is required");
        }
        Set<String> coordinates = new HashSet<>();
        Set<EditorSession> sessions = new HashSet<>();
        List<PreparedRegion> prepared = new ArrayList<>(requests.size());
        for (RegionSaveRequest request : requests) {
            if (!coordinates.add(request.regionX() + ":" + request.regionY())) {
                throw new IllegalArgumentException("Duplicate region in save batch: "
                        + request.regionX() + "," + request.regionY());
            }
            if (!sessions.add(request.session())) {
                throw new IllegalArgumentException("A session cannot represent multiple regions");
            }
            if (!request.session().canEdit()) {
                throw new IllegalStateException("Cannot save a read-only editor session");
            }
            WorldDocument document = request.session().world();
            byte[] landscape = OsrsRegionEncoder.encodeTerrain(document, maps.newTerrainFormat());
            byte[] locations = OsrsRegionEncoder.encodeLocations(document);
            prepared.add(new PreparedRegion(request, landscape, locations));
        }

        for (PreparedRegion region : prepared) {
            maps.writeLandscape(region.request().regionX(), region.request().regionY(),
                    region.landscape());
            maps.writeLocations(region.request().regionX(), region.request().regionY(),
                    region.locations());
        }
        maps.flush();

        List<SaveResult> results = new ArrayList<>(prepared.size());
        for (PreparedRegion region : prepared) {
            EditorSession session = region.request().session();
            session.markSaved();
            results.add(new SaveResult(region.request().regionX(), region.request().regionY(),
                    region.landscape().length, region.locations().length,
                    session.history().position()));
        }
        return new BatchSaveResult(results);
    }

    public record RegionSaveRequest(EditorSession session, int regionX, int regionY) {
        public RegionSaveRequest {
            session = Objects.requireNonNull(session, "session");
            validateCoordinate(regionX, "regionX");
            validateCoordinate(regionY, "regionY");
        }
    }

    public record BatchSaveResult(List<SaveResult> regions) {
        public BatchSaveResult {
            regions = List.copyOf(Objects.requireNonNull(regions, "regions"));
            if (regions.isEmpty()) throw new IllegalArgumentException("Save result cannot be empty");
        }
    }

    private record PreparedRegion(RegionSaveRequest request, byte[] landscape, byte[] locations) {
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

    private static void validateCoordinate(int coordinate, String name) {
        if (coordinate < 0 || coordinate > 255) {
            throw new IllegalArgumentException(name + " must be in [0, 255]");
        }
    }
}
