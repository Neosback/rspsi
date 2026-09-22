package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Flattens the immutable scene packet into deterministic world-space buffers. */
public final class GpuUploadPlanBuilder {
    public GpuUploadPlan build(GpuScenePacket packet) {
        Objects.requireNonNull(packet, "packet");
        List<GpuSceneVertex> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        List<GpuDrawCommand> commands = new ArrayList<>();
        List<GpuTextureTriangle> textureTriangles = new ArrayList<>();
        LinkedHashSet<SceneOccluder> occluders = new LinkedHashSet<>();

        for (SceneTileSnapshot tile : packet.tiles()) {
            occluders.addAll(tile.occluders());
            if (tile.terrain().isPresent()) {
                TerrainRenderPacket terrain = tile.terrain().orElseThrow();
                for (TerrainRenderFace face : terrain.faces()) {
                    TerrainRenderVertex a = terrain.vertices().get(face.a());
                    TerrainRenderVertex b = terrain.vertices().get(face.b());
                    TerrainRenderVertex c = terrain.vertices().get(face.c());
                    int first = indices.size();
                    int base = vertices.size();
                    vertices.add(terrainVertex(tile.worldAddress(), a, face));
                    vertices.add(terrainVertex(tile.worldAddress(), b, face));
                    vertices.add(terrainVertex(tile.worldAddress(), c, face));
                    indices.add(base);
                    indices.add(base + 1);
                    indices.add(base + 2);
                    appendCommand(commands, tile, SceneLayer.Kind.TERRAIN,
                            terrainPass(face),
                            first, face.textureId(), face.priority(),
                            terrainDepthBias(face), -1, GpuDrawCommand.RenderMode.DEFAULT);
                }
            }
            for (SceneLayer layer : tile.layers()) {
                if (layer.kind() == SceneLayer.Kind.TERRAIN) continue;
                for (int modelIndex : layer.modelIndices()) {
                    ModelRenderPacket model = tile.models().get(modelIndex);
                    model.textureTriangles().stream()
                            .map(mapping -> new GpuTextureTriangle(tile.worldAddress(),
                                    model.objectId(), mapping))
                            .forEach(textureTriangles::add);
                }
                appendModels(tile, layer, layer.modelIndices(), GpuDrawCommand.SubmissionPass.OPAQUE, packet.textures(),
                        vertices, indices, commands);
                appendModels(tile, layer, layer.modelIndices(), GpuDrawCommand.SubmissionPass.ALPHA, packet.textures(),
                        vertices, indices, commands);
            }
        }
        List<SceneOccluder> mergedOccluders = SceneOccluderMerger.merge(List.copyOf(occluders));
        return new GpuUploadPlan(vertices, indices, commands, textureTriangles, packet.textures(),
                mergedOccluders,
                fingerprint(packet.fingerprint(), vertices, indices, commands, textureTriangles,
                        packet.textures(), mergedOccluders));
    }

    private static GpuSceneVertex terrainVertex(WorldTileAddress tile,
                                                 TerrainRenderVertex vertex,
                                                 TerrainRenderFace face) {
        GpuColorEncoding encoding = face.textureId() >= 0
                ? GpuColorEncoding.TEXTURE_LIGHTNESS
                : GpuColorEncoding.PACKED_JAGEX_HSL;
        return new GpuSceneVertex(tile.worldX() * 128.0f + vertex.x(), vertex.height(),
                tile.worldY() * 128.0f + vertex.y(), vertex.u() / 128.0f, vertex.v() / 128.0f,
                vertex.packedHsl(), encoding,
                0, 0, 0, 0, 0, face.textureId(), face.alpha(), face.priority(),
                tile.plane(), tile.worldX(), tile.worldY(), PickerId.terrainSlot());
    }

