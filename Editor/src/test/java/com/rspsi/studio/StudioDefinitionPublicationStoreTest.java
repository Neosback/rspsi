package com.rspsi.studio;

import com.rspsi.cache.OsrsCacheMetadata;
import com.rspsi.cache.definition.ObjectDefinitionRawView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudioDefinitionPublicationStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void roundTripsVerifiedPublicationStateAndRetainsOtherSources() {
        Path file = temporaryDirectory.resolve("definition-publications.json");
        StudioDefinitionPublicationStore store =
                new StudioDefinitionPublicationStore(file);

        Path sourceA = temporaryDirectory.resolve("source-a");
        Path sourceB = temporaryDirectory.resolve("source-b");
        Path outputA = temporaryDirectory.resolve("output-a");
        Path outputB = temporaryDirectory.resolve("output-b");
        OsrsCacheMetadata identityA =
                new OsrsCacheMetadata(240, 2, "fingerprint-a");
        OsrsCacheMetadata identityB =
                new OsrsCacheMetadata(241, null, "fingerprint-b");

        ObjectDefinitionRawView first = raw(7, "First");
        ObjectDefinitionRawView second = raw(8, "Second");

        store.save(new StudioDefinitionPublicationStore.PublicationState(
                sourceA, identityA, outputA, Map.of(7, first)));
        store.save(new StudioDefinitionPublicationStore.PublicationState(
                sourceB, identityB, outputB, Map.of(8, second)));

        var restoredA = store.loadFor(sourceA, identityA).orElseThrow();
        assertEquals(sourceA.toAbsolutePath().normalize(), restoredA.sourceCache());
        assertEquals(outputA.toAbsolutePath().normalize(), restoredA.outputCache());
        assertEquals(identityA, restoredA.identity());
        assertEquals(Map.of(7, first), restoredA.publishedSnapshots());

        var restoredB = store.loadFor(sourceB, identityB).orElseThrow();
        assertEquals(outputB.toAbsolutePath().normalize(), restoredB.outputCache());
        assertEquals(Map.of(8, second), restoredB.publishedSnapshots());
    }

    @Test
    void replacesPriorStateForSameSourceAndRequiresExactIdentity() {
        Path file = temporaryDirectory.resolve("definition-publications.json");
        StudioDefinitionPublicationStore store =
                new StudioDefinitionPublicationStore(file);

        Path source = temporaryDirectory.resolve("source");
        OsrsCacheMetadata oldIdentity =
                new OsrsCacheMetadata(240, 1, "old");
        OsrsCacheMetadata newIdentity =
                new OsrsCacheMetadata(240, 2, "new");

        store.save(new StudioDefinitionPublicationStore.PublicationState(
                source,
                oldIdentity,
                temporaryDirectory.resolve("old-output"),
                Map.of(7, raw(7, "Old"))));
        store.save(new StudioDefinitionPublicationStore.PublicationState(
                source,
                newIdentity,
                temporaryDirectory.resolve("new-output"),
                Map.of(7, raw(7, "New"))));

        assertTrue(store.loadFor(source, oldIdentity).isEmpty());
        var restored = store.loadFor(source, newIdentity).orElseThrow();
        assertEquals(
                temporaryDirectory.resolve("new-output")
                        .toAbsolutePath().normalize(),
                restored.outputCache());
        assertEquals("New", restored.publishedSnapshots().get(7)
                .fields().get(0).value());
    }

    private static ObjectDefinitionRawView raw(int id, String name) {
        return new ObjectDefinitionRawView(
                id,
                List.of(new ObjectDefinitionRawView.Field(
                        "name",
                        "2",
                        ObjectDefinitionRawView.ValueType.STRING,
                        name)),
                List.of(new ObjectDefinitionRawView.Param(
                        100,
                        ObjectDefinitionRawView.ValueType.INTEGER,
                        "7")));
    }
}
