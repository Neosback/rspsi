package com.rspsi.editor.assets;

import com.rspsi.cache.definition.ModelGeometryView;

import java.util.List;
import java.util.Optional;

/** Asset lookup contract for tools and inspectors. */
public interface AssetRepository {
    List<AssetDescriptor> search(String query);

    Optional<AssetDescriptor> get(int id, String type);

    /** Optional lazy geometry for a selected model asset. */
    default Optional<ModelGeometryView> modelGeometry(int id) {
        return Optional.empty();
    }
}
