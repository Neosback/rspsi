package com.rspsi.api.model;

import com.rspsi.api.AABB;
import com.rspsi.api.Model;
import com.rspsi.editor.render.ClientModelBounds;
import com.rspsi.editor.render.ModelVertex;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link Model} produced by {@link DecodedModelData#light}. Owns copies of
 * the source geometry (the client shares them; a copy keeps a plugin's later
 * edit of the {@link com.rspsi.api.ModelData} from changing a lit model).
 * Bounds come from {@link ClientModelBounds}, the port of the client's
 * {@code calculateBoundsCylinder}/{@code calculateBoundingBox}.
 */
final class LitModel implements Model {
    final int[] faceColors1;
    final int[] faceColors2;
    final int[] faceColors3;
    private final int verticesCount;
    private final int[] verticesX;
    private final int[] verticesY;
    private final int[] verticesZ;
    private final int faceCount;
    private final int[] indices1;
    private final int[] indices2;
    private final int[] indices3;
    private final short[] unlitColors;
    private final byte[] faceAlphas;
    private final short[] faceTextures;
    private final byte[] faceRenderPriorities;
    private final byte[] textureFaces;
    private final int[] texIndices1;
    private final int[] texIndices2;
    private final int[] texIndices3;
    private final int[] normalsX;
    private final int[] normalsY;
    private final int[] normalsZ;
    private ClientModelBounds bounds;

    LitModel(DecodedModelData source, int[] normalsX, int[] normalsY, int[] normalsZ) {
        faceCount = source.faceCount;
        faceColors1 = new int[faceCount];
        faceColors2 = new int[faceCount];
        faceColors3 = new int[faceCount];
        verticesCount = source.verticesCount;
        verticesX = source.verticesX.clone();
        verticesY = source.verticesY.clone();
        verticesZ = source.verticesZ.clone();
        indices1 = source.indices1.clone();
        indices2 = source.indices2.clone();
        indices3 = source.indices3.clone();
        unlitColors = source.faceColors.clone();
        faceAlphas = source.faceAlphas == null ? null : source.faceAlphas.clone();
        faceTextures = source.faceTextures == null ? null : source.faceTextures.clone();
        faceRenderPriorities = source.faceRenderPriorities == null ? null : source.faceRenderPriorities.clone();
        this.normalsX = normalsX;
        this.normalsY = normalsY;
        this.normalsZ = normalsZ;

        // Texture triangles used by at least one face with texture render type 0
        // are compacted and re-indexed, as in the deob ModelData.toModel.
        if (source.textureTriangleCount > 0 && source.textureCoords != null) {
            int[] uses = new int[source.textureTriangleCount];
            for (int face = 0; face < faceCount; face++) {
                if (source.textureCoords[face] != -1) uses[source.textureCoords[face] & 255]++;
            }
            int kept = 0;
            for (int i = 0; i < source.textureTriangleCount; i++) {
                if (uses[i] > 0 && source.textureRenderTypes[i] == 0) kept++;
            }
            texIndices1 = new int[kept];
            texIndices2 = new int[kept];
            texIndices3 = new int[kept];
            int next = 0;
            for (int i = 0; i < source.textureTriangleCount; i++) {
                if (uses[i] > 0 && source.textureRenderTypes[i] == 0) {
                    texIndices1[next] = source.texTriangleX[i] & 0xFFFF;
                    texIndices2[next] = source.texTriangleY[i] & 0xFFFF;
                    texIndices3[next] = source.texTriangleZ[i] & 0xFFFF;
                    uses[i] = next++;
                } else {
                    uses[i] = -1;
                }
            }
            textureFaces = new byte[faceCount];
            for (int face = 0; face < faceCount; face++) {
                textureFaces[face] = source.textureCoords[face] != -1
                        ? (byte) uses[source.textureCoords[face] & 255] : (byte) -1;
            }
        } else {
            texIndices1 = null;
            texIndices2 = null;
            texIndices3 = null;
            textureFaces = null;
        }
    }

    @Override public int getVerticesCount() { return verticesCount; }
    @Override public int[] getVerticesX() { return verticesX; }
    @Override public int[] getVerticesY() { return verticesY; }
    @Override public int[] getVerticesZ() { return verticesZ; }
    @Override public int getFaceCount() { return faceCount; }
    @Override public int[] getFaceIndices1() { return indices1; }
    @Override public int[] getFaceIndices2() { return indices2; }
    @Override public int[] getFaceIndices3() { return indices3; }
    @Override public byte[] getFaceTransparencies() { return faceAlphas; }
    @Override public short[] getFaceTextures() { return faceTextures; }
    @Override public int[] getFaceColors1() { return faceColors1; }
    @Override public int[] getFaceColors2() { return faceColors2; }
    @Override public int[] getFaceColors3() { return faceColors3; }
    @Override public short[] getUnlitFaceColors() { return unlitColors; }
    @Override public byte[] getFaceRenderPriorities() { return faceRenderPriorities; }
    @Override public byte[] getTextureFaces() { return textureFaces; }
    @Override public int[] getTexIndices1() { return texIndices1; }
    @Override public int[] getTexIndices2() { return texIndices2; }
    @Override public int[] getTexIndices3() { return texIndices3; }
    @Override public int[] getVertexNormalsX() { return normalsX; }
    @Override public int[] getVertexNormalsY() { return normalsY; }
    @Override public int[] getVertexNormalsZ() { return normalsZ; }

    @Override public int getModelHeight() { return bounds().height(); }
    @Override public int getBottomY() { return bounds().bottomY(); }
    @Override public int getRadius() { return bounds().radius(); }
    @Override public int getDiameter() { return bounds().diameter(); }
    @Override public int getXYZMag() { return bounds().xzRadius(); }

    @Override
    public AABB getAABB(int orientation) {
        ClientModelBounds.Aabb box = ClientModelBounds.calculate(vertices(), orientation & 2047, false).drawAabb();
        return AABB.of(box.xMid(), box.yMid(), box.zMid(), box.xMidOffset(), box.yMidOffset(), box.zMidOffset());
    }

    @Override
    public Model rotateY90Ccw() {
        for (int i = 0; i < verticesCount; i++) {
            int x = verticesX[i];
            verticesX[i] = verticesZ[i];
            verticesZ[i] = -x;
        }
        bounds = null;
        return this;
    }

    @Override
    public Model rotateY180Ccw() {
        for (int i = 0; i < verticesCount; i++) {
            verticesX[i] = -verticesX[i];
            verticesZ[i] = -verticesZ[i];
        }
        bounds = null;
        return this;
    }

    @Override
    public Model rotateY270Ccw() {
        for (int i = 0; i < verticesCount; i++) {
            int z = verticesZ[i];
            verticesZ[i] = verticesX[i];
            verticesX[i] = -z;
        }
        bounds = null;
        return this;
    }

    @Override
    public Model translate(int x, int y, int z) {
        for (int i = 0; i < verticesCount; i++) {
            verticesX[i] += x;
            verticesY[i] += y;
            verticesZ[i] += z;
        }
        bounds = null;
        return this;
    }

    @Override
    public Model scale(int x, int y, int z) {
        for (int i = 0; i < verticesCount; i++) {
            verticesX[i] = verticesX[i] * x / 128;
            verticesY[i] = y * verticesY[i] / 128;
            verticesZ[i] = z * verticesZ[i] / 128;
        }
        bounds = null;
        return this;
    }

    private ClientModelBounds bounds() {
        if (bounds == null) bounds = ClientModelBounds.calculate(vertices(), 0, false);
        return bounds;
    }

    private List<ModelVertex> vertices() {
        List<ModelVertex> vertices = new ArrayList<>(verticesCount);
        for (int i = 0; i < verticesCount; i++) {
            vertices.add(new ModelVertex(verticesX[i], verticesY[i], verticesZ[i], 0, 0, 0, 0.0f, 0.0f));
        }
        return vertices;
    }
}
