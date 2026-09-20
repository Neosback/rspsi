package com.rspsi.editor.render;

import com.rspsi.editor.model.LocalTile;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.model.WorldWindow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NavigationServiceTest {
    @Test
    void convertsLocalNavigationThroughWorldWindow() {
        var controller = new ViewportController(new CameraState(0, -1000, 0, -0.5f, 0));
        var navigation = new NavigationService(controller);
        navigation.jumpToLocal(new LocalTile(0, 10, 20), new WorldWindow(3200, 3200, 64, 64));
        assertEquals(new WorldTile(0, 3210, 3220), navigation.current().orElseThrow());
    }

    @Test
    void navigationHistoryRestoresTargets() {
        var controller = new ViewportController(new CameraState(0, -1000, 0, -0.5f, 0));
        var navigation = new NavigationService(controller);
        navigation.jumpTo(new WorldTile(0, 3200, 3200));
        navigation.jumpTo(new WorldTile(0, 3300, 3300));
        assertTrue(navigation.back());
        assertEquals(new WorldTile(0, 3200, 3200), navigation.current().orElseThrow());
        assertTrue(navigation.forward());
        assertEquals(new WorldTile(0, 3300, 3300), navigation.current().orElseThrow());
    }

    @Test
    void regionNavigationUsesRegionCenter() {
        var controller = new ViewportController(new CameraState(0, -1000, 0, -0.5f, 0));
        var navigation = new NavigationService(controller);
        navigation.jumpToRegion(50, 50, 0);
        assertEquals(new WorldTile(0, 3232, 3232), navigation.current().orElseThrow());
    }
}
