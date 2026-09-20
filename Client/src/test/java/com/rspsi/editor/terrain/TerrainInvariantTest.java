package com.rspsi.editor.terrain;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.SetTileFlagsCommand;
import com.rspsi.editor.SetTileMaterialCommand;
import com.rspsi.editor.model.OsrsTileFlags;
import com.rspsi.editor.model.TerrainHeightSource;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TerrainInvariantTest {

    @Test
    void everyAuthoredShapeAndRotationHasUniqueTriangles() {
        TerrainMeshBuilder builder = new TerrainMeshBuilder();
        for (int shape = 0; shape <= 11; shape++) {
            for (int rotation = 0; rotation < 4; rotation++) {
                TileSnapshot tile = new TileSnapshot(
                        0, 0, 0, 0, 1, 1, shape, rotation, 0, List.of());
                TerrainMesh mesh = builder.build(tile);
                Set<String> triangles = new HashSet<>();
                for (TerrainFace face : mesh.faces()) {
                    String key = triangleKey(mesh, face);
                    assertTrue(triangles.add(key),
                            "duplicate terrain triangle for shape=" + shape + " rotation=" + rotation + ": " + key);
                }
            }
        }
    }

    @Test
    void sharedVertexMutationUpdatesEveryAdjacentCorner() {
        WorldDocument world = new WorldDocument(3, 3, 1);
        TerrainVertexLattice lattice = new TerrainVertexLattice(world);

        Set<TileCoordinate> changed = lattice.setHeight(0, 1, 1, 96);

        assertEquals(96, world.tile(0, 1, 1).snapshot().southWestHeight());
        assertEquals(96, world.tile(0, 0, 1).snapshot().southEastHeight());
        assertEquals(96, world.tile(0, 1, 0).snapshot().northWestHeight());
        assertEquals(96, world.tile(0, 0, 0).snapshot().northEastHeight());
        assertEquals(4, changed.size());
    }

    @Test
    void materialAndFlagCommandsPreserveHeightProvenance() {
        WorldDocument world = new WorldDocument(1, 1, 1);
        var tile = world.tile(0, 0, 0);
        TerrainHeightSource source = TerrainHeightSource.explicitSource(17);
        tile.restore(new TileSnapshot(32, 32, 32, 32, 1, 1, 0, 0, 0, List.of()), source);

        EditorSession session = new EditorSession(world);
        TileCoordinate coordinate = new TileCoordinate(0, 0, 0);
        TileSnapshot before = tile.snapshot();
        TileSnapshot materialAfter = new TileSnapshot(
                32, 32, 32, 32, 2, 3, 4, 1, 0, List.of(), source);
        session.execute(new SetTileMaterialCommand(
                coordinate, before, materialAfter, "material"));

        TileSnapshot flagBefore = tile.snapshot();
        TileSnapshot flagAfter = new TileSnapshot(
                32, 32, 32, 32,
                flagBefore.underlayId(), flagBefore.overlayId(),
                flagBefore.overlayShape(), flagBefore.overlayRotation(),
                OsrsTileFlags.REMOVE_ROOFS, List.of(), source);
        session.execute(new SetTileFlagsCommand(
                coordinate, flagBefore, flagAfter, "flags"));

        assertEquals(source, tile.heightSource());
        assertEquals(source, tile.snapshot().heightSource());

        session.undo();
        assertEquals(source, tile.heightSource());
        session.undo();
        assertEquals(source, tile.heightSource());
    }

    @Test
    void bridgeFlagResolvesEffectivePlane() {
        WorldDocument world = new WorldDocument(2, 2, 4);
        var bridgeTile = world.tile(1, 1, 1);
        TileSnapshot before = bridgeTile.snapshot();
        bridgeTile.restore(new TileSnapshot(
                before.southWestHeight(), before.southEastHeight(),
                before.northEastHeight(), before.northWestHeight(),
                before.underlayId(), before.overlayId(), before.overlayShape(),
                before.overlayRotation(), OsrsTileFlags.BRIDGE, before.objects()));

        assertEquals(0, world.effectivePlane(1, 1, 1));
        assertEquals(-1, world.effectivePlane(0, 1, 1));
        assertTrue(world.bridgeLink(new TileCoordinate(1, 1, 1)).isPresent());
    }

    private static String triangleKey(TerrainMesh mesh, TerrainFace face) {
        List<String> vertices = java.util.stream.Stream.of(face.a(), face.b(), face.c())
                .map(index -> mesh.vertices().get(index))
                .map(vertex -> vertex.x() + ":" + vertex.y() + ":" + vertex.height())
                .sorted()
                .toList();
        return String.join("|", vertices);
    }
}
