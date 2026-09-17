package com.rspsi.editor.minimap;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.MapSceneSpriteView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    @Test
    void shapedRasterUsesFourByFourTileMasksAndRotation() {
        WorldDocument document = new WorldDocument(3, 3, 1);
        document.tile(0, 1, 1).restore(new TileSnapshot(0, 0, 0, 0,
                1, 4, 1, 0, 0, List.of()));

        MinimapBuilder builder = new MinimapBuilder();
        MinimapImage rotationZero = builder.buildShaped(document, 0, definitions());
        assertEquals(12, rotationZero.width());
        assertEquals(12, rotationZero.height());
        assertEquals(0xFFA0B0C0, rotationZero.pixel(4, 4));
        assertEquals(0xFF102030, rotationZero.pixel(7, 4));
        assertEquals(0xFF102030, rotationZero.pixel(6, 5));

        document.tile(0, 1, 1).restore(new TileSnapshot(0, 0, 0, 0,
                1, 4, 1, 2, 0, List.of()));
        MinimapImage rotationTwo = builder.buildShaped(document, 0, definitions());
        assertEquals(0xFFA0B0C0, rotationTwo.pixel(5, 4));
        assertEquals(0xFFA0B0C0, rotationTwo.pixel(7, 4));
    }

    @Test
    void shapedRasterUsesOsrsHslWhenDefinitionProvidesBlendMetadata() {
        WorldDocument document = new WorldDocument(3, 3, 1);
        document.tile(0, 1, 1).restore(new TileSnapshot(0, 0, 0, 0,
                1, 0, 0, 0, 0, List.of()));
        DefinitionProvider definitions = new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) { return Optional.empty(); }
            @Override public Optional<FloorDefinitionView> underlay(int id) {
                return Optional.of(new FloorDefinitionView(id, -1, 0x102030,
                        32, 192, 96, 32, 256));
            }
            @Override public Optional<FloorDefinitionView> overlay(int id) { return Optional.empty(); }
        };

        int pixel = new MinimapBuilder().buildShaped(document, 0, definitions).pixel(4, 4);

        assertEquals(pixel, new MinimapBuilder().buildShaped(document, 0, definitions).pixel(7, 7));
        assertNotEquals(0xFF102030, pixel);
    }

    @Test
    void shapedRasterDrawsInteractiveWallMarkersAfterTerrain() {
        WorldDocument document = new WorldDocument(3, 3, 1);
        document.tile(0, 1, 1).restore(new TileSnapshot(0, 0, 0, 0,
                1, 0, 0, 0, 0,
                List.of(new WorldObject(99, 0, 1, 0, 1, 1))));
        DefinitionProvider definitions = new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) {
                return id == 99 ? Optional.of(new ObjectDefinitionView(99, "Gate", 1, 1,
                        List.of("Open"), new int[0])) : Optional.empty();
            }
            @Override public Optional<FloorDefinitionView> underlay(int id) {
                return Optional.of(floor(id, 0x102030));
            }
            @Override public Optional<FloorDefinitionView> overlay(int id) { return Optional.empty(); }
        };

        MinimapImage image = new MinimapBuilder().buildShaped(document, 0, definitions);

        assertEquals(0xFFEE0000, image.pixel(4, 4));
        assertEquals(0xFFEE0000, image.pixel(5, 4));
        assertNotEquals(0xFFEE0000, image.pixel(4, 5));
    }

    @Test
    void shapedRasterComposesNeutralMapSceneSpritesAtObjectBounds() {
        WorldDocument document = new WorldDocument(3, 3, 1);
        document.tile(0, 1, 1).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, 0,
                List.of(new WorldObject(99, 10, 0, 0, 1, 1))));
        DefinitionProvider definitions = new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) {
                return id == 99 ? Optional.of(new ObjectDefinitionView(99, "Castle", 1, 1,
                        List.of(), new int[0], 7)) : Optional.empty();
            }
            @Override public Optional<FloorDefinitionView> underlay(int id) { return Optional.empty(); }
            @Override public Optional<FloorDefinitionView> overlay(int id) { return Optional.empty(); }
            @Override public Optional<MapSceneSpriteView> mapScene(int id) {
                return id == 7 ? Optional.of(new MapSceneSpriteView(7, 2, 2, 0, 0,
                        new int[]{0, 0xFF123456, 0xFFABCDEF, 0})) : Optional.empty();
            }
        };

        MinimapImage image = new MinimapBuilder().buildShaped(document, 0, definitions);

        assertEquals(0xFF123456, image.pixel(6, 4));
        assertEquals(0xFFABCDEF, image.pixel(5, 5));
        assertEquals(0xFF000001, image.pixel(5, 4));
    }

    private static DefinitionProvider definitions() {
        return new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) { return Optional.empty(); }
            @Override public Optional<FloorDefinitionView> underlay(int id) {
                return id == 0 ? Optional.of(floor(id, 0x102030))
                        : id == 1 ? Optional.of(floor(id, 0x203040)) : Optional.empty();
            }
            @Override public Optional<FloorDefinitionView> overlay(int id) {
                return id == 2 ? Optional.of(floor(id, 0x102030))
                        : id == 3 ? Optional.of(floor(id, 0xA0B0C0)) : Optional.empty();
            }
        };
    }

    private static FloorDefinitionView floor(int id, int rgb) {
        return new FloorDefinitionView(id, -1, rgb, 0, 0, 0, 0, 0);
    }
}