    private static GpuSceneVertex modelVertex(WorldTileAddress tile, SceneLayer.Kind layer,
                                              ModelRenderPacket model, ModelVertex vertex,
                                              float u, float v, int color, ModelTriangle face) {
        return new GpuSceneVertex(model.anchor().x() * 128.0f + vertex.x(),
                model.placementHeight() + vertex.y(),
                model.anchor().y() * 128.0f + vertex.z(), u, v, color,
                face.textureId() >= 0 ? GpuColorEncoding.TEXTURE_LIGHTNESS
                        : GpuColorEncoding.PACKED_JAGEX_HSL,
                face.renderType(), vertex.normalX(), vertex.normalY(), vertex.normalZ(), vertex.normalMagnitude(),
                face.textureId(), face.alpha(), face.priority(),
                tile.plane(), tile.worldX(), tile.worldY(), PickerId.slotFor(layer));
    }

    /**
     * Selects the submission pass for one terrain face.
     *
     * <p>A textured floor stays in the opaque, depth-writing pass even when its
     * texture is partly transparent. The client's floor scanline
     * ({@code isObject = floor} with {@code floor = true} for tile tops) spends
     * the texture's alpha by mixing the texel toward the tile's own flat colour
     * and then writing the result OPAQUELY; only a zero-alpha texel skips the
     * write. No routing to a blend stream is needed, and keeping the depth write
     * is what stops coplanar floors from losing depth ownership. The shading
     * path does that mixing, so this only has to respect explicit face alpha.</p>
     */
    private static GpuDrawCommand.SubmissionPass terrainPass(TerrainRenderFace face) {
        return face.alpha() == 255
                ? GpuDrawCommand.SubmissionPass.OPAQUE
                : GpuDrawCommand.SubmissionPass.ALPHA;
    }

    /**
     * Overlay and underlay meshes occupy the same tile plane. Give the
     * authored overlay a minimal client-style bias so the native depth buffer
     * does not alternate between coplanar fragments along every tile seam.
     */
    private static int terrainDepthBias(TerrainRenderFace face) {
        return face.material() == 1 ? 1 : 0;
    }

    private static void appendModels(SceneTileSnapshot tile, SceneLayer layer,
                                     List<Integer> modelIndices, GpuDrawCommand.SubmissionPass pass,
                                     java.util.Map<Integer, RenderTextureResource> textures,
                                     List<GpuSceneVertex> vertices, List<Integer> indices,
                                     List<GpuDrawCommand> commands) {
        for (int modelIndex : modelIndices) {
            ModelRenderPacket model = tile.models().get(modelIndex);
            for (ModelTriangle face : model.triangles()) {
                // Model alpha follows the RuneScape convention: zero is
                // opaque and 255 is fully invisible. Terrain alpha is a
                // separate opacity convention and is handled above.
                if (face.renderType() == 2 || face.alpha() == 255) continue;
                // Match RuneLite's GPU uploader: texture cutouts stay in the
                // opaque/depth-writing stream and the fragment shader
                // discards transparent texels. Moving the entire triangle to
                // the alpha pass disables depth writes and makes banners and
                // foliage fight with their own coplanar/backing faces.
                //
                // Only real model alpha selects the blend pass. Render type is
                // a shading selector - the client's Mesh.renderFace maps 0 to
                // shaded, 1 to flat colour and 2/3 to textured - so treating
                // type 3 as 50% translucent drew a large share of roof, wall
                // and prop textures at half opacity.
                boolean transparent = face.alpha() != 0;
                if ((pass == GpuDrawCommand.SubmissionPass.ALPHA) != transparent) continue;
                ModelVertex a = model.vertices().get(face.a());
                ModelVertex b = model.vertices().get(face.b());
                ModelVertex c = model.vertices().get(face.c());
                // ModelData uses colorC = -1 for flat-rendered faces. That is
                // a source-format sentinel, not a third interpolated vertex
                // color. Expand the constant color at the upload boundary so
                // every backend receives ordinary vertex data.
                int flatColor = face.renderType() == 1 || face.renderType() == 3
                        ? face.colorA() : face.colorC();
                int first = indices.size();
                int base = vertices.size();
                vertices.add(modelVertex(tile.worldAddress(), layer.kind(), model, a, face.uA(), face.vA(), face.colorA(), face));
                vertices.add(modelVertex(tile.worldAddress(), layer.kind(), model, b, face.uB(), face.vB(),
                        face.renderType() == 1 || face.renderType() == 3 ? flatColor : face.colorB(), face));
                vertices.add(modelVertex(tile.worldAddress(), layer.kind(), model, c, face.uC(), face.vC(), flatColor, face));
                indices.add(base);
                indices.add(base + 1);
                indices.add(base + 2);
                appendCommand(commands, tile, layer.kind(), pass,
                        first, face.textureId(), submissionPriority(layer.kind(), face.priority()),
                        submissionDepthBias(layer.kind(), face.priority(), face.depthBias()), model.objectId(),
                        model.renderMode(), model.wallDecorationPresentation(),
                        model.gameObjectSceneMetadata(), model.clientRenderableBounds());
            }
        }
    }

