package com.rspsi.editor.render;

import com.rspsi.editor.model.BridgeLink;
import com.rspsi.editor.model.OsrsTileFlags;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.terrain.TerrainMesh;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Locks one mixed-plane scene snapshot until external parity fixtures replace it. */
class RenderSceneGoldenTest {
    private static final String GOLDEN =
            "898841d8457936158c95eb8a6b929eab21de92d69cfb91ccb60a451585ffb335";

    @Test
    void mixedTerrainObjectsAndBridgeHaveStableNeutralSceneSemantics() {
        WorldDocument document = fixture();
        RenderScene scene = new RenderSceneBuilder().build(document);

        assertEquals(8, scene.terrainMeshes().size());
        assertEquals(3, scene.objects().size());
        assertEquals(List.of(new BridgeLink(new TileCoordinate(1, 0, 1),
                new TileCoordinate(0, 0, 1))), scene.bridges());
        assertEquals(GOLDEN, digest(scene));
    }

    private static WorldDocument fixture() {
        WorldDocument document = new WorldDocument(2, 2, 2);
        document.tile(0, 0, 0).restore(new TileSnapshot(10, 20, 30, 40,
                1, 2, 0, 0, 0,
                List.of(new WorldObject(100, 10, 0, 0, 0, 0))));
        document.tile(0, 1, 0).restore(new TileSnapshot(20, 30, 40, 50,
                2, 3, 4, 1, 0,
                List.of(new WorldObject(101, 0, 1, 0, 1, 0))));
        document.tile(1, 0, 1).restore(new TileSnapshot(30, 40, 50, 60,
                3, 4, 7, 2, OsrsTileFlags.BRIDGE,
                List.of(new WorldObject(102, 22, 3, 1, 0, 1))));
        return document;
    }

    private static String digest(RenderScene scene) {
        StringBuilder value = new StringBuilder();
        for (int plane = 0; plane < scene.document().planes(); plane++) {
            for (int x = 0; x < scene.document().width(); x++) {
                for (int y = 0; y < scene.document().length(); y++) {
                    TileCoordinate coordinate = new TileCoordinate(plane, x, y);
                    TerrainMesh mesh = scene.terrainMeshes().get(coordinate);
                    value.append(coordinate).append('=').append(mesh.vertices())
                            .append('|').append(mesh.faces()).append(';');
                }
            }
        }
        value.append("objects=").append(scene.objects()).append(';');
        value.append("bridges=").append(scene.bridges());
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte byteValue : bytes) result.append(String.format("%02x", byteValue & 0xFF));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
