package com.rspsi.editor.core.module;

import com.rspsi.editor.core.CoreEditorModule;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.EditorToolContextRegistration;
import com.rspsi.editor.core.settings.TerrainToolSettings;
import com.rspsi.editor.tool.BlendTerrainTool;
import com.rspsi.editor.tool.ChangeHeightTool;
import com.rspsi.editor.tool.FlattenTerrainTool;
import com.rspsi.editor.tool.PaintFlagsTool;
import com.rspsi.editor.tool.PaintOverlayTool;
import com.rspsi.editor.tool.PaintUnderlayTool;
import com.rspsi.editor.tool.RampTerrainTool;
import com.rspsi.editor.tool.SmoothTerrainTool;
import com.rspsi.editor.tool.TerraceTerrainTool;

import java.util.List;
import java.util.function.Supplier;

/** Canonical always-on terrain editing module. */
public final class CoreTerrainModule implements CoreEditorModule {
    public static final String ID = "rspsi.tools.terrain";

    @Override public String id() { return ID; }
    @Override public int order() { return 10; }

    @Override
    public void install(EditorPluginContext context) {
        TerrainToolSettings settings = new TerrainToolSettings(context.settings());

        register(context, settings, "terrain.paint-underlay", "Paint underlay",
                "Terrain", "Paint", "PENCIL", "Shift+2", 20, () -> new PaintUnderlayTool(1));
        register(context, settings, "terrain.paint-overlay", "Paint overlay",
                "Terrain", "Paint", "BRUSH", "2", 10, () -> new PaintOverlayTool(1));
        register(context, settings, "terrain.flags", "Paint flags",
                "Terrain", "Paint", "FLAGS", null, 30, () -> new PaintFlagsTool(0));

        register(context, settings, "terrain.raise", "Raise height",
                "Height", "Height", "UP", "3", 10, () -> new ChangeHeightTool(8));
        register(context, settings, "terrain.lower", "Lower height",
                "Height", "Height", "DOWN", "Shift+3", 20, () -> new ChangeHeightTool(-8));
        register(context, settings, "terrain.flatten", "Flatten terrain",
                "Height", "Height", "FLATTEN", null, 30, () -> new FlattenTerrainTool(0));
        register(context, settings, "terrain.smooth", "Smooth terrain",
                "Height", "Height", "SMOOTH", null, 40, () -> new SmoothTerrainTool(50));
        register(context, settings, "terrain.blend", "Blend terrain",
                "Height", "Height", "HEIGHT", null, 45, () -> new BlendTerrainTool(50, 56));
        register(context, settings, "terrain.terrace", "Terrace terrain",
                "Height", "Height", "HEIGHT", null, 47, () -> new TerraceTerrainTool(16));
        register(context, settings, "terrain.ramp", "Ramp terrain",
                "Height", "Height", "RAMP", null, 50, () -> new RampTerrainTool(0, 64));

        context.registry().registerToolContext(new EditorToolContextRegistration(
                "terrain.context", "Terrain settings",
                List.of("terrain.paint-underlay", "terrain.paint-overlay", "terrain.raise",
                        "terrain.lower", "terrain.flatten", "terrain.smooth", "terrain.blend",
                        "terrain.terrace", "terrain.ramp", "terrain.flags"),
                0, () -> ignored -> settings.settings()));
    }

    private static void register(
            EditorPluginContext context,
            TerrainToolSettings settings,
            String id,
            String label,
            String category,
            String group,
            String icon,
            String shortcut,
            int order,
            Supplier<? extends com.rspsi.editor.tool.EditorTool> factory) {
        context.registry().registerTool(
                id, label, category, group, icon, shortcut, order,
                () -> {
                    var tool = factory.get();
                    settings.configure(id, tool);
                    return tool;
                });
    }
}
