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
                    TerrainAppearance appearance = scene.terrainAppearances().get(coordinate);
                    if (appearance != null) value.append("appearance=").append(appearance).append(';');
                    TerrainLight lighting = scene.terrainLighting().get(coordinate);
                    if (lighting != null) value.append("lighting=").append(lighting).append(';');
                    TerrainRenderPacket packet = scene.terrainPackets().get(coordinate);
                    if (packet != null) value.append("terrainPacket=").append(packet).append(';');
                    var collision = scene.collision().get(coordinate);
                    if (collision != null) value.append("collision=").append(collision.rawFlags()).append(';');
                }
            }
        }
        value.append("objects=").append(scene.objects()).append(';');
        value.append("renderObjects=").append(scene.renderObjects()).append(';');
        value.append("modelPackets=").append(scene.modelPackets()).append(';');
        value.append("bridges=").append(scene.bridges()).append(';');
        scene.textures().values().stream()
                .sorted(java.util.Comparator.comparingInt(RenderTextureResource::id))
                .forEach(texture -> value.append("texture=").append(texture.id())
                        .append(':').append(texture.definition())
                        .append(':').append(texture.pixelStatus())
                        .append(':').append(texture.width()).append('x').append(texture.height())
                        .append(':').append(java.util.Arrays.hashCode(texture.pixels())).append(';'));
        value.append("lightingProfile=").append(scene.lightingProfile());
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
