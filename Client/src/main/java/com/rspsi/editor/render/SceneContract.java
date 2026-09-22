package com.rspsi.editor.render;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Backend-neutral scene-level contract.
 *
 * <p>This separates semantic scene state from presentation-only RuneLite APIs,
 * mutation surfaces, and APIs that the editor deliberately defers. Renderers
 * consume semantic state instead of inferring it from UI settings.</p>
 */
public record SceneContract(
        int baseX,
        int baseY,
        int planes,
        int border,
        int minimumRenderLevel,
        int worldViewId,
        boolean instance,
        Set<Integer> mapRegionIds
) {
    public enum ApiClass {
        SEMANTIC,
        PRESENTATION_ONLY,
        MUTATION,
        DEFERRED
    }

    private static final Map<String, ApiClass> RUNELITE_API_SURFACE = apiSurface();

    public SceneContract {
        if (baseX < 0 || baseY < 0 || planes <= 0 || border < 0) {
            throw new IllegalArgumentException("Invalid scene contract coordinates or dimensions");
        }
        if (minimumRenderLevel < 0 || minimumRenderLevel >= planes) {
            throw new IllegalArgumentException("Minimum render level must be inside the scene plane range");
        }
        mapRegionIds = Set.copyOf(Objects.requireNonNull(mapRegionIds, "mapRegionIds"));
    }

    public static SceneContract from(SceneWindow window) {
        Objects.requireNonNull(window, "window");
        return new SceneContract(
                window.sceneBaseX(),
                window.sceneBaseY(),
                window.planes(),
                window.border(),
                window.minimumRenderLevel(),
                window.worldViewId(),
                window.instance(),
                window.sourceRegionIds());
    }

    /**
     * Classification of the RuneLite Scene API surfaces tracked by the parity
     * manifest. This is intentionally explicit so future work can add semantic
     * state without turning renderer contracts into a mirror of the client API.
     */
    public static Map<String, ApiClass> runeLiteApiSurface() {
        return RUNELITE_API_SURFACE;
    }

    private static Map<String, ApiClass> apiSurface() {
        Map<String, ApiClass> values = new LinkedHashMap<>();

        values.put("getBaseX", ApiClass.SEMANTIC);
        values.put("getBaseY", ApiClass.SEMANTIC);
        values.put("getMapRegions", ApiClass.SEMANTIC);
        values.put("getMinLevel", ApiClass.SEMANTIC);
        values.put("getOverlayIds", ApiClass.SEMANTIC);
        values.put("getTileHeights", ApiClass.SEMANTIC);
        values.put("getTiles", ApiClass.SEMANTIC);
        values.put("getTileShapes", ApiClass.SEMANTIC);
        values.put("getUnderlayIds", ApiClass.SEMANTIC);
        values.put("getWorldViewId", ApiClass.SEMANTIC);
        values.put("isInstance", ApiClass.SEMANTIC);
        values.put("getInstanceTemplateChunks", ApiClass.SEMANTIC);

        values.put("getDrawDistance", ApiClass.PRESENTATION_ONLY);
        values.put("getRoofRemovalMode", ApiClass.PRESENTATION_ONLY);
        values.put("getSkybox", ApiClass.PRESENTATION_ONLY);

        values.put("buildRoofs", ApiClass.MUTATION);

        values.put("getExtendedTiles", ApiClass.DEFERRED);
        values.put("getExtendedTileSettings", ApiClass.DEFERRED);
        values.put("getRoofs", ApiClass.DEFERRED);

        return Map.copyOf(values);
    }
}
