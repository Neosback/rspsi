package com.rspsi.studio;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.render.PickResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeSceneViewportTest {

    @Test
    void managesSelectionAndExposesNavigation() {
        NativeSceneViewport viewport = new NativeSceneViewport();
        assertNotNull(viewport.navigation(), "navigation controller must not be null");
        assertFalse(viewport.selection().isPresent(), "initial selection should be empty");

        PickResult hit = new PickResult(new TileCoordinate(1, 3200, 3200), 1, 1050, 42.0f);
        viewport.setSelection(hit);

        assertTrue(viewport.selection().isPresent());
        assertEquals(1050, viewport.selection().get().objectId());
        assertEquals(1, viewport.selection().get().plane());
        assertEquals(3200, viewport.selection().get().tile().x());

        viewport.clearSelection();
        assertFalse(viewport.selection().isPresent());
    }

    @Test
    void frameSelectionUpdatesNavigationCamera() {
        NativeSceneViewport viewport = new NativeSceneViewport();
        viewport.navigation().frameSelection(1000.0f, -500.0f, 2000.0f);

        assertEquals(1000.0f, viewport.navigation().camera().x(), 0.001f);
        assertEquals(-2100.0f, viewport.navigation().camera().y(), 0.001f);
        assertEquals(-400.0f, viewport.navigation().camera().z(), 0.001f);
    }
}
