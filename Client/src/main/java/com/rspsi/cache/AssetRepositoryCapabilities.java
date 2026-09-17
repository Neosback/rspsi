package com.rspsi.cache;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Immutable capability report for the session-scoped asset facade. */
public record AssetRepositoryCapabilities(Set<AssetCategory> categories, boolean lazy) {
    public AssetRepositoryCapabilities {
        Objects.requireNonNull(categories, "categories");
        EnumSet<AssetCategory> copy = EnumSet.noneOf(AssetCategory.class);
        copy.addAll(categories);
        categories = Set.copyOf(copy);
    }

    public static AssetRepositoryCapabilities none() {
        return new AssetRepositoryCapabilities(EnumSet.noneOf(AssetCategory.class), true);
    }

    public boolean supports(AssetCategory category) {
        return category != null && categories.contains(category);
    }
}
