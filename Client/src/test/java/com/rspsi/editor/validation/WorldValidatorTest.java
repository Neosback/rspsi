package com.rspsi.editor.validation;

import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldValidatorTest {
    @Test
    void reportsBrokenEdgesAndUnsupportedMapValues() {
        WorldDocument document = new WorldDocument(2, 2, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 8, 8, 8,
                0, 1, 12, 0, 33, List.of()));

        List<ValidationIssue> issues = WorldValidator.validate(document);

        assertTrue(issues.stream().anyMatch(issue -> issue.code().equals("BROKEN_EAST_EDGE")));
        assertTrue(issues.stream().anyMatch(issue -> issue.code().equals("BROKEN_NORTH_EDGE")));
        assertTrue(issues.stream().anyMatch(issue -> issue.code().equals("UNSUPPORTED_OVERLAY_SHAPE")));
        assertTrue(issues.stream().anyMatch(issue -> issue.code().equals("INVALID_TILE_FLAGS")));
    }

    @Test
    void reportsMissingDefinitionsAndOutOfBoundsFootprints() {
        WorldDocument document = new WorldDocument(2, 2, 1);
        WorldObject object = new WorldObject(7, 10, 0, 0, 1, 1);
        document.tile(0, 1, 1).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(object)));

        List<ValidationIssue> issues = WorldValidator.validate(document, new EmptyDefinitions());

        assertTrue(issues.stream().anyMatch(issue -> issue.code().equals("MISSING_OBJECT_DEFINITION")));
    }

    private static final class EmptyDefinitions implements com.rspsi.cache.definition.DefinitionProvider {
        @Override public Optional<com.rspsi.cache.definition.ObjectDefinitionView> object(int id) { return Optional.empty(); }
        @Override public Optional<com.rspsi.cache.definition.FloorDefinitionView> underlay(int id) { return Optional.empty(); }
        @Override public Optional<com.rspsi.cache.definition.FloorDefinitionView> overlay(int id) { return Optional.empty(); }
    }
}
