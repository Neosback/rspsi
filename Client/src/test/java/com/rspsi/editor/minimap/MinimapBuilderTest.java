package com.rspsi.editor.minimap;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class MinimapBuilderTest {
    @Test
    void buildsArgbPixelsFromOverlayAndBlendedUnderlayDefinitions() {
        WorldDocument document = new WorldDocument(2, 1, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0,
                1, 0, 0, 0, 0, List.of()));
        document.tile(0, 1, 0).restore(new TileSnapshot(0, 0, 0, 0,
                2, 3, 0, 0, 0, List.of()));

        MinimapImage image = new MinimapBuilder().build(document, 0, definitions());

        assertEquals(0xFF182838, image.pixel(0, 0));
        assertEquals(0xFF102030, image.pixel(1, 0));
        int[] first = image.argb();
        int[] second = image.argb();
        assertNotSame(first, second);
    }

    @Test
    void blockedTilesUseExplicitDiagnosticColorAndNoBlendingIsDeterministic() {
        WorldDocument document = new WorldDocument(1, 1, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0,
                1, 0, 0, 0, com.rspsi.editor.model.OsrsTileFlags.BLOCK_MAP_SQUARE, List.of()));

        assertEquals(0xFF1F2937, new MinimapBuilder().build(document, 0, definitions()).pixel(0, 0));
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0,
                1, 0, 0, 0, 0, List.of()));
        assertEquals(0xFF102030, new MinimapBuilder().build(document, 0, definitions(), false).pixel(0, 0));
    }

    private static DefinitionProvider definitions() {
        return new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) { return Optional.empty(); }
            @Override public Optional<FloorDefinitionView> underlay(int id) {
                return id == 1 ? Optional.of(floor(id, 0x102030))
                        : id == 2 ? Optional.of(floor(id, 0x203040)) : Optional.empty();
            }
            @Override public Optional<FloorDefinitionView> overlay(int id) {
                return id == 3 ? Optional.of(floor(id, 0x102030)) : Optional.empty();
            }
        };
    }

    private static FloorDefinitionView floor(int id, int rgb) {
        return new FloorDefinitionView(id, -1, rgb, 0, 0, 0, 0, 0);
    }
}
