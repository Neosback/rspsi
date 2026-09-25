package com.rspsi.studio.ui.hud;

import com.rspsi.studio.plugin.StudioPluginManager;
import com.rspsi.studio.theme.StudioIcons;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BrushSettingsHudTest {

    @Test
    void pluginIdentityAndDefaults() {
        BrushSettingsHud hud = new BrushSettingsHud();
        assertEquals(BrushSettingsHud.ID, hud.id());
        assertEquals("Brush Settings HUD", hud.name());
        assertEquals(StudioIcons.BRUSH, hud.icon());
        assertTrue(hud.isConfigurable());
        assertTrue(hud.isVisible());
        assertTrue(hud.isDocked());
        assertFalse(hud.isMinimized());
        assertFalse(hud.isPinned());
    }

    @Test
    void minimizeAndPinState() {
        BrushSettingsHud hud = new BrushSettingsHud();

        hud.setMinimized(true);
        assertTrue(hud.isMinimized());

        hud.setMinimized(false);
        assertFalse(hud.isMinimized());

        hud.setPinned(true);
        assertTrue(hud.isPinned());

        hud.setPinned(false);
        assertFalse(hud.isPinned());
    }

    @Test
    void cornerSnapRequests() {
        BrushSettingsHud hud = new BrushSettingsHud();
        for (BrushSettingsHud.Corner corner : BrushSettingsHud.Corner.values()) {
            hud.requestSnap(corner);
            assertNotNull(corner.label());
        }
    }

    @Test
    void registersInPluginManager() {
        StudioPluginManager manager = new StudioPluginManager();
        manager.discoverPlugins();

        var pluginOpt = manager.plugin(BrushSettingsHud.ID);
        assertTrue(pluginOpt.isPresent());
        assertInstanceOf(BrushSettingsHud.class, pluginOpt.get());
        assertTrue(manager.isEnabled(BrushSettingsHud.ID));
    }

    @Test
    void viewportHudManagerTracksCoordinates() {
        ViewportHudManager manager = new ViewportHudManager();
        manager.beginFrame(150.0f, 75.0f, 1024.0f, 768.0f);

        assertEquals(150.0f, manager.viewportX());
        assertEquals(75.0f, manager.viewportY());
        assertEquals(1024.0f, manager.viewportWidth());
        assertEquals(768.0f, manager.viewportHeight());
    }
}
