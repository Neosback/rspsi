package com.rspsi.editor.render;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.terrain.TerrainMesh;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Produces a deterministic digest for renderer-neutral scene data.
 *
 * <p>This is deliberately not a rendered-image hash. It gives the cache
 * verifier and future renderer parity harness a stable identity for the
 * scene snapshot constructed from the canonical world model.</p>
 */
public final class RenderSceneFingerprint {
    private RenderSceneFingerprint() {
    }

    public static String sha256(RenderScene scene) {
        if (scene == null) throw new NullPointerException("scene");
        StringBuilder value = new StringBuilder();
        for (int plane = 0; plane < scene.document().planes(); plane++) {
            for (int x = 0; x < scene.document().width(); x++) {
                for (int y = 0; y < scene.document().length(); y++) {
                    TileCoordinate coordinate = new TileCoordinate(plane, x, y);
                    TerrainMesh mesh = scene.terrainMeshes().get(coordinate);
                    value.append(coordinate).append('=').append(mesh == null ? "<missing>" : mesh.vertices())
                            .append('|').append(mesh == null ? "<missing>" : mesh.faces()).append(';');
                    TerrainMaterial material = scene.terrainMaterials().get(coordinate);
                    if (material != null) value.append("material=").append(material).append(';');
                    TerrainLight lighting = scene.terrainLighting().get(coordinate);
                    if (lighting != null) value.append("lighting=").append(lighting).append(';');
                }
            }
        }
        value.append("objects=").append(scene.objects()).append(';');
        value.append("bridges=").append(scene.bridges());
        return sha256(value.toString());
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte byteValue : bytes) result.append(String.format("%02x", byteValue & 0xFF));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
