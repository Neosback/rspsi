package com.rspsi.editor.inspector;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ObjectCollisionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.cache.definition.ModelGeometryView;
import com.rspsi.editor.model.ObjectCategory;
import com.rspsi.editor.model.WorldObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectInspectorSnapshotTest {
    @Test
    void flattensDefinitionAndCollisionDataForAnyFrontend() {
        WorldObject object = new WorldObject(42, 10, 1, 2, 3208, 3218);
        ObjectInspectorSnapshot snapshot = ObjectInspectorSnapshot.capture(object, new Definitions());

        assertEquals(ObjectCategory.GROUND, snapshot.category());
        assertEquals("Straight game object", snapshot.shapeName());
        assertEquals(3208, snapshot.x());
        assertEquals(3218, snapshot.y());
        assertEquals(2, snapshot.definition().orElseThrow().width());
        assertEquals(3, snapshot.definition().orElseThrow().length());
        assertEquals(List.of(500, 501), snapshot.definition().orElseThrow().modelIds());
        assertEquals(List.of("Open", "Search"), snapshot.definition().orElseThrow().actions());
        assertTrue(snapshot.collision().orElseThrow().blockProjectile());
        assertEquals(700, snapshot.appearance().orElseThrow().animationId());
        assertTrue(snapshot.appearance().orElseThrow().contouredGround());
        assertEquals(ObjectResolutionSummary.GeometryStatus.READY,
                snapshot.resolution().geometryStatus());
        assertEquals(List.of(500, 501), snapshot.resolution().selectedModelIds());
    }

    @Test
    void reportsPlacedAndDisplayDefinitionsForMultiloc() {
        WorldObject object = new WorldObject(1000, 10, 0, 0, 1, 1);
        DefinitionProvider definitions = new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) {
                if (id == 1000) {
                    return Optional.of(new ObjectDefinitionView(
                            id, "null", 2, 3, List.of(), new int[0], new int[0],
                            -1, false, 1234, -1, new int[]{2000}, 2000));
                }
                if (id == 2000) {
                    return Optional.of(new ObjectDefinitionView(
                            id, "Bush", 1, 1, List.of(), new int[]{7}, new int[]{10},
                            -1, false));
                }
                return Optional.empty();
            }

            @Override public Optional<ObjectAppearanceView> objectAppearance(int id) {
                return id == 2000
                        ? Optional.of(ObjectAppearanceView.empty())
                        : Optional.empty();
            }

            @Override public Optional<ModelGeometryView> modelGeometry(int id) {
                return id == 7 ? Optional.of(new ModelGeometryView(
                        7,
                        new int[]{0, 0, 0, 64, 0, 0, 0, 0, 64},
                        new int[]{0, 1, 2},
                        new short[]{100},
                        new int[]{0},
                        new int[]{-1})) : Optional.empty();
            }

            @Override public Optional<FloorDefinitionView> underlay(int id) { return Optional.empty(); }
            @Override public Optional<FloorDefinitionView> overlay(int id) { return Optional.empty(); }
        };

        ObjectInspectorSnapshot snapshot = ObjectInspectorSnapshot.capture(object, definitions);

        assertEquals("Object #1000", snapshot.definition().orElseThrow().name());
        assertEquals("Bush", snapshot.resolution().displayDefinition().orElseThrow().name());
        assertEquals(List.of(1000, 2000), snapshot.resolution().transformPath());
        assertEquals(List.of(7), snapshot.resolution().selectedModelIds());
        assertEquals(ObjectResolutionSummary.GeometryStatus.READY,
                snapshot.resolution().geometryStatus());
    }

    @Test
    void keepsMissingBackendDataExplicit() {
        ObjectInspectorSnapshot snapshot = ObjectInspectorSnapshot.capture(
                new WorldObject(99, 23, 0, 0, 1, 1), new Definitions());

        assertEquals(ObjectCategory.UNKNOWN, snapshot.category());
        assertEquals("Unknown shape", snapshot.shapeName());
        assertTrue(snapshot.definition().isEmpty());
        assertTrue(snapshot.collision().isEmpty());
    }

    @Test
    void definitionModelArraysUseValueEqualityAcrossAdapterBoundaries() {
        ObjectDefinitionView first = new ObjectDefinitionView(1, "Tree", 1, 1,
                List.of(), new int[]{10, 11});
        ObjectDefinitionView equal = new ObjectDefinitionView(1, "Tree", 1, 1,
                List.of(), new int[]{10, 11});
        ObjectDefinitionView different = new ObjectDefinitionView(1, "Tree", 1, 1,
                List.of(), new int[]{10, 12});

        assertEquals(equal, first);
        assertEquals(equal.hashCode(), first.hashCode());
        assertNotEquals(different, first);
    }

    private static final class Definitions implements DefinitionProvider {
        @Override
        public Optional<ObjectDefinitionView> object(int id) {
            return id == 42
                    ? Optional.of(new ObjectDefinitionView(42, "Test gate", 2, 3,
                    List.of("Open", "Search"), new int[]{500, 501}))
                    : Optional.empty();
        }

        @Override
        public Optional<ObjectCollisionView> objectCollision(int id) {
            return id == 42
                    ? Optional.of(new ObjectCollisionView(42, 2, 3, 2, true, false))
                    : Optional.empty();
        }

        @Override
        public Optional<ObjectAppearanceView> objectAppearance(int id) {
            return id == 42 ? Optional.of(new ObjectAppearanceView(700, true,
                    128, 128, 128, 0, 0, 0,
                    java.util.Map.of(10, 20), java.util.Map.of())) : Optional.empty();
        }

        @Override
        public Optional<ModelGeometryView> modelGeometry(int id) {
            if (id != 500 && id != 501) return Optional.empty();
            return Optional.of(new ModelGeometryView(
                    id,
                    new int[]{0, 0, 0, 64, 0, 0, 0, 0, 64},
                    new int[]{0, 1, 2},
                    new short[]{100},
                    new int[]{0},
                    new int[]{-1}));
        }

        @Override public Optional<FloorDefinitionView> underlay(int id) { return Optional.empty(); }
        @Override public Optional<FloorDefinitionView> overlay(int id) { return Optional.empty(); }
    }
}
