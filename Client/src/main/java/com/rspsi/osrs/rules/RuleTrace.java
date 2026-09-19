package com.rspsi.osrs.rules;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.osrs.rules.loc.LocPlacementRules;
import com.rspsi.osrs.rules.loc.LocShapeCatalog;
import com.rspsi.osrs.rules.loc.LocShapeCatalog.LocShapeDescriptor;
import com.rspsi.osrs.rules.loc.WallDecorationRules;
import com.rspsi.osrs.rules.loc.WallRules;
import com.rspsi.osrs.rules.loc.WallRules.LocModelVariant;
import com.rspsi.osrs.rules.model.ModelTransformPipeline;
import com.rspsi.osrs.rules.tile.BridgeRules;
import com.rspsi.osrs.rules.tile.TileFlagRules;
import com.rspsi.osrs.rules.tile.TileFlagRules.ResolvedTileFlags;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Diagnostic trace recording all formal OSRS rules applied to a tile or placed object.
 *
 * <p>Enables live "Explain Rendering" inspection in the editor UI.</p>
 */
public final class RuleTrace {
    private RuleTrace() {}

    public record ObjectRuleTrace(
            int objectId,
            String objectName,
            LocShapeDescriptor shapeDescriptor,
            int variantCount,
            boolean mirrorApplied,
            int displacementUsed,
            boolean mergeNormalsEligible,
            boolean contourGroundApplied,
            int contourGroundType,
            int placementHeight
    ) {}

    public record TileRuleTrace(
            TileCoordinate coordinate,
            ResolvedTileFlags flags,
            int effectivePlane,
            int underlayId,
            int overlayId,
            int overlayShape,
            int overlayRotation,
            int elevation
    ) {}

    /** Generates a complete rule trace for a placed object instance. */
    public static Optional<ObjectRuleTrace> traceObject(
            WorldObject object,
            WorldDocument document,
            DefinitionProvider definitions
    ) {
        Objects.requireNonNull(object, "object");
        Objects.requireNonNull(document, "document");
        if (definitions == null) return Optional.empty();

        Optional<ObjectDefinitionView> def = definitions.object(object.id());
        if (def.isEmpty()) return Optional.empty();
        ObjectDefinitionView definition = def.get();
        ObjectAppearanceView appearance = definitions.objectAppearance(object.id())
                .orElseGet(ObjectAppearanceView::empty);

        LocShapeDescriptor shape = LocShapeCatalog.get(object.type()).orElse(null);
        int displacement = WallDecorationRules.resolveDisplacement(object, appearance, document, definitions);
        List<LocModelVariant> variants = WallRules.expandVariants(object, displacement);

        boolean mirror = !variants.isEmpty() && ModelTransformPipeline.shouldMirror(
                appearance.rotated(), variants.get(0).rotation());

        int footprintWidth = LocPlacementRules.rotatedWidth(definition.width(), definition.length(), object.rotation());
        int footprintLength = LocPlacementRules.rotatedLength(definition.width(), definition.length(), object.rotation());
        int placementHeight = (document.tile(object.plane(), object.x(), object.y()).snapshot().southWestHeight());

        return Optional.of(new ObjectRuleTrace(
                object.id(),
                definition.name() == null ? "" : definition.name(),
                shape,
                variants.size(),
                mirror,
                displacement,
                appearance.mergeNormals(),
                appearance.contourGroundType() >= 0,
                appearance.contourGroundType(),
                placementHeight
        ));
    }

    /** Generates a complete rule trace for a terrain tile. */
    public static TileRuleTrace traceTile(WorldDocument document, TileCoordinate coordinate) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(coordinate, "coordinate");
        TileSnapshot snap = document.tile(coordinate.plane(), coordinate.x(), coordinate.y()).snapshot();
        ResolvedTileFlags flags = TileFlagRules.resolve(snap.flags());
        int effectivePlane = BridgeRules.resolveEffectivePlane(coordinate.plane(), snap.flags());
        int elevation = (snap.southWestHeight() + snap.southEastHeight()
                + snap.northEastHeight() + snap.northWestHeight()) >> 2;

        return new TileRuleTrace(
                coordinate,
                flags,
                effectivePlane,
                snap.underlayId(),
                snap.overlayId(),
                snap.overlayShape(),
                snap.overlayRotation(),
                elevation
        );
    }
}
