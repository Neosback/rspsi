package com.rspsi.editor.render.compiler;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.render.LightingProfile;
import com.rspsi.editor.render.RenderChanges;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IncrementalSceneCompilerTest {
    @Test
    void reusesUnmodifiedTerrainAndRecompilesOnlyDirtyZoneNeighborhood() {
        WorldDocument world = new WorldDocument(24, 24, 1);
        IncrementalSceneCompiler compiler =
                new IncrementalSceneCompiler(new EmptyDefinitions(), LightingProfile.osrs());
        var initial = compiler.compileInitial(world, 0);

        TileCoordinate untouched = new TileCoordinate(0, 20, 20);
        var untouchedMesh = initial.terrainMeshes().get(untouched);

        TileCoordinate edited = new TileCoordinate(0, 2, 2);
        TileSnapshot before = world.tile(edited).snapshot();
        world.tile(edited).restore(new TileSnapshot(
                before.southWestHeight(), before.southEastHeight(),
                before.northEastHeight(), before.northWestHeight(),
                before.underlayId(), 1, before.overlayShape(), before.overlayRotation(),
                before.flags(), before.objects(), before.heightSource()));

        long baseline = compiler.baselineRevision();
        var updated = compiler.compile(initial, new RenderChanges(Set.of(edited)),
                InvalidationGraph.InvalidationCause.OVERLAY_EDIT, 0);

        assertSame(untouchedMesh, updated.terrainMeshes().get(untouched));
        assertNotSame(initial.terrainMeshes().get(edited), updated.terrainMeshes().get(edited));
        assertTrue(compiler.baselineRevision() > baseline);
        assertTrue(compiler.cachedZones().get(new InvalidationGraph.ZoneCoordinate(0, 0, 0))
                .revision() > baseline);
        assertEquals(9, compiler.cachedZoneCount());
    }

    @Test
    void emptyChangeSetReturnsExactPreviousScene() {
        WorldDocument world = new WorldDocument(8, 8, 1);
        IncrementalSceneCompiler compiler =
                new IncrementalSceneCompiler(new EmptyDefinitions(), LightingProfile.osrs());
        var initial = compiler.compileInitial(world, 0);

        assertSame(initial, compiler.compile(initial, RenderChanges.none(),
                InvalidationGraph.InvalidationCause.HEIGHT_EDIT, 0));
    }

    private static final class EmptyDefinitions implements DefinitionProvider {
        @Override public java.util.Optional<com.rspsi.cache.definition.ObjectDefinitionView> object(int id) {
            return java.util.Optional.empty();
        }
        @Override public java.util.Optional<com.rspsi.cache.definition.FloorDefinitionView> underlay(int id) {
            return java.util.Optional.empty();
        }
        @Override public java.util.Optional<com.rspsi.cache.definition.FloorDefinitionView> overlay(int id) {
            return java.util.Optional.empty();
        }
    }
}
