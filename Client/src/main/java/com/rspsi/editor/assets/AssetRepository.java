package com.rspsi.editor.assets;

import java.util.List;
import java.util.Optional;

/** Asset lookup contract for tools and inspectors. */
public interface AssetRepository {
    List<AssetDescriptor> search(String query);

    Optional<AssetDescriptor> get(int id, String type);
}
