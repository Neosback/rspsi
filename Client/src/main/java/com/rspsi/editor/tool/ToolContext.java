package com.rspsi.editor.tool;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.viewport.Viewport;

import java.util.Objects;

/** Narrow service access supplied to an editor tool. */
public record ToolContext(
        EditorSession session,
        AssetRepository assets,
        Viewport viewport
) {
    public ToolContext {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(assets, "assets");
        Objects.requireNonNull(viewport, "viewport");
    }
}
