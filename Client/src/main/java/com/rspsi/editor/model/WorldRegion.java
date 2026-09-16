package com.rspsi.editor.model;

import java.util.Objects;

/** Canonical OSRS region identity paired with its editable world document. */
public record WorldRegion(int regionX, int regionY, WorldDocument document) {
    public static final int REGION_SIZE = 64;

    public WorldRegion {
        if (regionX < 0 || regionX > 255 || regionY < 0 || regionY > 255) {
            throw new IllegalArgumentException("OSRS region coordinates must be in [0, 255]");
        }
        document = Objects.requireNonNull(document, "document");
        if (document.width() != REGION_SIZE || document.length() != REGION_SIZE) {
            throw new IllegalArgumentException("An OSRS region document must be 64x64");
        }
    }

    public int regionId() {
        return (regionX << 8) | regionY;
    }

    public WorldWindow window() {
        return new WorldWindow(regionX * REGION_SIZE, regionY * REGION_SIZE,
                REGION_SIZE, REGION_SIZE);
    }
}
