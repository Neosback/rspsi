package com.rspsi.editor.plugin.builtin;

import com.rspsi.editor.plugin.EditorPlugin;

import java.util.List;

/** Factory for the built-in vertical tool plugins. */
public final class CoreToolsPlugin {
    private CoreToolsPlugin() {
    }

    public static List<EditorPlugin> builtIns() {
        return List.of(new TerrainToolsPlugin(), new ObjectToolsPlugin(),
                new SelectionToolsPlugin(), new RendererDiagnosticsPlugin());
    }
}
