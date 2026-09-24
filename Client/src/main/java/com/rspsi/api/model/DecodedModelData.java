package com.rspsi.api.model;

import com.rspsi.api.Model;
import com.rspsi.api.ModelData;
import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ModelGeometryView;

import java.util.Optional;

/**
 * {@link ModelData} over a decoded cache model. Every instance from
 * {@link #load} owns fresh arrays, so plugins can transform it freely.
 *
 * <p>Transforms and {@link #light} are ports of the deob {@code ModelData}
 * ({@code runescape-client/ModelData.java}: {@code method5273}/{@code
 * method5274}/{@code method5275} rotations, {@code changeOffset},
 * {@code resize}, {@code recolor}, {@code retexture},
 * {@code calculateVertexNormals} and {@code toModel}).</p>
 */
public final class DecodedModelData implements ModelData {
    int verticesCount;
    int[] verticesX;
    int[] verticesY;
    int[] verticesZ;
    int faceCount;
    int[] indices1;
    int[] indices2;
    int[] indices3;
    short[] faceColors;
    byte[] faceAlphas;
    short[] faceTextures;
    byte[] faceRenderTypes;
    byte[] faceRenderPriorities;
    byte[] textureCoords;
    int textureTriangleCount;
    int[] texTriangleX;
    int[] texTriangleY;
    int[] texTriangleZ;
    byte[] textureRenderTypes;

    private DecodedModelData() {
    }

    /** Loads a model as a fresh, independently mutable copy. */
    public static Optional<ModelData> load(DefinitionProvider definitions, int modelId) {
        return definitions.modelGeometry(modelId).map(DecodedModelData::of);
    }

    public static DecodedModelData of(ModelGeometryView geometry) {
        DecodedModelData data = new DecodedModelData();
        int[] positions = geometry.vertexPositions();
        data.verticesCount = geometry.vertexCount();
        data.verticesX = new int[data.verticesCount];
        data.verticesY = new int[data.verticesCount];
        data.verticesZ = new int[data.verticesCount];
        for (int i = 0; i < data.verticesCount; i++) {
            data.verticesX[i] = positions[i * 3];
            data.verticesY[i] = positions[i * 3 + 1];
            data.verticesZ[i] = positions[i * 3 + 2];
        }
        int[] triangles = geometry.triangleIndices();
        data.faceCount = geometry.triangleCount();
        data.indices1 = new int[data.faceCount];
        data.indices2 = new int[data.faceCount];
        data.indices3 = new int[data.faceCount];
        for (int i = 0; i < data.faceCount; i++) {
            data.indices1[i] = triangles[i * 3];
            data.indices2[i] = triangles[i * 3 + 1];
            data.indices3[i] = triangles[i * 3 + 2];
        }
        data.faceColors = geometry.triangleColors().clone();
        data.faceAlphas = bytesOrNull(geometry.triangleAlphas(), data.faceCount);
        int[] textures = geometry.triangleTextures();
        if (textures.length == data.faceCount && textures.length > 0) {
            data.faceTextures = new short[data.faceCount];
            for (int i = 0; i < data.faceCount; i++) data.faceTextures[i] = (short) textures[i];
        }
        data.faceRenderTypes = bytesOrNull(geometry.triangleRenderTypes(), data.faceCount);
        data.faceRenderPriorities = bytesOrNull(geometry.triangleRenderPriorities(), data.faceCount);
        data.textureCoords = bytesOrNull(geometry.textureCoordinates(), data.faceCount);
        int[] textureTriangles = geometry.textureTriangleIndices();
        data.textureTriangleCount = textureTriangles.length / 3;
        data.texTriangleX = new int[data.textureTriangleCount];
        data.texTriangleY = new int[data.textureTriangleCount];
        data.texTriangleZ = new int[data.textureTriangleCount];
        for (int i = 0; i < data.textureTriangleCount; i++) {
            data.texTriangleX[i] = textureTriangles[i * 3];
            data.texTriangleY[i] = textureTriangles[i * 3 + 1];
            data.texTriangleZ[i] = textureTriangles[i * 3 + 2];
        }
        int[] textureTypes = geometry.textureRenderTypes();
        data.textureRenderTypes = new byte[data.textureTriangleCount];
        for (int i = 0; i < Math.min(textureTypes.length, data.textureTriangleCount); i++) {
            data.textureRenderTypes[i] = (byte) textureTypes[i];
        }
        return data;
    }

