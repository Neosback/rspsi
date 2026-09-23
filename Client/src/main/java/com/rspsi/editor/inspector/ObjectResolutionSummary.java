package com.rspsi.editor.inspector;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ModelGeometryView;
import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.cache.definition.ObjectDefinitionResolver;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.osrs.rules.loc.LocModelSelection;
import com.rspsi.osrs.rules.loc.WallDecorationRules;
import com.rspsi.osrs.rules.loc.WallRules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Neutral explanation of how a placed object resolves into visible model data.
 *
 * <p>This deliberately stops before scene/camera visibility. It explains the
 * deterministic definition and model-geometry stages shared by the inspector,
 * renderer, real-cache verifier, and future semantic plugin API.</p>
 */
public record ObjectResolutionSummary(
        ObjectDefinitionResolver.Status definitionStatus,
        List<Integer> transformPath,
        Optional<ObjectDefinitionSummary> displayDefinition,
        Optional<ObjectAppearanceView> displayAppearance,
        GeometryStatus geometryStatus,
        List<Integer> selectedModelIds,
        List<Integer> missingGeometryIds,
        List<Integer> emptyGeometryIds
) {
    public ObjectResolutionSummary {
        definitionStatus = Objects.requireNonNull(definitionStatus, "definitionStatus");
        transformPath = List.copyOf(Objects.requireNonNull(transformPath, "transformPath"));
        displayDefinition = Objects.requireNonNull(displayDefinition, "displayDefinition");
        displayAppearance = Objects.requireNonNull(displayAppearance, "displayAppearance");
        geometryStatus = Objects.requireNonNull(geometryStatus, "geometryStatus");
        selectedModelIds = List.copyOf(Objects.requireNonNull(selectedModelIds, "selectedModelIds"));
        missingGeometryIds = List.copyOf(Objects.requireNonNull(missingGeometryIds, "missingGeometryIds"));
        emptyGeometryIds = List.copyOf(Objects.requireNonNull(emptyGeometryIds, "emptyGeometryIds"));
    }

    public boolean definitionResolved() {
        return displayDefinition.isPresent();
    }

    public boolean transformed() {
        return transformPath.size() > 1;
    }

    public boolean renderableGeometryReady() {
        return geometryStatus == GeometryStatus.READY
                || geometryStatus == GeometryStatus.PARTIAL_GEOMETRY;
    }

    /** Compact frontend-neutral explanation suitable for inspectors and preview errors. */
    public String diagnosticSummary() {
        if (!definitionResolved()) {
            return switch (definitionStatus) {
                case MISSING_PLACED_DEFINITION -> "Object definition is missing";
                case NO_DEFAULT_TRANSFORM -> "Multiloc has no default transform for editor state";
                case MISSING_TRANSFORM_DEFINITION -> "Default transform definition is missing";
                default -> "Object definition could not be resolved: " + definitionStatus;
            };
        }
        return switch (geometryStatus) {
            case DEFINITION_UNRESOLVED -> "Object definition could not be resolved";
            case NO_MODEL_FOR_SHAPE -> "Definition has no model for this loc shape";
            case MISSING_MODEL_GEOMETRY -> "Selected model geometry is missing: " + missingGeometryIds;
            case EMPTY_RENDERABLE_GEOMETRY -> "Selected models contain no renderable triangles";
            case PARTIAL_GEOMETRY -> "Some selected model geometry is unavailable";
            case READY -> transformed()
                    ? "Resolved transform " + transformPath.get(0) + " -> " + transformPath.get(transformPath.size() - 1)
                    : "Renderable model data resolved";
        };
    }

    public static ObjectResolutionSummary capture(WorldObject object, DefinitionProvider definitions) {
        Objects.requireNonNull(object, "object");
        Objects.requireNonNull(definitions, "definitions");

        ObjectDefinitionResolver.Resolution resolution =
                new ObjectDefinitionResolver(definitions).resolveEditorDisplay(object.id());
        if (!resolution.resolved()) {
            return new ObjectResolutionSummary(
                    resolution.status(), resolution.transformPath(), Optional.empty(),
                    Optional.empty(), GeometryStatus.DEFINITION_UNRESOLVED,
                    List.of(), List.of(), List.of());
        }

        ObjectDefinitionView display = resolution.displayDefinition().orElseThrow();
        LinkedHashSet<Integer> selected = new LinkedHashSet<>();
        for (WallRules.LocModelVariant variant :
                WallRules.expandVariants(object, WallDecorationRules.DEFAULT_FALLBACK_DISPLACEMENT)) {
            selected.addAll(LocModelSelection.select(display, variant.sourceType()));
        }

        if (selected.isEmpty()) {
            return new ObjectResolutionSummary(
                    resolution.status(), resolution.transformPath(),
                    Optional.of(ObjectInspectorSnapshot.summary(display)),
                    definitions.objectAppearance(display.id()),
                    GeometryStatus.NO_MODEL_FOR_SHAPE,
                    List.of(), List.of(), List.of());
        }

        List<Integer> missing = new ArrayList<>();
        List<Integer> empty = new ArrayList<>();
        int usable = 0;
        for (int modelId : selected) {
            Optional<ModelGeometryView> geometry = definitions.modelGeometry(modelId);
            if (geometry.isEmpty()) {
                missing.add(modelId);
                continue;
            }
            ModelGeometryView value = geometry.orElseThrow();
            if (value.vertexCount() <= 0 || value.triangleCount() <= 0) {
                empty.add(modelId);
                continue;
            }
            usable++;
        }

        GeometryStatus geometryStatus;
        if (usable == selected.size()) {
            geometryStatus = GeometryStatus.READY;
        } else if (usable > 0) {
            geometryStatus = GeometryStatus.PARTIAL_GEOMETRY;
        } else if (!missing.isEmpty()) {
            geometryStatus = GeometryStatus.MISSING_MODEL_GEOMETRY;
        } else {
            geometryStatus = GeometryStatus.EMPTY_RENDERABLE_GEOMETRY;
        }

        return new ObjectResolutionSummary(
                resolution.status(), resolution.transformPath(),
                Optional.of(ObjectInspectorSnapshot.summary(display)),
                definitions.objectAppearance(display.id()),
                geometryStatus, List.copyOf(selected), missing, empty);
    }

    public enum GeometryStatus {
        DEFINITION_UNRESOLVED,
        NO_MODEL_FOR_SHAPE,
        MISSING_MODEL_GEOMETRY,
        EMPTY_RENDERABLE_GEOMETRY,
        PARTIAL_GEOMETRY,
        READY
    }
}
