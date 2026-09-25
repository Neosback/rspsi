package com.rspsi.editor.core.module;

import com.rspsi.editor.core.CoreEditorModule;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.ui.UiSurfaceContribution;
import com.rspsi.editor.ui.DockRegion;

import java.util.EnumSet;

/** Canonical always-on declarations for Studio-managed utility/HUD surfaces. */
public final class CoreUiModule implements CoreEditorModule {
    public static final String ID = "rspsi.ui.core-surfaces";

    @Override public String id() { return ID; }
    @Override public int order() { return 50; }

    @Override
    public void install(EditorPluginContext context) {
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
