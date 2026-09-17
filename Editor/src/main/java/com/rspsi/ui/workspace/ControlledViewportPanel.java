package com.rspsi.ui.workspace;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.model.WorldWindow;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;

import java.util.Objects;

/** Hosts either the legacy viewport or the canonical OSRS preview. */
public final class ControlledViewportPanel extends StackPane implements AutoCloseable {
    private final Node legacyViewport;
    private final CanonicalSceneViewport canonicalViewport = new CanonicalSceneViewport();

    public ControlledViewportPanel(Node legacyViewport) {
        this.legacyViewport = Objects.requireNonNull(legacyViewport, "legacyViewport");
        getStyleClass().add("controlled-viewport-panel");
        getChildren().add(legacyViewport);
    }

    public CanonicalSceneViewport canonicalViewport() {
        return canonicalViewport;
    }

    public void showCanonical(EditorSession session, WorldWindow window) {
        canonicalViewport.bind(session, window);
        getChildren().setAll(canonicalViewport);
    }

    public void showCanonical(EditorSession session, WorldWindow window,
                              DefinitionProvider definitions) {
        canonicalViewport.bind(session, window, definitions);
        getChildren().setAll(canonicalViewport);
    }

    public void showCanonical(EditorSession session, WorldWindow window,
                              DefinitionProvider definitions, AssetRepository assets) {
        canonicalViewport.bind(session, window, definitions, assets);
        getChildren().setAll(canonicalViewport);
    }

    public void showLegacy() {
        canonicalViewport.close();
        getChildren().setAll(legacyViewport);
    }

    @Override
    public void close() {
        canonicalViewport.close();
    }
}
