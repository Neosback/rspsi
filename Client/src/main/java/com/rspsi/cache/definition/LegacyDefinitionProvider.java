package com.rspsi.cache.definition;

import com.jagex.cache.def.Floor;
import com.jagex.cache.def.ObjectDefinition;
import com.jagex.cache.loader.floor.FloorDefinitionLoader;
import com.jagex.cache.loader.object.ObjectDefinitionLoader;

import java.util.Arrays;
import java.util.Optional;

/** Adapter over the current static legacy definition loaders. */
public final class LegacyDefinitionProvider implements DefinitionProvider {

    @Override
    public Optional<ObjectDefinitionView> object(int id) {
        try {
            ObjectDefinition definition = ObjectDefinitionLoader.lookup(id);
            if (definition == null) {
                return Optional.empty();
            }
            return Optional.of(new ObjectDefinitionView(
                    id,
                    definition.getName(),
                    definition.getWidth(),
                    definition.getLength(),
                    definition.getInteractions() == null
                            ? java.util.List.of()
                            : Arrays.stream(definition.getInteractions())
                            .filter(java.util.Objects::nonNull)
                            .toList(),
                    definition.getModelIds(), definition.getModelTypes(), -1,
                    definition.isInteractive()));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ObjectCollisionView> objectCollision(int id) {
        try {
            ObjectDefinition definition = ObjectDefinitionLoader.lookup(id);
            if (definition == null) {
                return Optional.empty();
            }
            return Optional.of(new ObjectCollisionView(id, Math.max(1, definition.getWidth()),
                    Math.max(1, definition.getLength()), definition.isSolid() ? 2 : 0,
                    definition.isImpenetrable(), definition.isHollow()));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ObjectAppearanceView> objectAppearance(int id) {
        try {
            ObjectDefinition definition = ObjectDefinitionLoader.lookup(id);
            if (definition == null) return Optional.empty();
            return Optional.of(new ObjectAppearanceView(
                    definition.getAnimation(), definition.isContouredGround(),
                    Math.max(1, definition.getScaleX()), Math.max(1, definition.getScaleY()),
                    Math.max(1, definition.getScaleZ()), definition.getTranslateX(),
                    definition.getTranslateY(), definition.getTranslateZ(),
                    ObjectAppearanceView.pairs(definition.getOriginalColours(), definition.getReplacementColours()),
                    ObjectAppearanceView.pairs(definition.getRetextureToFind(), definition.getTextureToReplace()),
                    definition.isCastsShadow(), definition.occludes(), false, false,
                    definition.getAmbientLighting(), definition.getLightDiffusion() * 25,
                    definition.getDecorDisplacement(), definition.isContouredGround() ? 1 : -1, 0,
                    false, definition.isInverted(), definition.obstructsGround(), 0));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<FloorDefinitionView> underlay(int id) {
        return floor(id, true);
    }

    @Override
    public Optional<FloorDefinitionView> overlay(int id) {
        return floor(id, false);
    }

    private Optional<FloorDefinitionView> floor(int id, boolean underlay) {
        try {
            Floor floor = underlay
                    ? FloorDefinitionLoader.getUnderlay(id)
                    : FloorDefinitionLoader.getOverlay(id);
            if (floor == null) {
                return Optional.empty();
            }
            return Optional.of(new FloorDefinitionView(
                    id,
                    floor.getTexture(),
                    floor.getRgb(),
                    floor.getHue(),
                    floor.getSaturation(),
                    floor.getLuminance(),
                    floor.getWeightedHue(),
                    floor.getChroma()));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }
}