    /**
     * Model face priorities (0..11) determine the render order of coplanar
     * faces within and between models.  Wall decorations do not clamp priority
     * to beat their wall; separation from the mounting wall is handled by
     * {@link #submissionDepthBias} in view-space depth, leaving authored face
     * priorities intact so layered models (e.g. banners with cloth, trim,
     * patterns, and crests) render in proper ascending order without self-z-fighting.
     */
    private static int submissionPriority(SceneLayer.Kind layer, int facePriority) {
        return facePriority;
    }

    /**
     * The client draws a tile as underlay/overlay, then the boundary object
     * (wall), then the wall decoration - and its scene renderer has no depth
     * buffer at all, so that paint order alone guarantees a decoration wins
     * against the wall it is mounted on (Scene's per-tile draw sequence).
     *
     * <p>A depth-buffered renderer gets no such guarantee. A decoration
     * authored flush against its wall (shape 4 places it with no
     * displacement whatsoever) is geometrically coplanar, and when its cache
     * face bias is 0 the two surfaces differ only by float rounding - which
     * resolves per pixel, producing the speckled z-fighting the painter's
     * algorithm never had. Give wall decorations the client's smallest
     * nonzero bias step so the ordering the client got implicitly is stated
     * explicitly here, exactly as terrainDepthBias above does for a coplanar
     * overlay over its underlay.</p>
     *
     * <p>That flat step alone only separates a decoration from its wall - it
     * does nothing for a decoration's OWN internal layers (a banner's cloth,
     * trim and crest are separate coplanar faces at ascending priorities,
     * per {@link #submissionPriority}'s javadoc). Those faces all shared the
     * same flat bias, so they z-fought each other exactly as the wall/decor
     * pair did before this method existed. Folding facePriority into the
     * bias gives each priority tier its own depth step, same as the wall
     * case, instead of only stating intent in a comment nothing enforced.</p>
     */
    private static int submissionDepthBias(SceneLayer.Kind layer, int facePriority, int faceBias) {
        if (layer != SceneLayer.Kind.WALL_DECORATION) return faceBias;
        return Math.min(255, Math.max(1, faceBias) + facePriority);
    }

    private static void appendCommand(List<GpuDrawCommand> commands, SceneTileSnapshot tile,
                                      SceneLayer.Kind layer, GpuDrawCommand.SubmissionPass pass,
                                      int firstIndex, int textureId, int priority, int depthBias, int objectId,
                                      GpuDrawCommand.RenderMode renderMode) {
        appendCommand(commands, tile, layer, pass, firstIndex, textureId, priority,
                depthBias, objectId, renderMode, WallDecorationPresentation.none());
    }

    private static void appendCommand(List<GpuDrawCommand> commands, SceneTileSnapshot tile,
                                      SceneLayer.Kind layer, GpuDrawCommand.SubmissionPass pass,
                                      int firstIndex, int textureId, int priority, int depthBias, int objectId,
                                      GpuDrawCommand.RenderMode renderMode,
                                      WallDecorationPresentation wallDecorationPresentation) {
        appendCommand(commands, tile, layer, pass, firstIndex, textureId, priority,
                depthBias, objectId, renderMode, wallDecorationPresentation,
                GameObjectSceneMetadata.none());
    }

