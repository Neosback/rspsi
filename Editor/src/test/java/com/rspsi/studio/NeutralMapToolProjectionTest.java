package com.rspsi.studio;

import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.plugin.EditorPluginRegistry;
import com.rspsi.editor.plugin.EditorToolRegistration;
import com.rspsi.editor.plugin.ToolUiDescriptor;
import com.rspsi.editor.tool.EditorTool;
import com.rspsi.editor.tool.ToolContext;
import com.rspsi.studio.plugin.StudioPluginManager;
import com.rspsi.studio.plugin.StudioToolPlugin;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeutralMapToolProjectionTest {

    @Test
    void neutralExtensionToolProjectsIntoNativeStudioCatalog() {
        EditorPluginRegistry registry = new EditorPluginRegistry();
        registry.registerTool(new EditorToolRegistration(
                "community.biome-painter",
                "Biome Painter",
                "Terrain",
                "terrain",
                "forest",
                "B",
                35,
                new ToolUiDescriptor(
                        EnumSet.of(ToolUiDescriptor.ToolSurface.BOTTOM_BAR),
                        ToolUiDescriptor.BrushUiMode.SHARED_SETTINGS,
                        EnumSet.of(
                                ToolUiDescriptor.ToolCapability.TILE_TARGET,
                                ToolUiDescriptor.ToolCapability.BRUSH_FOOTPRINT,
                                ToolUiDescriptor.ToolCapability.WORLD_EDIT,
                                ToolUiDescriptor.ToolCapability.CONTEXT_DRAWER),
                        true),
                () -> new NoOpTool("community.biome-painter")));

        StudioPluginManager manager = new StudioPluginManager();
        manager.bindEditorPluginRegistry(registry);

        var tool = manager.toolView("community.biome-painter").orElseThrow();

        assertEquals("Biome Painter", tool.name());
        assertEquals("forest", tool.icon());
        assertEquals("B", tool.shortcut());
        assertEquals(35, tool.railPriority());
        assertTrue(tool.surfaces().contains(StudioToolPlugin.ToolSurface.BOTTOM_BAR));
        assertFalse(tool.surfaces().contains(StudioToolPlugin.ToolSurface.FLOATING_TOOLBAR));
        assertEquals(StudioToolPlugin.BrushUiMode.SHARED_SETTINGS, tool.brushUiMode());
        assertTrue(tool.hasContextDrawerContent());
        assertNull(tool.nativePlugin());
        assertTrue(manager.usesSharedBrushSettings("community.biome-painter"));
    }

    private static final class NoOpTool implements EditorTool {
        private final String id;

        private NoOpTool(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public void activate(ToolContext context) {
        }

        @Override
        public void deactivate() {
        }

        @Override
        public void pointerDown(PointerEvent event) {
        }

        @Override
        public void pointerDrag(PointerEvent event) {
        }

        @Override
        public void pointerUp(PointerEvent event) {
        }
    }
}
