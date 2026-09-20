package com.rspsi.editor.plugin.runtime;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.EditorPluginDescriptor;

import java.util.Objects;

/** Makes the installed manifest authoritative for host-visible plugin metadata. */
final class ManifestBackedEditorPlugin implements EditorPlugin {
    private final ExternalPluginManifest manifest;
    private final EditorPlugin delegate;

    ManifestBackedEditorPlugin(ExternalPluginManifest manifest, EditorPlugin delegate) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (!manifest.id().equals(delegate.id())) {
            throw new IllegalArgumentException("Manifest id " + manifest.id()
                    + " does not match plugin id " + delegate.id());
        }
    }

    @Override public String id() { return manifest.id(); }
    @Override public EditorPluginDescriptor descriptor() { return manifest.descriptor(); }
    @Override public int loadOrder() { return delegate.loadOrder(); }
    @Override public void initialize(EditorPluginContext context) { delegate.initialize(context); }
    @Override public void shutdown(EditorPluginContext context) { delegate.shutdown(context); }

    EditorPlugin delegate() { return delegate; }
}
