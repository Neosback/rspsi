package com.rspsi.cache.definition;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectDefinitionResolverTest {
    @Test
    void untransformedDefinitionResolvesToItself() {
        DefinitionProvider definitions = definitions(
                object(100, "Tree", new int[]{7}, new int[]{10}, -1, new int[0]));

        ObjectDefinitionResolver.Resolution resolution =
                new ObjectDefinitionResolver(definitions).resolveEditorDisplay(100);

        assertEquals(ObjectDefinitionResolver.Status.RESOLVED, resolution.status());
        assertEquals(100, resolution.displayId());
        assertEquals(List.of(100), resolution.transformPath());
        assertFalse(resolution.transformed());
    }

    @Test
    void transformedDefinitionUsesDefaultEvenWhenPlacedShellHasModels() {
        DefinitionProvider definitions = definitions(
                object(100, "Shell", new int[]{1}, new int[]{10}, 200, new int[]{201, 200}),
                object(200, "Bush", new int[]{7}, new int[]{10}, -1, new int[0]),
                object(201, "Other state", new int[]{8}, new int[]{10}, -1, new int[0]));

        ObjectDefinitionResolver.Resolution resolution =
                new ObjectDefinitionResolver(definitions).resolveEditorDisplay(100);

        assertEquals(ObjectDefinitionResolver.Status.RESOLVED, resolution.status());
        assertEquals(200, resolution.displayId());
        assertEquals(List.of(100, 200), resolution.transformPath());
        assertTrue(resolution.transformed());
    }

    @Test
    void missingDefaultIsExplicitlyUnresolved() {
        DefinitionProvider definitions = definitions(
                object(100, "Shell", new int[0], new int[0], -1, new int[]{200}));

        ObjectDefinitionResolver.Resolution resolution =
                new ObjectDefinitionResolver(definitions).resolveEditorDisplay(100);

        assertEquals(ObjectDefinitionResolver.Status.NO_DEFAULT_TRANSFORM, resolution.status());
        assertFalse(resolution.resolved());
        assertEquals(-1, resolution.displayId());
    }

    @Test
    void missingTransformedDefinitionIsExplicitlyUnresolved() {
        DefinitionProvider definitions = definitions(
                object(100, "Shell", new int[0], new int[0], 999, new int[]{999}));

        ObjectDefinitionResolver.Resolution resolution =
                new ObjectDefinitionResolver(definitions).resolveEditorDisplay(100);

        assertEquals(ObjectDefinitionResolver.Status.MISSING_TRANSFORM_DEFINITION,
                resolution.status());
        assertEquals(999, resolution.unresolvedDefinitionId());
        assertFalse(resolution.resolved());
    }

    @Test
    void nestedTransformChildIsReportedButNotRecursivelyResolved() {
        DefinitionProvider definitions = definitions(
                object(100, "Shell", new int[0], new int[0], 200, new int[]{200}),
                object(200, "Nested shell", new int[]{7}, new int[]{10}, 300, new int[]{300}),
                object(300, "Grandchild", new int[]{8}, new int[]{10}, -1, new int[0]));

        ObjectDefinitionResolver.Resolution resolution =
                new ObjectDefinitionResolver(definitions).resolveEditorDisplay(100);

        assertEquals(ObjectDefinitionResolver.Status.RESOLVED_NESTED_TRANSFORM_CHILD,
                resolution.status());
        assertTrue(resolution.resolved());
        assertEquals(200, resolution.displayId(),
                "DynamicObject performs one ObjectComposition.transform() step");
        assertEquals(List.of(100, 200), resolution.transformPath());
    }

    @Test
    void clientNullNameUsesSafeEditorLabel() {
        ObjectDefinitionView definition =
                object(42, "null", new int[]{7}, new int[]{10}, -1, new int[0]);

        assertFalse(definition.hasDisplayName());
        assertEquals("Object #42", definition.displayName());
    }

    private static ObjectDefinitionView object(int id, String name, int[] modelIds,
                                               int[] modelTypes, int defaultTransform,
                                               int[] transforms) {
        return new ObjectDefinitionView(id, name, 1, 1, List.of(),
                modelIds, modelTypes, -1, false,
                transforms.length == 0 ? -1 : 1234,
                -1, transforms, defaultTransform);
    }

    private static DefinitionProvider definitions(ObjectDefinitionView... objects) {
        java.util.Map<Integer, ObjectDefinitionView> byId = new java.util.HashMap<>();
        for (ObjectDefinitionView object : objects) byId.put(object.id(), object);
        return new DefinitionProvider() {
            @Override
            public Optional<ObjectDefinitionView> object(int id) {
                return Optional.ofNullable(byId.get(id));
            }

            @Override
            public Optional<FloorDefinitionView> underlay(int id) {
                return Optional.empty();
            }

            @Override
            public Optional<FloorDefinitionView> overlay(int id) {
                return Optional.empty();
            }
        };
    }
}