    private static void appendCommand(List<GpuDrawCommand> commands, SceneTileSnapshot tile,
                                      SceneLayer.Kind layer, GpuDrawCommand.SubmissionPass pass,
                                      int firstIndex, int textureId, int priority, int depthBias, int objectId,
                                      GpuDrawCommand.RenderMode renderMode,
                                      WallDecorationPresentation wallDecorationPresentation,
                                      GameObjectSceneMetadata gameObjectSceneMetadata) {
        appendCommand(commands, tile, layer, pass, firstIndex, textureId, priority,
                depthBias, objectId, renderMode, wallDecorationPresentation,
                gameObjectSceneMetadata, List.of());
    }

    private static void appendCommand(List<GpuDrawCommand> commands, SceneTileSnapshot tile,
                                      SceneLayer.Kind layer, GpuDrawCommand.SubmissionPass pass,
                                      int firstIndex, int textureId, int priority, int depthBias, int objectId,
                                      GpuDrawCommand.RenderMode renderMode,
                                      WallDecorationPresentation wallDecorationPresentation,
                                      GameObjectSceneMetadata gameObjectSceneMetadata,
                                      List<ClientModelBounds> clientRenderableBounds) {
        if (!commands.isEmpty()) {
            int last = commands.size() - 1;
            GpuDrawCommand previous = commands.get(last);
            // Transparent faces must remain independently sortable. RuneLite's
            // alpha path orders faces by depth; merging them into one draw
            // range would make the native backend blend them in source order.
            if (pass != GpuDrawCommand.SubmissionPass.ALPHA
                    && previous.canMerge(tile.worldAddress(), tile.effectivePlane(),
                    tile.planeCullLevel(), layer, pass, textureId, priority, depthBias,
                    objectId, firstIndex, renderMode, wallDecorationPresentation,
                    gameObjectSceneMetadata, clientRenderableBounds)) {
                commands.set(last, previous.extend(3));
                return;
            }
        }
        commands.add(new GpuDrawCommand(tile.worldAddress(), tile.effectivePlane(),
                tile.planeCullLevel(), layer, pass, firstIndex, 3,
                textureId, priority, depthBias, objectId, renderMode,
                wallDecorationPresentation, gameObjectSceneMetadata, clientRenderableBounds));
    }

    static String fingerprint(String packetFingerprint, List<GpuSceneVertex> vertices,
                              List<Integer> indices, List<GpuDrawCommand> commands,
                              List<GpuTextureTriangle> textureTriangles,
                              java.util.Map<Integer, RenderTextureResource> textures,
                              List<SceneOccluder> occluders) {
        return fingerprint(packetFingerprint, vertices.size(), indices.size(), indices::get,
                commands, textureTriangles, textures, occluders);
    }

    static String fingerprint(String packetFingerprint, LazyGpuFlatGeometry geometry,
                              List<GpuDrawCommand> commands,
                              List<GpuTextureTriangle> textureTriangles,
                              java.util.Map<Integer, RenderTextureResource> textures,
                              List<SceneOccluder> occluders) {
        Objects.requireNonNull(geometry, "geometry");
        return fingerprint(packetFingerprint, geometry.vertexCount(), geometry.indexCount(),
                geometry::directIndex, commands, textureTriangles, textures, occluders);
    }

    private static String fingerprint(String packetFingerprint, int vertexCount, int indexCount,
                                      java.util.function.IntUnaryOperator indexAt,
                                      List<GpuDrawCommand> commands,
                                      List<GpuTextureTriangle> textureTriangles,
                                      java.util.Map<Integer, RenderTextureResource> textures,
                                      List<SceneOccluder> occluders) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(packetFingerprint.getBytes(StandardCharsets.UTF_8));
            ByteBuffer counts = ByteBuffer.allocate(32);
            counts.putInt(vertexCount).putInt(indexCount)
                    .putInt(commands.size()).putInt(textureTriangles.size())
                    .putInt(occluders.size()).putInt(textures.size());
            counts.flip();
            digest.update(counts);