    private static byte[] bytesOrNull(int[] values, int count) {
        if (values == null || values.length != count || count == 0) return null;
        byte[] result = new byte[count];
        for (int i = 0; i < count; i++) result[i] = (byte) values[i];
        return result;
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
    @Override public short[] getFaceColors() { return faceColors; }

    @Override
    public ModelData rotateY90Ccw() {
        for (int i = 0; i < verticesCount; i++) {
            int x = verticesX[i];
            verticesX[i] = verticesZ[i];
            verticesZ[i] = -x;
        }
        return this;
    }

    @Override
    public ModelData rotateY180Ccw() {
        for (int i = 0; i < verticesCount; i++) {
            verticesX[i] = -verticesX[i];
            verticesZ[i] = -verticesZ[i];
        }
        return this;
    }

    @Override
    public ModelData rotateY270Ccw() {
        for (int i = 0; i < verticesCount; i++) {
            int z = verticesZ[i];
            verticesZ[i] = verticesX[i];
            verticesX[i] = -z;
        }
        return this;
    }

    @Override
    public ModelData translate(int x, int y, int z) {
        for (int i = 0; i < verticesCount; i++) {
            verticesX[i] += x;
            verticesY[i] += y;
            verticesZ[i] += z;
        }
        return this;
    }

    @Override
    public ModelData scale(int x, int y, int z) {
        for (int i = 0; i < verticesCount; i++) {
            verticesX[i] = verticesX[i] * x / 128;
            verticesY[i] = y * verticesY[i] / 128;
            verticesZ[i] = z * verticesZ[i] / 128;
        }
        return this;
    }

    @Override
    public ModelData recolor(short colorToReplace, short colorToReplaceWith) {
        for (int i = 0; i < faceCount; i++) {
            if (faceColors[i] == colorToReplace) faceColors[i] = colorToReplaceWith;
        }
        return this;
    }

    @Override
    public ModelData retexture(short find, short replace) {
        if (faceTextures != null) {
            for (int i = 0; i < faceCount; i++) {
                if (faceTextures[i] == find) faceTextures[i] = replace;
            }
        }
        return this;
    }

    @Override
    public ModelData shallowCopy() {
        DecodedModelData copy = new DecodedModelData();
        copy.verticesCount = verticesCount;
        copy.verticesX = verticesX;
        copy.verticesY = verticesY;
        copy.verticesZ = verticesZ;
        copy.faceCount = faceCount;
        copy.indices1 = indices1;
        copy.indices2 = indices2;
        copy.indices3 = indices3;
        copy.faceColors = faceColors;
        copy.faceAlphas = faceAlphas;
        copy.faceTextures = faceTextures;
        copy.faceRenderTypes = faceRenderTypes;
        copy.faceRenderPriorities = faceRenderPriorities;
        copy.textureCoords = textureCoords;
        copy.textureTriangleCount = textureTriangleCount;
        copy.texTriangleX = texTriangleX;
        copy.texTriangleY = texTriangleY;
        copy.texTriangleZ = texTriangleZ;
        copy.textureRenderTypes = textureRenderTypes;
        return copy;
    }

    @Override
    public ModelData cloneVertices() {
        verticesX = verticesX.clone();
        verticesY = verticesY.clone();
        verticesZ = verticesZ.clone();
        return this;
    }

    @Override
    public ModelData cloneColors() {
        faceColors = faceColors.clone();
        return this;
    }

    @Override
    public ModelData cloneTextures() {
        if (faceTextures != null) faceTextures = faceTextures.clone();
        return this;
    }

    @Override
    public ModelData cloneTransparencies() {
        return cloneTransparencies(false);
    }

    @Override
    public ModelData cloneTransparencies(boolean force) {
        if (faceAlphas != null) faceAlphas = faceAlphas.clone();
        else if (force) faceAlphas = new byte[faceCount];
        return this;
    }

    @Override
    public Model light(int ambient, int contrast, int x, int y, int z) {
        int[] normalX = new int[verticesCount];
        int[] normalY = new int[verticesCount];
        int[] normalZ = new int[verticesCount];
        int[] normalMagnitude = new int[verticesCount];
        int[][] faceNormals = new int[faceCount][];
        for (int face = 0; face < faceCount; face++) {
            int a = indices1[face];
            int b = indices2[face];
            int c = indices3[face];
            int dx1 = verticesX[b] - verticesX[a];
            int dy1 = verticesY[b] - verticesY[a];
            int dz1 = verticesZ[b] - verticesZ[a];
            int dx2 = verticesX[c] - verticesX[a];
            int dy2 = verticesY[c] - verticesY[a];
            int dz2 = verticesZ[c] - verticesZ[a];
            int nx = dy1 * dz2 - dy2 * dz1;
            int ny = dz1 * dx2 - dz2 * dx1;
            int nz;
            for (nz = dx1 * dy2 - dx2 * dy1; nx > 8192 || ny > 8192 || nz > 8192 || nx < -8192 || ny < -8192
                    || nz < -8192; nz >>= 1) {
                nx >>= 1;
                ny >>= 1;
            }
            int length = (int) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length <= 0) length = 1;
            nx = nx * 256 / length;
            ny = ny * 256 / length;
            nz = nz * 256 / length;
            int renderType = faceRenderTypes == null ? 0 : faceRenderTypes[face];
            if (renderType == 0) {
                for (int vertex : new int[]{a, b, c}) {
                    normalX[vertex] += nx;
                    normalY[vertex] += ny;
                    normalZ[vertex] += nz;
                    normalMagnitude[vertex]++;
                }
            } else if (renderType == 1) {
                faceNormals[face] = new int[]{nx, ny, nz};
            }
        }

        int lightMagnitude = (int) Math.sqrt(z * z + x * x + y * y);
        int intensity = lightMagnitude * contrast >> 8;
        LitModel model = new LitModel(this, normalX, normalY, normalZ);
        for (int face = 0; face < faceCount; face++) {
            int renderType = faceRenderTypes == null ? 0 : faceRenderTypes[face];
            int alpha = faceAlphas == null ? 0 : faceAlphas[face];
            int texture = faceTextures == null ? -1 : faceTextures[face];
            if (alpha == -2) renderType = 3;
            if (alpha == -1) renderType = 2;
            int a = indices1[face];
            int b = indices2[face];
            int c = indices3[face];
            if (texture == -1) {
                int color = faceColors[face] & 0xFFFF;
                if (renderType == 0) {
                    model.faceColors1[face] = lightHsl(color, vertexLight(ambient, intensity, x, y, z,
                            normalX[a], normalY[a], normalZ[a], normalMagnitude[a]));
                    model.faceColors2[face] = lightHsl(color, vertexLight(ambient, intensity, x, y, z,
                            normalX[b], normalY[b], normalZ[b], normalMagnitude[b]));
                    model.faceColors3[face] = lightHsl(color, vertexLight(ambient, intensity, x, y, z,
                            normalX[c], normalY[c], normalZ[c], normalMagnitude[c]));
                } else if (renderType == 1) {
                    int[] normal = faceNormals[face];
                    int light = (y * normal[1] + z * normal[2] + x * normal[0])
                            / Math.max(1, intensity / 2 + intensity) + ambient;
                    model.faceColors1[face] = lightHsl(color, light);
                    model.faceColors3[face] = -1;
                } else if (renderType == 3) {
                    model.faceColors1[face] = 128;
                    model.faceColors3[face] = -1;
                } else {
                    model.faceColors3[face] = -2;
                }
            } else if (renderType == 0) {
                model.faceColors1[face] = clampLight(vertexLight(ambient, intensity, x, y, z,
                        normalX[a], normalY[a], normalZ[a], normalMagnitude[a]));
                model.faceColors2[face] = clampLight(vertexLight(ambient, intensity, x, y, z,
                        normalX[b], normalY[b], normalZ[b], normalMagnitude[b]));
                model.faceColors3[face] = clampLight(vertexLight(ambient, intensity, x, y, z,
                        normalX[c], normalY[c], normalZ[c], normalMagnitude[c]));
            } else if (renderType == 1) {
                int[] normal = faceNormals[face];
                int light = (y * normal[1] + z * normal[2] + x * normal[0])
                            / Math.max(1, intensity / 2 + intensity) + ambient;
                model.faceColors1[face] = clampLight(light);
                model.faceColors3[face] = -1;
            } else {
                model.faceColors3[face] = -2;
            }
        }
        return model;
    }

    private static int vertexLight(int ambient, int intensity, int x, int y, int z,
                                   int nx, int ny, int nz, int magnitude) {
        // The client divides by intensity * magnitude unguarded; a zero light
        // vector or contrast would throw there, so clamp the divisor to 1.
        return (y * ny + z * nz + x * nx) / Math.max(1, intensity * magnitude) + ambient;
    }

    /** Deob {@code ModelData.method5263}: scales HSL lightness, clamped to 2..126. */
    static int lightHsl(int hsl, int light) {
        light = (hsl & 127) * light >> 7;
        if (light < 2) light = 2;
        else if (light > 126) light = 126;
        return (hsl & 0xFF80) + light;
    }

    /** Deob {@code ModelData.method5264}: textured-face light clamped to 2..126. */
    static int clampLight(int light) {
        if (light < 2) return 2;
        return Math.min(light, 126);
    }
}
