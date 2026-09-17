package com.rspsi.editor.assets;

import java.util.List;
import java.util.Optional;

/** Empty neutral asset source used by read-only or compatibility workspaces. */
public final class EmptyAssetRepository implements AssetRepository {
    public static final EmptyAssetRepository INSTANCE = new EmptyAssetRepository();

    private EmptyAssetRepository() {
    }

    @Override
    public List<AssetDescriptor> search(String query) {
        return List.of();
    }

    @Override
    public Optional<AssetDescriptor> get(int id, String type) {
        return Optional.empty();
    }
}