            if (!commands.isEmpty()) {
                for (GpuDrawCommand cmd : commands) {
                    ByteBuffer cmdBuffer = ByteBuffer.allocate(
                            104 + cmd.clientRenderableBounds().size() * 60);
                    cmdBuffer.putInt(cmd.tile().plane())
                            .putInt(cmd.tile().worldX())
                            .putInt(cmd.tile().worldY())
                            .putInt(cmd.scenePlane())
                            .putInt(cmd.planeCullLevel())
                            .putInt(cmd.layer().ordinal())
                            .putInt(cmd.pass().ordinal())
                            .putInt(cmd.firstIndex())
                            .putInt(cmd.indexCount())
                            .putInt(cmd.textureId())
                            .putInt(cmd.priority())
                            .putInt(cmd.depthBias())
                            .putInt(cmd.objectId())
                            .putInt(cmd.renderMode().ordinal())
                            .putInt(cmd.wallDecorationPresentation().part().ordinal())
                            .putInt(cmd.wallDecorationPresentation().offsetX())
                            .putInt(cmd.wallDecorationPresentation().offsetZ())
                            .putInt(cmd.wallDecorationPresentation().orientation())
                            .putInt(cmd.gameObjectSceneMetadata().present() ? 1 : 0)
                            .putInt(cmd.gameObjectSceneMetadata().minTileX())
                            .putInt(cmd.gameObjectSceneMetadata().minTileY())
                            .putInt(cmd.gameObjectSceneMetadata().maxTileX())
                            .putInt(cmd.gameObjectSceneMetadata().maxTileY())
                            .putInt(cmd.gameObjectSceneMetadata().modelOrientation())
                            .putInt(cmd.gameObjectSceneMetadata().orientation())
                            .putInt(cmd.clientRenderableBounds().size());
                    for (ClientModelBounds bounds : cmd.clientRenderableBounds()) {
                        ClientModelBounds.Aabb aabb = bounds.drawAabb();
                        cmdBuffer.putInt(bounds.present() ? 1 : 0)
                                .putInt(bounds.height())
                                .putInt(bounds.bottomY())
                                .putInt(bounds.xzRadius())
                                .putInt(bounds.radius())
                                .putInt(bounds.diameter())
                                .putInt(bounds.singleTile() ? 1 : 0)
                                .putInt(aabb.present() ? 1 : 0)
                                .putInt(aabb.orientation())
                                .putInt(aabb.xMid())
                                .putInt(aabb.yMid())
                                .putInt(aabb.zMid())
                                .putInt(aabb.xMidOffset())
                                .putInt(aabb.yMidOffset())
                                .putInt(aabb.zMidOffset());
                    }
                    cmdBuffer.flip();
                    digest.update(cmdBuffer);
                }
            }

            if (indexCount > 0) {
                int sampleCount = Math.min(indexCount, 256);
                ByteBuffer idxBuffer = ByteBuffer.allocate(sampleCount * Integer.BYTES);
                int stride = Math.max(1, indexCount / sampleCount);
                for (int i = 0; i < indexCount && idxBuffer.hasRemaining(); i += stride) {
                    idxBuffer.putInt(indexAt.applyAsInt(i));
                }
                idxBuffer.flip();
                digest.update(idxBuffer);
            }

            textures.values().stream().sorted(java.util.Comparator.comparingInt(RenderTextureResource::id))
                    .forEach(texture -> {
                        digest.update(Integer.toString(texture.id()).getBytes(StandardCharsets.UTF_8));
                        digest.update(texture.pixelStatus().name().getBytes(StandardCharsets.UTF_8));
                        ByteBuffer dims = ByteBuffer.allocate(12);
                        dims.putInt(texture.width()).putInt(texture.height())
                                .putInt(java.util.Arrays.hashCode(texture.pixels()));
                        dims.flip();
                        digest.update(dims);
                    });

            byte[] hash = digest.digest();
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte item : hash) result.append(String.format("%02x", item & 0xFF));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

}
