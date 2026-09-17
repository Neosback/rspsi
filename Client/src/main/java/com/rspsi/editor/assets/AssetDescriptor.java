package com.rspsi.editor.assets;

import java.util.List;
import java.util.Optional;

/** Neutral searchable asset metadata; definitions remain backend-owned. */
public record AssetDescriptor(int id, String type, String name, Optional<String> symbolicName,
                             List<String> details) {
    public AssetDescriptor(int id, String type, String name) {
        this(id, type, name, Optional.empty(), List.of());
    }

    public AssetDescriptor(int id, String type, String name, Optional<String> symbolicName) {
        this(id, type, name, symbolicName, List.of());
    }

    public AssetDescriptor {
        if (id < 0 || type == null || name == null || symbolicName == null || details == null) {
            throw new IllegalArgumentException("Asset metadata is invalid");
        }
        symbolicName = symbolicName.map(String::trim).filter(value -> !value.isEmpty());
        details = List.copyOf(details);
        if (details.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Asset details cannot be blank");
        }
    }
}
