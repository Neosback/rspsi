package com.rspsi.editor.plugin.builtin;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.builtin.tool.AttributeSelectToolPlugin;
import com.rspsi.editor.plugin.builtin.tool.BoxSelectToolPlugin;
import com.rspsi.editor.plugin.builtin.tool.DeleteObjectToolPlugin;
import com.rspsi.editor.plugin.builtin.tool.DuplicateObjectToolPlugin;
import com.rspsi.editor.plugin.builtin.tool.DuplicateSelectionToolPlugin;
import com.rspsi.editor.plugin.builtin.tool.FlattenTerrainToolPlugin;
import com.rspsi.editor.plugin.builtin.tool.LassoSelectToolPlugin;
import com.rspsi.editor.plugin.builtin.tool.LowerHeightToolPlugin;
import com.rspsi.editor.plugin.builtin.tool.MoveObjectToolPlugin;
import com.rspsi.editor.plugin.builtin.tool.MoveSelectionToolPlugin;
import com.rspsi.editor.plugin.builtin.tool.PaintFlagsToolPlugin;
import com.rspsi.editor.plugin.builtin.tool.PaintOverlayToolPlugin;
import com.rspsi.editor.plugin.builtin.tool.PaintUnderlayToolPlugin;
import com.rspsi.editor.plugin.builtin.tool.PlaceObjectToolPlugin;
import com.rspsi.editor.plugin.builtin.tool.RaiseHeightToolPlugin;
import com.rspsi.editor.plugin.builtin.tool.RampTerrainToolPlugin;
import com.rspsi.editor.plugin.builtin.tool.ReplaceSelectionToolPlugin;
import com.rspsi.editor.plugin.builtin.tool.RotateObjectToolPlugin;
import com.rspsi.editor.plugin.builtin.tool.RotateSelectionToolPlugin;
import com.rspsi.editor.plugin.builtin.tool.SmoothTerrainToolPlugin;
import com.rspsi.editor.plugin.builtin.tool.SplinePathToolPlugin;
import com.rspsi.editor.plugin.builtin.tool.TilePainterToolPlugin;

import java.util.List;

/** Factory for the built-in vertical tool plugins and individual tool plugins. */
public final class CoreToolsPlugin {
    private CoreToolsPlugin() {
    }

    public static List<EditorPlugin> builtIns() {
        return List.of(new TerrainToolsPlugin(), new ObjectToolsPlugin(),
                new SelectionToolsPlugin(), new RendererDiagnosticsPlugin(),
                new CoreUiSurfacesPlugin());
    }

    /** Returns all individual tool plugins. */
    public static List<EditorPlugin> individualToolPlugins() {
        return List.of(
                new BoxSelectToolPlugin(),
                new LassoSelectToolPlugin(),
                new AttributeSelectToolPlugin(),
                new MoveSelectionToolPlugin(),
                new RotateSelectionToolPlugin(),
                new DuplicateSelectionToolPlugin(),
                new ReplaceSelectionToolPlugin(),
                new PaintOverlayToolPlugin(),
                new PaintUnderlayToolPlugin(),
                new PaintFlagsToolPlugin(),
                new RaiseHeightToolPlugin(),
                new LowerHeightToolPlugin(),
                new FlattenTerrainToolPlugin(),
                new SmoothTerrainToolPlugin(),
                new RampTerrainToolPlugin(),
                new PlaceObjectToolPlugin(),
                new MoveObjectToolPlugin(),
                new RotateObjectToolPlugin(),
                new DuplicateObjectToolPlugin(),
                new DeleteObjectToolPlugin(),
                new TilePainterToolPlugin(),
                new SplinePathToolPlugin()
        );
    }
}
