package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TilePreviewBuilderTest {
    private static final DefinitionProvider FLOORS = new DefinitionProvider() {
        @Override
        public Optional<com.rspsi.cache.definition.ObjectDefinitionView> object(int id) {
            return Optional.empty();
        }

        @Override
        public Optional<FloorDefinitionView> underlay(int id) {
            return switch (id) {
                case 0 -> Optional.of(new FloorDefinitionView(0, -1, 0xAA0000, 16, 200, 80, 64, 256));
                case 1 -> Optional.of(new FloorDefinitionView(1, -1, 0x00AA00, 48, 160, 120, 192, 256));
                default -> Optional.empty();
            };
        }

        @Override
        public Optional<FloorDefinitionView> overlay(int id) {
            return Optional.empty();
        }
    };

    @Test
    void blendedPreviewIsTheScenePacketForThatTile() {
        WorldDocument document = document();
        TerrainRenderPacket preview = new TilePreviewBuilder()
                .build(document, FLOORS, 0, 5, 5, TilePreviewBuilder.Mode.BLENDED).orElseThrow();

        TerrainRenderPacket scene = new RenderSceneBuilder(FLOORS).build(document).terrainPackets()
                .get(new com.rspsi.editor.model.TileCoordinate(0, 5, 5));
        assertEquals(scene.vertices(), preview.vertices());
    }

    @Test
    void unblendedPreviewPaintsOnlyTheTilesOwnUnderlay() {
        WorldDocument document = document();
        TilePreviewBuilder builder = new TilePreviewBuilder();
        TerrainRenderPacket blended = builder.build(document, FLOORS, 0, 5, 5,
                TilePreviewBuilder.Mode.BLENDED).orElseThrow();
        TerrainRenderPacket unblended = builder.build(document, FLOORS, 0, 5, 5,
                TilePreviewBuilder.Mode.UNBLENDED).orElseThrow();

        assertNotEquals(blended.vertices(), unblended.vertices(),
                "the red neighbours bleed into this tile only when blending is on");
        assertEquals(blended.faces().size(), unblended.faces().size());
        int hueSaturation = unblended.vertices().get(0).packedHsl() & 0xFF80;
        assertTrue(unblended.vertices().stream().allMatch(v -> (v.packedHsl() & 0xFF80) == hueSaturation),
                "one underlay colour on every corner, only the light varies");
    }

    @Test
    void tileWithoutFloorHasNoPreview() {
        WorldDocument document = new WorldDocument(11, 11, 1);
        assertTrue(new TilePreviewBuilder().build(document, FLOORS, 0, 5, 5,
                TilePreviewBuilder.Mode.BLENDED).isEmpty());
    }

    /** A green tile at (5,5) inside a field of red underlay. */
    private static WorldDocument document() {
        WorldDocument document = new WorldDocument(11, 11, 1);
        for (int x = 0; x < 11; x++) {
            for (int y = 0; y < 11; y++) {
                document.tile(0, x, y).restore(new TileSnapshot(0, 0, 0, 0, 1, 0, 0, 0, 0, List.of()));
            }
        }
        document.tile(0, 5, 5).restore(new TileSnapshot(0, 0, 0, 0, 2, 0, 0, 0, 0, List.of()));
        return document;
    }
}
