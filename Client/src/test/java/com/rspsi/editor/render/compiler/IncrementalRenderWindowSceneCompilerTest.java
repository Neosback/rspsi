package com.rspsi.editor.render.compiler;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.editor.model.OsrsTileFlags;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldRegion;
import com.rspsi.editor.model.WorldRegionWindow;
import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.render.RenderWindowScene;
import com.rspsi.editor.render.RenderWindowSceneBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncrementalRenderWindowSceneCompilerTest {
    @Test
    void underlayEditRecompilesOnlyWindowZonesAndMatchesFullStitchedBuild() {
        WorldDocument west = filledUnderlay(1);
        WorldDocument east = filledUnderlay(1);
        WorldRegion westRegion = new WorldRegion(10, 20, west);
        WorldRegion eastRegion = new WorldRegion(11, 20, east);
        WorldRegionWindow window = new WorldRegionWindow(10, 20, 2, 1,
                Map.of(westRegion.regionId(), westRegion, eastRegion.regionId(), eastRegion));
        DefinitionProvider definitions = definitions();
        RenderWindowSceneBuilder fullBuilder = new RenderWindowSceneBuilder(definitions);
        RenderWindowScene initial = fullBuilder.build(window);

        WorldTileAddress edited = WorldTileAddress.of(11 * 64, 20 * 64 + 32, 0);
        WorldTileAddress affectedAcrossBoundary =
                WorldTileAddress.of(10 * 64 + 63, 20 * 64 + 32, 0);
        WorldTileAddress untouched = WorldTileAddress.of(10 * 64 + 8, 20 * 64 + 8, 0);
        var oldUntouched = initial.terrainPackets().get(untouched);
        var oldBoundary = initial.terrainPackets().get(affectedAcrossBoundary);

        TileSnapshot before = east.tile(0, 0, 32).snapshot();
        east.tile(0, 0, 32).restore(new TileSnapshot(
                before.southWestHeight(), before.southEastHeight(),
                before.northEastHeight(), before.northWestHeight(),
                2, before.overlayId(), before.overlayShape(), before.overlayRotation(),
                before.flags(), before.objects(), before.heightSource()));

        IncrementalRenderWindowSceneCompiler compiler =
                new IncrementalRenderWindowSceneCompiler(definitions);
        var update = compiler.compile(initial, window, Set.of(edited), 0);
        RenderWindowScene expected = fullBuilder.build(window);
        RenderWindowScene actual = update.scene();

        assertFalse(update.fullRebuild());
        assertTrue(update.compiledVisibleTiles() > 0);
        assertTrue(update.compiledVisibleTiles() < initial.terrainPackets().size());
        assertTrue(!update.dirtyZones().isEmpty());

        assertEquals(expected.terrainMeshes(), actual.terrainMeshes());
        assertEquals(expected.terrainMaterials(), actual.terrainMaterials());
        assertEquals(expected.terrainAppearances(), actual.terrainAppearances());
        assertEquals(expected.terrainLighting(), actual.terrainLighting());
        assertEquals(expected.terrainPackets(), actual.terrainPackets());
        assertEquals(expected.textures(), actual.textures());

        assertSame(oldUntouched, actual.terrainPackets().get(untouched),
                "far terrain packets must be reused by identity");
        assertNotSame(oldBoundary, actual.terrainPackets().get(affectedAcrossBoundary),
                "radius-five blending must invalidate the neighboring region edge");
    }

    @Test
    void structuralMutationUsesConservativeFullBuildFallback() {
        WorldDocument document = filledUnderlay(1);
        WorldRegion region = new WorldRegion(10, 20, document);
        WorldRegionWindow window = new WorldRegionWindow(10, 20, 1, 1,
                Map.of(region.regionId(), region));
        DefinitionProvider definitions = definitions();
        RenderWindowScene initial = new RenderWindowSceneBuilder(definitions).build(window);

        TileSnapshot before = document.tile(1, 4, 5).snapshot();
        document.tile(1, 4, 5).restore(new TileSnapshot(
                before.southWestHeight(), before.southEastHeight(),
                before.northEastHeight(), before.northWestHeight(),
                before.underlayId(), before.overlayId(), before.overlayShape(),
                before.overlayRotation(), OsrsTileFlags.BRIDGE,
                before.objects(), before.heightSource()));

        WorldTileAddress changed = WorldTileAddress.of(10 * 64 + 4, 20 * 64 + 5, 1);
        var update = new IncrementalRenderWindowSceneCompiler(definitions)
                .compile(initial, window, Set.of(changed), 0);

        assertTrue(update.fullRebuild());
        assertEquals("structural terrain/object change", update.reason());
        assertEquals(0, update.scene().terrainPackets().get(changed).coordinate().plane());
    }

    @Test
    void emptyChangeSetReturnsExactPreviousScene() {
        WorldDocument document = filledUnderlay(1);
        WorldRegion region = new WorldRegion(10, 20, document);
        WorldRegionWindow window = new WorldRegionWindow(10, 20, 1, 1,
                Map.of(region.regionId(), region));
        DefinitionProvider definitions = definitions();
        RenderWindowScene initial = new RenderWindowSceneBuilder(definitions).build(window);

        var update = new IncrementalRenderWindowSceneCompiler(definitions)
                .compile(initial, window, Set.of(), 0);

        assertSame(initial, update.scene());
        assertFalse(update.fullRebuild());
        assertEquals(0, update.compiledVisibleTiles());
    }

    private static WorldDocument filledUnderlay(int underlayId) {
        WorldDocument document = new WorldDocument(64, 64, 4);
        for (int plane = 0; plane < document.planes(); plane++) {
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    document.tile(plane, x, y).restore(new TileSnapshot(
                            0, 0, 0, 0, underlayId, 0, 0, 0, 0, List.of()));
                }
            }
        }
        return document;
    }

    private static DefinitionProvider definitions() {
        return new DefinitionProvider() {
            @Override
            public Optional<com.rspsi.cache.definition.ObjectDefinitionView> object(int id) {
                return Optional.empty();
            }

            @Override
            public Optional<FloorDefinitionView> underlay(int id) {
                if (id == 0) {
                    return Optional.of(new FloorDefinitionView(
                            id, -1, 0x334455, 24, 96, 72, 48, 256));
                }
                if (id == 1) {
                    return Optional.of(new FloorDefinitionView(
                            id, -1, 0x886633, 72, 112, 88, 64, 256));
                }
                return Optional.empty();
            }

            @Override
            public Optional<FloorDefinitionView> overlay(int id) {
                return Optional.empty();
            }
        };
    }
}
