package com.rspsi.editor.inspector;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ModelGeometryView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.render.RenderScene;
import com.rspsi.editor.render.RenderSceneBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectSceneResolutionAuditTest {
    @Test
    void accountsForSubmittedMissingAndUnresolvedPlacements() {
        WorldDocument document = new WorldDocument(4, 4, 1);
        WorldObject ready = new WorldObject(100, 10, 0, 0, 0, 0);
        WorldObject missingGeometry = new WorldObject(200, 10, 0, 0, 1, 0);
        WorldObject missingDefinition = new WorldObject(300, 10, 0, 0, 2, 0);
        WorldObject wrongShape = new WorldObject(400, 22, 0, 0, 3, 0);

        document.tile(0, 0, 0).restore(tile(ready));
        document.tile(0, 1, 0).restore(tile(missingGeometry));
        document.tile(0, 2, 0).restore(tile(missingDefinition));
        document.tile(0, 3, 0).restore(tile(wrongShape));

        DefinitionProvider definitions = new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) {
                return switch (id) {
                    case 100 -> Optional.of(new ObjectDefinitionView(
                            id, "ready", 1, 1, List.of(), new int[]{7}));
                    case 200 -> Optional.of(new ObjectDefinitionView(
                            id, "missing geometry", 1, 1, List.of(), new int[]{8}));
                    case 400 -> Optional.of(new ObjectDefinitionView(
                            id, "wrong shape", 1, 1, List.of(), new int[]{9}));
                    default -> Optional.empty();
                };
            }

            @Override public Optional<ModelGeometryView> modelGeometry(int id) {
                return id == 7 ? Optional.of(triangle(7)) : Optional.empty();
            }

            @Override public Optional<FloorDefinitionView> underlay(int id) { return Optional.empty(); }
            @Override public Optional<FloorDefinitionView> overlay(int id) { return Optional.empty(); }
        };

        RenderScene scene = new RenderSceneBuilder(definitions).build(document);
        ObjectSceneResolutionAudit.Report report =
                ObjectSceneResolutionAudit.audit(document, definitions, scene);

        assertEquals(4, report.entries().size());
        assertEquals(1, report.submittedCount());
        assertEquals(1, report.failureCount());
        assertEquals(2, report.warningCount());

        assertEquals(ObjectSceneResolutionAudit.Stage.PACKET_SUBMITTED,
                report.entries().get(0).stage());
        assertEquals(ObjectSceneResolutionAudit.Stage.MISSING_MODEL_GEOMETRY,
                report.entries().get(1).stage());
        assertEquals(ObjectSceneResolutionAudit.Stage.DEFINITION_UNRESOLVED,
                report.entries().get(2).stage());
        assertEquals(ObjectSceneResolutionAudit.Stage.NO_MODEL_FOR_SHAPE,
                report.entries().get(3).stage());
        assertTrue(report.failureCount() > 0);
    }

    @Test
    void exactDuplicatePlacementsRemainIndividuallyAccounted() {
        WorldObject duplicate = new WorldObject(100, 10, 0, 0, 0, 0);
        WorldDocument document = new WorldDocument(1, 1, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of(duplicate, duplicate)));

        DefinitionProvider definitions = new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) {
                return Optional.of(new ObjectDefinitionView(
                        id, "duplicate", 1, 1, List.of(), new int[]{7}));
            }

            @Override public Optional<ModelGeometryView> modelGeometry(int id) {
                return Optional.of(triangle(id));
            }

            @Override public Optional<FloorDefinitionView> underlay(int id) { return Optional.empty(); }
            @Override public Optional<FloorDefinitionView> overlay(int id) { return Optional.empty(); }
        };

        RenderScene scene = new RenderSceneBuilder(definitions).build(document);
        ObjectSceneResolutionAudit.Report report =
                ObjectSceneResolutionAudit.audit(document, definitions, scene);

        assertEquals(2, report.entries().size());
        assertEquals(0, report.entries().get(0).occurrence());
        assertEquals(1, report.entries().get(1).occurrence());
        assertEquals(2, report.submittedCount());
    }

    private static TileSnapshot tile(WorldObject object) {
        return new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0, List.of(object));
    }

    private static ModelGeometryView triangle(int id) {
        return new ModelGeometryView(
                id,
                new int[]{0, 0, 0, 64, 0, 0, 0, 0, 64},
                new int[]{0, 1, 2},
                new short[]{100},
                new int[]{0},
                new int[]{-1});
    }
}
