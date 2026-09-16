package com.rspsi.editor.assets;

import java.util.Optional;

/** Neutral searchable asset metadata; definitions remain backend-owned. */
public record AssetDescriptor(int id, String type, String name, Optional<String> symbolicName) {
    public AssetDescriptor(int id, String type, String name) {
        this(id, type, name, Optional.empty());
    }

    public AssetDescriptor {
        if (id < 0 || type == null || name == null || symbolicName == null) {
            throw new IllegalArgumentException("Asset metadata is invalid");
        }
        symbolicName = symbolicName.map(String::trim).filter(value -> !value.isEmpty());
    }
}
