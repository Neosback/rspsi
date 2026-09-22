package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldRegionWindow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneContractTest {
    @Test
    void projectsSceneWindowIntoStableSemanticContract() {
        WorldRegionWindow regions = new WorldRegionWindow(10, 20, 1, 1, Map.of());
        SceneWindow window = new SceneWindow(
                regions, 640, 1280, 4, 5,
                1, 77, Set.of(0x0A14), List.of());

        SceneContract contract = window.contract();

        assertEquals(640, contract.baseX());
        assertEquals(1280, contract.baseY());
        assertEquals(4, contract.planes());
        assertEquals(5, contract.border());
        assertEquals(1, contract.minimumRenderLevel());
        assertEquals(77, contract.worldViewId());
        assertTrue(!contract.instance());
        assertEquals(Set.of(0x0A14), contract.mapRegionIds());
    }

    @Test
    void classifiesTrackedRuneLiteSceneSurfaceDeliberately() {
        Map<String, SceneContract.ApiClass> surface = SceneContract.runeLiteApiSurface();

        assertEquals(SceneContract.ApiClass.SEMANTIC, surface.get("getBaseX"));
        assertEquals(SceneContract.ApiClass.SEMANTIC, surface.get("getRenderLevel"));
        assertEquals(SceneContract.ApiClass.PRESENTATION_ONLY, surface.get("getDrawDistance"));
        assertEquals(SceneContract.ApiClass.PRESENTATION_ONLY, surface.get("getRoofRemovalMode"));
        assertEquals(SceneContract.ApiClass.MUTATION, surface.get("buildRoofs"));
        assertEquals(SceneContract.ApiClass.DEFERRED, surface.get("getExtendedTiles"));
        assertEquals(SceneContract.ApiClass.DEFERRED, surface.get("getRoofs"));
    }
}
