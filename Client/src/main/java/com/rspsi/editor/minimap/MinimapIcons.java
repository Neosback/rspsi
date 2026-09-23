package com.rspsi.editor.minimap;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.MapElementDefinitionView;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Map-function icons (bank, altar, shop, ...) a document shows on the
 * minimap: every placed object whose definition names a map element that is
 * minimap-visible, on the given plane. RuneLite exposes the same data as
 * {@code MapElementConfig} (sprite, category) and {@code Client#getMapIcons}.
 */
public final class MinimapIcons {
    private MinimapIcons() {
    }

    /** One icon at a document-local tile. */
    public record Icon(int x, int y, int mapElementId, int spriteId, String name) {
    }

    public static List<Icon> locate(WorldDocument document, DefinitionProvider definitions, int plane) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(definitions, "definitions");
        List<Icon> icons = new ArrayList<>();
        if (plane < 0 || plane >= document.planes()) return icons;
        Set<Long> placed = new LinkedHashSet<>();
        for (int x = 0; x < document.width(); x++) {
            for (int y = 0; y < document.length(); y++) {
                for (WorldObject object : document.tile(plane, x, y).objects()) {
                    var element = definitions.objectMapElement(object.id());
                    if (element.isEmpty()) continue;
                    MapElementDefinitionView view = definitions.mapElement(element.getAsInt()).orElse(null);
                    if (view == null || !view.minimapVisible() || view.spriteId() < 0) continue;
                    // One icon per element per tile, even when several locs share it.
                    if (!placed.add(((long) view.id() << 32) | ((long) x << 16) | y)) continue;
                    icons.add(new Icon(x, y, view.id(), view.spriteId(), view.name()));
                }
            }
        }
        return icons;
    }
}
