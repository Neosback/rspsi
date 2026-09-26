package com.rspsi.editor.paste;

import com.rspsi.editor.change.ChangePlan;
import com.rspsi.editor.model.TileBounds;
import com.rspsi.editor.model.WorldFragment;
import com.rspsi.editor.model.WorldTile;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WorldFragmentPasteContractCompatibilityTest {

    @Test
    void policyRemainsJvmRecordWithStaticPresets() throws Exception {
        assertTrue(WorldFragmentPastePolicy.class.isRecord());

        for (String factory : List.of(
                "replaceAll", "terrainOnly", "objectsOnly", "mergeObjects")) {
            var method = WorldFragmentPastePolicy.class.getDeclaredMethod(factory);
            assertTrue(Modifier.isPublic(method.getModifiers()));
            assertTrue(Modifier.isStatic(method.getModifiers()));
        }

        var replace = WorldFragmentPastePolicy.replaceAll();
        assertEquals(WorldFragmentPastePolicy.TerrainMode.REPLACE, replace.terrainMode());
        assertEquals(WorldFragmentPastePolicy.ObjectMode.REPLACE, replace.objectMode());
        assertEquals(WorldFragmentPastePolicy.HeightMode.SOURCE_ABSOLUTE, replace.heightMode());
        assertEquals(WorldFragmentPastePolicy.ConflictMode.REPORT, replace.conflictMode());
        assertEquals(Optional.empty(), replace.heightAnchor());
        assertTrue(replace.objectTypes().isEmpty());
    }

    @Test
    void policyKeepsImmutableValidatedObjectTypeCopy() {
        var source = new java.util.LinkedHashSet<>(List.of(10, 22));
        var policy = WorldFragmentPastePolicy.objectsOnly().withObjectTypes(source);

        source.clear();
        assertEquals(Set.of(10, 22), policy.objectTypes());
        assertThrows(UnsupportedOperationException.class,
                () -> policy.objectTypes().add(0));
        assertTrue(policy.includesObjectType(10));
        assertFalse(policy.includesObjectType(3));

        var allTypes = WorldFragmentPastePolicy.objectsOnly();
        assertTrue(allTypes.includesObjectType(3));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> allTypes.withObjectTypes(Set.of(23)));
        assertEquals(
                "OSRS location type filter must be between 0 and 22: 23",
                failure.getMessage());
    }

    @Test
    void policyPreservesConstructorAndBuilderNullMessages() {
        NullPointerException terrainFailure = assertThrows(
                NullPointerException.class,
                () -> new WorldFragmentPastePolicy(
                        null,
                        WorldFragmentPastePolicy.ObjectMode.REPLACE,
                        WorldFragmentPastePolicy.HeightMode.SOURCE_ABSOLUTE,
                        WorldFragmentPastePolicy.ConflictMode.REPORT,
                        Optional.empty(),
                        Set.of()));
        assertEquals("terrainMode", terrainFailure.getMessage());

        NullPointerException typesFailure = assertThrows(
                NullPointerException.class,
                () -> WorldFragmentPastePolicy.replaceAll().withObjectTypes(null));
        assertEquals("objectTypes", typesFailure.getMessage());

        NullPointerException anchorFailure = assertThrows(
                NullPointerException.class,
                () -> WorldFragmentPastePolicy.replaceAll().withHeightAnchor(null));
        assertEquals("anchor", anchorFailure.getMessage());
    }

    @Test
    void heightAnchorRemainsRecordAndPolicyValidationIsSourceAware() {
        assertTrue(WorldFragmentPastePolicy.HeightAnchor.class.isRecord());

        IllegalArgumentException negativeFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new WorldFragmentPastePolicy.HeightAnchor(0, -1, 0));
        assertEquals("Paste height anchor cannot be negative", negativeFailure.getMessage());

        WorldFragment fragment = new WorldFragment(
                new TileBounds(10, 20, 11, 21),
                List.of(),
                List.of());

        var valid = WorldFragmentPastePolicy.replaceAll()
                .withHeightAnchor(new WorldFragmentPastePolicy.HeightAnchor(0, 10, 20));
        assertDoesNotThrow(() -> valid.validateFor(fragment));

        var outside = WorldFragmentPastePolicy.replaceAll()
                .withHeightAnchor(new WorldFragmentPastePolicy.HeightAnchor(0, 12, 20));
        IllegalArgumentException outsideFailure = assertThrows(
                IllegalArgumentException.class,
                () -> outside.validateFor(fragment));
        assertEquals(
                "Paste height anchor must be inside fragment bounds",
                outsideFailure.getMessage());

        NullPointerException fragmentFailure = assertThrows(
                NullPointerException.class,
                () -> valid.validateFor(null));
        assertEquals("fragment", fragmentFailure.getMessage());
    }

    @Test
    void resultRemainsRecordWithDefensiveConflictCopy() {
        ChangePlan plan = ChangePlan.builder("paste").build();
        var conflict = new WorldFragmentPasteResult.Conflict(
                WorldFragmentPasteResult.ConflictCode.UNLOADED_REGION,
                new WorldTile(0, 3200, 3200),
                "missing");
        var source = new ArrayList<>(List.of(conflict));

        var result = new WorldFragmentPasteResult(
                plan,
                source,
                WorldFragmentPastePolicy.ConflictMode.REPORT);

        assertTrue(WorldFragmentPasteResult.class.isRecord());
        assertSame(plan, result.candidatePlan());
        source.clear();
        assertEquals(List.of(conflict), result.conflicts());
        assertThrows(UnsupportedOperationException.class,
                () -> result.conflicts().clear());
    }

    @Test
    void resultPreservesCommitPolicyAndFailureMessage() {
        ChangePlan plan = ChangePlan.builder("paste").build();
        var conflict = new WorldFragmentPasteResult.Conflict(
                WorldFragmentPasteResult.ConflictCode.UNLOADED_REGION,
                new WorldTile(0, 3200, 3200),
                "missing");

        var report = new WorldFragmentPasteResult(
                plan,
                List.of(conflict),
                WorldFragmentPastePolicy.ConflictMode.REPORT);
        assertFalse(report.canCommit());
        assertFalse(report.partial());
        IllegalStateException reportFailure = assertThrows(
                IllegalStateException.class,
                report::requireCommittablePlan);
        assertEquals(
                "Fragment paste has 1 unresolved planning conflict(s)",
                reportFailure.getMessage());

        var skip = new WorldFragmentPasteResult(
                plan,
                List.of(conflict),
                WorldFragmentPastePolicy.ConflictMode.SKIP);
        assertTrue(skip.canCommit());
        assertTrue(skip.partial());
        assertSame(plan, skip.requireCommittablePlan());

        NullPointerException windowFailure = assertThrows(
                NullPointerException.class,
                () -> report.commit(null));
        assertEquals("window", windowFailure.getMessage());
    }

    @Test
    void conflictRemainsRecordAndPreservesValidation() {
        assertTrue(WorldFragmentPasteResult.Conflict.class.isRecord());

        WorldTile tile = new WorldTile(0, 3200, 3200);
        NullPointerException codeFailure = assertThrows(
                NullPointerException.class,
                () -> new WorldFragmentPasteResult.Conflict(null, tile, "missing"));
        assertEquals("code", codeFailure.getMessage());

        NullPointerException tileFailure = assertThrows(
                NullPointerException.class,
                () -> new WorldFragmentPasteResult.Conflict(
                        WorldFragmentPasteResult.ConflictCode.UNLOADED_REGION,
                        null,
                        "missing"));
        assertEquals("tile", tileFailure.getMessage());

        IllegalArgumentException blankFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new WorldFragmentPasteResult.Conflict(
                        WorldFragmentPasteResult.ConflictCode.UNLOADED_REGION,
                        tile,
                        "   "));
        assertEquals("Paste conflict message cannot be blank", blankFailure.getMessage());
    }
}
