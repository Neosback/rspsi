package com.rspsi.editor.tool;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.model.DocumentCoordinates;
import com.rspsi.editor.model.LocalTile;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.model.WorldWindow;
import com.rspsi.editor.viewport.Viewport;

import java.util.Objects;
import java.util.Optional;

/**
 * Narrow service access supplied to an editor tool.
 *
 * <p>The viewport speaks absolute {@link WorldTile}; the document speaks
 * {@link LocalTile}. This class is the only normal tool-side conversion
 * boundary between those spaces.</p>
 */
public record ToolContext(
        EditorSession session,
        AssetRepository assets,
        Viewport viewport,
        DocumentCoordinates coordinates
) {
    public ToolContext {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(assets, "assets");
        Objects.requireNonNull(viewport, "viewport");
        Objects.requireNonNull(coordinates, "coordinates");
        if (coordinates.document() != session.world()) {
            throw new IllegalArgumentException("Tool coordinates must belong to the active session document");
        }
    }

    public ToolContext(EditorSession session, AssetRepository assets, Viewport viewport,
                       WorldWindow window) {
        this(session, assets, viewport,
                new DocumentCoordinates(session.world(), Objects.requireNonNull(window, "window")));
    }

    /**
     * Compatibility constructor for local-only fixtures. Production map tools
     * should receive the real loaded-region WorldWindow.
     */
    public ToolContext(EditorSession session, AssetRepository assets, Viewport viewport) {
        this(session, assets, viewport, session.coordinates());
    }

    public Optional<WorldTile> worldTileAt(float x, float y) {
        return viewport.tileAt(x, y);
    }

    public Optional<LocalTile> localTileAt(float x, float y) {
        return viewport.tileAt(x, y).flatMap(coordinates::toLocal);
    }

    public Optional<LocalTile> local(WorldTile tile) {
        return coordinates.toLocal(tile);
    }

    public WorldTile world(LocalTile tile) {
        return coordinates.toWorld(tile);
    }
}
