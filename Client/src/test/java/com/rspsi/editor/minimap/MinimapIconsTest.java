package com.rspsi.editor.minimap;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.MapElementDefinitionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinimapIconsTest {
    private static final DefinitionProvider DEFINITIONS = new DefinitionProvider() {
        @Override
        public Optional<ObjectDefinitionView> object(int id) {
            return Optional.of(new ObjectDefinitionView(id, "Booth", 1, 1, List.of(), new int[0]));
        }

        @Override
        public OptionalInt objectMapElement(int objectId) {
            return switch (objectId) {
                case 1 -> OptionalInt.of(5);   // bank, minimap-visible
                case 2 -> OptionalInt.of(6);   // world-map only
                default -> OptionalInt.empty();
            };
        }

        @Override
        public Optional<MapElementDefinitionView> mapElement(int id) {
            return switch (id) {
                case 5 -> Optional.of(new MapElementDefinitionView(5, 1453, -1, "Bank", 0, 0, 0,
                        true, true, false, List.of()));
                case 6 -> Optional.of(new MapElementDefinitionView(6, 1454, -1, "Label", 0, 0, 0,
                        true, false, false, List.of()));
                default -> Optional.empty();
            };
        }

        @Override public Optional<FloorDefinitionView> underlay(int id) { return Optional.empty(); }
        @Override public Optional<FloorDefinitionView> overlay(int id) { return Optional.empty(); }
    };

    @Test
    void onlyMinimapVisibleElementsOnThePlaneAppearOncePerTile() {
        WorldDocument document = new WorldDocument(3, 1, 2);
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of(new WorldObject(1, 10, 0, 0, 0, 0), new WorldObject(1, 10, 1, 0, 0, 0))));
        document.tile(0, 1, 0).restore(new TileSnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of(new WorldObject(2, 10, 0, 0, 1, 0))));
        document.tile(1, 2, 0).restore(new TileSnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of(new WorldObject(1, 10, 0, 1, 2, 0))));

        List<MinimapIcons.Icon> plane0 = MinimapIcons.locate(document, DEFINITIONS, 0);

        assertEquals(List.of(new MinimapIcons.Icon(0, 0, 5, 1453, "Bank")), plane0);
        assertEquals(1, MinimapIcons.locate(document, DEFINITIONS, 1).size());
    }
}
