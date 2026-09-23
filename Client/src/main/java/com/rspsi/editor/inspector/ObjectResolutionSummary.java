package com.rspsi.editor.inspector;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ModelGeometryView;
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
        GeometryStatus geometryStatus,
        List<Integer> selectedModelIds,
        List<Integer> missingGeometryIds,
        List<Integer> emptyGeometryIds
) {
    public ObjectResolutionSummary {
        definitionStatus = Objects.requireNonNull(definitionStatus, "definitionStatus");
        transformPath = List.copyOf(Objects.requireNonNull(transformPath, "transformPath"));
        displayDefinition = Objects.requireNonNull(displayDefinition, "displayDefinition");
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
                case HIDDEN_IN_VAR_STATE -> "Multiloc shows nothing in the current var state (fresh account)";
                case MISSING_TRANSFORM_DEFINITION -> "Default transform definition is missing";
                default -> "Object definition could not be resolved: " + definitionStatus;
            };
        }
        return switch (geometryStatus) {
            case DEFINITION_UNRESOLVED -> "Object definition could not be resolved";
            case NO_MODEL_FOR_SHAPE -> "Definition has no model for this loc shape";
            case MISSING_MODEL_GEOMETRY -> "Selected model geometry is missing: " + missingGeometryIds;
            case EMPTY_RENDERABLE_GEOMETRY -> "Selected models are authored empty (invisible loc): " + emptyGeometryIds;
            case PARTIAL_GEOMETRY -> "Some selected model geometry is unavailable";
            case READY -> transformed()
                    ? "Resolved transform " + transformPath.get(0) + " -> " + transformPath.get(transformPath.size() - 1)
                    + (definitionStatus == ObjectDefinitionResolver.Status.RESOLVED_NESTED_TRANSFORM_CHILD
                    ? " (display definition has its own transforms; not applied)" : "")
                    : "Renderable model data resolved";
        };
    }

    public static ObjectResolutionSummary capture(WorldObject object, DefinitionProvider definitions) {
        return capture(object, definitions, com.rspsi.cache.definition.ObjectVarState.freshAccount());
    }

    /** Resolution for a specific player var state (e.g. the Studio's simulated player). */
    public static ObjectResolutionSummary capture(WorldObject object, DefinitionProvider definitions,
                                                  com.rspsi.cache.definition.ObjectVarState varState) {
        Objects.requireNonNull(object, "object");
        Objects.requireNonNull(definitions, "definitions");

        ObjectDefinitionResolver.Resolution resolution =
                new ObjectDefinitionResolver(definitions, varState).resolveEditorDisplay(object.id());
        if (!resolution.resolved()) {
            return new ObjectResolutionSummary(
                    resolution.status(), resolution.transformPath(), Optional.empty(),
                    GeometryStatus.DEFINITION_UNRESOLVED,
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

        // An authored-empty model renders nothing in the client either, so it
        // does not make an otherwise usable selection partial.
        GeometryStatus geometryStatus;
        if (usable > 0 && missing.isEmpty()) {
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
