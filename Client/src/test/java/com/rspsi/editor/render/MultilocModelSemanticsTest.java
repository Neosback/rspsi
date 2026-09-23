package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ModelGeometryView;
import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultilocModelSemanticsTest {
    @Test
    void clientKeepsPlacedSceneFootprintButUsesDisplaySizeAndAppearanceForModel() {
        WorldDocument document = new WorldDocument(8, 8, 1);
        WorldObject placed = new WorldObject(1000, 10, 1, 0, 2, 3);
        document.tile(0, 2, 3).restore(new TileSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(placed)));

        ModelGeometryView geometry = new ModelGeometryView(
                7,
                new int[]{0, 0, 0, 64, 0, 0, 0, 0, 64},
                new int[]{0, 1, 2},
                new short[]{100},
                new int[]{0},
                new int[]{-1});

        ObjectAppearanceView placedAppearance = new ObjectAppearanceView(
                77, false, 128, 128, 128,
                0, 0, 0, Map.of(), Map.of());
        ObjectAppearanceView displayAppearance = new ObjectAppearanceView(
                88, false, 256, 128, 128,
                10, 0, 0, Map.of(), Map.of());

        DefinitionProvider definitions = new DefinitionProvider() {
            @Override
            public Optional<ObjectDefinitionView> object(int id) {
                if (id == 1000) {
                    return Optional.of(new ObjectDefinitionView(
                            id, "Placed shell", 2, 3, List.of(),
                            new int[0], new int[0], -1, false,
                            1234, -1, new int[]{2000}, 2000));
                }
                if (id == 2000) {
                    return Optional.of(new ObjectDefinitionView(
                            id, "Display child", 1, 1, List.of(),
                            new int[]{7}, new int[]{10}, -1, false));
                }
                return Optional.empty();
            }

            @Override
            public Optional<ObjectAppearanceView> objectAppearance(int id) {
                return switch (id) {
                    case 1000 -> Optional.of(placedAppearance);
                    case 2000 -> Optional.of(displayAppearance);
                    default -> Optional.empty();
                };
            }

            @Override
            public Optional<ModelGeometryView> modelGeometry(int id) {
                return id == 7 ? Optional.of(geometry) : Optional.empty();
            }

            @Override
            public Optional<FloorDefinitionView> underlay(int id) {
                return Optional.empty();
            }

            @Override
            public Optional<FloorDefinitionView> overlay(int id) {
                return Optional.empty();
            }
        };

        ModelRenderPacket packet =
                new ModelPacketBuilder(definitions).build(placed, document).orElseThrow();

        // FriendSystem.addObjects creates the scene occupancy rectangle from
        // the placed 2x3 definition. Rotation 1 swaps it to 3x2.
        assertEquals(3, packet.gameObjectSceneMetadata().sizeX());
        assertEquals(2, packet.gameObjectSceneMetadata().sizeY());
        assertEquals(3, packet.sceneObjectIdentity().footprintWidth());
        assertEquals(2, packet.sceneObjectIdentity().footprintLength());

        // DynamicObject.getModel() resolves the child first. The display child
        // is 1x1, so its model centre is one half-tile (64) from the anchor.
        // Its scaleX=256 and offsetX=10 expand/shift this triangle to x 74..202.
        assertEquals(74, packet.minX());
        assertEquals(202, packet.maxX());

        // DynamicObject was constructed with the placed definition's
        // animation id even though getModelDynamic() runs on the child.
        assertEquals(77, packet.animationId());
    }

    @Test
    void neutralRenderObjectUsesDisplayModelsAndAppearanceButPlacedFootprint() {
        WorldDocument document = new WorldDocument(4, 4, 1);
        WorldObject placed = new WorldObject(1000, 10, 0, 0, 1, 1);
        document.tile(0, 1, 1).restore(new TileSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(placed)));

        ObjectAppearanceView displayAppearance = new ObjectAppearanceView(
                -1, false, 192, 128, 128,
                4, 0, 0, Map.of(), Map.of());

        DefinitionProvider definitions = new DefinitionProvider() {
            @Override
            public Optional<ObjectDefinitionView> object(int id) {
                if (id == 1000) {
                    return Optional.of(new ObjectDefinitionView(
                            id, "Placed shell", 2, 3, List.of(),
                            new int[0], new int[0], -1, false,
                            1234, -1, new int[]{2000}, 2000));
                }
                if (id == 2000) {
                    return Optional.of(new ObjectDefinitionView(
                            id, "Display child", 1, 1, List.of(),
                            new int[]{7}, new int[]{10}, -1, false));
                }
                return Optional.empty();
            }

            @Override
            public Optional<ObjectAppearanceView> objectAppearance(int id) {
                return id == 2000 ? Optional.of(displayAppearance) : Optional.empty();
            }

            @Override
            public Optional<FloorDefinitionView> underlay(int id) {
                return Optional.empty();
            }

            @Override
            public Optional<FloorDefinitionView> overlay(int id) {
                return Optional.empty();
            }
        };

        RenderObject object = new RenderSceneBuilder(definitions)
                .build(document).renderObjects().get(0);

        assertEquals(2, object.footprintWidth());
        assertEquals(3, object.footprintLength());
        assertEquals(List.of(7),
                java.util.Arrays.stream(object.modelIds()).boxed().toList());
        assertEquals(displayAppearance, object.appearance());
    }
}
