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
import com.rspsi.editor.render.RenderSceneParity;
import com.rspsi.editor.render.RenderWindowScene;
import com.rspsi.editor.render.RenderWindowSceneBuilder;
import com.rspsi.editor.validation.ValidationIssue;
import com.rspsi.editor.validation.WorldValidator;

import java.io.IOException;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Files;
import javax.imageio.ImageIO;
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
                Integer.parseInt(args[3]), parityFixturePath(), requireExternalParity());
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
        return inspectRegion(path, regionX, regionY, revision, parityFixturePath, false);
    }

    /**
     * Inspects a region with an optional release-level requirement for
     * independent RuneLite/TSPS render and minimap evidence.
     */
    public static VerificationReport inspectRegion(Path path, int regionX, int regionY, int revision,
                                                   Path parityFixturePath, boolean requireExternalParity) {
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
            List<VerificationCheck> definitionAudit = RevisionAudit.auditDefinitions(definitions);
            messages.add("definition provider: ready; map-scene sprites: " + definitions.mapSceneIds().size());
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
            exportParityImages(parityOutputPath(), minimaps, shapedMinimaps, collision, messages, errors);
            byte[] encodedTerrain = OsrsRegionEncoder.encodeTerrain(document, profile.newTerrainFormat());
            byte[] encodedLocations = OsrsRegionEncoder.encodeLocations(document);
            WorldDocument roundTrip = OsrsRegionDecoder.decode(encodedTerrain, encodedLocations,
                    regionX, regionY, profile.newTerrainFormat());
            boolean equal = semanticallyEqual(document, roundTrip);
            RenderScene roundTripScene = new RenderSceneBuilder(definitions).build(roundTrip);
            RenderSceneParity.Report sceneRoundTripReport = RenderSceneParity.compare(scene, roundTripScene);
            boolean sceneRoundTripEqual = sceneRoundTripReport.matches();
            messages.add("decode-encode-decode semantic equality: " + equal);
            messages.add("neutral scene round-trip equality: " + sceneRoundTripEqual
                    + " (" + sceneRoundTripReport.differenceCount() + " differences)");
            if (!equal) errors.add("semantic round-trip mismatch");
            if (!sceneRoundTripEqual) {
                errors.add("neutral scene round-trip mismatch: "
                        + sceneRoundTripReport.differenceCount() + " differences");
            }

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
            VerificationCheck terrainParity = terrainParityCheck(fixture, fixtureProblems, document, messages);
            VerificationCheck locationParity = locationParityCheck(fixture, fixtureProblems, document, messages);
            VerificationCheck geometryParity = sceneGeometryParityCheck(fixture, fixtureProblems,
                    document, messages);
            VerificationCheck collisionParity = collisionParityCheck(fixture, fixtureProblems,
                    collision, messages);
            VerificationCheck minimapParity = minimapParityCheck(fixture, fixtureProblems,
                    document, definitions, minimaps, shapedMinimaps, messages, errors);
            if (renderParity.status() == VerificationCheck.Status.FAIL) {
                errors.add("render parity failed: " + renderParity.detail());
            }
            if (minimapParity.status() == VerificationCheck.Status.FAIL) {
                errors.add("minimap parity failed: " + minimapParity.detail());
            }
            if (terrainParity.status() == VerificationCheck.Status.FAIL) {
                errors.add("terrain parity failed: " + terrainParity.detail());
            }
            if (locationParity.status() == VerificationCheck.Status.FAIL) {
                errors.add("location parity failed: " + locationParity.detail());
            }
            if (geometryParity.status() == VerificationCheck.Status.FAIL) {
                errors.add("scene geometry parity failed: " + geometryParity.detail());
            }
            if (collisionParity.status() == VerificationCheck.Status.FAIL) {
                errors.add("collision parity failed: " + collisionParity.detail());
            }
            // TSPS collision flags are useful diagnostics, but are not the
            // authoritative gate: its client scene uses clipType and omits
            // scene-edge locations, while RSPSi follows OpenRune's
            // solid/blockWalk and routefinder vocabulary. The authoritative
            // collision vectors remain in the normal test suite until a
            // normalized RuneLite/OpenRune fixture is available.
            errors.addAll(requiredParityErrors(renderParity, geometryParity, terrainParity,
                    locationParity, minimapParity, requireExternalParity));
            if (issueErrors > 0) errors.add("world validation reported errors");
            if (revisionAudit.stream().anyMatch(check -> check.status() == VerificationCheck.Status.FAIL)) {
                errors.add("revision audit reported incompatible cache assumptions");
            }
            if (definitionAudit.stream().anyMatch(check -> check.status() == VerificationCheck.Status.FAIL)) {
                errors.add("definition audit reported unavailable required definitions");
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
                    concatChecks(revisionAudit, concatChecks(definitionAudit, List.of(
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
                            check("scene.roundtrip", sceneRoundTripEqual
                                            ? VerificationCheck.Status.PASS : VerificationCheck.Status.FAIL,
                                    "neutral scene equality after round trip: " + sceneRoundTripEqual
                                            + " (" + sceneRoundTripReport.differenceCount() + " differences)"),
                            renderParity,
                            terrainParity,
                            locationParity,
                            geometryParity,
                            collisionParity,
                            minimapParity))));
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

    private static VerificationCheck terrainParityCheck(OsrsParityFixture fixture,
                                                        List<String> fixtureProblems,
                                                        WorldDocument document,
                                                        List<String> messages) {
        if (fixture == null && fixtureProblems.isEmpty()) {
            return check("terrain.parity", VerificationCheck.Status.NOT_RUN,
                    "independent terrain semantics fixture was not supplied");
        }
        if (!fixtureProblems.isEmpty()) {
            return check("terrain.parity", VerificationCheck.Status.FAIL,
                    "fixture is incompatible or could not be loaded");
        }
        if (fixture.terrainSemantics() == null) {
            return check("terrain.parity", VerificationCheck.Status.WARN,
                    "fixture contains no terrain-semantics.json export");
        }
        OsrsTerrainSemanticFixture.Comparison comparison = fixture.terrainSemantics().compare(document);
        messages.add("terrain semantic parity: " + comparison.differenceCount()
                + " differing fields" + (comparison.samples().isEmpty()
                ? "" : "; samples=" + comparison.samples()));
        return check("terrain.parity", comparison.matches()
                        ? VerificationCheck.Status.PASS : VerificationCheck.Status.FAIL,
                comparison.matches()
                        ? "independent terrain semantics match"
                        : comparison.differenceCount() + " differing terrain fields"
                        + (comparison.samples().isEmpty() ? "" : "; " + comparison.samples()));
    }

    private static VerificationCheck locationParityCheck(OsrsParityFixture fixture,
                                                         List<String> fixtureProblems,
                                                         WorldDocument document,
                                                         List<String> messages) {
        if (fixture == null && fixtureProblems.isEmpty()) {
            return check("location.parity", VerificationCheck.Status.NOT_RUN,
                    "independent location semantics fixture was not supplied");
        }
        if (!fixtureProblems.isEmpty()) {
            return check("location.parity", VerificationCheck.Status.FAIL,
                    "fixture is incompatible or could not be loaded");
        }
        if (fixture.locations() == null) {
            return check("location.parity", VerificationCheck.Status.WARN,
                    "fixture contains no locations.json export");
        }
        OsrsLocationSemanticFixture.Comparison comparison = fixture.locations().compare(document);
        messages.add("location semantic parity: " + comparison.differenceCount()
                + " differing placements" + (comparison.samples().isEmpty()
                ? "" : "; samples=" + comparison.samples()));
        return check("location.parity", comparison.matches()
                        ? VerificationCheck.Status.PASS : VerificationCheck.Status.FAIL,
                comparison.matches()
                        ? "independent location semantics match"
                        : comparison.differenceCount() + " differing location placements"
                        + (comparison.samples().isEmpty() ? "" : "; " + comparison.samples()));
    }

    private static VerificationCheck sceneGeometryParityCheck(OsrsParityFixture fixture,
                                                               List<String> fixtureProblems,
                                                               WorldDocument document,
                                                               List<String> messages) {
        if (fixture == null && fixtureProblems.isEmpty()) {
            return check("scene.geometry.parity", VerificationCheck.Status.NOT_RUN,
                    "independent scene geometry fixture was not supplied");
        }
        if (!fixtureProblems.isEmpty()) {
            return check("scene.geometry.parity", VerificationCheck.Status.FAIL,
                    "fixture is incompatible or could not be loaded");
        }
        if (fixture.sceneGeometry() == null) {
            return check("scene.geometry.parity", VerificationCheck.Status.WARN,
                    "fixture contains no scene-geometry.json export");
        }
        OsrsSceneGeometryFixture.Comparison comparison = fixture.sceneGeometry().compare(document);
        messages.add("scene geometry parity: " + comparison.differenceCount()
                + " differing tiles" + (comparison.samples().isEmpty()
                ? "" : "; samples=" + comparison.samples()));
        return check("scene.geometry.parity", comparison.matches()
                        ? VerificationCheck.Status.PASS : VerificationCheck.Status.FAIL,
                comparison.matches()
                        ? "independent terrain geometry matches"
                        : comparison.differenceCount() + " differing scene geometry tiles"
                        + (comparison.samples().isEmpty() ? "" : "; " + comparison.samples()));
    }

    private static VerificationCheck collisionParityCheck(OsrsParityFixture fixture,
                                                          List<String> fixtureProblems,
                                                          com.rspsi.editor.collision.CollisionMap collision,
                                                          List<String> messages) {
        if (fixture == null && fixtureProblems.isEmpty()) {
            return check("collision.parity", VerificationCheck.Status.NOT_RUN,
                    "independent collision fixture was not supplied");
        }
        if (!fixtureProblems.isEmpty()) {
            return check("collision.parity", VerificationCheck.Status.FAIL,
                    "fixture is incompatible or could not be loaded");
        }
        if (fixture.collision() == null) {
            return check("collision.parity", VerificationCheck.Status.WARN,
                    "fixture contains no collision.json export");
        }
        OsrsCollisionSemanticFixture.Comparison comparison = fixture.collision().compare(collision);
        messages.add("collision semantic parity: " + comparison.differenceCount()
                + " differing interior tiles" + (comparison.samples().isEmpty()
                ? "" : "; samples=" + comparison.samples()));
        return check("collision.parity", comparison.matches()
                        ? VerificationCheck.Status.PASS : VerificationCheck.Status.WARN,
                comparison.matches()
                        ? "independent collision flags match"
                        : comparison.differenceCount() + " differing client/server collision tiles; "
                        + "diagnostic only until the fixture is normalized to OpenRune route semantics"
                        + (comparison.samples().isEmpty() ? "" : "; " + comparison.samples()));
    }

    private static VerificationCheck minimapParityCheck(OsrsParityFixture fixture,
                                                        List<String> fixtureProblems,
                                                        WorldDocument document,
                                                        DefinitionProvider definitions,
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
        Map<Integer, MinimapImage> comparisonShapedMinimaps = actualShapedMinimaps;
        if (!fixture.mapSceneSprites() && !fixture.shapedMinimaps().isEmpty()) {
            comparisonShapedMinimaps = new LinkedHashMap<>();
            MinimapBuilder builder = new MinimapBuilder();
            DefinitionProvider terrainOnlyDefinitions = withoutMapScenes(definitions);
            for (int plane = 0; plane < document.planes(); plane++) {
                comparisonShapedMinimaps.put(plane,
                        builder.buildShaped(document, plane, terrainOnlyDefinitions));
            }
            messages.add("minimap parity: map-scene sprites excluded; fixture does not declare minimap.mapScenes=true");
        }
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
                    + " differing pixels (expected " + report.expectedWidth() + "x"
                    + report.expectedHeight() + ", actual " + report.actualWidth() + "x"
                    + report.actualHeight() + ")" + sampleDifferences(report));
        }
        for (Map.Entry<Integer, MinimapImage> entry : fixture.shapedMinimaps().entrySet()) {
            MinimapImage actual = comparisonShapedMinimaps.get(entry.getKey());
            if (actual == null) {
                missingImages++;
                errors.add("fixture references missing shaped minimap plane " + entry.getKey());
                continue;
            }
            MinimapParity.Report report = MinimapParity.compare(entry.getValue(), actual);
            compared++;
            differingPixels += report.differingPixels();
            messages.add("shaped minimap parity plane " + entry.getKey() + ": "
                    + report.differingPixels() + " differing pixels (expected "
                    + report.expectedWidth() + "x" + report.expectedHeight() + ", actual "
                    + report.actualWidth() + "x" + report.actualHeight() + ")"
                    + sampleDifferences(report));
        }
        boolean matches = compared > 0 && differingPixels == 0 && missingImages == 0;
        return check("minimap.parity", matches ? VerificationCheck.Status.PASS : VerificationCheck.Status.FAIL,
                compared + " fixture images compared; " + differingPixels
                        + " differing pixels" + (missingImages == 0 ? "" : ", " + missingImages + " missing"));
    }

    private static DefinitionProvider withoutMapScenes(DefinitionProvider delegate) {
        return new DefinitionProvider() {
            @Override
            public java.util.Optional<com.rspsi.cache.definition.ObjectDefinitionView> object(int id) {
                return delegate.object(id);
            }

            @Override
            public java.util.Optional<com.rspsi.cache.definition.FloorDefinitionView> underlay(int id) {
                return delegate.underlay(id);
            }

            @Override
            public java.util.Optional<com.rspsi.cache.definition.FloorDefinitionView> overlay(int id) {
                return delegate.overlay(id);
            }

            @Override
            public java.util.Optional<com.rspsi.cache.definition.TextureDefinitionView> texture(int id) {
                return delegate.texture(id);
            }
        };
    }

    private static String sampleDifferences(MinimapParity.Report report) {
        if (report.differences().isEmpty()) return "";
        MinimapParity.PixelDifference first = report.differences().get(0);
        return "; first difference at (" + first.x() + "," + first.y() + ") expected=0x"
                + Integer.toHexString(first.expected()) + " actual=0x"
                + Integer.toHexString(first.actual());
    }

    private static Path parityFixturePath() {
        String value = System.getenv("RSPSI_OSRS_PARITY_FIXTURE");
        return value == null || value.isBlank() ? null : Path.of(value);
    }

    private static Path parityOutputPath() {
        String value = System.getenv("RSPSI_OSRS_PARITY_OUTPUT");
        return value == null || value.isBlank() ? null : Path.of(value);
    }

    /** Writes only explicitly requested, derived rasters for visual parity review. */
    private static void exportParityImages(Path output,
                                           Map<Integer, MinimapImage> minimaps,
                                           Map<Integer, MinimapImage> shapedMinimaps,
                                           com.rspsi.editor.collision.CollisionMap collision,
                                           List<String> messages,
                                           List<String> errors) {
        if (output == null) return;
        try {
            Files.createDirectories(output);
            writeImages(output, "minimap-plane-", minimaps);
            writeImages(output, "minimap-shaped-plane-", shapedMinimaps);
            writeCollision(output.resolve("rspsi-collision.json"), collision);
            messages.add("parity rasters exported: " + output);
        } catch (IOException | RuntimeException exception) {
            errors.add("could not export parity rasters: " + exception.getMessage());
        }
    }

    private static void writeCollision(Path target,
                                       com.rspsi.editor.collision.CollisionMap collision) throws IOException {
        List<Integer> flags = new ArrayList<>(collision.width() * collision.length() * collision.planes());
        for (int plane = 0; plane < collision.planes(); plane++) {
            for (int x = 0; x < collision.width(); x++) {
                for (int y = 0; y < collision.length(); y++) {
                    flags.add(collision.flags(plane, x, y));
                }
            }
        }
        var root = new com.google.gson.JsonObject();
        root.addProperty("formatVersion", 1);
        root.addProperty("width", collision.width());
        root.addProperty("length", collision.length());
        root.addProperty("planes", collision.planes());
        root.add("flags", new com.google.gson.Gson().toJsonTree(flags));
        Files.writeString(target, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n");
    }

    private static void writeImages(Path output, String prefix,
                                    Map<Integer, MinimapImage> images) throws IOException {
        for (Map.Entry<Integer, MinimapImage> entry : images.entrySet()) {
            MinimapImage image = entry.getValue();
            BufferedImage buffered = new BufferedImage(image.width(), image.height(),
                    BufferedImage.TYPE_INT_ARGB);
            buffered.setRGB(0, 0, image.width(), image.height(), image.argb(), 0, image.width());
            Path target = output.resolve(prefix + entry.getKey() + ".png");
            if (!ImageIO.write(buffered, "png", target.toFile())) {
                throw new IOException("PNG writer unavailable");
            }
        }
    }

    private static boolean requireExternalParity() {
        String value = System.getenv("RSPSI_OSRS_REQUIRE_PARITY");
        return value != null && value.equalsIgnoreCase("true");
    }

    static List<String> requiredParityErrors(VerificationCheck renderParity,
                                              VerificationCheck minimapParity,
                                              boolean required) {
        if (!required) return List.of();
        List<String> errors = new ArrayList<>();
        if (renderParity.status() != VerificationCheck.Status.PASS) {
            errors.add("required render parity is not passing: " + renderParity.status());
        }
        if (minimapParity.status() != VerificationCheck.Status.PASS) {
            errors.add("required minimap parity is not passing: " + minimapParity.status());
        }
        return List.copyOf(errors);
    }

    static List<String> requiredParityErrors(VerificationCheck renderParity,
                                              VerificationCheck terrainParity,
                                              VerificationCheck locationParity,
                                              VerificationCheck minimapParity,
                                              boolean required) {
        return requiredParityErrors(renderParity,
                new VerificationCheck("scene.geometry.parity", VerificationCheck.Status.NOT_RUN,
                        "fixture pending"), terrainParity, locationParity,
                new VerificationCheck("collision.parity", VerificationCheck.Status.NOT_RUN,
                        "fixture pending"), minimapParity, required);
    }

    /** Compatibility overload retained for callers that have terrain geometry but no collision fixture yet. */
    static List<String> requiredParityErrors(VerificationCheck renderParity,
                                              VerificationCheck geometryParity,
                                              VerificationCheck terrainParity,
                                              VerificationCheck locationParity,
                                              VerificationCheck minimapParity,
                                              boolean required) {
        if (!required) return List.of();
        List<String> errors = new ArrayList<>();
        if (renderParity.status() != VerificationCheck.Status.PASS
                && geometryParity.status() != VerificationCheck.Status.PASS) {
            errors.add("required scene parity is not passing: render=" + renderParity.status()
                    + ", geometry=" + geometryParity.status());
        }
        if (terrainParity.status() != VerificationCheck.Status.PASS) {
            errors.add("required terrain parity is not passing: " + terrainParity.status());
        }
        if (locationParity.status() != VerificationCheck.Status.PASS) {
            errors.add("required location parity is not passing: " + locationParity.status());
        }
        if (minimapParity.status() != VerificationCheck.Status.PASS) {
            errors.add("required minimap parity is not passing: " + minimapParity.status());
        }
        return List.copyOf(errors);
    }

    static List<String> requiredParityErrors(VerificationCheck renderParity,
                                              VerificationCheck geometryParity,
                                              VerificationCheck terrainParity,
                                              VerificationCheck locationParity,
                                              VerificationCheck collisionParity,
                                              VerificationCheck minimapParity,
                                              boolean required) {
        if (!required) return List.of();
        List<String> errors = new ArrayList<>();
        if (renderParity.status() != VerificationCheck.Status.PASS
                && geometryParity.status() != VerificationCheck.Status.PASS) {
            errors.add("required scene parity is not passing: render=" + renderParity.status()
                    + ", geometry=" + geometryParity.status());
        }
        if (terrainParity.status() != VerificationCheck.Status.PASS) {
            errors.add("required terrain parity is not passing: " + terrainParity.status());
        }
        if (locationParity.status() != VerificationCheck.Status.PASS) {
            errors.add("required location parity is not passing: " + locationParity.status());
        }
        if (collisionParity.status() != VerificationCheck.Status.PASS) {
            errors.add("required collision parity is not passing: " + collisionParity.status());
        }
        if (minimapParity.status() != VerificationCheck.Status.PASS) {
            errors.add("required minimap parity is not passing: " + minimapParity.status());
        }
        return List.copyOf(errors);
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
