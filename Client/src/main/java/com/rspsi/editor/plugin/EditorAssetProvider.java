package com.rspsi.editor.plugin;

import com.rspsi.editor.assets.AssetDescriptor;

import java.util.List;
import java.util.Optional;

/** Optional plugin-owned asset source layered into the shell's asset browser. */
public interface EditorAssetProvider {
    List<AssetDescriptor> search(EditorPluginContext context, String query);

    default Optional<AssetDescriptor> get(EditorPluginContext context, int id, String type) {
        return Optional.empty();
    }
}
