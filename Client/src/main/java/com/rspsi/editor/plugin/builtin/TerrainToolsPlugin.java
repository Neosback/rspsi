package com.rspsi.editor.plugin.builtin;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.EditorPluginRegistry;
import com.rspsi.editor.plugin.EditorToolContextRegistration;
import com.rspsi.editor.tool.ChangeHeightTool;
import com.rspsi.editor.tool.FlattenTerrainTool;
import com.rspsi.editor.tool.PaintFlagsTool;
import com.rspsi.editor.tool.PaintOverlayTool;
import com.rspsi.editor.tool.PaintUnderlayTool;
import com.rspsi.editor.tool.RampTerrainTool;
import com.rspsi.editor.tool.SmoothTerrainTool;

import java.util.List;

/** First-party vertical plugin for terrain editing tools. */
public final class TerrainToolsPlugin implements EditorPlugin {
    public static final String ID = "rspsi.tools.terrain";
    private final TerrainToolSettings settings = new TerrainToolSettings();

    @Override public String id() { return ID; }

    @Override public int loadOrder() { return 10; }

    @Override
    public void initialize(EditorPluginContext context) {
        EditorPluginRegistry registry = context.registry();
        register(registry, "terrain.paint-underlay", "Paint underlay", "Terrain");
        register(registry, "terrain.paint-overlay", "Paint overlay", "Terrain");
        register(registry, "terrain.raise", "Raise", "Terrain");
        register(registry, "terrain.lower", "Lower", "Terrain");
        register(registry, "terrain.flatten", "Flatten", "Terrain");
        register(registry, "terrain.smooth", "Smooth", "Terrain");
        register(registry, "terrain.ramp", "Ramp", "More terrain");
        register(registry, "terrain.flags", "Paint flags", "More terrain");
        registry.registerToolContext(new EditorToolContextRegistration(
                "terrain.context", "Terrain settings",
                List.of("terrain.paint-underlay", "terrain.paint-overlay", "terrain.raise",
                        "terrain.lower", "terrain.flatten", "terrain.smooth", "terrain.ramp",
                        "terrain.flags"), 0, () -> ignored -> settings.settings()));
    }

    private void register(EditorPluginRegistry registry, String id, String label, String category) {
        registry.registerTool(id, label, category, () -> {
            var tool = switch (id) {
                case "terrain.paint-underlay" -> new PaintUnderlayTool(1);
                case "terrain.paint-overlay" -> new PaintOverlayTool(1);
                case "terrain.raise" -> new ChangeHeightTool(8);
                case "terrain.lower" -> new ChangeHeightTool(-8);
                case "terrain.flatten" -> new FlattenTerrainTool(0);
                case "terrain.smooth" -> new SmoothTerrainTool(50);
                case "terrain.ramp" -> new RampTerrainTool(0, 64);
                case "terrain.flags" -> new PaintFlagsTool(0);
                default -> throw new IllegalArgumentException("Unknown terrain tool: " + id);
            };
            settings.configure(id, tool);
            return tool;
        });
    }
}
