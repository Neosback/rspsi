package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.ModelGeometryView;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;

import java.util.Objects;

/**
 * Corner-based terrain shadow contributions used by OSRS tile lighting.
 *
 * <p>The legacy client stores shadow strength on the shared terrain-corner
 * grid, not on rendered triangles. Keeping that grid as a neutral contract
 * lets wall placement, model preparation, and lighting remain separate while
 * preserving the client's later weighted corner-penalty calculation.</p>
 */
public final class TerrainShadowMap {
    private final int[][][] cornerStrength;

    public TerrainShadowMap(int planes, int width, int length) {
        if (planes <= 0 || width <= 0 || length <= 0) {
            throw new IllegalArgumentException("Terrain shadow dimensions must be positive");
        }
        cornerStrength = new int[planes][width + 1][length + 1];
    }

    /** Adds the strongest contribution at one shared terrain corner. */
    public void addCornerShadow(int plane, int x, int y, int strength) {
        if (plane < 0 || plane >= cornerStrength.length || strength < 0) {
            throw new IllegalArgumentException("Invalid terrain shadow contribution");
        }
        if (x < 0 || x >= cornerStrength[plane].length
                || y < 0 || y >= cornerStrength[plane][x].length) {
            return;
        }
        cornerStrength[plane][x][y] = Math.max(cornerStrength[plane][x][y], strength);
    }

    public int cornerStrength(int plane, int x, int y) {
        if (plane < 0 || plane >= cornerStrength.length
                || x < 0 || x >= cornerStrength[plane].length
                || y < 0 || y >= cornerStrength[plane][x].length) {
            return 0;
        }
        return cornerStrength[plane][x][y];
    }

    /**
     * Adds the wall shadow placements used by object shapes 0, 1, and 3.
     * Shape 2 contributes occlusion flags but no terrain shadow in the client.
     */
    public void addWallShadow(WorldObject object, ObjectAppearanceView appearance) {
        Objects.requireNonNull(object, "object");
        Objects.requireNonNull(appearance, "appearance");
        if (!appearance.castsShadow()) return;
        int plane = object.plane();
        int x = object.x();
        int y = object.y();
        switch (object.type()) {
            case 0 -> addTwoCornerWallShadow(plane, x, y, object.rotation(), 50);
            case 1, 3 -> addOneCornerWallShadow(plane, x, y, object.rotation(), 50);
            default -> {
                // Game objects and wall decorations require model bounds or
                // another explicit source rule; do not invent their shadow.
            }
        }
    }

    /**
     * Adds the client light-occlusion footprint for a clipped static game
     * object (location types 10 and 11). This is deliberately separate from
     * wall shadows: TSPS/RuneLite store this as terrain-light occlusion, not
     * as a renderer occluder and not as collision.
     */
    public void addClippedObjectOcclusion(WorldObject object,
                                          ObjectAppearanceView appearance,
                                          ObjectDefinitionView definition,
                                          DefinitionProvider definitions) {
        Objects.requireNonNull(object, "object");
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(definitions, "definitions");
        if (!appearance.modelClipped() || (object.type() != 10 && object.type() != 11)) return;

        int width = Math.max(1, definition.width());
        int length = Math.max(1, definition.length());
        if ((object.rotation() & 1) != 0) {
            int swap = width;
            width = length;
            length = swap;
        }
        int strength = modelOcclusionStrength(object, appearance, definition, definitions);
        for (int x = object.x(); x <= object.x() + width; x++) {
            for (int y = object.y(); y <= object.y() + length; y++) {
                addCornerShadow(object.plane(), x, y, strength);
            }
        }
    }

    /** Builds contributions for all objects whose definition exposes appearance data. */
    public static TerrainShadowMap from(WorldDocument document, DefinitionProvider definitions) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(definitions, "definitions");
        TerrainShadowMap result = new TerrainShadowMap(document.planes(),
                document.width(), document.length());
        for (int plane = 0; plane < document.planes(); plane++) {
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    for (WorldObject object : document.tile(plane, x, y).objects()) {
                        definitions.objectAppearance(object.id()).ifPresent(appearance -> {
                            result.addWallShadow(object, appearance);
                            definitions.object(object.id()).ifPresent(definition ->
                                    result.addClippedObjectOcclusion(object, appearance,
                                            definition, definitions));
                        });
                    }
                }
            }
        }
        return result;
    }

    private void addTwoCornerWallShadow(int plane, int x, int y, int rotation, int strength) {
        switch (rotation & 3) {
            case 0 -> {
                addCornerShadow(plane, x, y, strength);
                addCornerShadow(plane, x, y + 1, strength);
            }
            case 1 -> {
                addCornerShadow(plane, x, y + 1, strength);
                addCornerShadow(plane, x + 1, y + 1, strength);
            }
            case 2 -> {
                addCornerShadow(plane, x + 1, y, strength);
                addCornerShadow(plane, x + 1, y + 1, strength);
            }
            case 3 -> {
                addCornerShadow(plane, x, y, strength);
                addCornerShadow(plane, x + 1, y, strength);
            }
        }
    }

    private static int modelOcclusionStrength(WorldObject object,
                                              ObjectAppearanceView appearance,
                                              ObjectDefinitionView definition,
                                              DefinitionProvider definitions) {
        int geometryStrength = 0;
        boolean geometryAvailable = false;
        int[] modelIds = definition.modelIds();
        int[] modelTypes = definition.modelTypes();
        int selectedType = object.type() == 11 ? 10 : object.type();
        for (int index = 0; index < modelIds.length; index++) {
            if (modelTypes.length > 0 && (index >= modelTypes.length || modelTypes[index] != selectedType)) {
                continue;
            }
            ModelGeometryView geometry = definitions.modelGeometry(modelIds[index]).orElse(null);
            if (geometry == null) continue;
            geometryAvailable = true;
            int radius = 0;
            int[] positions = geometry.vertexPositions();
            for (int vertex = 0; vertex + 2 < positions.length; vertex += 3) {
                double scaledX = positions[vertex] * appearance.scaleX() / 128.0;
                double scaledZ = positions[vertex + 2] * appearance.scaleZ() / 128.0;
                radius = Math.max(radius, (int) Math.sqrt(scaledX * scaledX + scaledZ * scaledZ));
            }
            geometryStrength = Math.max(geometryStrength, radius / 4);
        }
        // SceneBuilder defaults to 15 only when the entity is not a decoded
        // Model. Once geometry is available, the client uses its actual XZ
        // radius, including values below 15.
        return geometryAvailable ? Math.min(30, geometryStrength) : 15;
    }

    private void addOneCornerWallShadow(int plane, int x, int y, int rotation, int strength) {
        switch (rotation & 3) {
            case 0 -> addCornerShadow(plane, x, y + 1, strength);
            case 1 -> addCornerShadow(plane, x + 1, y + 1, strength);
            case 2 -> addCornerShadow(plane, x + 1, y, strength);
            case 3 -> addCornerShadow(plane, x, y, strength);
        }
    }
}
