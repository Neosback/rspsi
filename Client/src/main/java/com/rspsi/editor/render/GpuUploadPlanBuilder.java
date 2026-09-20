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
                    appendCommand(commands, tile.worldAddress(), SceneLayer.Kind.TERRAIN,
                            face.alpha() == 255 ? GpuDrawCommand.SubmissionPass.OPAQUE
                                    : GpuDrawCommand.SubmissionPass.ALPHA,
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
                boolean transparent = face.alpha() != 0 || face.renderType() == 3;
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
                appendCommand(commands, tile.worldAddress(), layer.kind(), pass,
                        first, face.textureId(), submissionPriority(layer.kind(), face.priority()),
                        submissionDepthBias(layer.kind(), face.depthBias()), model.objectId(),
                        model.renderMode());
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
     */
    private static int submissionDepthBias(SceneLayer.Kind layer, int faceBias) {
        return layer == SceneLayer.Kind.WALL_DECORATION ? Math.max(1, faceBias) : faceBias;
    }

    private static void appendCommand(List<GpuDrawCommand> commands, WorldTileAddress tile,
                                      SceneLayer.Kind layer, GpuDrawCommand.SubmissionPass pass,
                                      int firstIndex, int textureId, int priority, int depthBias, int objectId,
                                      GpuDrawCommand.RenderMode renderMode) {
        if (!commands.isEmpty()) {
            int last = commands.size() - 1;
            GpuDrawCommand previous = commands.get(last);
            // Transparent faces must remain independently sortable. RuneLite's
            // alpha path orders faces by depth; merging them into one draw
            // range would make the native backend blend them in source order.
            if (pass != GpuDrawCommand.SubmissionPass.ALPHA
                    && previous.canMerge(tile, layer, pass, textureId, priority, depthBias,
                    objectId, firstIndex, renderMode)) {
                commands.set(last, previous.extend(3));
                return;
            }
        }
        commands.add(new GpuDrawCommand(tile, layer, pass, firstIndex, 3,
                textureId, priority, depthBias, objectId, renderMode));
    }

    private static String fingerprint(String packetFingerprint, List<GpuSceneVertex> vertices,
                                      List<Integer> indices, List<GpuDrawCommand> commands,
                                      List<GpuTextureTriangle> textureTriangles,
                                      java.util.Map<Integer, RenderTextureResource> textures,
                                      List<SceneOccluder> occluders) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(packetFingerprint.getBytes(StandardCharsets.UTF_8));
            ByteBuffer counts = ByteBuffer.allocate(32);
            counts.putInt(vertices.size()).putInt(indices.size())
                    .putInt(commands.size()).putInt(textureTriangles.size())
                    .putInt(occluders.size()).putInt(textures.size());
            counts.flip();
            digest.update(counts);

            if (!commands.isEmpty()) {
                ByteBuffer cmdBuffer = ByteBuffer.allocate(commands.size() * 36);
                for (GpuDrawCommand cmd : commands) {
                    cmdBuffer.putInt(cmd.tile().plane())
                            .putInt(cmd.tile().worldX())
                            .putInt(cmd.tile().worldY())
                            .putInt(cmd.layer().ordinal())
                            .putInt(cmd.pass().ordinal())
                            .putInt(cmd.firstIndex())
                            .putInt(cmd.indexCount())
                            .putInt(cmd.textureId())
                            .putInt(cmd.renderMode().ordinal());
                }
                cmdBuffer.flip();
                digest.update(cmdBuffer);
            }

            if (!indices.isEmpty()) {
                int sampleCount = Math.min(indices.size(), 256);
                ByteBuffer idxBuffer = ByteBuffer.allocate(sampleCount * Integer.BYTES);
                int stride = Math.max(1, indices.size() / sampleCount);
                for (int i = 0; i < indices.size() && idxBuffer.hasRemaining(); i += stride) {
                    idxBuffer.putInt(indices.get(i));
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
