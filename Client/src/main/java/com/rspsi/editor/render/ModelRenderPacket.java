package com.rspsi.editor.render;

import com.rspsi.editor.model.ObjectCategory;
import com.rspsi.editor.model.TileCoordinate;

import java.util.List;
import java.util.Objects;

/** Complete renderer-neutral model geometry and material packet. */
public record ModelRenderPacket(
        TileCoordinate anchor,
        int objectId,
        ObjectCategory category,
        List<ModelVertex> vertices,
        List<ModelTriangle> triangles,
        List<TextureTriangle> textureTriangles,
        int animationId,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        boolean supportsAnimation,
        boolean supportsParticles,
        int placementHeight,
        boolean roofRelated,
        GpuDrawCommand.RenderMode renderMode,
        WallDecorationPresentation wallDecorationPresentation,
        GameObjectSceneMetadata gameObjectSceneMetadata,
        List<ClientModelBounds> clientRenderableBounds,
        List<ClientRenderablePlacement> clientRenderablePlacements,
        ModelContourContract contourContract,
        SceneObjectIdentity sceneObjectIdentity
) {
    /** Compatibility constructor before contour metadata was retained. */
    public ModelRenderPacket(TileCoordinate anchor, int objectId, ObjectCategory category,
                             List<ModelVertex> vertices, List<ModelTriangle> triangles,
                             List<TextureTriangle> textureTriangles, int animationId,
                             int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                             boolean supportsAnimation, boolean supportsParticles,
                             int placementHeight, boolean roofRelated,
                             GpuDrawCommand.RenderMode renderMode,
                             WallDecorationPresentation wallDecorationPresentation,
                             GameObjectSceneMetadata gameObjectSceneMetadata,
                             List<ClientModelBounds> clientRenderableBounds,
                             List<ClientRenderablePlacement> clientRenderablePlacements,
                             SceneObjectIdentity sceneObjectIdentity) {
        this(anchor, objectId, category, vertices, triangles, textureTriangles, animationId,
                minX, minY, minZ, maxX, maxY, maxZ, supportsAnimation, supportsParticles,
                placementHeight, roofRelated, renderMode, wallDecorationPresentation,
                gameObjectSceneMetadata, clientRenderableBounds, clientRenderablePlacements,
                ModelContourContract.none(), sceneObjectIdentity);
    }

    /** Compatibility constructor before stable scene-object identity was retained. */
    public ModelRenderPacket(TileCoordinate anchor, int objectId, ObjectCategory category,
                             List<ModelVertex> vertices, List<ModelTriangle> triangles,
                             List<TextureTriangle> textureTriangles, int animationId,
                             int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                             boolean supportsAnimation, boolean supportsParticles,
                             int placementHeight, boolean roofRelated,
                             GpuDrawCommand.RenderMode renderMode,
                             WallDecorationPresentation wallDecorationPresentation,
                             GameObjectSceneMetadata gameObjectSceneMetadata,
                             List<ClientModelBounds> clientRenderableBounds) {
        this(anchor, objectId, category, vertices, triangles, textureTriangles, animationId,
                minX, minY, minZ, maxX, maxY, maxZ, supportsAnimation, supportsParticles,
                placementHeight, roofRelated, renderMode, wallDecorationPresentation,
                gameObjectSceneMetadata, clientRenderableBounds,
                defaultPlacements(clientRenderableBounds), ModelContourContract.none(),
                SceneObjectIdentity.none());
    }

    /** Compatibility constructor before client model bounds metadata was retained. */
    public ModelRenderPacket(TileCoordinate anchor, int objectId, ObjectCategory category,
                             List<ModelVertex> vertices, List<ModelTriangle> triangles,
                             List<TextureTriangle> textureTriangles, int animationId,
                             int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                             boolean supportsAnimation, boolean supportsParticles,
                             int placementHeight, boolean roofRelated,
                             GpuDrawCommand.RenderMode renderMode,
                             WallDecorationPresentation wallDecorationPresentation,
                             GameObjectSceneMetadata gameObjectSceneMetadata) {
        this(anchor, objectId, category, vertices, triangles, textureTriangles, animationId,
                minX, minY, minZ, maxX, maxY, maxZ, supportsAnimation, supportsParticles,
                placementHeight, roofRelated, renderMode, wallDecorationPresentation,
                gameObjectSceneMetadata, List.of());
    }

    /** Compatibility constructor before game-object scene metadata was retained. */
    public ModelRenderPacket(TileCoordinate anchor, int objectId, ObjectCategory category,
                             List<ModelVertex> vertices, List<ModelTriangle> triangles,
                             List<TextureTriangle> textureTriangles, int animationId,
                             int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                             boolean supportsAnimation, boolean supportsParticles,
                             int placementHeight, boolean roofRelated,
                             GpuDrawCommand.RenderMode renderMode,
                             WallDecorationPresentation wallDecorationPresentation) {
        this(anchor, objectId, category, vertices, triangles, textureTriangles, animationId,
                minX, minY, minZ, maxX, maxY, maxZ, supportsAnimation, supportsParticles,
                placementHeight, roofRelated, renderMode, wallDecorationPresentation,
                GameObjectSceneMetadata.none());
    }

    /** Compatibility constructor before wall-decoration renderables were retained separately. */
    public ModelRenderPacket(TileCoordinate anchor, int objectId, ObjectCategory category,
                             List<ModelVertex> vertices, List<ModelTriangle> triangles,
                             List<TextureTriangle> textureTriangles, int animationId,
                             int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                             boolean supportsAnimation, boolean supportsParticles,
                             int placementHeight, boolean roofRelated,
                             GpuDrawCommand.RenderMode renderMode) {
        this(anchor, objectId, category, vertices, triangles, textureTriangles, animationId,
                minX, minY, minZ, maxX, maxY, maxZ, supportsAnimation, supportsParticles,
                placementHeight, roofRelated, renderMode, WallDecorationPresentation.none());
    }

    /** Compatibility constructor before model placement elevation was explicit. */
    public ModelRenderPacket(TileCoordinate anchor, int objectId, ObjectCategory category,
                             List<ModelVertex> vertices, List<ModelTriangle> triangles,
                             List<TextureTriangle> textureTriangles, int animationId,
                             int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                             boolean supportsAnimation, boolean supportsParticles) {
        this(anchor, objectId, category, vertices, triangles, textureTriangles, animationId,
                minX, minY, minZ, maxX, maxY, maxZ, supportsAnimation, supportsParticles,
                0, false, GpuDrawCommand.RenderMode.DEFAULT, WallDecorationPresentation.none());
    }

    /** Compatibility constructor before explicit placement and roof metadata. */
    public ModelRenderPacket(TileCoordinate anchor, int objectId, ObjectCategory category,
                             List<ModelVertex> vertices, List<ModelTriangle> triangles,
                             List<TextureTriangle> textureTriangles, int animationId,
                             int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                             boolean supportsAnimation, boolean supportsParticles,
                             int placementHeight, boolean roofRelated) {
        this(anchor, objectId, category, vertices, triangles, textureTriangles, animationId,
                minX, minY, minZ, maxX, maxY, maxZ, supportsAnimation, supportsParticles,
                placementHeight, roofRelated, GpuDrawCommand.RenderMode.DEFAULT,
                WallDecorationPresentation.none());
    }

    public ModelRenderPacket {
        anchor = Objects.requireNonNull(anchor, "anchor");
        category = Objects.requireNonNull(category, "category");
        vertices = List.copyOf(Objects.requireNonNull(vertices, "vertices"));
        triangles = List.copyOf(Objects.requireNonNull(triangles, "triangles"));
        textureTriangles = List.copyOf(Objects.requireNonNull(textureTriangles, "textureTriangles"));
        renderMode = Objects.requireNonNull(renderMode, "renderMode");
        wallDecorationPresentation = Objects.requireNonNull(
                wallDecorationPresentation, "wallDecorationPresentation");
        gameObjectSceneMetadata = Objects.requireNonNull(
                gameObjectSceneMetadata, "gameObjectSceneMetadata");
        clientRenderableBounds = List.copyOf(Objects.requireNonNull(
                clientRenderableBounds, "clientRenderableBounds"));
        clientRenderablePlacements = List.copyOf(Objects.requireNonNull(
                clientRenderablePlacements, "clientRenderablePlacements"));
        contourContract = Objects.requireNonNull(contourContract, "contourContract");
        sceneObjectIdentity = Objects.requireNonNull(sceneObjectIdentity, "sceneObjectIdentity");
        if (clientRenderableBounds.stream().anyMatch(value -> value == null || !value.present())) {
            throw new IllegalArgumentException("Client renderable bounds must be present");
        }
        if (clientRenderablePlacements.size() != clientRenderableBounds.size()
                || clientRenderablePlacements.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Client renderable placements must match bounds");
        }
        contourContract.validateVertexCount(vertices.size());
        if (objectId < 0 || animationId < -1 || minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Invalid model packet identity or bounds");
        }
        for (ModelTriangle triangle : triangles) {
            if (triangle.a() >= vertices.size() || triangle.b() >= vertices.size()
                    || triangle.c() >= vertices.size()) {
                throw new IllegalArgumentException("Model triangle references a missing vertex");
            }
        }
        for (TextureTriangle triangle : textureTriangles) {
            if (triangle.a() >= vertices.size() || triangle.b() >= vertices.size()
                    || triangle.c() >= vertices.size()) {
                throw new IllegalArgumentException("Texture triangle references a missing vertex");
            }
        }
    }

    /** Returns the same derived geometry projected onto a world tile anchor. */
    public ModelRenderPacket withAnchor(TileCoordinate newAnchor) {
        int deltaX = newAnchor.x() - anchor.x();
        int deltaY = newAnchor.y() - anchor.y();
        return new ModelRenderPacket(newAnchor, objectId, category, vertices, triangles,
                textureTriangles, animationId, minX, minY, minZ, maxX, maxY, maxZ,
                supportsAnimation, supportsParticles, placementHeight, roofRelated, renderMode,
                wallDecorationPresentation, gameObjectSceneMetadata.translated(deltaX, deltaY),
                clientRenderableBounds, clientRenderablePlacements, contourContract, sceneObjectIdentity.withAnchor(newAnchor));
    }

    /** Returns this packet with an explicit RuneLite-compatible render mode. */
    public ModelRenderPacket withRenderMode(GpuDrawCommand.RenderMode newRenderMode) {
        return new ModelRenderPacket(anchor, objectId, category, vertices, triangles,
                textureTriangles, animationId, minX, minY, minZ, maxX, maxY, maxZ,
                supportsAnimation, supportsParticles, placementHeight, roofRelated,
                newRenderMode, wallDecorationPresentation, gameObjectSceneMetadata,
                clientRenderableBounds, clientRenderablePlacements, contourContract, sceneObjectIdentity);
    }

    /** Returns this packet with explicit wall-decoration renderable identity. */
    public ModelRenderPacket withWallDecorationPresentation(
            WallDecorationPresentation presentation) {
        return new ModelRenderPacket(anchor, objectId, category, vertices, triangles,
                textureTriangles, animationId, minX, minY, minZ, maxX, maxY, maxZ,
                supportsAnimation, supportsParticles, placementHeight, roofRelated,
                renderMode, Objects.requireNonNull(presentation, "presentation"),
                gameObjectSceneMetadata, clientRenderableBounds, clientRenderablePlacements, contourContract, sceneObjectIdentity);
    }

    /** Returns this packet with explicit client game-object scene metadata. */
    public ModelRenderPacket withGameObjectSceneMetadata(GameObjectSceneMetadata metadata) {
        return new ModelRenderPacket(anchor, objectId, category, vertices, triangles,
                textureTriangles, animationId, minX, minY, minZ, maxX, maxY, maxZ,
                supportsAnimation, supportsParticles, placementHeight, roofRelated,
                renderMode, wallDecorationPresentation, Objects.requireNonNull(metadata, "metadata"),
                clientRenderableBounds, clientRenderablePlacements, contourContract, sceneObjectIdentity);
    }

    /** Returns this packet with one client-local bounds entry per client renderable. */
    public ModelRenderPacket withClientRenderableBounds(List<ClientModelBounds> bounds) {
        List<ClientModelBounds> copied = List.copyOf(Objects.requireNonNull(bounds, "bounds"));
        List<ClientRenderablePlacement> placements = copied.size() == clientRenderablePlacements.size()
                ? clientRenderablePlacements : defaultPlacements(copied);
        return new ModelRenderPacket(anchor, objectId, category, vertices, triangles,
                textureTriangles, animationId, minX, minY, minZ, maxX, maxY, maxZ,
                supportsAnimation, supportsParticles, placementHeight, roofRelated,
                renderMode, wallDecorationPresentation, gameObjectSceneMetadata,
                copied, placements, contourContract, sceneObjectIdentity);
    }

    /** Returns this packet with per-renderable scene placement offsets. */
    public ModelRenderPacket withClientRenderablePlacements(List<ClientRenderablePlacement> placements) {
        return new ModelRenderPacket(anchor, objectId, category, vertices, triangles,
                textureTriangles, animationId, minX, minY, minZ, maxX, maxY, maxZ,
                supportsAnimation, supportsParticles, placementHeight, roofRelated,
                renderMode, wallDecorationPresentation, gameObjectSceneMetadata,
                clientRenderableBounds, List.copyOf(Objects.requireNonNull(placements, "placements")),
                contourContract, sceneObjectIdentity);
    }

    /** Convenience for the common one-renderable packet. */
    public ModelRenderPacket withClientModelBounds(ClientModelBounds bounds) {
        return withClientRenderableBounds(List.of(Objects.requireNonNull(bounds, "bounds")));
    }

    /** Returns this packet with explicit contour-ground runtime metadata. */
    public ModelRenderPacket withContourContract(ModelContourContract contract) {
        return new ModelRenderPacket(anchor, objectId, category, vertices, triangles,
                textureTriangles, animationId, minX, minY, minZ, maxX, maxY, maxZ,
                supportsAnimation, supportsParticles, placementHeight, roofRelated,
                renderMode, wallDecorationPresentation, gameObjectSceneMetadata,
                clientRenderableBounds, clientRenderablePlacements,
                Objects.requireNonNull(contract, "contract"), sceneObjectIdentity);
    }

    /** Returns this packet with the semantic identity of its placed object. */
    public ModelRenderPacket withSceneObjectIdentity(SceneObjectIdentity identity) {
        return new ModelRenderPacket(anchor, objectId, category, vertices, triangles,
                textureTriangles, animationId, minX, minY, minZ, maxX, maxY, maxZ,
                supportsAnimation, supportsParticles, placementHeight, roofRelated,
                renderMode, wallDecorationPresentation, gameObjectSceneMetadata,
                clientRenderableBounds, clientRenderablePlacements, contourContract,
                Objects.requireNonNull(identity, "identity"));
    }

    private static List<ClientRenderablePlacement> defaultPlacements(
            List<ClientModelBounds> bounds) {
        Objects.requireNonNull(bounds, "bounds");
        return java.util.stream.IntStream.range(0, bounds.size())
                .mapToObj(ignored -> ClientRenderablePlacement.none())
                .toList();
    }

    /**
     * Reconstructs RuneLite's HILLSKEW unskewed model vertex stream when the
     * client contour pass actually produced a warped copy.
     */
    public java.util.Optional<List<ModelVertex>> unskewedVertices() {
        if (!contourContract.hasUnskewedModel()) return java.util.Optional.empty();
        java.util.ArrayList<ModelVertex> result = new java.util.ArrayList<>(vertices.size());
        for (int index = 0; index < vertices.size(); index++) {
            ModelVertex vertex = vertices.get(index);
            result.add(new ModelVertex(vertex.x(), contourContract.unskewedY(index), vertex.z(),
                    vertex.normalX(), vertex.normalY(), vertex.normalZ(),
                    vertex.normalMagnitude(), vertex.u(), vertex.v()));
        }
        return java.util.Optional.of(List.copyOf(result));
    }

    /** Triangle indices suitable for the opaque submission pass. */
    public List<Integer> opaqueTriangleIndices() {
        return triangleIndices(false);
    }

    /** Triangle indices that require alpha/visibility-aware submission. */
    public List<Integer> transparentTriangleIndices() {
        return triangleIndices(true);
    }

    private List<Integer> triangleIndices(boolean transparent) {
        java.util.ArrayList<Integer> indices = new java.util.ArrayList<>();
        for (int index = 0; index < triangles.size(); index++) {
            ModelTriangle face = triangles.get(index);
            // Render type 2 is an intentionally hidden face, not an opaque
            // submission.
            if (face.renderType() == 2 || face.alpha() == 255) continue;
            // Render type is a shading selector, never an opacity, so only
            // real model alpha decides the blend stream.
            boolean faceTransparent = face.alpha() != 0;
            if (faceTransparent == transparent) indices.add(index);
        }
        return List.copyOf(indices);
    }
}
