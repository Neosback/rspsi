package com.rspsi.editor.plugin.builtin;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.ui.UiSurfaceContribution;
import com.rspsi.editor.ui.DockRegion;

import java.util.EnumSet;

/** Neutral built-in declarations for Studio-managed utility/HUD surfaces. */
public final class CoreUiSurfacesPlugin implements EditorPlugin {
    public static final String ID = "rspsi.ui.core-surfaces";

    @Override public String id() { return ID; }

    @Override public int loadOrder() { return 50; }

    @Override
    public void initialize(EditorPluginContext context) {
        context.registry().registerUiSurface(new UiSurfaceContribution(
                "studio.minimap-hud", "Minimap HUD", "map",
                UiSurfaceContribution.SurfaceType.VIEWPORT_HUD,
                DockRegion.OVERLAY, EnumSet.of(DockRegion.OVERLAY),
                UiSurfaceContribution.SizeClass.EXPANDED,
                false, true, "", 10));
        context.registry().registerUiSurface(new UiSurfaceContribution(
                "studio.tile-info-hud", "Tile Information HUD", "explore",
                UiSurfaceContribution.SurfaceType.VIEWPORT_HUD,
                DockRegion.OVERLAY, EnumSet.of(DockRegion.OVERLAY),
                UiSurfaceContribution.SizeClass.COMPACT,
                false, true, "", 20));
    }
}
