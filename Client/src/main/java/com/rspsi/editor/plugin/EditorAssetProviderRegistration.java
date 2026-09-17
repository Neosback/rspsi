package com.rspsi.editor.plugin;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Metadata and factory for one plugin-owned asset provider. */
public record EditorAssetProviderRegistration(
        String id,
        String label,
        List<String> assetTypes,
        int order,
        Supplier<? extends EditorAssetProvider> factory) {
    public EditorAssetProviderRegistration {
        id = text(id, "asset provider id");
        label = text(label, "asset provider label");
        assetTypes = List.copyOf(assetTypes == null ? List.of() : assetTypes);
        if (assetTypes.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Asset provider types cannot be blank");
        }
        Objects.requireNonNull(factory, "asset provider factory");
    }

    private static String text(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return normalized;
    }
}
