package com.rspsi.editor.validation;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ObjectCollisionView;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Cheap deterministic document checks used by save/parity gates and UI diagnostics. */
public final class WorldValidator {
    private WorldValidator() {
    }

    public static List<ValidationIssue> validate(WorldDocument document) {
        return validate(document, null);
    }

    /** Adds definition-backed object checks when a cache provider is available. */
    public static List<ValidationIssue> validate(WorldDocument document, DefinitionProvider definitions) {
        Objects.requireNonNull(document, "document");
        List<ValidationIssue> issues = new ArrayList<>();
        for (int plane = 0; plane < document.planes(); plane++) {
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    TileCoordinate coordinate = new TileCoordinate(plane, x, y);
                    TileSnapshot tile = document.tile(coordinate).snapshot();
                    if (tile.overlayShape() > 11) {
                        issues.add(error("UNSUPPORTED_OVERLAY_SHAPE",
                                "OSRS map encoding supports overlay shapes 0 through 11", coordinate));
                    }
                    if (tile.flags() < 0 || tile.flags() > 32) {
                        issues.add(error("INVALID_TILE_FLAGS",
                                "Tile flags must fit the OSRS map rule range 0 through 32", coordinate));
                    }
                    Set<WorldObject> seen = new HashSet<>();
                    for (WorldObject object : tile.objects()) {
                        if (!seen.add(object)) {
                            issues.add(error("DUPLICATE_OBJECT",
                                    "The same object appears more than once on a tile", coordinate));
                        }
                        if (!object.category().isKnown()) {
                            issues.add(error("INVALID_OBJECT_TYPE",
                                    "OSRS location shape must be between 0 and 22", coordinate));
                        }
                        if (definitions != null) {
                            ObjectCollisionView definition = definitions.objectCollision(object.id()).orElse(null);
                            if (definition == null) {
                                issues.add(warning("MISSING_OBJECT_DEFINITION",
                                        "No collision definition is available for object " + object.id(), coordinate));
                            } else {
                                int width = definition.width();
                                int length = definition.length();
                                if (object.rotation() == 1 || object.rotation() == 3) {
                                    int swap = width;
                                    width = length;
                                    length = swap;
                                }
                                if (object.x() + width > document.width()
                                        || object.y() + length > document.length()) {
                                    issues.add(error("OBJECT_OUT_OF_BOUNDS",
                                            "Object footprint crosses the loaded document boundary", coordinate));
                                }
                            }
                        }
                    }
                }
            }
        }
        checkSharedEdges(document, issues);
        return List.copyOf(issues);
    }

    private static void checkSharedEdges(WorldDocument document, List<ValidationIssue> issues) {
        for (int plane = 0; plane < document.planes(); plane++) {
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    TileSnapshot tile = document.tile(plane, x, y).snapshot();
                    if (x + 1 < document.width()
                            && tile.southEastHeight() != document.tile(plane, x + 1, y).snapshot().southWestHeight()) {
                        issues.add(error("BROKEN_EAST_EDGE", "Adjacent tile east edge heights do not match", 
                                new TileCoordinate(plane, x, y)));
                    }
                    if (y + 1 < document.length()
                            && tile.northWestHeight() != document.tile(plane, x, y + 1).snapshot().southWestHeight()) {
                        issues.add(error("BROKEN_NORTH_EDGE", "Adjacent tile north edge heights do not match",
                                new TileCoordinate(plane, x, y)));
                    }
                }
            }
        }
    }

    private static ValidationIssue error(String code, String message, TileCoordinate location) {
        return new ValidationIssue(ValidationIssue.Severity.ERROR, code, message, location);
    }

    private static ValidationIssue warning(String code, String message, TileCoordinate location) {
        return new ValidationIssue(ValidationIssue.Severity.WARNING, code, message, location);
    }
}
