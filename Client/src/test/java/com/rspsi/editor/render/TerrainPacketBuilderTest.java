package com.rspsi.editor.render;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.terrain.TerrainMesh;
import com.rspsi.editor.terrain.TerrainMeshBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainPacketBuilderTest {
    @Test
    void combinesTopologyHslAndCornerLightIntoRendererPacket() {
        TileSnapshot tile = new TileSnapshot(0, 0, 0, 0,
                1, 0, 0, 0, 0, List.of());
        TerrainMesh topology = new TerrainMeshBuilder().build(tile);
        TerrainAppearance appearance = new TerrainAppearance(
                OsrsTerrainColorMath.packHsl(64, 128, 96), -1, -1,
                -1, -1, 0, 0, false);

        TerrainRenderPacket packet = new TerrainPacketBuilder().build(
                new TileCoordinate(0, 0, 0), topology, appearance,
                new TerrainLight(64, 96, 128, 80));

        assertEquals(2, packet.faces().size());
        assertEquals(appearance.underlayHsl(), packet.underlayHsl());
        assertTrue(packet.vertices().stream().anyMatch(vertex ->
                vertex.packedHsl() == OsrsTerrainColorMath.adjustPackedHslLight(
                        appearance.underlayHsl(), 64)));
        assertFalse(packet.faces().stream().anyMatch(face -> face.material() == 1));
    }


    @Test
    void carriesAuthoritativeTerrainNormalsIntoRenderVertices() {
        TileSnapshot tile = new TileSnapshot(0, 0, 0, 0,
                1, 0, 0, 0, 0, List.of());
        TerrainNormal southWest = new TerrainNormal(32, 250, -16, 1);
        TerrainNormalTile normals = new TerrainNormalTile(
                southWest, TerrainNormal.FLAT, TerrainNormal.FLAT, TerrainNormal.FLAT);

        TerrainRenderPacket packet = new TerrainPacketBuilder().build(
                new TileCoordinate(0, 0, 0),
                new TerrainMeshBuilder().build(tile),
                new TerrainAppearance(
                        OsrsTerrainColorMath.packHsl(64, 128, 96), -1, -1,
                        -1, -1, 0, 0, false),
                new TerrainLight(96, 96, 96, 96),
                normals);

        TerrainRenderVertex corner = packet.vertices().stream()
                .filter(vertex -> vertex.x() == 0 && vertex.y() == 0)
                .findFirst()
                .orElseThrow();
        assertEquals(southWest.x(), corner.normalX());
        assertEquals(southWest.y(), corner.normalY());
        assertEquals(southWest.z(), corner.normalZ());
        assertEquals(southWest.magnitude(), corner.normalMagnitude());
    }

    @Test
    void hiddenOverlayFacesAreOmittedButUnderlayFacesRemain() {
        TileSnapshot tile = new TileSnapshot(0, 0, 0, 0,
                1, 2, 2, 0, 0, List.of());
        TerrainRenderPacket packet = new TerrainPacketBuilder().build(
                new TileCoordinate(0, 0, 0),
                new TerrainMeshBuilder().build(tile),
                new TerrainAppearance(100, 200, -1, -1, -1, 1, 0, true),
                new TerrainLight(96, 96, 96, 96));

        assertTrue(packet.faces().stream().allMatch(face -> face.material() == 0));
        assertFalse(packet.faces().isEmpty());
    }

    @Test
    void usesPerCornerUnderlayHslAndClientMidpointMixing() {
        TileSnapshot tile = new TileSnapshot(0, 0, 0, 0,
                1, 1, 6, 0, 0, List.of());
        TerrainMesh topology = new TerrainMeshBuilder().build(tile);
        int southWest = OsrsTerrainColorMath.packHsl(8, 64, 40);
        int southEast = OsrsTerrainColorMath.packHsl(16, 96, 60);
        int northEast = OsrsTerrainColorMath.packHsl(24, 128, 80);
        int northWest = OsrsTerrainColorMath.packHsl(32, 160, 100);
        TerrainAppearance appearance = new TerrainAppearance(
                southWest, southWest, southEast, northEast, northWest,
                -1, -1, -1, -1, 6, 0, false);

        TerrainRenderPacket packet = new TerrainPacketBuilder().build(
                new TileCoordinate(0, 0, 0), topology, appearance,
                new TerrainLight(128, 128, 128, 128));

        assertTrue(packet.vertices().stream().anyMatch(vertex -> vertex.x() == 64
                && vertex.y() == 0
                && vertex.packedHsl() == OsrsTerrainColorMath.mixPackedHsl(southWest, southEast)));
    }

    @Test
    void texturedOverlayVerticesCarryOnlyTheTileLight() {
        TileSnapshot tile = new TileSnapshot(0, 0, 0, 0,
                0, 1, 0, 0, 0, List.of());
        TerrainAppearance appearance = new TerrainAppearance(
                -1, -1, -1, -1, -1,
                -1, -1, 9, 0x1234, 0, 0, false, 0x1234);

        TerrainRenderPacket packet = new TerrainPacketBuilder().build(
                new TileCoordinate(0, 0, 0), new TerrainMeshBuilder().build(tile),
                appearance, new TerrainLight(64, 96, 128, 80));

        assertEquals(-1, packet.overlayHsl());
        assertEquals(0x1234, packet.overlayMinimapHsl());
        assertTrue(packet.faces().stream().anyMatch(face -> face.material() == 1
                && face.textureId() == 9));
        // runescape-client class470 gives a textured overlay the colour -1,
        // which becomes a bare 7-bit light: no hue or saturation. The texture's
        // own RGB supplies the colour when it is rasterised (class272), so the
        // texture average must not leak into the vertex colour.
        assertTrue(packet.vertices().stream().allMatch(vertex ->
                (vertex.packedHsl() & 0xFF80) == 0));
        assertTrue(packet.vertices().stream().allMatch(vertex ->
                vertex.packedHsl() >= 2 && vertex.packedHsl() <= 126));
        assertEquals(1, packet.shape());
        assertFalse(packet.flat());
    }

    @Test
    void quarterShapePointsKeepTheirSourceCornerColours() {
        TileSnapshot tile = new TileSnapshot(0, 0, 0, 0,
                1, 1, 11, 0, 0, List.of());
        int southWest = OsrsTerrainColorMath.packHsl(8, 64, 40);
        int southEast = OsrsTerrainColorMath.packHsl(16, 96, 60);
        int northEast = OsrsTerrainColorMath.packHsl(24, 128, 80);
        int northWest = OsrsTerrainColorMath.packHsl(32, 160, 100);
        TerrainAppearance appearance = new TerrainAppearance(
                0, southWest, southEast, northEast, northWest,
                -1, -1, -1, -1, 10, 0, false);

        TerrainRenderPacket packet = new TerrainPacketBuilder().build(
                new TileCoordinate(0, 0, 0), new TerrainMeshBuilder().build(tile),
                appearance, new TerrainLight(128, 128, 128, 128));

        assertTrue(packet.vertices().stream().anyMatch(vertex -> vertex.x() == 32
                && vertex.y() == 32 && vertex.packedHsl() == southWest));
        assertTrue(packet.vertices().stream().anyMatch(vertex -> vertex.x() == 96
                && vertex.y() == 32 && vertex.packedHsl() == southEast));
    }
}
