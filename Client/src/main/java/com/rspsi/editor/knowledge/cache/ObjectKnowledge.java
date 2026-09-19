package com.rspsi.editor.knowledge.cache;

import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.cache.definition.ObjectCollisionView;
import com.rspsi.cache.definition.ObjectDefinitionView;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Layer 1 Cache Fact: Exact, zero-heuristics properties of an authored object definition.
 */
public record ObjectKnowledge(
        int id,
        String name,
        int width,
        int length,
        List<String> actions,
        Set<Integer> modelIds,
        Set<Integer> locShapes,
        boolean interactive,
        boolean solid,
        boolean castsShadow,
        boolean mergeNormals,
        int contourGroundType,
        int animationId,
        Map<Integer, Integer> recolors,
        Map<Integer, Integer> retextures
) {
    public static ObjectKnowledge from(
            ObjectDefinitionView definition,
            ObjectAppearanceView appearance,
            ObjectCollisionView collision
    ) {
        Objects.requireNonNull(definition, "definition");
        ObjectAppearanceView app = appearance != null ? appearance : ObjectAppearanceView.empty();
        boolean solid = collision != null && collision.blockWalk() != 0;

        Set<Integer> models = IntStream.of(definition.modelIds()).boxed().collect(Collectors.toUnmodifiableSet());
        Set<Integer> shapes = IntStream.of(definition.modelTypes()).boxed().collect(Collectors.toUnmodifiableSet());

        return new ObjectKnowledge(
                definition.id(),
                definition.name() == null ? "" : definition.name(),
                definition.width(),
                definition.length(),
                List.copyOf(definition.interactions()),
                models,
                shapes,
                definition.interactive(),
                solid,
                app.castsShadow(),
                app.mergeNormals(),
                app.contourGroundType(),
                app.animationId(),
                Map.copyOf(app.recolors()),
                Map.copyOf(app.retextures())
        );
    }
}
