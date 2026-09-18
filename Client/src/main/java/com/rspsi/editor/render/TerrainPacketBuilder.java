package com.rspsi.editor.render;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.terrain.TerrainFace;
import com.rspsi.editor.terrain.TerrainMesh;
import com.rspsi.editor.terrain.TerrainVertex;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Combines neutral terrain geometry, appearance, and lighting into one tile
 * packet for a renderer backend.
 *
 * <p>This builder deliberately does not know about FileStore, JavaFX, OpenGL,
 * or the legacy software renderer. The packet retains packed OSRS HSL values;
 * a backend may convert those values through the selected client palette or
 * shader without changing the authored scene.</p>
 */
public final class TerrainPacketBuilder {
    public TerrainRenderPacket build(TileCoordinate coordinate,
                                     TerrainMesh topology,
                                     TerrainAppearance appearance,
                                     TerrainLight cornerLighting) {
        Objects.requireNonNull(coordinate, "coordinate");
        Objects.requireNonNull(topology, "topology");
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(cornerLighting, "cornerLighting");

        List<TerrainRenderVertex> vertices = new ArrayList<>();
        List<TerrainRenderFace> faces = new ArrayList<>();
        Map<VertexKey, Integer> vertexIndexes = new LinkedHashMap<>();

        for (TerrainFace sourceFace : topology.faces()) {
            if (sourceFace.material() == 1 && appearance.overlayHidden()) {
                // The client treats the hidden overlay sentinel as a hole;
                // the underlay remains available through material 0 faces.
                continue;
            }

            if (!renderableFace(sourceFace.material(), appearance)) {
                continue;
            }

            int a = vertexIndex(sourceFace.material(), sourceFace.a(), topology,
                    cornerLighting, appearance, vertices, vertexIndexes);
            int b = vertexIndex(sourceFace.material(), sourceFace.b(), topology,
                    cornerLighting, appearance, vertices, vertexIndexes);
            int c = vertexIndex(sourceFace.material(), sourceFace.c(), topology,
                    cornerLighting, appearance, vertices, vertexIndexes);

            int textureId = sourceFace.material() == 1 ? appearance.textureId() : -1;
            faces.add(new TerrainRenderFace(a, b, c, sourceFace.material(),
                    textureId, 255, 0));
        }

        boolean overlayPresent = appearance.overlayHsl() != -1 || appearance.textureId() >= 0;
        int sceneShape = overlayPresent ? appearance.shape() + 1 : 0;
        return new TerrainRenderPacket(coordinate, vertices, faces,
                sceneShape, overlayPresent ? appearance.rotation() : 0, appearance.textureId(),
                appearance.underlayHsl(), appearance.overlayHsl(),
                !overlayPresent, appearance.overlayHidden(),
                appearance.overlayMinimapHsl());
    }

    private static boolean renderableFace(int material, TerrainAppearance appearance) {
        if (material == 0) {
            return appearance.underlayHsl() >= 0;
        }
        if (appearance.overlayHidden() || appearance.overlayHsl() == -2) {
            return false;
        }
        // A textured overlay is rendered with light-only vertex HSL (-1).
        return appearance.overlayHsl() >= 0 || appearance.textureId() >= 0;
    }

    private static int underlayHsl(int sourceIndex, TerrainMesh topology,
                                   TerrainAppearance appearance) {
        TerrainVertex vertex = topology.vertices().get(sourceIndex);
        int x = vertex.x();
        int y = vertex.y();
        int southWest = appearance.underlayHslSouthWest();
        int southEast = appearance.underlayHslSouthEast();
        int northEast = appearance.underlayHslNorthEast();
        int northWest = appearance.underlayHslNorthWest();
        if (x == 64 && y <= 32) return OsrsTerrainColorMath.mixPackedHsl(southWest, southEast);
        if (x == 96 && y == 64) return OsrsTerrainColorMath.mixPackedHsl(southEast, northEast);
        if (x == 64 && y >= 96) return OsrsTerrainColorMath.mixPackedHsl(northWest, northEast);
        if (x == 32 && y == 64) return OsrsTerrainColorMath.mixPackedHsl(northWest, southWest);
        // Shape points 13..16 are quarter points that inherit the corner
        // colour directly in the client SceneTileModel builder. They are not
        // the tile-average HSL, even though their positions are interior.
        if (x == 32 && y == 32) return southWest;
        if (x == 96 && y == 32) return southEast;
        if (x == 96 && y == 96) return northEast;
        if (x == 32 && y == 96) return northWest;
        if (x < 64 && y < 64) return southWest;
        if (x > 64 && y < 64) return southEast;
        if (x > 64 && y > 64) return northEast;
        if (x < 64 && y > 64) return northWest;
        return appearance.underlayHsl();
    }

    private static int vertexIndex(int material,
                                   int sourceIndex,
                                   TerrainMesh topology,
                                     TerrainLight lighting,
                                     TerrainAppearance appearance,
                                   List<TerrainRenderVertex> vertices,
                                   Map<VertexKey, Integer> vertexIndexes) {
        TerrainVertex source = topology.vertices().get(sourceIndex);
        VertexKey key = new VertexKey(material, sourceIndex);
        Integer existing = vertexIndexes.get(key);
        if (existing != null) {
            return existing;
        }

        int light = bilinearLight(source.x(), source.y(), lighting);
        int baseHsl = material == 1
                ? OsrsTerrainColorMath.adjustOverlayHslLight(appearance.overlayHsl(), light)
                : OsrsTerrainColorMath.adjustPackedHslLight(
                underlayHsl(sourceIndex, topology, appearance), light);
        int index = vertices.size();
        vertices.add(new TerrainRenderVertex(source.x(), source.y(), source.height(),
                baseHsl,
                source.x(), source.y()));
        vertexIndexes.put(key, index);
        return index;
    }

    /** Bilinear interpolation preserves exact corner values and shaped edges. */
    private static int bilinearLight(int x, int y, TerrainLight light) {
        int inverseX = 128 - x;
        int inverseY = 128 - y;
        long weighted = (long) light.southWest() * inverseX * inverseY
                + (long) light.southEast() * x * inverseY
                + (long) light.northEast() * x * y
                + (long) light.northWest() * inverseX * y;
        return (int) ((weighted + 8192) / 16384);
    }

    private record VertexKey(int material, int sourceIndex) {
    }
}
