package com.rspsi.cache.definition;

/**
 * Backend-neutral model geometry for previews and future scene renderers.
 *
 * <p>Vertices are packed as {@code x,y,z} triples and triangle indices as
 * {@code a,b,c} triples. Optional per-face arrays are empty when the cache
 * does not provide that channel. The view owns all arrays so a decoder or
 * renderer cannot mutate cached model data through this boundary.</p>
 */
public record ModelGeometryView(
        int id,
        int[] vertexPositions,
        int[] triangleIndices,
        short[] triangleColors,
        int[] triangleAlphas,
        int[] triangleTextures,
        int[] triangleRenderTypes,
        int[] triangleRenderPriorities,
        int[] textureCoordinates,
        int[] textureTriangleIndices,
        int[] textureRenderTypes,
        int[] textureScaleX,
        int[] textureScaleY,
        int[] textureScaleZ,
        int[] textureRotations,
        int[] textureDirections,
        int[] textureSpeeds,
        int[] textureTranslationsU,
        int[] textureTranslationsV,
        int[] vertexSkins,
        int[] vertexNormals,
        int[] faceNormals,
        int[] triangleSkins,
        int[] triangleDepthBias
) {
    /** Compatibility constructor for the original reduced geometry view. */
    public ModelGeometryView(int id, int[] vertexPositions, int[] triangleIndices,
                             short[] triangleColors, int[] triangleAlphas,
                             int[] triangleTextures) {
        this(id, vertexPositions, triangleIndices, triangleColors, triangleAlphas,
                triangleTextures, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
    }

    /** Compatibility constructor for the pre-texture-metadata neutral view. */
    public ModelGeometryView(int id, int[] vertexPositions, int[] triangleIndices,
                             short[] triangleColors, int[] triangleAlphas,
                             int[] triangleTextures, int[] triangleRenderTypes,
                             int[] triangleRenderPriorities, int[] textureCoordinates,
                             int[] textureTriangleIndices, int[] vertexNormals,
                             int[] faceNormals) {
        this(id, vertexPositions, triangleIndices, triangleColors, triangleAlphas,
                triangleTextures, triangleRenderTypes, triangleRenderPriorities,
                textureCoordinates, textureTriangleIndices, null, null, null, null,
                null, null, null, null, null, null, vertexNormals, faceNormals, null, null);
    }

    /** Compatibility constructor for texture metadata before vertex skin groups were exposed. */
    public ModelGeometryView(int id, int[] vertexPositions, int[] triangleIndices,
                             short[] triangleColors, int[] triangleAlphas, int[] triangleTextures,
                             int[] triangleRenderTypes, int[] triangleRenderPriorities,
                             int[] textureCoordinates, int[] textureTriangleIndices,
                             int[] textureRenderTypes, int[] textureScaleX, int[] textureScaleY,
                             int[] textureScaleZ, int[] textureRotations, int[] textureDirections,
                             int[] textureSpeeds, int[] textureTranslationsU,
                             int[] textureTranslationsV, int[] vertexNormals, int[] faceNormals) {
        this(id, vertexPositions, triangleIndices, triangleColors, triangleAlphas, triangleTextures,
                triangleRenderTypes, triangleRenderPriorities, textureCoordinates, textureTriangleIndices,
                textureRenderTypes, textureScaleX, textureScaleY, textureScaleZ, textureRotations,
                textureDirections, textureSpeeds, textureTranslationsU, textureTranslationsV,
                null, vertexNormals, faceNormals, null, null);
    }

    public ModelGeometryView {
        if (id < 0 || vertexPositions == null || triangleIndices == null) {
            throw new IllegalArgumentException("Model geometry identity and arrays are required");
        }
        if (vertexPositions.length % 3 != 0 || triangleIndices.length % 3 != 0) {
            throw new IllegalArgumentException("Model geometry arrays must contain complete triples");
        }
        int vertexCount = vertexPositions.length / 3;
        for (int index : triangleIndices) {
            if (index < 0 || index >= vertexCount) {
                throw new IllegalArgumentException("Model triangle index is outside the vertex array");
            }
        }
        int triangleCount = triangleIndices.length / 3;
        triangleColors = optionalFaceArray(triangleColors, triangleCount, "colors");
        triangleAlphas = optionalFaceArray(triangleAlphas, triangleCount, "alphas");
        triangleTextures = optionalFaceArray(triangleTextures, triangleCount, "textures");
        triangleRenderTypes = optionalFaceArray(triangleRenderTypes, triangleCount, "render types");
        triangleRenderPriorities = optionalFaceArray(triangleRenderPriorities, triangleCount, "render priorities");
        textureCoordinates = optionalFaceArray(textureCoordinates, triangleCount, "texture coordinates");
        textureTriangleIndices = optionalTriples(textureTriangleIndices, "texture triangles");
        int textureTriangleCount = textureTriangleIndices.length / 3;
        textureRenderTypes = optionalTextureArray(textureRenderTypes, textureTriangleCount, "texture render types");
        textureScaleX = optionalTextureArray(textureScaleX, textureTriangleCount, "texture scale X");
        textureScaleY = optionalTextureArray(textureScaleY, textureTriangleCount, "texture scale Y");
        textureScaleZ = optionalTextureArray(textureScaleZ, textureTriangleCount, "texture scale Z");
        textureRotations = optionalTextureArray(textureRotations, textureTriangleCount, "texture rotations");
        textureDirections = optionalTextureArray(textureDirections, textureTriangleCount, "texture directions");
        textureSpeeds = optionalTextureArray(textureSpeeds, textureTriangleCount, "texture speeds");
        textureTranslationsU = optionalTextureArray(textureTranslationsU, textureTriangleCount,
                "texture translations U");
        textureTranslationsV = optionalTextureArray(textureTranslationsV, textureTriangleCount,
                "texture translations V");
        vertexSkins = optionalVertexArray(vertexSkins, vertexCount, "vertex skins");
        vertexNormals = optionalNormalArray(vertexNormals, "vertex normals");
        faceNormals = optionalTriples(faceNormals, "face normals");
        triangleSkins = optionalFaceArray(triangleSkins, triangleCount, "triangle skins");
        triangleDepthBias = optionalFaceArray(triangleDepthBias, triangleCount, "triangle depth bias");
        vertexPositions = vertexPositions.clone();
        triangleIndices = triangleIndices.clone();
    }

    /** Returns this geometry with a new vertex position array and all metadata preserved. */
    public ModelGeometryView withVertexPositions(int[] positions) {
        return new ModelGeometryView(id, positions, triangleIndices, triangleColors,
                triangleAlphas, triangleTextures, triangleRenderTypes,
                triangleRenderPriorities, textureCoordinates, textureTriangleIndices,
                textureRenderTypes, textureScaleX, textureScaleY, textureScaleZ,
                textureRotations, textureDirections, textureSpeeds,
                textureTranslationsU, textureTranslationsV, vertexSkins,
                vertexNormals, faceNormals, triangleSkins, triangleDepthBias);
    }

    /** Returns this geometry with animated positions and face alpha data. */
    public ModelGeometryView withAnimatedData(int[] positions, int[] alphas) {
        return new ModelGeometryView(id, positions, triangleIndices, triangleColors,
                alphas, triangleTextures, triangleRenderTypes, triangleRenderPriorities,
                textureCoordinates, textureTriangleIndices, textureRenderTypes,
                textureScaleX, textureScaleY, textureScaleZ, textureRotations,
                textureDirections, textureSpeeds, textureTranslationsU,
                textureTranslationsV, vertexSkins, vertexNormals, faceNormals, triangleSkins,
                triangleDepthBias);
    }

    /** Adds optional vertex skin groups when an animation-capable decoder supplies them. */
    public ModelGeometryView withVertexSkins(int[] skins) {
        return new ModelGeometryView(id, vertexPositions, triangleIndices, triangleColors,
                triangleAlphas, triangleTextures, triangleRenderTypes, triangleRenderPriorities,
                textureCoordinates, textureTriangleIndices, textureRenderTypes,
                textureScaleX, textureScaleY, textureScaleZ, textureRotations,
                textureDirections, textureSpeeds, textureTranslationsU,
                textureTranslationsV, skins, vertexNormals, faceNormals, triangleSkins,
                triangleDepthBias);
    }

    /** Adds optional triangle skin groups used by legacy type-5 alpha animation. */
    public ModelGeometryView withTriangleSkins(int[] skins) {
        return new ModelGeometryView(id, vertexPositions, triangleIndices, triangleColors,
                triangleAlphas, triangleTextures, triangleRenderTypes, triangleRenderPriorities,
                textureCoordinates, textureTriangleIndices, textureRenderTypes,
                textureScaleX, textureScaleY, textureScaleZ, textureRotations,
                textureDirections, textureSpeeds, textureTranslationsU,
                textureTranslationsV, vertexSkins, vertexNormals, faceNormals, skins,
                triangleDepthBias);
    }

    /** Adds raw RuneScape per-face depth bias when the cache exposes it. */
    public ModelGeometryView withTriangleDepthBias(int[] bias) {
        return new ModelGeometryView(id, vertexPositions, triangleIndices, triangleColors,
                triangleAlphas, triangleTextures, triangleRenderTypes, triangleRenderPriorities,
                textureCoordinates, textureTriangleIndices, textureRenderTypes, textureScaleX,
                textureScaleY, textureScaleZ, textureRotations, textureDirections, textureSpeeds,
                textureTranslationsU, textureTranslationsV, vertexSkins, vertexNormals,
                faceNormals, triangleSkins, bias);
    }

    private static short[] optionalFaceArray(short[] values, int triangleCount, String name) {
        if (values == null || values.length == 0) return new short[0];
        if (values.length != triangleCount) {
            throw new IllegalArgumentException("Model " + name + " count does not match triangles");
        }
        return values.clone();
    }

    private static int[] optionalFaceArray(int[] values, int triangleCount, String name) {
        if (values == null || values.length == 0) return new int[0];
        if (values.length != triangleCount) {
            throw new IllegalArgumentException("Model " + name + " count does not match triangles");
        }
        return values.clone();
    }

    private static int[] optionalTriples(int[] values, String name) {
        if (values == null || values.length == 0) return new int[0];
        if (values.length % 3 != 0) {
            throw new IllegalArgumentException("Model " + name + " must contain triples");
        }
        return values.clone();
    }

    private static int[] optionalTextureArray(int[] values, int textureTriangleCount, String name) {
        if (values == null || values.length == 0) return new int[0];
        if (values.length != textureTriangleCount) {
            throw new IllegalArgumentException("Model " + name + " count does not match texture triangles");
        }
        return values.clone();
    }

    private static int[] optionalNormalArray(int[] values, String name) {
        if (values == null || values.length == 0) return new int[0];
        if (values.length % 4 != 0) {
            throw new IllegalArgumentException("Model " + name + " must contain x/y/z/magnitude groups");
        }
        return values.clone();
    }

    private static int[] optionalVertexArray(int[] values, int vertexCount, String name) {
        if (values == null || values.length == 0) return new int[0];
        if (values.length != vertexCount) {
            throw new IllegalArgumentException("Model " + name + " count does not match vertices");
        }
        return values.clone();
    }

    public int vertexCount() {
        return vertexPositions.length / 3;
    }

    public int triangleCount() {
        return triangleIndices.length / 3;
    }

    public int[] vertexSkins() {
        return vertexSkins.clone();
    }

    @Override
    public int[] vertexPositions() {
        return vertexPositions.clone();
    }

    @Override
    public int[] triangleIndices() {
        return triangleIndices.clone();
    }

    @Override
    public short[] triangleColors() {
        return triangleColors.clone();
    }

    @Override
    public int[] triangleAlphas() {
        return triangleAlphas.clone();
    }

    @Override
    public int[] triangleTextures() {
        return triangleTextures.clone();
    }

    @Override
    public int[] triangleRenderTypes() {
        return triangleRenderTypes.clone();
    }

    @Override
    public int[] triangleRenderPriorities() {
        return triangleRenderPriorities.clone();
    }

    @Override
    public int[] textureCoordinates() {
        return textureCoordinates.clone();
    }

    @Override
    public int[] textureTriangleIndices() {
        return textureTriangleIndices.clone();
    }

    @Override
    public int[] textureRenderTypes() {
        return textureRenderTypes.clone();
    }

    @Override
    public int[] textureScaleX() {
        return textureScaleX.clone();
    }

    @Override
    public int[] textureScaleY() {
        return textureScaleY.clone();
    }

    @Override
    public int[] textureScaleZ() {
        return textureScaleZ.clone();
    }

    @Override
    public int[] textureRotations() {
        return textureRotations.clone();
    }

    @Override
    public int[] textureDirections() {
        return textureDirections.clone();
    }

    @Override
    public int[] textureSpeeds() {
        return textureSpeeds.clone();
    }

    @Override
    public int[] textureTranslationsU() {
        return textureTranslationsU.clone();
    }

    @Override
    public int[] textureTranslationsV() {
        return textureTranslationsV.clone();
    }

    @Override
    public int[] vertexNormals() {
        return vertexNormals.clone();
    }

    @Override
    public int[] faceNormals() {
        return faceNormals.clone();
    }

    @Override
    public int[] triangleDepthBias() {
        return triangleDepthBias.clone();
    }
}
