package com.rspsi.cache.verify;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.map.MapIndexTable;
import com.rspsi.cache.map.OsrsMapService;
import com.rspsi.cache.map.OsrsRegionDecoder;
import com.rspsi.cache.map.OsrsRegionEncoder;
import com.rspsi.cache.map.OsrsRevisionProfile;
import com.rspsi.cache.store.OpenRuneCacheStore;
import com.rspsi.editor.collision.OsrsCollisionBuilder;
import com.rspsi.editor.assets.AssetDescriptor;
import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.assets.DefinitionAssetRepository;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldRegionWindow;
import com.rspsi.editor.minimap.MinimapBuilder;
import com.rspsi.editor.minimap.MinimapImage;
import com.rspsi.editor.minimap.MinimapParity;
import com.rspsi.editor.render.RenderScene;
import com.rspsi.editor.render.RenderSceneBuilder;
import com.rspsi.editor.render.RenderSceneFingerprint;
import com.rspsi.editor.render.RenderWindowScene;
import com.rspsi.editor.render.RenderWindowSceneBuilder;
import com.rspsi.editor.validation.ValidationIssue;
import com.rspsi.editor.validation.WorldValidator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
                : inspectRegion(path, Integer.parseInt(args[1]), Integer.parseInt(args[2]),
                Integer.parseInt(args[3]), parityFixturePath());
        report.lines().forEach(System.out::println);
        if (!report.errors().isEmpty()) System.exit(1);
    }

    public static VerificationReport inspectIndex(Path path) {
        try (OpenRuneCacheStore store = OpenRuneCacheStore.open(path)) {
            MapIndexTable index = MapIndexTable.discover(store, OsrsMapService.OSRS_MAP_INDEX);
            List<String> errors = index.size() == 0
                    ? List.of("no OSRS map archives found; supplied cache is not accepted as OSRS evidence")
                    : List.of();
            return new VerificationReport(path, null, null, null, index.size(), true,
                    false, false, List.of("cache opened", "map index entries: " + index.size()), errors,
                    List.of(
                            check("cache.open", VerificationCheck.Status.PASS, "OpenRune cache opened"),
                            check("cache.capabilities", VerificationCheck.Status.PASS,
                                    capabilities(store)),
                            check("map.index", index.size() == 0 ? VerificationCheck.Status.FAIL : VerificationCheck.Status.PASS,
                                    index.size() == 0 ? "no OSRS map groups found" : index.size() + " map groups discovered"),
                            check("region.verify", VerificationCheck.Status.NOT_RUN, "no region was selected")));
        }
    }

    public static VerificationReport inspectRegion(Path path, int regionX, int regionY, int revision) {
        return inspectRegion(path, regionX, regionY, revision, parityFixturePath());
    }

    /**
     * Inspects a region and optionally compares it with an external fixture.
     * The fixture path is explicit so normal verification never reads an
     * implicit cache or silently picks up local reference data.
     */
    public static VerificationReport inspectRegion(Path path, int regionX, int regionY, int revision,
                                                   Path parityFixturePath) {
        List<String> messages = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        try (OpenRuneCacheStore store = OpenRuneCacheStore.open(path)) {
            var metadata = store.metadata(revision).orElseThrow();
            messages.add("cache metadata: " + metadata);
            OsrsRevisionProfile profile = OsrsRevisionProfile.forRevision(revision);
            messages.add("revision profile: " + profile.mapGroupLayout()
                    + ", terrain=" + (profile.newTerrainFormat() ? "short" : "byte"));
            OsrsMapService maps = new OsrsMapService(store, revision);
            messages.add("map index entries: " + maps.index().size());
            byte[] landscape = maps.readLandscape(regionX, regionY);
            byte[] locations = maps.readLocations(regionX, regionY);
            if (landscape == null) {
                errors.add("terrain payload missing for " + regionX + "," + regionY);
                return new VerificationReport(path, regionX, regionY, revision, maps.index().size(),
                        true, false, false, messages, errors,
                        List.of(
                                check("cache.open", VerificationCheck.Status.PASS, "OpenRune cache opened"),
                                check("cache.capabilities", VerificationCheck.Status.PASS, capabilities(store)),
                                check("cache.metadata", VerificationCheck.Status.PASS,
                                        "revision " + metadata.revision() + ", fingerprint " + metadata.fingerprint()),
                                check("map.index", maps.index().size() == 0 ? VerificationCheck.Status.FAIL : VerificationCheck.Status.PASS,
                                        maps.index().size() + " map groups discovered"),
                                check("map.payload", VerificationCheck.Status.FAIL, "region payload missing"),
                                check("region.verify", VerificationCheck.Status.FAIL, "region could not be decoded")));
            }
            boolean emptyLocations = locations == null;
            if (emptyLocations) locations = new byte[0];
            messages.add("terrain bytes: " + landscape.length);
            messages.add("location bytes: " + locations.length + (emptyLocations ? " (archive absent)" : ""));
            DefinitionProvider definitions = store.definitionProvider(revision);
            List<VerificationCheck> revisionAudit = RevisionAudit.audit(store, revision, maps.index());
            messages.add("definition provider: ready");
            AssetRepository assets = new DefinitionAssetRepository(definitions, store.symbolicNameProvider());
            List<AssetDescriptor> availableAssets = assets.search("");
            messages.add("asset descriptors: " + availableAssets.size());
            WorldDocument document = OsrsRegionDecoder.decode(landscape, locations, regionX, regionY,
                    profile.newTerrainFormat());
            int contextMinX = Math.max(0, regionX - 1);
            int contextMinY = Math.max(0, regionY - 1);
            int contextWidth = Math.min(256, regionX + 2) - contextMinX;
            int contextHeight = Math.min(256, regionY + 2) - contextMinY;
            WorldRegionWindow context = maps.loadWindow(contextMinX, contextMinY,
                    contextWidth, contextHeight);
            int rawBoundaryMismatches = context.boundaryMismatches().size();
            int stitchedVertices = context.stitchSharedEdges();
            int boundaryMismatches = context.boundaryMismatches().size();
            RenderWindowScene windowScene = new RenderWindowSceneBuilder(definitions).build(context);
            int expectedWindowTiles = context.loadedRegionCount()
                    * OsrsRegionDecoder.REGION_SIZE * OsrsRegionDecoder.REGION_SIZE
                    * OsrsRegionDecoder.PLANES;
            boolean windowSceneComplete = windowScene.terrainMeshes().size() == expectedWindowTiles;
            int bridgeLinks = document.bridgeLinks().size();
            messages.add("context window: " + context.loadedRegionCount() + "/"
                    + context.expectedRegionCount() + " regions; missing "
                    + context.missingRegionIds().size());
            messages.add("region boundaries: " + rawBoundaryMismatches
                    + " provisional mismatches, " + stitchedVertices
                    + " vertices stitched, " + boundaryMismatches
                    + " remaining; bridge links: " + bridgeLinks);
            messages.add("window scene tiles: " + windowScene.terrainMeshes().size()
                    + "; loaded-region expectation: " + expectedWindowTiles
                    + "; collision tiles: " + windowScene.collision().size()
                    + "; world objects: " + windowScene.objects().size());
            if (boundaryMismatches > 0) {
                errors.add("loaded region boundaries have " + boundaryMismatches + " height mismatches");
            }
            List<ValidationIssue> issues = WorldValidator.validate(document, definitions,
                    WorldValidator.BoundaryMode.REGION_CONTEXT);
            long issueErrors = issues.stream().filter(issue -> issue.severity() == ValidationIssue.Severity.ERROR).count();
            messages.add("validation errors: " + issueErrors);
            messages.add("validation warnings: " + (issues.size() - issueErrors));
            var collision = OsrsCollisionBuilder.fromTerrainAndObjects(document, definitions);
            RenderScene scene = new RenderSceneBuilder(definitions).build(document);
            boolean sceneComplete = scene.terrainMeshes().size()
                    == document.width() * document.length() * document.planes();
            boolean objectProjectionComplete = scene.renderObjects().size() == scene.objects().size();
            String sceneFingerprint = RenderSceneFingerprint.sha256(scene);
            messages.add("neutral scene meshes: " + scene.terrainMeshes().size()
                    + "; terrain materials: " + scene.terrainMaterials().size()
                    + "; objects: " + scene.objects().size()
                    + "; render objects: " + scene.renderObjects().size());
            messages.add("collision non-empty tiles: " + collision.nonEmptyTileCount());
            messages.add("neutral scene fingerprint: " + sceneFingerprint);
            boolean minimapComplete = true;
            int minimapPixels = 0;
            int shapedMinimapPixels = 0;
            Map<Integer, MinimapImage> minimaps = new LinkedHashMap<>();
            Map<Integer, MinimapImage> shapedMinimaps = new LinkedHashMap<>();
            for (int plane = 0; plane < document.planes(); plane++) {
                MinimapImage minimap = new MinimapBuilder().build(document, plane, definitions);
                minimaps.put(plane, minimap);
                minimapComplete &= minimap.width() == document.width()
                        && minimap.height() == document.length();
                minimapPixels += minimap.width() * minimap.height();
                MinimapImage shapedMinimap = new MinimapBuilder().buildShaped(document, plane, definitions);
                shapedMinimaps.put(plane, shapedMinimap);
                minimapComplete &= shapedMinimap.width() == document.width() * 4
                        && shapedMinimap.height() == document.length() * 4;
                shapedMinimapPixels += shapedMinimap.width() * shapedMinimap.height();
            }
            messages.add("neutral minimap rasters: " + document.planes()
                    + " planes; " + minimapPixels + " semantic pixels; "
                    + shapedMinimapPixels + " shaped pixels");
            byte[] encodedTerrain = OsrsRegionEncoder.encodeTerrain(document, profile.newTerrainFormat());
            byte[] encodedLocations = OsrsRegionEncoder.encodeLocations(document);
            WorldDocument roundTrip = OsrsRegionDecoder.decode(encodedTerrain, encodedLocations,
                    regionX, regionY, profile.newTerrainFormat());
            boolean equal = semanticallyEqual(document, roundTrip);
            messages.add("decode-encode-decode semantic equality: " + equal);
            if (!equal) errors.add("semantic round-trip mismatch");

            OsrsParityFixture fixture = null;
            List<String> fixtureProblems = new ArrayList<>();
            if (parityFixturePath != null) {
                try {
                    fixture = OsrsParityFixture.load(parityFixturePath);
                    fixtureProblems.addAll(fixture.compatibilityProblems(regionX, regionY,
                            revision, metadata.fingerprint()));
                    if (!fixtureProblems.isEmpty()) {
                        errors.addAll(fixtureProblems);
                    }
                    messages.add("parity fixture: " + parityFixturePath);
                } catch (IOException | RuntimeException exception) {
                    fixtureProblems.add("could not load parity fixture: " + exception.getMessage());
                    errors.addAll(fixtureProblems);
                }
            }
            VerificationCheck renderParity = renderParityCheck(fixture, fixtureProblems, sceneFingerprint);
            VerificationCheck minimapParity = minimapParityCheck(fixture, fixtureProblems,
                    minimaps, shapedMinimaps, messages, errors);
            if (renderParity.status() == VerificationCheck.Status.FAIL) {
                errors.add("render parity failed: " + renderParity.detail());
            }
            if (minimapParity.status() == VerificationCheck.Status.FAIL) {
                errors.add("minimap parity failed: " + minimapParity.detail());
            }
            if (issueErrors > 0) errors.add("world validation reported errors");
            if (revisionAudit.stream().anyMatch(check -> check.status() == VerificationCheck.Status.FAIL)) {
                errors.add("revision audit reported incompatible cache assumptions");
            }
            if (!sceneComplete) errors.add("neutral scene did not cover every document tile");
            if (!objectProjectionComplete) errors.add("neutral scene object projections did not match canonical objects");
            if (!windowSceneComplete) errors.add("window scene did not cover every loaded region tile");
            if (windowScene.collision().size() != expectedWindowTiles) {
                errors.add("window scene collision did not cover every loaded region tile");
            }
            if (!minimapComplete) errors.add("neutral minimap dimensions did not match the document");
            return new VerificationReport(path, regionX, regionY, revision, maps.index().size(),
                    true, true, equal, messages, errors,
                    concatChecks(revisionAudit, List.of(
                            check("cache.open", VerificationCheck.Status.PASS, "OpenRune cache opened"),
                            check("cache.capabilities", VerificationCheck.Status.PASS,
                                    capabilities(store)),
                            check("cache.metadata", VerificationCheck.Status.PASS,
                                    "revision " + metadata.revision() + ", profile " + profile.mapGroupLayout()
                                            + ", terrain=" + (profile.newTerrainFormat() ? "short" : "byte")
                                            + ", fingerprint " + metadata.fingerprint()),
                            check("map.index", maps.index().size() == 0 ? VerificationCheck.Status.FAIL : VerificationCheck.Status.PASS,
                                    maps.index().size() + " map groups discovered"),
                            check("definitions", VerificationCheck.Status.PASS, "neutral definition provider ready"),
                            check("assets", availableAssets.isEmpty()
                                            ? VerificationCheck.Status.WARN
                                            : VerificationCheck.Status.PASS,
                                    availableAssets.isEmpty()
                                            ? "no neutral asset descriptors were discovered"
                                            : availableAssets.size() + " neutral asset descriptors available"),
                            check("map.payload", VerificationCheck.Status.PASS,
                                    "terrain " + landscape.length + " bytes; locations " + locations.length + " bytes"),
                            check("location.archive", emptyLocations ? VerificationCheck.Status.WARN : VerificationCheck.Status.PASS,
                                    emptyLocations ? "location archive absent; treated as empty" : "location archive present"),
                            check("terrain.decode", VerificationCheck.Status.PASS, "64x64x4 terrain decoded"),
                            check("location.decode", emptyLocations
                                            ? VerificationCheck.Status.WARN
                                            : VerificationCheck.Status.PASS,
                                    emptyLocations ? "no location payload to decode" : "location payload decoded"),
                            check("region.window", context.loadedRegionCount() == context.expectedRegionCount()
                                            ? VerificationCheck.Status.PASS : VerificationCheck.Status.WARN,
                                    context.loadedRegionCount() + "/" + context.expectedRegionCount()
                                            + " neighboring regions loaded; "
                                            + context.missingRegionIds().size() + " holes preserved"),
                            check("region.windowScene", windowSceneComplete
                                            ? VerificationCheck.Status.PASS : VerificationCheck.Status.FAIL,
                                    windowScene.terrainMeshes().size() + "/" + expectedWindowTiles
                                            + " world-addressed terrain tiles built; "
                                            + windowScene.objects().size() + " world objects"),
                            check("region.boundary", boundaryMismatches == 0
                                            ? VerificationCheck.Status.PASS : VerificationCheck.Status.FAIL,
                                    rawBoundaryMismatches + " provisional mismatches; "
                                            + stitchedVertices + " shared vertices materialized; "
                                            + boundaryMismatches + " remaining"),
                            check("plane.semantics", document.planes() == 4
                                            ? VerificationCheck.Status.PASS : VerificationCheck.Status.FAIL,
                                    document.planes() + " authored planes; " + bridgeLinks + " bridge links"),
                            check("world.validation", issueErrors > 0 ? VerificationCheck.Status.FAIL : VerificationCheck.Status.PASS,
                                    issueErrors + " validation errors; " + (issues.size() - issueErrors) + " warnings"),
                            check("collision.decode", VerificationCheck.Status.PASS,
                                    "collision map constructed; " + collision.nonEmptyTileCount() + " non-empty tiles"),
                            check("scene.construction", sceneComplete && objectProjectionComplete
                                            ? VerificationCheck.Status.PASS
                                            : VerificationCheck.Status.FAIL,
                                    scene.terrainMeshes().size() + " neutral terrain meshes and "
                                            + scene.renderObjects().size() + "/" + scene.objects().size()
                                            + " object projections built; fingerprint " + sceneFingerprint),
                            check("minimap.construction", minimapComplete
                                            ? VerificationCheck.Status.PASS
                                            : VerificationCheck.Status.FAIL,
                                    document.planes() + " neutral minimap planes built; "
                                            + minimapPixels + " semantic pixels; "
                                            + shapedMinimapPixels + " shaped pixels"),
                            check("location.parity", equal ? VerificationCheck.Status.PASS : VerificationCheck.Status.FAIL,
                                    "locations included in canonical semantic comparison: " + equal),
                            check("semantic.roundtrip", equal ? VerificationCheck.Status.PASS : VerificationCheck.Status.FAIL,
                                    "decode -> encode -> decode semantic equality: " + equal),
                            renderParity,
                            minimapParity)));
        } catch (RuntimeException exception) {
            errors.add(exception.getClass().getSimpleName() + ": " + exception.getMessage());
            return new VerificationReport(path, regionX, regionY, revision, 0,
                    false, false, false, messages, errors,
                    List.of(
                            check("cache.open", VerificationCheck.Status.FAIL, exception.getClass().getSimpleName()),
                            check("region.verify", VerificationCheck.Status.FAIL, exception.getMessage() == null ? "verification failed" : exception.getMessage())));
        }
    }

    private static VerificationCheck check(String id, VerificationCheck.Status status, String detail) {
        return new VerificationCheck(id, status, detail);
    }

    private static VerificationCheck renderParityCheck(OsrsParityFixture fixture,
                                                       List<String> fixtureProblems,
                                                       String actualFingerprint) {
        if (fixture == null && fixtureProblems.isEmpty()) {
            return check("render.parity", VerificationCheck.Status.NOT_RUN,
                    "RuneLite/TSPS render fixture directory was not supplied");
        }
        if (!fixtureProblems.isEmpty()) {
            return check("render.parity", VerificationCheck.Status.FAIL,
                    "fixture is incompatible or could not be loaded");
        }
        if (fixture.sceneFingerprint() == null) {
            return check("render.parity", VerificationCheck.Status.WARN,
                    "fixture has no scene.fingerprint value");
        }
        boolean matches = fixture.sceneFingerprint().equals(actualFingerprint);
        return check("render.parity", matches ? VerificationCheck.Status.PASS : VerificationCheck.Status.FAIL,
                matches ? "neutral scene fingerprint matches fixture"
                        : "neutral scene fingerprint differs; expected=" + fixture.sceneFingerprint()
                        + ", actual=" + actualFingerprint);
    }

    private static VerificationCheck minimapParityCheck(OsrsParityFixture fixture,
                                                        List<String> fixtureProblems,
                                                        Map<Integer, MinimapImage> actualMinimaps,
                                                        Map<Integer, MinimapImage> actualShapedMinimaps,
                                                        List<String> messages,
                                                        List<String> errors) {
        if (fixture == null && fixtureProblems.isEmpty()) {
            return check("minimap.parity", VerificationCheck.Status.NOT_RUN,
                    "minimap fixture directory was not supplied");
        }
        if (!fixtureProblems.isEmpty()) {
            return check("minimap.parity", VerificationCheck.Status.FAIL,
                    "fixture is incompatible or could not be loaded");
        }
        if (!fixture.hasMinimapImages()) {
            return check("minimap.parity", VerificationCheck.Status.WARN,
                    "fixture contains no minimap PNGs");
        }

        int compared = 0;
        int differingPixels = 0;
        int missingImages = 0;
        for (Map.Entry<Integer, MinimapImage> entry : fixture.minimaps().entrySet()) {
            MinimapImage actual = actualMinimaps.get(entry.getKey());
            if (actual == null) {
                missingImages++;
                errors.add("fixture references missing semantic minimap plane " + entry.getKey());
                continue;
            }
            MinimapParity.Report report = MinimapParity.compare(entry.getValue(), actual);
            compared++;
            differingPixels += report.differingPixels();
            messages.add("minimap parity plane " + entry.getKey() + ": " + report.differingPixels()
                    + " differing pixels");
        }
        for (Map.Entry<Integer, MinimapImage> entry : fixture.shapedMinimaps().entrySet()) {
            MinimapImage actual = actualShapedMinimaps.get(entry.getKey());
            if (actual == null) {
                missingImages++;
                errors.add("fixture references missing shaped minimap plane " + entry.getKey());
                continue;
            }
            MinimapParity.Report report = MinimapParity.compare(entry.getValue(), actual);
            compared++;
            differingPixels += report.differingPixels();
            messages.add("shaped minimap parity plane " + entry.getKey() + ": "
                    + report.differingPixels() + " differing pixels");
        }
        boolean matches = compared > 0 && differingPixels == 0 && missingImages == 0;
        return check("minimap.parity", matches ? VerificationCheck.Status.PASS : VerificationCheck.Status.FAIL,
                compared + " fixture images compared; " + differingPixels
                        + " differing pixels" + (missingImages == 0 ? "" : ", " + missingImages + " missing"));
    }

    private static Path parityFixturePath() {
        String value = System.getenv("RSPSI_OSRS_PARITY_FIXTURE");
        return value == null || value.isBlank() ? null : Path.of(value);
    }

    private static List<VerificationCheck> concatChecks(List<VerificationCheck> prefix,
                                                        List<VerificationCheck> suffix) {
        List<VerificationCheck> checks = new ArrayList<>(prefix.size() + suffix.size());
        checks.addAll(prefix);
        checks.addAll(suffix);
        return List.copyOf(checks);
    }

    private static String capabilities(OpenRuneCacheStore store) {
        var capabilities = store.capabilities();
        return "writable=" + capabilities.writable()
                + ", namedArchives=" + capabilities.namedArchives()
                + ", mapPacking=" + capabilities.mapPacking()
                + ", writeMode=" + capabilities.writeMode();
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
            List<String> errors,
            List<VerificationCheck> checks
    ) {
        public VerificationReport(
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
            this(cachePath, regionX, regionY, revision, mapIndexEntries, cacheOpened, regionDecoded,
                    roundTripEqual, messages, errors, List.of());
        }

        public VerificationReport {
            messages = List.copyOf(messages);
            errors = List.copyOf(errors);
            checks = List.copyOf(checks);
        }

        public List<String> lines() {
            List<String> result = new ArrayList<>(checks.size() + messages.size() + errors.size() + 1);
            result.add("OSRS revision verification: " + cachePath);
            checks.forEach(check -> result.add(check.status() + " " + check.id() + ": " + check.detail()));
            result.addAll(messages);
            errors.forEach(error -> result.add("ERROR: " + error));
            return result;
        }
    }
}
