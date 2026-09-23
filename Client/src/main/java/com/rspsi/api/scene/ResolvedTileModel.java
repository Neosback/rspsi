package com.rspsi.api.scene;

import com.rspsi.api.Perspective;
import com.rspsi.api.SceneTileModel;
import com.rspsi.editor.render.TerrainRenderFace;
import com.rspsi.editor.render.TerrainRenderPacket;
import com.rspsi.editor.render.TerrainRenderVertex;

/** {@link SceneTileModel} over a shaped-overlay {@link TerrainRenderPacket}. */
final class ResolvedTileModel implements SceneTileModel {
    private final TerrainRenderPacket terrain;
    private final int[] vertexX;
    private final int[] vertexY;
    private final int[] vertexZ;
    private final int[] faceA;
    private final int[] faceB;
    private final int[] faceC;
    private final int[] colorA;
    private final int[] colorB;
    private final int[] colorC;
    private final int[] textures;
    private final boolean flat;

    private ResolvedTileModel(TerrainRenderPacket terrain, int sceneX, int sceneY, boolean flat) {
        this.terrain = terrain;
        this.flat = flat;
        int vertices = terrain.vertices().size();
        vertexX = new int[vertices];
        vertexY = new int[vertices];
        vertexZ = new int[vertices];
        int baseX = sceneX << Perspective.LOCAL_COORD_BITS;
        int baseZ = sceneY << Perspective.LOCAL_COORD_BITS;
        for (int i = 0; i < vertices; i++) {
            TerrainRenderVertex vertex = terrain.vertices().get(i);
            vertexX[i] = baseX + vertex.x();
            vertexY[i] = vertex.height();
            vertexZ[i] = baseZ + vertex.y();
        }
        int faces = terrain.faces().size();
        faceA = new int[faces];
        faceB = new int[faces];
        faceC = new int[faces];
        colorA = new int[faces];
        colorB = new int[faces];
        colorC = new int[faces];
        textures = new int[faces];
        for (int i = 0; i < faces; i++) {
            TerrainRenderFace face = terrain.faces().get(i);
            faceA[i] = face.a();
            faceB[i] = face.b();
            faceC[i] = face.c();
            colorA[i] = terrain.vertices().get(face.a()).packedHsl();
            colorB[i] = terrain.vertices().get(face.b()).packedHsl();
            colorC[i] = terrain.vertices().get(face.c()).packedHsl();
            textures[i] = face.textureId();
        }
    }

    static SceneTileModel of(TerrainRenderPacket terrain, int sceneX, int sceneY, boolean flat) {
        return new ResolvedTileModel(terrain, sceneX, sceneY, flat);
    }

    @Override public int getShape() { return terrain.shape(); }
    @Override public int getRotation() { return terrain.rotation(); }
    @Override public int getModelUnderlay() { return terrain.underlayHsl(); }
    @Override public int getModelOverlay() { return terrain.overlayHsl(); }
    @Override public int[] getVertexX() { return vertexX.clone(); }
    @Override public int[] getVertexY() { return vertexY.clone(); }
    @Override public int[] getVertexZ() { return vertexZ.clone(); }
    @Override public int[] getFaceX() { return faceA.clone(); }
    @Override public int[] getFaceY() { return faceB.clone(); }
    @Override public int[] getFaceZ() { return faceC.clone(); }
    @Override public int[] getTriangleColorA() { return colorA.clone(); }
    @Override public int[] getTriangleColorB() { return colorB.clone(); }
    @Override public int[] getTriangleColorC() { return colorC.clone(); }
    @Override public int[] getTriangleTextureId() { return textures.clone(); }
    @Override public boolean isFlat() { return flat; }
}
