package com.rspsi.ui.workspace;

import com.rspsi.editor.assets.AssetDescriptor;
import com.rspsi.editor.plugin.EditorPluginHost;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;

import java.util.Objects;

/** Keeps the existing legacy tool rail while providing a canonical OSRS rail. */
public final class AdaptiveToolPanel extends StackPane implements AutoCloseable {
    private final Node legacy;
    private final CanonicalToolPanel canonical = new CanonicalToolPanel();

    public AdaptiveToolPanel(Node legacy) {
        this.legacy = Objects.requireNonNull(legacy, "legacy");
        getStyleClass().add("adaptive-tool-panel");
        getChildren().add(legacy);
    }

    public void showCanonical(CanonicalSceneViewport viewport) {
        canonical.bind(viewport);
        getChildren().setAll(canonical);
    }

    public void bindPluginHost(EditorPluginHost host) {
        canonical.bindPluginHost(host);
    }

    public void showLegacy() {
        canonical.close();
        getChildren().setAll(legacy);
    }

    /** Passes a neutral asset selection to the canonical tool rail when mounted. */
    public void setObjectAsset(AssetDescriptor asset) {
        canonical.setObjectAsset(asset);
    }

    @Override
    public void close() {
        canonical.close();
    }
}
