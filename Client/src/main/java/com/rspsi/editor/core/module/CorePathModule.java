package com.rspsi.editor.core.module;

import com.rspsi.editor.core.CoreEditorModule;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.ui.UiSurfaceContribution;
import com.rspsi.editor.tool.SplinePathTool;
import com.rspsi.editor.ui.DockRegion;

import java.util.EnumSet;

/** Canonical always-on spline/path editing module. */
public final class CorePathModule implements CoreEditorModule {
    public static final String ID = "rspsi.tools.path.spline";

    @Override public String id() { return ID; }
    @Override public int order() { return 17; }

    @Override
    public void install(EditorPluginContext context) {
        context.registry().registerTool(
                SplinePathTool.ID, "Spline Path", "Terrain", "Paint",
                "PATH", "P", 45,
                SplinePathTool::new);

        context.registry().registerUiSurface(new UiSurfaceContribution(
                "studio.path-context", "Path Builder", "path",
                UiSurfaceContribution.SurfaceType.BOTTOM_CONTEXT,
                DockRegion.BOTTOM, EnumSet.of(DockRegion.BOTTOM, DockRegion.RIGHT),
                UiSurfaceContribution.SizeClass.EXPANDED,
                true, true, SplinePathTool.ID, 15));
        context.registry().registerUiSurface(new UiSurfaceContribution(
                "studio.path-hud", "Path HUD", "path",
                UiSurfaceContribution.SurfaceType.VIEWPORT_HUD,
                DockRegion.OVERLAY, EnumSet.of(DockRegion.OVERLAY),
                UiSurfaceContribution.SizeClass.COMPACT,
                false, true, SplinePathTool.ID, 35));
    }
}
