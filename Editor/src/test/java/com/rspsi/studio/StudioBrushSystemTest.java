package com.rspsi.studio;

import com.rspsi.editor.brush.BrushCapability;
import com.rspsi.editor.brush.EditorBrush;
import com.rspsi.studio.brush.StudioBrushManager;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StudioBrushSystemTest {

    @Test
    void registersBuiltInsAndFiltersByCapability() {
        StudioBrushManager manager = new StudioBrushManager();

        assertTrue(manager.allBrushes().size() >= 7);

        var spatial = manager.compatibleBrushes(Set.of(BrushCapability.SPATIAL_FOOTPRINT));
        assertTrue(spatial.stream().map(EditorBrush::id).toList().contains("square"));
        assertTrue(spatial.stream().map(EditorBrush::id).toList().contains("circle"));
        assertTrue(spatial.stream().map(EditorBrush::id).toList().contains("gaussian"));

        var height = manager.compatibleBrushes(Set.of(
                BrushCapability.SPATIAL_FOOTPRINT,
                BrushCapability.HEIGHT_MANIPULATION));
        assertTrue(height.stream().map(EditorBrush::id).toList().contains("slope"));
        assertTrue(height.stream().map(EditorBrush::id).toList().contains("terrace"));
        assertFalse(height.stream().map(EditorBrush::id).toList().contains("square"));
    }

    @Test
    void brushSelectionIsTrackedPerTool() {
        StudioBrushManager manager = new StudioBrushManager();
        manager.setActiveBrush("terrain.tile-painter", "circle");
        manager.setActiveBrush("terrain.raise", "gaussian");

        assertEquals("circle", manager.activeBrush(
                "terrain.tile-painter",
                Set.of(BrushCapability.SPATIAL_FOOTPRINT)).id());
        assertEquals("gaussian", manager.activeBrush(
                "terrain.raise",
                Set.of(BrushCapability.SPATIAL_FOOTPRINT)).id());
    }

    @Test
    void cannotDisableLastEnabledBrush() {
        StudioBrushManager manager = new StudioBrushManager();
        for (EditorBrush brush : manager.allBrushes()) {
            if (!"circle".equals(brush.id())) {
                assertTrue(manager.setEnabled(brush.id(), false));
            }
        }

        assertEquals(1, manager.enabledBrushes().size());
        assertFalse(manager.setEnabled("circle", false));
        assertTrue(manager.isEnabled("circle"));
    }

    @Test
    void hostExtensionBrushesAppearAndUnloadCleanly() {
        StudioBrushManager manager = new StudioBrushManager();

        EditorBrush extensionBrush = new EditorBrush() {
            @Override public String id() { return "community.star"; }
            @Override public String name() { return "Star"; }
            @Override public Set<BrushCapability> capabilities() {
                return Set.of(BrushCapability.SPATIAL_FOOTPRINT);
            }
            @Override public double weight(int dx, int dy, int radius) {
                return dx == 0 || dy == 0 ? 1.0 : 0.0;
            }
        };

        manager.syncHostBrushes(java.util.List.of(extensionBrush));
        assertTrue(manager.allBrushes().stream()
                .anyMatch(brush -> "community.star".equals(brush.id())));

        manager.setActiveBrush("community.tool", "community.star");
        assertEquals("community.star", manager.activeBrush(
                "community.tool", Set.of(BrushCapability.SPATIAL_FOOTPRINT)).id());

        manager.syncHostBrushes(java.util.List.of());
        assertFalse(manager.allBrushes().stream()
                .anyMatch(brush -> "community.star".equals(brush.id())));
    }

    @Test
    void radiusIsValidated() {
        StudioBrushManager manager = new StudioBrushManager();
        manager.setBrushRadius(5);
        assertEquals(5, manager.brushRadius());
        assertThrows(IllegalArgumentException.class, () -> manager.setBrushRadius(-1));
        assertThrows(IllegalArgumentException.class, () -> manager.setBrushRadius(65));
    }
}
