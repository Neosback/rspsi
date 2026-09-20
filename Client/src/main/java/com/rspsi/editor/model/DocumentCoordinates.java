package com.rspsi.editor.model;

import java.util.Objects;
import java.util.Optional;

/**
 * The single conversion boundary between absolute OSRS tiles and one local
 * document window.
 */
public final class DocumentCoordinates {
    private final WorldDocument document;
    private final WorldWindow window;

    public DocumentCoordinates(WorldDocument document, WorldWindow window) {
        this.document = Objects.requireNonNull(document, "document");
        this.window = Objects.requireNonNull(window, "window");
        if (document.width() != window.width() || document.length() != window.length()) {
            throw new IllegalArgumentException(
                    "Document/window dimensions differ: "
                            + document.width() + "x" + document.length() + " vs "
                            + window.width() + "x" + window.length());
        }
    }

    public WorldDocument document() { return document; }
    public WorldWindow window() { return window; }

    public Optional<LocalTile> toLocal(WorldTile worldTile) {
        if (worldTile == null || worldTile.plane() >= document.planes()) {
            return Optional.empty();
        }
        return window.tryToLocal(worldTile)
                .filter(document::contains);
    }

    public LocalTile requireLocal(WorldTile worldTile) {
        return toLocal(worldTile).orElseThrow(() ->
                new IndexOutOfBoundsException("World tile outside document window: " + worldTile));
    }

    public WorldTile toWorld(LocalTile localTile) {
        Objects.requireNonNull(localTile, "localTile");
        if (!document.contains(localTile)) {
            throw new IndexOutOfBoundsException("Local tile outside document: " + localTile);
        }
        return window.toWorld(localTile);
    }
}
