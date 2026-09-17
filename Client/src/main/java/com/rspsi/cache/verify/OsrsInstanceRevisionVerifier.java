package com.rspsi.cache.verify;

import com.rspsi.cache.map.OsrsMapService;
import com.rspsi.cache.map.OsrsRegionDecoder;
import com.rspsi.cache.map.OsrsRevisionProfile;
import com.rspsi.cache.store.CacheStoreFactory;
import com.rspsi.cache.store.OpenRuneCacheStore;
import com.rspsi.editor.model.InstanceObjectFootprintResolver;
import com.rspsi.editor.model.InstanceWorldBuilder;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldRegionWindow;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Verifies a reference-client instance template fixture against the neutral builder. */
public final class OsrsInstanceRevisionVerifier {
    private OsrsInstanceRevisionVerifier() {
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: OsrsInstanceRevisionVerifier <cache-path> <fixture> <revision>");
            System.exit(2);
        }
        List<String> lines = verify(Path.of(args[0]), Path.of(args[1]), Integer.parseInt(args[2]));
        lines.forEach(System.out::println);
        if (lines.stream().anyMatch(line -> line.startsWith("FAIL"))) System.exit(1);
    }

    public static List<String> verify(Path cachePath, Path fixturePath, int revision) {
        List<String> lines = new ArrayList<>();
        try (OpenRuneCacheStore store = CacheStoreFactory.openOsrs(cachePath)) {
            OsrsInstanceSemanticFixture fixture = OsrsInstanceSemanticFixture.load(fixturePath);
            OsrsMapService maps = new OsrsMapService(store, revision);
            WorldRegionWindow source = maps.loadWindow(
                    fixture.sourceRegionX(), fixture.sourceRegionY(), 1, 1);
            if (!source.complete()) {
                return List.of("FAIL instance.source: source region "
                        + fixture.sourceRegionX() + "," + fixture.sourceRegionY() + " is missing");
            }
            var definitions = store.definitionProvider(revision);
            InstanceObjectFootprintResolver footprints = object -> definitions.object(object.id())
                    .map(definition -> new InstanceObjectFootprintResolver.Footprint(
                            definition.width(), definition.length()))
                    .orElseGet(() -> new InstanceObjectFootprintResolver.Footprint(1, 1));
            WorldDocument actual = new InstanceWorldBuilder().build(
                    source, fixture.grid(), fixture.width(), fixture.length(), fixture.planes(), footprints,
                    OsrsRegionDecoder::generatedHeightAtWorldNoiseCoordinate);
            OsrsInstanceSemanticFixture.Comparison terrain = fixture.compareTerrain(actual);
            OsrsLocationSemanticFixture.Comparison objects = fixture.compareObjects(actual);
            lines.add("OSRS instance verification: " + fixturePath);
            lines.add("PASS instance.templates: " + fixture.grid().transforms().size()
                    + " packed chunk transforms; source region "
                    + fixture.sourceRegionX() + "," + fixture.sourceRegionY());
            lines.add((terrain.matches() ? "PASS" : "FAIL") + " instance.terrain: "
                    + terrain.differenceCount() + " differing fields"
                    + samples(terrain.samples()));
            lines.add((objects.matches() ? "PASS" : "FAIL") + " instance.objects: "
                    + objects.differenceCount() + " differing placements"
                    + samples(objects.samples()));
            return List.copyOf(lines);
        } catch (Exception exception) {
            return List.of("FAIL instance.verify: " + exception.getClass().getSimpleName()
                    + ": " + exception.getMessage());
        }
    }

    private static String samples(List<String> samples) {
        return samples.isEmpty() ? "" : "; " + String.join("; ", samples);
    }
}
