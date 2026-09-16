package com.rspsi.editor.assets;

/** Neutral searchable asset metadata; definitions remain backend-owned. */
public record AssetDescriptor(int id, String type, String name) {
    public AssetDescriptor {
        if (id < 0 || type == null || name == null) {
            throw new IllegalArgumentException("Asset metadata is invalid");
        }
    }
}
