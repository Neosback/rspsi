package com.rspsi.cache.verify;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.map.MapIndexTable;
import com.rspsi.cache.map.OsrsMapService;
import com.rspsi.cache.map.OsrsRegionDecoder;
import com.rspsi.cache.map.OsrsRegionEncoder;
import com.rspsi.cache.store.OpenRuneCacheStore;
import com.rspsi.editor.collision.OsrsCollisionBuilder;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.validation.ValidationIssue;
import com.rspsi.editor.validation.WorldValidator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Explicit real-cache verification entry point. It is never run against an
 * implicit user cache; callers must provide the path and revision themselves.
 */
public final class OsrsRevisionVerifier {
    private OsrsRevisionVerifier() {
    }

    public static void main(String[] args) {
        if (args.length != 1 && args.length != 4) {
            System.err.println("Usage: OsrsRevisionVerifier <cache-path> [<region-x> <region-y> <revision>]");
            System.exit(2);
        }
        Path path = Path.of(args[0]);
        VerificationReport report = args.length == 1
                ? inspectIndex(path)
                : inspectRegion(path, Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
        report.lines().forEach(System.out::println);
        if (!report.errors().isEmpty()) System.exit(1);
    }

    public static VerificationReport inspectIndex(Path path) {
        try (OpenRuneCacheStore store = OpenRuneCacheStore.open(path)) {
            MapIndexTable index = MapIndexTable.discover(store, OsrsMapService.OSRS_MAP_INDEX);
            List<String> errors = index.size() == 0
                    ? List.of("no named OSRS map archives found; supplied cache is not accepted as OSRS evidence")
                    : List.of();
            return new VerificationReport(path, null, null, null, index.size(), true,
                    false, false, List.of("cache opened", "map index entries: " + index.size()), errors);
        }
    }

    public static VerificationReport inspectRegion(Path path, int regionX, int regionY, int revision) {
        List<String> messages = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        try (OpenRuneCacheStore store = OpenRuneCacheStore.open(path)) {
            OsrsMapService maps = new OsrsMapService(store);
            messages.add("cache metadata: " + store.metadata(revision));
            messages.add("map index entries: " + maps.index().size());
            byte[] landscape = maps.readLandscape(regionX, regionY);
            byte[] locations = maps.readLocations(regionX, regionY);
            if (landscape == null || locations == null) {
                errors.add("region payload missing for " + regionX + "," + regionY);
                return new VerificationReport(path, regionX, regionY, revision, maps.index().size(),
                        false, false, false, messages, errors);
            }
            messages.add("terrain bytes: " + landscape.length);
            messages.add("location bytes: " + locations.length);
            DefinitionProvider definitions = store.definitionProvider(revision);
            WorldDocument document = OsrsRegionDecoder.decode(landscape, locations, regionX, regionY);
            List<ValidationIssue> issues = WorldValidator.validate(document, definitions);
            long issueErrors = issues.stream().filter(issue -> issue.severity() == ValidationIssue.Severity.ERROR).count();
            messages.add("validation errors: " + issueErrors);
            messages.add("validation warnings: " + (issues.size() - issueErrors));
            OsrsCollisionBuilder.fromTerrainAndObjects(document, definitions);
            byte[] encodedTerrain = OsrsRegionEncoder.encodeTerrain(document);
            byte[] encodedLocations = OsrsRegionEncoder.encodeLocations(document);
            WorldDocument roundTrip = OsrsRegionDecoder.decode(encodedTerrain, encodedLocations, regionX, regionY);
            boolean equal = semanticallyEqual(document, roundTrip);
            messages.add("decode-encode-decode semantic equality: " + equal);
            if (!equal) errors.add("semantic round-trip mismatch");
            if (issueErrors > 0) errors.add("world validation reported errors");
            return new VerificationReport(path, regionX, regionY, revision, maps.index().size(),
                    true, true, equal, messages, errors);
        } catch (RuntimeException exception) {
            errors.add(exception.getClass().getSimpleName() + ": " + exception.getMessage());
            return new VerificationReport(path, regionX, regionY, revision, 0,
                    false, false, false, messages, errors);
        }
    }

    private static boolean semanticallyEqual(WorldDocument first, WorldDocument second) {
        if (first.width() != second.width() || first.length() != second.length() || first.planes() != second.planes()) {
            return false;
        }
        for (int plane = 0; plane < first.planes(); plane++) {
            for (int x = 0; x < first.width(); x++) {
                for (int y = 0; y < first.length(); y++) {
                    TileSnapshot a = first.tile(plane, x, y).snapshot();
                    if (!a.equals(second.tile(plane, x, y).snapshot())) return false;
                }
            }
        }
        return true;
    }

    public record VerificationReport(
            Path cachePath,
            Integer regionX,
            Integer regionY,
            Integer revision,
            int mapIndexEntries,
            boolean cacheOpened,
            boolean regionDecoded,
            boolean roundTripEqual,
            List<String> messages,
            List<String> errors
    ) {
        public VerificationReport {
            messages = List.copyOf(messages);
            errors = List.copyOf(errors);
        }

        public List<String> lines() {
            List<String> result = new ArrayList<>(messages.size() + errors.size() + 1);
            result.add("OSRS revision verification: " + cachePath);
            result.addAll(messages);
            errors.forEach(error -> result.add("ERROR: " + error));
            return result;
        }
    }
}
