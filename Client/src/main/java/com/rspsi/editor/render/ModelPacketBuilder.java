package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ModelGeometryView;
import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.AnimationFrameView;
import com.rspsi.cache.definition.CachedSkeletalAnimationView;
import com.rspsi.cache.definition.ModelSkeletalSkinView;
import com.rspsi.cache.definition.SkeletonDefinitionView;
import com.rspsi.cache.definition.SequenceDefinitionView;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.osrs.rules.loc.WallRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds renderer-neutral model packets using the OSRS location-model order.
 *
 * <p>This deliberately stops at the packet boundary. It does not cache or
 * mutate backend model objects, and it never asks a cache implementation to
 * render. The order mirrors the client/TSPS path: select model parts, mirror,
 * rotate, recolor/retexture, resize, translate, light, then contour (the
 * client bakes lighting from pre-contour normals and warps the lit model's
 * vertex heights afterwards).</p>
 */
public final class ModelPacketBuilder {
    private final DefinitionProvider definitions;
    private final LightingProfile lighting;

    public ModelPacketBuilder(DefinitionProvider definitions) {
        this(definitions, LightingProfile.osrs());
    }

    public ModelPacketBuilder(DefinitionProvider definitions, LightingProfile lighting) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.lighting = Objects.requireNonNull(lighting, "lighting");
    }

    /** Builds every available static model packet in document order. */
    public List<ModelRenderPacket> build(WorldDocument document) {
        return build(document, 0);
    }

    /** Builds animated model packets at an explicit client-cycle position. */
    public List<ModelRenderPacket> build(WorldDocument document, int clientCycle) {
        Objects.requireNonNull(document, "document");
        if (clientCycle < 0) throw new IllegalArgumentException("Client cycle cannot be negative");
        List<ModelRenderPacket> packets = new ArrayList<>();
        for (int plane = 0; plane < document.planes(); plane++) {
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    java.util.Map<WorldObject, Integer> occurrences = new java.util.HashMap<>();
                    for (WorldObject object : document.tile(plane, x, y).objects()) {
                        int occurrence = occurrences.getOrDefault(object, 0);
                        occurrences.put(object, occurrence + 1);
                        packets.addAll(buildScenePackets(object, document, clientCycle, occurrence));
                    }
                }
            }
        }
        return List.copyOf(mergeNormals(packets));
    }

    /** Builds one packet when its definition and at least one model are available. */
    public Optional<ModelRenderPacket> build(WorldObject object, WorldDocument document) {
        return build(object, document, 0);
    }

    /** Builds one model packet using the sequence frame active at clientCycle. */
    public Optional<ModelRenderPacket> build(WorldObject object, WorldDocument document,
                                             int clientCycle) {
        ResolvedModelBuild resolved = resolveBuild(object, document, clientCycle);
        if (resolved == null) return Optional.empty();
        return buildResolvedPacket(object, document, resolved,
                variantsFor(object, resolved.decorDisplacement()),
                WallDecorationPresentation.none());
    }

    /**
     * Scene rendering keeps shape-8 wall decoration renderables distinct.
     * The normal client traversal can submit the two sides in different tile
     * phases, with camera position deciding which side is submitted first.
     * The compatibility single-object API above remains flattened, while the
     * scene path preserves both renderables and their camera-order metadata.
     */
    private List<ModelRenderPacket> buildScenePackets(WorldObject object, WorldDocument document,
                                                       int clientCycle, int occurrence) {
        ResolvedModelBuild resolved = resolveBuild(object, document, clientCycle);
        if (resolved == null) return List.of();
        List<WallRules.LocModelVariant> variants = variantsFor(object, resolved.decorDisplacement());
        if (object.type() == 8 && variants.size() == 2) {
            WallRules.LocModelVariant primaryVariant = variants.get(0);
            WallRules.LocModelVariant secondaryVariant = variants.get(1);
            List<ModelRenderPacket> result = new ArrayList<>(2);
            buildResolvedPacket(object, document, resolved, List.of(primaryVariant),
                    WallDecorationPresentation.primary(
                            primaryVariant.decorX(), primaryVariant.decorZ(), object.rotation()),
                    occurrence)
                    .ifPresent(result::add);
            buildResolvedPacket(object, document, resolved, List.of(secondaryVariant),
                    WallDecorationPresentation.secondary(object.rotation()), occurrence)
                    .ifPresent(result::add);
            return List.copyOf(result);
        }

        return buildResolvedPacket(object, document, resolved, variants,
                WallDecorationPresentation.none(), occurrence)
                .map(List::of).orElseGet(List::of);
    }

    private ResolvedModelBuild resolveBuild(WorldObject object, WorldDocument document,
                                            int clientCycle) {
        Objects.requireNonNull(object, "object");
        Objects.requireNonNull(document, "document");
        if (clientCycle < 0) throw new IllegalArgumentException("Client cycle cannot be negative");
        Optional<ObjectDefinitionView> definition = definitions.object(object.id());
        if (definition.isEmpty()) return null;
        ObjectDefinitionView placementDefinition = definition.orElseThrow();
        ObjectDefinitionView objectDefinition = resolveDisplayDefinition(placementDefinition);
        ObjectAppearanceView appearance = definitions.objectAppearance(object.id())
                .orElseGet(ObjectAppearanceView::empty);
        ResolvedAnimation animation = resolveAnimation(appearance.animationId(), clientCycle);
        int decorDisplacement = wallDecorationDisplacement(object, appearance, document);
        // Scene occupancy belongs to the placed/base loc definition. A multiloc may
        // resolve to another definition for its visible model, but the client creates
        // the GameObject start/end tile rectangle before that runtime transform.
        int footprintWidth = object.rotation() % 2 == 0
                ? placementDefinition.width() : placementDefinition.length();
        int footprintLength = object.rotation() % 2 == 0
                ? placementDefinition.length() : placementDefinition.width();
        return new ResolvedModelBuild(objectDefinition, appearance, animation.frame(),
                animation.cachedSkeletal(), animation.skeleton(), animation.state(),
                decorDisplacement, footprintWidth, footprintLength);
    }

    private Optional<ModelRenderPacket> buildResolvedPacket(
            WorldObject object,
            WorldDocument document,
            ResolvedModelBuild resolved,
            List<WallRules.LocModelVariant> variants,
            WallDecorationPresentation presentation) {
        return buildResolvedPacket(object, document, resolved, variants, presentation, 0);
    }

    private Optional<ModelRenderPacket> buildResolvedPacket(
            WorldObject object,
            WorldDocument document,
            ResolvedModelBuild resolved,
            List<WallRules.LocModelVariant> variants,
            WallDecorationPresentation presentation,
            int occurrence) {
        PacketParts parts = new PacketParts();
        for (WallRules.LocModelVariant variant : variants) {
            int renderableBoundsStart = parts.clientBoundsVertices.size();
            for (int modelId : modelIdsFor(resolved.objectDefinition(), variant.sourceType())) {
                Optional<ModelGeometryView> geometry = definitions.modelGeometry(modelId);
                if (geometry.isEmpty()) continue;
                ModelGeometryView baseGeometry = geometry.orElseThrow();
                ModelGeometryView animatedGeometry = baseGeometry;
                if (resolved.cachedSkeletal().isPresent()
                        && resolved.animationSkeleton().isPresent()) {
                    Optional<ModelSkeletalSkinView> skin =
                            definitions.modelSkeletalSkin(modelId);
                    if (skin.isPresent()) {
                        animatedGeometry = CachedSkeletalModelAnimation.apply(
                                baseGeometry, skin.orElseThrow(),
                                resolved.animationSkeleton().orElseThrow(),
                                resolved.cachedSkeletal().orElseThrow(),
                                resolved.animationState().frameIndex());
                    }
                } else if (resolved.animation().isPresent()
                        && resolved.animationSkeleton().isPresent()) {
                    animatedGeometry = ModelAnimation.apply(baseGeometry,
                            resolved.animation().orElseThrow(),
                            resolved.animationSkeleton().orElseThrow());
                }
                parts.animationTransformed |= animatedGeometry != baseGeometry;
                int variantStart = parts.vertices.size();
                append(parts, object, resolved.appearance(), animatedGeometry, document,
                        variant, resolved.footprintWidth(), resolved.footprintLength());
                if (variant.sourceType() == 2 && parts.vertices.size() > variantStart) {
                    // TSPS keeps the two type-2 L-wall models separate until
                    // ModelData.mergeNormals(model0, model1, 0, 0, 0, false).
                    parts.wallVariantRanges.add(new VertexRange(variantStart, parts.vertices.size()));
                }
            }
            if (parts.clientBoundsVertices.size() > renderableBoundsStart) {
                parts.clientRenderableRanges.add(new VertexRange(
                        renderableBoundsStart, parts.clientBoundsVertices.size()));
                parts.clientRenderablePlacements.add(
                        new ClientRenderablePlacement(variant.decorX(), variant.decorZ()));
            }
        }
        if (parts.vertices.isEmpty() || parts.triangles.isEmpty()) return Optional.empty();
        int[] bounds = bounds(parts.vertices);
        int modelDrawOrientation = object.type() == 11 ? 256 : 0;
        List<ClientModelBounds> clientRenderableBounds = parts.clientRenderableRanges.stream()
                .map(range -> ClientModelBounds.calculate(
                        parts.clientBoundsVertices.subList(range.start(), range.end()),
                        modelDrawOrientation, false))
                .toList();
        GameObjectSceneMetadata sceneMetadata = object.category()
                == com.rspsi.editor.model.ObjectCategory.GROUND
                ? GameObjectSceneMetadata.of(object.x(), object.y(),
                        resolved.footprintWidth(), resolved.footprintLength(),
                        object.rotation(), modelDrawOrientation)
                : GameObjectSceneMetadata.none();
        SceneObjectIdentity sceneObjectIdentity = SceneObjectIdentity.of(
                object, resolved.footprintWidth(), resolved.footprintLength(), occurrence);
        int placementHeight = objectCenterHeight(
                document, object, resolved.footprintWidth(), resolved.footprintLength());
        ModelContourContract contourContract = resolved.appearance().contourGroundType() >= 0
                ? ModelContourContract.of(
                        resolved.appearance().contourGroundType(),
                        resolved.appearance().contourGroundParameter(),
                        placementHeight, parts.contourApplied, parts.unskewedVertexY)
                : ModelContourContract.none();
        ModelRenderPacket packet = new ModelRenderPacket(
                new TileCoordinate(object.plane(), object.x(), object.y()), object.id(),
                object.category(), parts.vertices, parts.triangles, parts.textureTriangles,
                resolved.appearance().animationId(), bounds[0], bounds[1], bounds[2],
                bounds[3], bounds[4], bounds[5], resolved.animationState().active(), false,
                placementHeight,
                object.shape().map(shape -> shape.id() >= 12 && shape.id() <= 21).orElse(false),
                GpuDrawCommand.RenderMode.DEFAULT, presentation, sceneMetadata,
                clientRenderableBounds, parts.clientRenderablePlacements,
                contourContract,
                resolved.animationState().withTransformed(parts.animationTransformed),
                sceneObjectIdentity);
        return Optional.of(resolved.appearance().mergeNormals()
                ? mergeWallVariantNormals(packet, parts.wallVariantRanges) : packet);
    }

    private record ResolvedModelBuild(ObjectDefinitionView objectDefinition,
                                      ObjectAppearanceView appearance,
                                      Optional<AnimationFrameView> animation,
                                      Optional<CachedSkeletalAnimationView> cachedSkeletal,
                                      Optional<SkeletonDefinitionView> animationSkeleton,
                                      ModelAnimationState animationState,
                                      int decorDisplacement,
                                      int footprintWidth,
                                      int footprintLength) {
    }

    private record ResolvedAnimation(Optional<AnimationFrameView> frame,
                                     Optional<CachedSkeletalAnimationView> cachedSkeletal,
                                     Optional<SkeletonDefinitionView> skeleton,
                                     ModelAnimationState state) {
    }

    private ResolvedAnimation resolveAnimation(int animationId, int clientCycle) {
        if (animationId < 0) {
            return new ResolvedAnimation(Optional.empty(), Optional.empty(), Optional.empty(),
                    ModelAnimationState.none());
        }
        Optional<SequenceDefinitionView> sequence = definitions.sequence(animationId);
        if (sequence.isEmpty()) {
            return new ResolvedAnimation(Optional.empty(), Optional.empty(), Optional.empty(),
                    ModelAnimationState.unresolved(animationId, clientCycle));
        }

        SequenceDefinitionView value = sequence.orElseThrow();
        if (value.cachedSkeletal() && value.cachedFrameCount() > 0) {
            int selectedIndex = com.rspsi.osrs.rules.model.AnimationResolver.cachedFrameIndex(
                    value.cachedFrameCount(), value.frameStep(), clientCycle);
            Optional<CachedSkeletalAnimationView> cached =
                    definitions.cachedSkeletalAnimation(value.skeletalId());
            Optional<SkeletonDefinitionView> skeleton = cached.isPresent()
                    ? definitions.skeleton(cached.orElseThrow().skeletonId())
                    : Optional.empty();
            return new ResolvedAnimation(Optional.empty(), cached, skeleton,
                    ModelAnimationState.selected(animationId, selectedIndex,
                            value.skeletalId(), clientCycle,
                            value.animationHeightOffset(), false));
        }

        int[] frameIds = value.frameIds();
        if (frameIds.length == 0) {
            return new ResolvedAnimation(Optional.empty(), Optional.empty(), Optional.empty(),
                    ModelAnimationState.unresolved(animationId, clientCycle,
                            value.animationHeightOffset()));
        }

        int selectedIndex = animationFrameIndex(
                frameIds.length, value.frameLengths(), value.frameStep(), clientCycle);
        int selectedFrameId = frameIds[selectedIndex];
        Optional<AnimationFrameView> frame = definitions.animationFrame(selectedFrameId);
        Optional<SkeletonDefinitionView> skeleton = frame.isPresent()
                ? definitions.skeleton(frame.orElseThrow().skeletonId())
                : Optional.empty();
        return new ResolvedAnimation(frame, Optional.empty(), skeleton,
                ModelAnimationState.selected(animationId, selectedIndex, selectedFrameId,
                        clientCycle, value.animationHeightOffset(), false));
    }

    /**
     * Selects the frame using the client sequence loop rule. A positive
     * frameStep rewinds that many frames after the initial pass; it is not a
     * simple modulo of the full sequence duration.
     */
    static int animationFrameIndex(int frameCount, int[] frameLengths,
                                   int frameStep, int clientCycle) {
        return com.rspsi.osrs.rules.model.AnimationResolver.animationFrameIndex(
                frameCount, frameLengths, frameStep, clientCycle);
    }

    private static long sum(long[] values, int from, int count) {
        long total = 0;
        for (int index = from; index < from + count; index++) total += values[index];
        return total;
    }

    private static List<Integer> modelIdsFor(ObjectDefinitionView definition, int sourceType) {
        int[] ids = definition.modelIds();
        int[] types = definition.modelTypes();
        List<Integer> selected = new ArrayList<>();
        if (types.length == 0) {
            if (sourceType == 10) {
                for (int id : ids) selected.add(id);
            }
            return selected;
        }
        for (int index = 0; index < Math.min(ids.length, types.length); index++) {
            if (types[index] == sourceType) selected.add(ids[index]);
        }
        return selected;
    }

    /**
     * Delegates location variant decomposition to the formal OSRS rule layer.
     * Renderer code must consume these rules rather than maintain a second
     * shape/rotation/displacement switch.
     */
    private static List<WallRules.LocModelVariant> variantsFor(
            WorldObject object, int decorDisplacement) {
        return WallRules.expandVariants(object, decorDisplacement);
    }

    /**
     * OSRS wall decorations use the displacement stored by the wall they are
     * attached to, not an arbitrary renderer default. When no supporting
     * wall is found (a sparse/editing document, or a decoration placed
     * before its wall), the client falls back to a hardcoded literal - 16,
     * used unhalved by variantsFor's shape-5 case - rather than the
     * decoration's own definition value. variantsFor already halves this
     * shared displacement for the diagonal shapes 6/8, so returning 16 here
     * also reproduces the client's separate "8" diagonal default without a
     * second case.
     */
    /**
     * A "multiloc" definition (opcodes 77/92: {@code multiVarBit}/{@code
     * multiVarp}/{@code transforms}) carries no models of its own - the
     * client swaps in one of its {@code transforms} entries based on live
     * varbit/varp state, falling back to {@code multiDefault} (an object id,
     * not an index - see OpenRune's {@code Transforms.readTransforms}, which
     * appends it as the array's own last slot) when no player state applies.
     * An editor session has no player state at all, so {@code multiDefault}
     * IS the client's "no state" case, not an approximation of it. Skipping
     * this resolution renders such objects as nothing - Lumbridge's castle
     * bushes are exactly this: the placed id is a bare multiloc shell.
     */
    private ObjectDefinitionView resolveDisplayDefinition(ObjectDefinitionView definition) {
        ObjectDefinitionView current = definition;
        for (int hop = 0; hop < 8 && current.modelIds().length == 0
                && current.hasTransforms() && current.defaultTransform() >= 0; hop++) {
            Optional<ObjectDefinitionView> next = definitions.object(current.defaultTransform());
            if (next.isEmpty() || next.orElseThrow().id() == current.id()) break;
            current = next.orElseThrow();
        }
        return current;
    }

    private int wallDecorationDisplacement(WorldObject decoration,
                                           ObjectAppearanceView ownAppearance,
                                           WorldDocument document) {
        return com.rspsi.osrs.rules.loc.WallDecorationRules.resolveDisplacement(decoration, ownAppearance, document, definitions);
    }

    private void append(PacketParts parts, WorldObject object, ObjectAppearanceView appearance,
                        ModelGeometryView geometry,
        WorldDocument document, WallRules.LocModelVariant variant,
                        int footprintWidth, int footprintLength) {
        int vertexOffset = parts.vertices.size();
        int[] positions = geometry.vertexPositions();
        // The client's getModelData mirrors via isRotated XOR (rotationParam
        // > 3), applied uniformly for every shape through the rotation value
        // passed to it - not an OR gated to sourceType==2. The ">3" case
        // covers shape 2's first wall piece (rot+4) and every diagonal
        // wall-decoration variant (shapes 6/7/8/11, which also use a "+4"
        // rotation); those must mirror only when isRotated is false, and a
        // straight variant (rotation always <=3) must mirror only when
        // isRotated is true - an unconditional OR mirrors straight-shape-2
        // pieces and never mirrors diagonal decorations at all when
        // isRotated is false, which is the common case.
        boolean mirror = appearance.rotated() ^ (variant.rotation() > 3);
        // TSPS/RuneLite place a location at the centre of its footprint, not
        // at the south-west corner. The packet keeps x/z relative to the
        // anchor tile, so the centre is footprint * halfTile.
        int centerX = footprintWidth * 64;
        int centerZ = footprintLength * 64;

        List<RawVertex> transformed = new ArrayList<>(geometry.vertexCount());
        for (int index = 0; index < geometry.vertexCount(); index++) {
            int offset = index * 3;
            int x = positions[offset];
            int y = positions[offset + 1];
            int z = positions[offset + 2];
            if (mirror) {
                z = -z;
            }
            if (variant.sourceType() == 4 && variant.rotation() > 3) {
                int[] diagonal = rotateJagexAngle(x, z, 256);
                x = diagonal[0] + 45;
                z = diagonal[1] - 45;
            }
            int[] rotated = rotateQuarterTurn(x, z, variant.rotation());
            x = rotated[0];
            z = rotated[1];
            x = x * appearance.scaleX() / 128;
            y = y * appearance.scaleY() / 128;
            z = z * appearance.scaleZ() / 128;
            // LocModelLoader applies definition offsets in model-local space,
            // then applies the NORMAL diagonal 256-angle rotation, and only
            // then places the model at the footprint centre. Rotating after
            // adding centerX/centerZ would rotate the world placement and
            // definition offsets around the wrong origin.
            x += appearance.offsetX();
            y += appearance.offsetY();
            z += appearance.offsetZ();
            if (variant.rotateAfterScale()) {
                int[] diagonal = rotateJagexAngle(x, z, 256);
                x = diagonal[0];
                z = diagonal[1];
            }
            x += centerX + variant.decorX();
            z += centerZ + variant.decorZ();
            transformed.add(new RawVertex(x, y, z));
        }

        // The client bakes lighting from pre-contour normals: LocModelLoader
        // lights the placed model and SceneBuilder applies contourGround to
        // the already-lit result. Contouring first would feed the light pass
        // slope-shifted normals and visibly tilt shading on hills.
        List<Normal> normals = calculateNormals(transformed, geometry, mirror);
        int[] colors = toUnsignedColors(geometry.triangleColors());
        int[] alphas = geometry.triangleAlphas();
        int[] textures = geometry.triangleTextures();
        int[] renderTypes = geometry.triangleRenderTypes();
        int[] priorities = geometry.triangleRenderPriorities();
        int[] depthBias = geometry.triangleDepthBias();
        int renderPriority = definitions.model(geometry.id()).map(view -> view.renderPriority()).orElse(0);
        int[] indices = geometry.triangleIndices();
        TextureProjection[] textureProjections = buildTextureProjections(geometry);
        for (int face = 0; face < geometry.triangleCount(); face++) {
            int index = face * 3;
            int a = indices[index];
            int b = indices[index + 1];
            int c = indices[index + 2];
            TextureUv uv = textureUv(geometry, face, a, b, c, textureProjections);
            if (mirror) {
                int swap = b;
                b = c;
                c = swap;
                uv = new TextureUv(uv.u0, uv.v0, uv.u2, uv.v2, uv.u1, uv.v1);
            }
            int rawAlpha = valueAt(alphas, face, 0);
            int renderType = valueAt(renderTypes, face, 0);
            // The cache stores one SIGNED byte of per-face transparency and the
            // client normalises it to 0..255 while loading (MeshOSRSType3:
            // `if (faceTransparencies[face] < 0) faceTransparencies[face] += 256`).
            // The definition provider hands that raw signed byte through, so
            // 0x80..0xFF arrives negative. Clamping it to zero instead drew
            // every translucent face fully opaque - which is what made gates and
            // doors read as solid slabs and left translucent wall trim fighting
            // the wall it decorates. 0xFF is invisible in the client (its
            // transparency is spent before the write), so it stays out of both
            // submission passes exactly as the old -1 sentinel did.
            int alpha = rawAlpha & 0xFF;
            if (renderType == -1) renderType = 2;
            if (alpha == 255) renderType = 2;
            int texture = valueAt(textures, face, -1);
            int color = valueAt(colors, face, 0);
            color = recolor(color, appearance.recolors());
            int priority = clamp(valueAt(priorities, face, renderPriority), 0, 255);
            int bias = clamp(valueAt(depthBias, face, 0), 0, 255);
            Normal faceNormal = faceNormal(transformed.get(a), transformed.get(b), transformed.get(c));
            ModelFaceColorContract.LitFace lit = ModelFaceColorContract.shade(
                    color, texture >= 0, renderType,
                    lightness(normals.get(a), appearance),
                    lightness(normals.get(b), appearance),
                    lightness(normals.get(c), appearance),
                    flatLightness(faceNormal, appearance));
            parts.triangles.add(new ModelTriangle(vertexOffset + a, vertexOffset + b,
                    vertexOffset + c, lit.colorA(), lit.colorB(), lit.colorC(),
                    texture < 0 ? -1 : retexture(texture, appearance.retextures()),
                    alpha, priority, renderType, uv.u0, uv.v0, uv.u1, uv.v1, uv.u2, uv.v2,
                    color, bias));
        }
        // Lighting and face colors are locked in above from pre-contour
        // geometry; the client warps vertex Y afterwards (SceneBuilder applies
        // contourGround to the already-lit model). Retain that pre-contour Y
        // stream so HILLSKEW-style consumers can reconstruct the unskewed
        // model when the client actually creates a contoured copy.
        List<RawVertex> unskewed = List.copyOf(transformed);
        // The client gates on clipType >= 0, not on the legacy boolean.
        if (appearance.contourGroundType() >= 0) {
            List<RawVertex> contoured = applyContour(document, object, footprintWidth,
                    footprintLength, transformed, appearance,
                    variant.decorX(), variant.decorZ());
            if (contoured != null) {
                transformed = contoured;
                parts.contourApplied = true;
            }
        }
        for (int vertex = 0; vertex < transformed.size(); vertex++) {
            RawVertex value = transformed.get(vertex);
            Normal normal = normals.get(vertex);
            parts.unskewedVertexY.add(unskewed.get(vertex).y());
            parts.vertices.add(new ModelVertex(value.x, value.y, value.z,
                    normal.x, normal.y, normal.z, normal.magnitude,
                    normalized(value.x, transformed, true),
                    normalized(value.z, transformed, false)));
            parts.clientBoundsVertices.add(new ModelVertex(
                    value.x - centerX - variant.decorX(), value.y,
                    value.z - centerZ - variant.decorZ(),
                    0, 0, 0, 0, 0.0f, 0.0f));
        }
        int[] textureIndices = geometry.textureTriangleIndices();
        for (int index = 0; index + 2 < textureIndices.length; index += 3) {
            int textureTriangle = index / 3;
            parts.textureTriangles.add(new TextureTriangle(vertexOffset + textureIndices[index],
                    vertexOffset + textureIndices[index + 1], vertexOffset + textureIndices[index + 2],
                    valueAt(geometry.textureRenderTypes(), textureTriangle, 0),
                    valueAt(geometry.textureScaleX(), textureTriangle, 0),
                    valueAt(geometry.textureScaleY(), textureTriangle, 0),
                    valueAt(geometry.textureScaleZ(), textureTriangle, 0),
                    valueAt(geometry.textureRotations(), textureTriangle, 0),
                    valueAt(geometry.textureDirections(), textureTriangle, 0),
                    valueAt(geometry.textureSpeeds(), textureTriangle, 0),
                    valueAt(geometry.textureTranslationsU(), textureTriangle, 0),
                    valueAt(geometry.textureTranslationsV(), textureTriangle, 0)));
        }
    }

    private static int[] rotateQuarterTurn(int x, int z, int rotation) {
        return switch (rotation & 3) {
            case 1 -> new int[]{z, -x};
            case 2 -> new int[]{-x, -z};
            case 3 -> new int[]{-z, x};
            default -> new int[]{x, z};
        };
    }

    /**
     * Applies the client/TSPS shared-vertex normal merge after all objects
     * have been placed in one document. Matching uses absolute plane-space
     * coordinates, including the packet's placement elevation, rather than
     * comparing object-local coordinates.
     */
    private List<ModelRenderPacket> mergeNormals(List<ModelRenderPacket> packets) {
        if (packets.size() < 2) return packets;
        Map<VertexKey, List<VertexReference>> references = new java.util.LinkedHashMap<>();
        boolean[] mergeEnabled = new boolean[packets.size()];
        for (int packetIndex = 0; packetIndex < packets.size(); packetIndex++) {
            ModelRenderPacket packet = packets.get(packetIndex);
            mergeEnabled[packetIndex] = definitions.objectAppearance(packet.objectId())
                    .map(ObjectAppearanceView::mergeNormals).orElse(false);
            for (int vertexIndex = 0; vertexIndex < packet.vertices().size(); vertexIndex++) {
                ModelVertex vertex = packet.vertices().get(vertexIndex);
                if (vertex.normalMagnitude() == 0) continue;
                VertexKey key = new VertexKey(packet.anchor().plane(),
                        packet.anchor().x() * 128 + vertex.x(),
                        packet.placementHeight() + vertex.y(),
                        packet.anchor().y() * 128 + vertex.z());
                references.computeIfAbsent(key, ignored -> new ArrayList<>())
                        .add(new VertexReference(packetIndex, vertexIndex, vertex));
            }
        }

        @SuppressWarnings("unchecked")
        List<ModelVertex>[] mergedVertices = new List[packets.size()];
        @SuppressWarnings("unchecked")
        List<ModelTriangle>[] packetTriangles = new List[packets.size()];
        boolean[] changed = new boolean[packets.size()];
        for (int index = 0; index < packets.size(); index++) {
            mergedVertices[index] = new ArrayList<>(packets.get(index).vertices());
            packetTriangles[index] = new ArrayList<>(packets.get(index).triangles());
        }
        for (List<VertexReference> group : references.values()) {
            if (group.size() < 2 || group.stream().noneMatch(ref -> mergeEnabled[ref.packetIndex()])) {
                continue;
            }
            for (VertexReference target : group) {
                // RuneLite/TSPS leave only mergeNormals locations unlit until
                // the scene-wide merge pass. A neighboring model that was
                // already lit must remain untouched even if it shares a
                // geometric vertex with a merge-enabled location.
                if (!mergeEnabled[target.packetIndex()]) continue;
                Normal accumulated = normal(target.vertex());
                boolean foundOtherPacket = false;
                for (VertexReference source : group) {
                    if (source.packetIndex() == target.packetIndex()
                            || !mergeEnabled[source.packetIndex()]
                            || source.vertex().normalMagnitude() == 0) continue;
                    Normal sourceNormal = normal(source.vertex());
                    accumulated = new Normal(accumulated.x + sourceNormal.x,
                            accumulated.y + sourceNormal.y,
                            accumulated.z + sourceNormal.z,
                            accumulated.magnitude + sourceNormal.magnitude);
                    foundOtherPacket = true;
                }
                if (!foundOtherPacket) continue;
                ModelVertex original = target.vertex();
                mergedVertices[target.packetIndex()].set(target.vertexIndex(),
                        new ModelVertex(original.x(), original.y(), original.z(),
                                accumulated.x, accumulated.y, accumulated.z,
                                accumulated.magnitude, original.u(), original.v()));
                changed[target.packetIndex()] = true;
            }
        }

        // OSRS/TSPS hideOccludedFaces contract: when two merge-enabled models
        // meet at a seam, coplanar duplicate faces sharing all 3 vertex positions
        // are marked as hidden (renderType = 2) to eliminate internal z-fighting.
        Map<FaceKey, List<FaceReference>> faces = new java.util.LinkedHashMap<>();
        for (int packetIndex = 0; packetIndex < packets.size(); packetIndex++) {
            if (!mergeEnabled[packetIndex]) continue;
            ModelRenderPacket packet = packets.get(packetIndex);
            List<ModelVertex> vertices = packet.vertices();
            int plane = packet.anchor().plane();
            int anchorX = packet.anchor().x() * 128;
            int height = packet.placementHeight();
            int anchorZ = packet.anchor().y() * 128;
            for (int faceIndex = 0; faceIndex < packet.triangles().size(); faceIndex++) {
                ModelTriangle face = packet.triangles().get(faceIndex);
                if (face.renderType() == 2) continue;
                ModelVertex vA = vertices.get(face.a());
                ModelVertex vB = vertices.get(face.b());
                ModelVertex vC = vertices.get(face.c());
                VertexKey kA = new VertexKey(plane, anchorX + vA.x(), height + vA.y(), anchorZ + vA.z());
                VertexKey kB = new VertexKey(plane, anchorX + vB.x(), height + vB.y(), anchorZ + vB.z());
                VertexKey kC = new VertexKey(plane, anchorX + vC.x(), height + vC.y(), anchorZ + vC.z());
                FaceKey faceKey = FaceKey.canonical(kA, kB, kC);
                faces.computeIfAbsent(faceKey, ignored -> new ArrayList<>())
                        .add(new FaceReference(packetIndex, faceIndex));
            }
        }

        for (List<FaceReference> group : faces.values()) {
            if (group.size() < 2 || group.stream().map(FaceReference::packetIndex).distinct().count() < 2) {
                continue;
            }
            for (FaceReference ref : group) {
                ModelTriangle original = packetTriangles[ref.packetIndex()].get(ref.faceIndex());
                if (original.renderType() != 2) {
                    packetTriangles[ref.packetIndex()].set(ref.faceIndex(), original.withRenderType(2));
                    changed[ref.packetIndex()] = true;
                }
            }
        }

        List<ModelRenderPacket> result = new ArrayList<>(packets.size());
        for (int index = 0; index < packets.size(); index++) {
            ModelRenderPacket packet = packets.get(index);
            result.add(changed[index]
                    ? relight(packet, List.copyOf(mergedVertices[index]), List.copyOf(packetTriangles[index]))
                    : packet);
        }
        return result;
    }

    private ModelRenderPacket relight(ModelRenderPacket packet, List<ModelVertex> vertices) {
        return relight(packet, vertices, packet.triangles());
    }

    private ModelRenderPacket relight(ModelRenderPacket packet, List<ModelVertex> vertices,
                                      List<ModelTriangle> sourceTriangles) {
        ObjectAppearanceView appearance = definitions.objectAppearance(packet.objectId())
                .orElseGet(ObjectAppearanceView::empty);
        List<ModelTriangle> triangles = new ArrayList<>(sourceTriangles.size());
        for (ModelTriangle face : sourceTriangles) {
            ModelVertex first = vertices.get(face.a());
            ModelVertex second = vertices.get(face.b());
            ModelVertex third = vertices.get(face.c());
            ModelFaceColorContract.LitFace lit = ModelFaceColorContract.shade(
                    face.baseColor(), face.textureId() >= 0, face.renderType(),
                    lightness(normal(first), appearance),
                    lightness(normal(second), appearance),
                    lightness(normal(third), appearance),
                    flatLightness(faceNormal(first, second, third), appearance));
            triangles.add(face.withColors(lit.colorA(), lit.colorB(), lit.colorC()));
        }
        return new ModelRenderPacket(packet.anchor(), packet.objectId(), packet.category(),
                vertices, triangles, packet.textureTriangles(), packet.animationId(),
                packet.minX(), packet.minY(), packet.minZ(), packet.maxX(), packet.maxY(),
                packet.maxZ(), packet.supportsAnimation(), packet.supportsParticles(),
                packet.placementHeight(), packet.roofRelated(), packet.renderMode(),
                packet.wallDecorationPresentation(), packet.gameObjectSceneMetadata(),
                packet.clientRenderableBounds(), packet.clientRenderablePlacements(),
                packet.contourContract(), packet.animationState(), packet.sceneObjectIdentity());
    }

    /**
     * Reproduces the TSPS/ModelData merge between the two models that make up
     * a type-2 L-wall. The ranges are intentionally limited to source type 2
     * variants so ordinary duplicate vertices within one model are not
     * accidentally smoothed across a model seam.
     */
    private ModelRenderPacket mergeWallVariantNormals(ModelRenderPacket packet,
                                                       List<VertexRange> ranges) {
        if (ranges.size() < 2) return packet;
        Map<PositionKey, List<VertexReference>> references = new java.util.LinkedHashMap<>();
        for (int rangeIndex = 0; rangeIndex < ranges.size(); rangeIndex++) {
            VertexRange range = ranges.get(rangeIndex);
            for (int vertexIndex = range.start(); vertexIndex < range.end(); vertexIndex++) {
                ModelVertex vertex = packet.vertices().get(vertexIndex);
                if (vertex.normalMagnitude() == 0) continue;
                references.computeIfAbsent(new PositionKey(vertex.x(), vertex.y(), vertex.z()),
                        ignored -> new ArrayList<>())
                        .add(new VertexReference(rangeIndex, vertexIndex, vertex));
            }
        }

        List<ModelVertex> merged = new ArrayList<>(packet.vertices());
        boolean changed = false;
        for (List<VertexReference> group : references.values()) {
            if (group.size() < 2 || group.stream().map(VertexReference::packetIndex).distinct().count() < 2) {
                continue;
            }
            for (VertexReference target : group) {
                Normal accumulated = normal(target.vertex());
                boolean foundOtherRange = false;
                for (VertexReference source : group) {
                    if (source.packetIndex() == target.packetIndex()) continue;
                    Normal sourceNormal = normal(source.vertex());
                    accumulated = new Normal(accumulated.x + sourceNormal.x,
                            accumulated.y + sourceNormal.y,
                            accumulated.z + sourceNormal.z,
                            accumulated.magnitude + sourceNormal.magnitude);
                    foundOtherRange = true;
                }
                if (!foundOtherRange) continue;
                ModelVertex original = target.vertex();
                merged.set(target.vertexIndex(), new ModelVertex(original.x(), original.y(), original.z(),
                        accumulated.x, accumulated.y, accumulated.z, accumulated.magnitude,
                        original.u(), original.v()));
                changed = true;
            }
        }
        return changed ? relight(packet, List.copyOf(merged)) : packet;
    }

    private static Normal normal(ModelVertex vertex) {
        return new Normal(vertex.normalX(), vertex.normalY(), vertex.normalZ(),
                vertex.normalMagnitude());
    }

    private static Normal faceNormal(ModelVertex first, ModelVertex second, ModelVertex third) {
        return faceNormal(new RawVertex(first.x(), first.y(), first.z()),
                new RawVertex(second.x(), second.y(), second.z()),
                new RawVertex(third.x(), third.y(), third.z()));
    }

    private record VertexKey(int plane, int x, int y, int z) implements Comparable<VertexKey> {
        @Override
        public int compareTo(VertexKey other) {
            int cmp = Integer.compare(plane, other.plane);
            if (cmp != 0) return cmp;
            cmp = Integer.compare(x, other.x);
            if (cmp != 0) return cmp;
            cmp = Integer.compare(y, other.y);
            if (cmp != 0) return cmp;
            return Integer.compare(z, other.z);
        }
    }

    private record FaceKey(VertexKey v1, VertexKey v2, VertexKey v3) {
        static FaceKey canonical(VertexKey a, VertexKey b, VertexKey c) {
            VertexKey x = a;
            VertexKey y = b;
            VertexKey z = c;
            if (x.compareTo(y) > 0) { VertexKey t = x; x = y; y = t; }
            if (y.compareTo(z) > 0) { VertexKey t = y; y = z; z = t; }
            if (x.compareTo(y) > 0) { VertexKey t = x; x = y; y = t; }
            return new FaceKey(x, y, z);
        }
    }

    private record FaceReference(int packetIndex, int faceIndex) {
    }

    private record PositionKey(int x, int y, int z) {
    }

    private record VertexReference(int packetIndex, int vertexIndex, ModelVertex vertex) {
    }

    /** Matches the client ModelData.rotate(angle) 2048-unit angle table. */
    private static int[] rotateJagexAngle(int x, int z, int angle) {
        int sine = (int) (65536.0 * Math.sin(angle * Math.PI * 2.0 / 2048.0));
        int cosine = (int) (65536.0 * Math.cos(angle * Math.PI * 2.0 / 2048.0));
        int rotatedX = (sine * z + cosine * x) >> 16;
        int rotatedZ = (cosine * z - sine * x) >> 16;
        return new int[]{rotatedX, rotatedZ};
    }

    /**
     * Computes the client texture coordinates for all four OSRS model mapping
     * types. The values are kept per face because adjacent faces may legitimately
     * use different seams on the same model vertex.
     */
    private static TextureUv textureUv(ModelGeometryView geometry, int face, int a, int b, int c,
                                        TextureProjection[] projections) {
        int texture = valueAt(geometry.triangleTextures(), face, -1);
        if (texture < 0) return TextureUv.EMPTY;
        int coordinate = valueAt(geometry.textureCoordinates(), face, -1);
        int mappingIndex = coordinate < 0 ? -1 : coordinate & 0xFF;
        int type = mappingIndex >= 0 && mappingIndex < projections.length
                ? projections[mappingIndex].type : 0;
        if (type == 0) {
            int p = a;
            int m = b;
            int n = c;
            int[] mapping = geometry.textureTriangleIndices();
            int offset = mappingIndex * 3;
            if (mappingIndex >= 0 && offset + 2 < mapping.length) {
                p = mapping[offset];
                m = mapping[offset + 1];
                n = mapping[offset + 2];
            }
            return simpleTextureUv(geometry.vertexPositions(), p, m, n, a, b, c);
        }
        TextureProjection projection = projections[mappingIndex];
        if (projection == null) return TextureUv.EMPTY;
        TextureUv uv = switch (type) {
            case 1 -> projection.cylindrical(geometry.vertexPositions(), a, b, c);
            case 2 -> projection.planar(geometry.vertexPositions(), a, b, c);
            case 3 -> projection.spherical(geometry.vertexPositions(), a, b, c);
            default -> TextureUv.EMPTY;
        };
        return fixSeams(uv, type, projection.direction, projection.scaleZ);
    }

    private static TextureProjection[] buildTextureProjections(ModelGeometryView geometry) {
        int[] mapping = geometry.textureTriangleIndices();
        int count = mapping.length / 3;
        TextureProjection[] result = new TextureProjection[count];
        int[] types = geometry.textureRenderTypes();
        int[] textureCoordinates = geometry.textureCoordinates();
        int[] positions = geometry.vertexPositions();
        for (int coordinate = 0; coordinate < count; coordinate++) {
            int type = valueAt(types, coordinate, 0);
            if (type <= 0) {
                result[coordinate] = new TextureProjection(0, 0.0, 0.0, 0.0, 0,
                        0.0, 0.0, 0.0, 0.0, new float[9], 1.0, 1.0, 1.0);
                continue;
            }
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (int face = 0; face < geometry.triangleCount(); face++) {
                int faceCoordinate = valueAt(textureCoordinates, face, -1);
                if (faceCoordinate < 0 || (faceCoordinate & 0xFF) != coordinate) continue;
                int index = face * 3;
                for (int corner = 0; corner < 3; corner++) {
                    int vertex = geometry.triangleIndices()[index + corner];
                    int offset = vertex * 3;
                    minX = Math.min(minX, positions[offset]);
                    maxX = Math.max(maxX, positions[offset]);
                    minY = Math.min(minY, positions[offset + 1]);
                    maxY = Math.max(maxY, positions[offset + 1]);
                    minZ = Math.min(minZ, positions[offset + 2]);
                    maxZ = Math.max(maxZ, positions[offset + 2]);
                }
            }
            if (minX == Integer.MAX_VALUE) {
                minX = minY = minZ = maxX = maxY = maxZ = 0;
            }
            int scaleXValue = valueAt(geometry.textureScaleX(), coordinate, 0);
            int scaleYValue = valueAt(geometry.textureScaleY(), coordinate, 0);
            int scaleZValue = valueAt(geometry.textureScaleZ(), coordinate, 0);
            double scaleX;
            double scaleY;
            double scaleZ;
            if (type == 1) {
                if (scaleXValue == 0) {
                    scaleX = 1.0;
                    scaleZ = 1.0;
                } else if (scaleXValue < 0) {
                    scaleX = -scaleXValue / 1024.0;
                    scaleZ = 1.0;
                } else {
                    scaleX = 1.0;
                    scaleZ = scaleXValue / 1024.0;
                }
                scaleY = reciprocalOrOne(scaleYValue / 64.0);
            } else if (type == 2) {
                scaleX = reciprocalOrOne(scaleXValue / 64.0);
                scaleY = reciprocalOrOne(scaleYValue / 64.0);
                scaleZ = reciprocalOrOne(scaleZValue / 64.0);
            } else {
                scaleX = scaleXValue / 1024.0;
                scaleY = scaleYValue / 1024.0;
                scaleZ = scaleZValue / 1024.0;
            }
            int mappingOffset = coordinate * 3;
            float[] matrix = buildRotationScaleMatrix(mapping[mappingOffset], mapping[mappingOffset + 1],
                    mapping[mappingOffset + 2], valueAt(geometry.textureRotations(), coordinate, 0) & 0xFF,
                    scaleX, scaleY, scaleZ);
            result[coordinate] = new TextureProjection(type,
                    (minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2,
                    valueAt(geometry.textureDirections(), coordinate, 0),
                    valueAt(geometry.textureSpeeds(), coordinate, 0) / 256.0,
                    valueAt(geometry.textureTranslationsU(), coordinate, 0) / 256.0,
                    valueAt(geometry.textureTranslationsV(), coordinate, 0) / 256.0,
                    type == 1 ? valueAt(geometry.textureScaleZ(), coordinate, 0) / 1024.0 : 0,
                    matrix, type == 2 ? scaleX : 1.0, type == 2 ? scaleY : 1.0,
                    type == 2 ? scaleZ : 1.0);
        }
        return result;
    }

    private static double reciprocalOrOne(double value) {
        return value == 0.0 ? 1.0 : 1.0 / value;
    }

    private static TextureUv simpleTextureUv(int[] positions, int p, int m, int n, int a, int b, int c) {
        if (!validVertex(positions, p) || !validVertex(positions, m) || !validVertex(positions, n)
                || !validVertex(positions, a) || !validVertex(positions, b) || !validVertex(positions, c)) {
            return TextureUv.EMPTY;
        }
        double originX = positions[p * 3], originY = positions[p * 3 + 1], originZ = positions[p * 3 + 2];
        double edgeU_X = positions[m * 3] - originX;
        double edgeU_Y = positions[m * 3 + 1] - originY;
        double edgeU_Z = positions[m * 3 + 2] - originZ;
        double edgeV_X = positions[n * 3] - originX;
        double edgeV_Y = positions[n * 3 + 1] - originY;
        double edgeV_Z = positions[n * 3 + 2] - originZ;
        double faceNormalX = edgeU_Y * edgeV_Z - edgeU_Z * edgeV_Y;
        double faceNormalY = edgeU_Z * edgeV_X - edgeU_X * edgeV_Z;
        double faceNormalZ = edgeU_X * edgeV_Y - edgeU_Y * edgeV_X;
        double uBasisX = edgeV_Y * faceNormalZ - edgeV_Z * faceNormalY;
        double uBasisY = edgeV_Z * faceNormalX - edgeV_X * faceNormalZ;
        double uBasisZ = edgeV_X * faceNormalY - edgeV_Y * faceNormalX;
        double denominator = uBasisX * edgeU_X + uBasisY * edgeU_Y + uBasisZ * edgeU_Z;
        if (Math.abs(denominator) < 1.0e-9) return TextureUv.EMPTY;
        double[] u = new double[3];
        int[] corners = {a, b, c};
        for (int index = 0; index < 3; index++) {
            int vertex = corners[index] * 3;
            u[index] = (uBasisX * (positions[vertex] - originX) + uBasisY * (positions[vertex + 1] - originY)
                    + uBasisZ * (positions[vertex + 2] - originZ)) / denominator;
        }
        double vBasisX = edgeU_Y * faceNormalZ - edgeU_Z * faceNormalY;
        double vBasisY = edgeU_Z * faceNormalX - edgeU_X * faceNormalZ;
        double vBasisZ = edgeU_X * faceNormalY - edgeU_Y * faceNormalX;
        denominator = vBasisX * edgeV_X + vBasisY * edgeV_Y + vBasisZ * edgeV_Z;
        if (Math.abs(denominator) < 1.0e-9) return TextureUv.EMPTY;
        double[] v = new double[3];
        for (int index = 0; index < 3; index++) {
            int vertex = corners[index] * 3;
            v[index] = (vBasisX * (positions[vertex] - originX) + vBasisY * (positions[vertex + 1] - originY)
                    + vBasisZ * (positions[vertex + 2] - originZ)) / denominator;
        }
        float u0 = (float) u[0], u1 = (float) u[1], u2 = (float) u[2];
        float v0 = (float) v[0], v1 = (float) v[1], v2 = (float) v[2];
        if (u1 - u0 > 0.99f && u1 - u0 < 1.1f) u1 = 1.0f;
        if (u2 - u1 > 0.99f && u2 - u1 < 1.1f) u2 = 1.0f;
        if (u0 - u2 > 0.99f && u0 - u2 < 1.1f) u0 = 1.0f;
        if (u0 - u1 > 0.99f && u0 - u1 < 1.1f) u0 = 1.0f;
        if (u1 - u2 > 0.99f && u1 - u2 < 1.1f) u1 = 1.0f;
        if (u2 - u0 > 0.99f && u2 - u0 < 1.1f) u2 = 1.0f;
        return new TextureUv(u0, v0, u1, v1, u2, v2);
    }

    private static boolean validVertex(int[] positions, int vertex) {
        return vertex >= 0 && vertex * 3 + 2 < positions.length;
    }

    private static float[] buildRotationScaleMatrix(int mappingP, int mappingM, int mappingN, int rotation,
                                                      double scaleX, double scaleY, double scaleZ) {
        double axisX = 1.0;
        double axisZ = 0.0;
        double normalComponent = mappingM / 32767.0;
        double normalSine = -Math.sqrt(Math.max(0.0, 1.0 - normalComponent * normalComponent));
        double oneMinusNormalComponent = 1.0 - normalComponent;
        double length = Math.sqrt((double) mappingP * mappingP + (double) mappingN * mappingN);
        if (length != 0.0) {
            axisX = -mappingN / length;
            axisZ = mappingP / length;
        }
        float[] base = new float[]{
                (float) (normalComponent + axisX * axisX * oneMinusNormalComponent),
                (float) (axisZ * normalSine),
                (float) (axisZ * axisX * oneMinusNormalComponent),
                (float) (-axisZ * normalSine), (float) normalComponent,
                (float) (axisX * normalSine),
                (float) (axisX * axisZ * oneMinusNormalComponent),
                (float) (-axisX * normalSine),
                (float) (normalComponent + axisZ * axisZ * oneMinusNormalComponent)
        };
        double cosine = Math.cos(rotation * Math.PI / 128.0);
        double sine = Math.sin(rotation * Math.PI / 128.0);
        float[] rotated = new float[]{
                (float) cosine, 0.0f, (float) sine,
                0.0f, 1.0f, 0.0f,
                (float) -sine, 0.0f, (float) cosine
        };
        float[] result = new float[9];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                result[row * 3 + column] = rotated[row * 3] * base[column]
                        + rotated[row * 3 + 1] * base[3 + column]
                        + rotated[row * 3 + 2] * base[6 + column];
            }
        }
        for (int column = 0; column < 3; column++) result[column] *= (float) scaleX;
        for (int column = 0; column < 3; column++) result[3 + column] *= (float) scaleY;
        for (int column = 0; column < 3; column++) result[6 + column] *= (float) scaleZ;
        return result;
    }

    private static float[] applyMatrix(float[] matrix, double x, double y, double z) {
        return new float[]{
                (float) (x * matrix[0] + y * matrix[1] + z * matrix[2]),
                (float) (x * matrix[3] + y * matrix[4] + z * matrix[5]),
                (float) (x * matrix[6] + y * matrix[7] + z * matrix[8])
        };
    }

    private static float[] position(int[] positions, int index) {
        int offset = index * 3;
        return new float[]{positions[offset], positions[offset + 1], positions[offset + 2]};
    }

    private static TextureUv fixSeams(TextureUv uv, int type, int direction, double scaleZ) {
        float u0 = uv.u0, u1 = uv.u1, u2 = uv.u2;
        float v0 = uv.v0, v1 = uv.v1, v2 = uv.v2;
        if (type == 1) {
            double half = scaleZ / 2.0;
            if ((direction & 1) == 0) {
                if (u1 - u0 > half) u1 -= (float) scaleZ; else if (u0 - u1 > half) u1 += (float) scaleZ;
                if (u2 - u0 > half) u2 -= (float) scaleZ; else if (u0 - u2 > half) u2 += (float) scaleZ;
            } else {
                if (v1 - v0 > half) v1 -= (float) scaleZ; else if (v0 - v1 > half) v1 += (float) scaleZ;
                if (v2 - v0 > half) v2 -= (float) scaleZ; else if (v0 - v2 > half) v2 += (float) scaleZ;
            }
        } else if (type == 3) {
            if ((direction & 1) == 0) {
                if (u1 - u0 > 0.5f) u1--; else if (u0 - u1 > 0.5f) u1++;
                if (u2 - u0 > 0.5f) u2--; else if (u0 - u2 > 0.5f) u2++;
            } else {
                if (v1 - v0 > 0.5f) v1--; else if (v0 - v1 > 0.5f) v1++;
                if (v2 - v0 > 0.5f) v2--; else if (v0 - v2 > 0.5f) v2++;
            }
        }
        return new TextureUv(u0, v0, u1, v1, u2, v2);
    }

    private record TextureUv(float u0, float v0, float u1, float v1, float u2, float v2) {
        private static final TextureUv EMPTY = new TextureUv(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
    }

    private static final class TextureProjection {
        private final int type;
        private final double centerX;
        private final double centerY;
        private final double centerZ;
        private final int direction;
        private final double speed;
        private final double uOffset;
        private final double vOffset;
        private final double scaleZ;
        private final float[] matrix;
        private final double normalScaleX;
        private final double normalScaleY;
        private final double normalScaleZ;

        private TextureProjection(int type, double centerX, double centerY, double centerZ,
                                  int direction, double speed, double uOffset, double vOffset,
                                  double scaleZ, float[] matrix,
                                  double normalScaleX, double normalScaleY, double normalScaleZ) {
            this.type = type;
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.direction = direction;
            this.speed = speed;
            this.uOffset = uOffset;
            this.vOffset = vOffset;
            this.scaleZ = scaleZ;
            this.matrix = matrix;
            this.normalScaleX = normalScaleX;
            this.normalScaleY = normalScaleY;
            this.normalScaleZ = normalScaleZ;
        }

        private TextureUv cylindrical(int[] positions, int a, int b, int c) {
            return mapThree(positions, a, b, c, this::cylindrical);
        }

        private float[] cylindrical(int[] positions, int index) {
            float[] point = position(positions, index);
            point[0] -= centerX;
            point[1] -= centerY;
            point[2] -= centerZ;
            float[] mapped = applyMatrix(matrix, point[0], point[1], point[2]);
            double u = Math.atan2(mapped[0], mapped[2]) / (Math.PI * 2.0) + 0.5;
            if (scaleZ != 1.0) u *= scaleZ;
            double v = mapped[1] + 0.5 + speed;
            return rotateDirection(u, v, direction);
        }

        private TextureUv planar(int[] positions, int a, int b, int c) {
            float[] first = position(positions, a);
            float[] second = position(positions, b);
            float[] third = position(positions, c);
            double edge1X = second[0] - first[0];
            double edge1Y = second[1] - first[1];
            double edge1Z = second[2] - first[2];
            double edge2X = third[0] - first[0];
            double edge2Y = third[1] - first[1];
            double edge2Z = third[2] - first[2];
            float[] normal = applyMatrix(matrix,
                    edge1Y * edge2Z - edge2Y * edge1Z,
                    edge1Z * edge2X - edge2Z * edge1X,
                    edge1X * edge2Y - edge2X * edge1Y);
            int scaleType = dominantAxis(normal[0] / normalScaleX,
                    normal[1] / normalScaleY, normal[2] / normalScaleZ);
            return mapThree(positions, a, b, c, (points, index) -> planar(points, index, scaleType));
        }

        private float[] planar(int[] positions, int index, int scaleType) {
            float[] point = position(positions, index);
            point[0] -= centerX;
            point[1] -= centerY;
            point[2] -= centerZ;
            float[] mapped = applyMatrix(matrix, point[0], point[1], point[2]);
            double u;
            double v;
            if (scaleType == 0) {
                u = mapped[0] + speed + 0.5;
                v = -mapped[2] + vOffset + 0.5;
            } else if (scaleType == 1) {
                u = mapped[0] + speed + 0.5;
                v = mapped[2] + vOffset + 0.5;
            } else if (scaleType == 2) {
                u = -mapped[0] + speed + 0.5;
                v = -mapped[1] + uOffset + 0.5;
            } else if (scaleType == 3) {
                u = mapped[0] + speed + 0.5;
                v = -mapped[1] + uOffset + 0.5;
            } else if (scaleType == 4) {
                u = mapped[2] + vOffset + 0.5;
                v = -mapped[1] + uOffset + 0.5;
            } else {
                u = -mapped[2] + vOffset + 0.5;
                v = -mapped[1] + uOffset + 0.5;
            }
            return rotateDirection(u, v, direction);
        }

        private TextureUv spherical(int[] positions, int a, int b, int c) {
            return mapThree(positions, a, b, c, this::spherical);
        }

        private float[] spherical(int[] positions, int index) {
            float[] point = position(positions, index);
            point[0] -= centerX;
            point[1] -= centerY;
            point[2] -= centerZ;
            float[] mapped = applyMatrix(matrix, point[0], point[1], point[2]);
            double length = Math.sqrt((double) mapped[0] * mapped[0] + (double) mapped[1] * mapped[1]
                    + (double) mapped[2] * mapped[2]);
            if (length == 0.0) return new float[]{0.0f, 0.0f};
            double u = Math.atan2(mapped[0], mapped[2]) / (Math.PI * 2.0) + 0.5;
            double v = Math.asin(mapped[1] / length) / Math.PI + 0.5 + speed;
            return rotateDirection(u, v, direction);
        }

        private static TextureUv mapThree(int[] positions, int a, int b, int c,
                                           java.util.function.BiFunction<int[], Integer, float[]> mapper) {
            float[] first = mapper.apply(positions, a);
            float[] second = mapper.apply(positions, b);
            float[] third = mapper.apply(positions, c);
            return new TextureUv(first[0], first[1], second[0], second[1], third[0], third[1]);
        }

        private static float[] rotateDirection(double u, double v, int direction) {
            return switch (direction) {
                case 1 -> new float[]{(float) -v, (float) u};
                case 2 -> new float[]{(float) -u, (float) -v};
                case 3 -> new float[]{(float) v, (float) -u};
                default -> new float[]{(float) u, (float) v};
            };
        }

        private static int dominantAxis(double x, double y, double z) {
            double ax = Math.abs(x);
            double ay = Math.abs(y);
            double az = Math.abs(z);
            if (ay > ax && ay > az) return y > 0.0 ? 0 : 1;
            if (az > ax && az > ay) return z > 0.0 ? 2 : 3;
            return x > 0.0 ? 4 : 5;
        }
    }

    /**
     * Applies the client contourGround pass to an already-lit model, mirroring
     * melxin {@code Model.contourGround}: per-vertex bilinear height minus the
     * placement height, guarded by the client's radius-box bounds and
     * fully-flat-footprint skips. The partial mode (clipType &gt; 0) warps
     * vertices by {@code (-y << 16) / max(-y)} against the clip-type
     * parameter so only the model's upper span conforms to the terrain; the
     * full mode (clipType == 0) attaches every vertex. Types 3-5 mirror TSPS's
     * generalized {@code ModelData.contourGround} for completeness.
     *
     * @return the warped vertices, or {@code null} when the client would leave
     *         the model unchanged
     */
    private List<RawVertex> applyContour(WorldDocument document, WorldObject object,
                                         int footprintWidth, int footprintLength,
                                         List<RawVertex> transformed,
                                         ObjectAppearanceView appearance,
                                         int decorX, int decorZ) {
        int type = appearance.contourGroundType();
        int parameter = appearance.contourGroundParameter();
        if (type < 0) return null;
        boolean usesAbovePlane = type == 4 || type == 5;
        if (usesAbovePlane && document.planes() <= object.plane() + 1) return null;

        // Client contourGround calculates its cylinder from Model-local
        // coordinates, then receives the Scene placement centre separately.
        // RSPSi's neutral packet has already baked footprint-centre and wall-
        // decoration displacement into X/Z, so remove both before calculating
        // the client radius. Wall-decoration displacement is Scene-only and
        // must not participate in contour sampling.
        int centerX = footprintWidth * 64;
        int centerZ = footprintLength * 64;
        int downwardHeight = 0;
        long radiusSquared = 0L;
        int modelMinY = Integer.MAX_VALUE;
        int modelMaxY = Integer.MIN_VALUE;
        for (RawVertex vertex : transformed) {
            int localX = vertex.x() - centerX - decorX;
            int localZ = vertex.z() - centerZ - decorZ;
            if (-vertex.y() > downwardHeight) downwardHeight = -vertex.y();
            long squared = (long) localX * localX + (long) localZ * localZ;
            if (squared > radiusSquared) radiusSquared = squared;
            modelMinY = Math.min(modelMinY, vertex.y());
            modelMaxY = Math.max(modelMaxY, vertex.y());
        }
        downwardHeight = Math.max(1, downwardHeight);
        int xzRadius = (int) (Math.sqrt((double) radiusSquared) + 0.99D);
        int verticalSpan = Math.max(1, modelMaxY - modelMinY);

        int contourCenterX = object.x() * 128 + centerX;
        int contourCenterZ = object.y() * 128 + centerZ;
        int plane = object.plane();
        int minWorldX = contourCenterX - xzRadius;
        int maxWorldX = contourCenterX + xzRadius;
        int minWorldZ = contourCenterZ - xzRadius;
        int maxWorldZ = contourCenterZ + xzRadius;
        // Out-of-scene footprints are left untouched (client bounds guard:
        // every vertex satisfies tx+1 < width because xzRadius bounds them).
        if (minWorldX < 0 || (maxWorldX + 128) >> 7 >= document.width()
                || minWorldZ < 0 || (maxWorldZ + 128) >> 7 >= document.length()) {
            return null;
        }
        int sceneHeight = objectCenterHeight(document, object, footprintWidth, footprintLength);
        int startTileX = minWorldX >> 7;
        int endTileX = (maxWorldX + 127) >> 7;
        int startTileZ = minWorldZ >> 7;
        int endTileZ = (maxWorldZ + 127) >> 7;
        // Fully flat footprints skip the warp: the client compares the radius
        // box's corner tile heights against the placement height (the client
        // flat-skips both modes; types 4/5 always warp).
        if (!usesAbovePlane
                && sampleGrid(document, plane, startTileX, startTileZ) == sceneHeight
                && sampleGrid(document, plane, endTileX, startTileZ) == sceneHeight
                && sampleGrid(document, plane, startTileX, endTileZ) == sceneHeight
                && sampleGrid(document, plane, endTileX, endTileZ) == sceneHeight) {
            return null;
        }

        List<RawVertex> result = new ArrayList<>(transformed.size());
        for (RawVertex vertex : transformed) {
            int localX = vertex.x() - centerX - decorX;
            int localZ = vertex.z() - centerZ - decorZ;
            int worldX = contourCenterX + localX;
            int worldZ = contourCenterZ + localZ;
            int fractionX = worldX & 127;
            int fractionZ = worldZ & 127;
            int tileX = worldX >> 7;
            int tileZ = worldZ >> 7;            // Client-exact bilinear: shifted, not divided, so negative scene
            // heights (the OSRS convention) floor toward negative infinity
            // exactly as the client's arithmetic shift does.
            int south = contourBlend(sampleGrid(document, plane, tileX, tileZ),
                    sampleGrid(document, plane, tileX + 1, tileZ), fractionX);
            int north = contourBlend(sampleGrid(document, plane, tileX, tileZ + 1),
                    sampleGrid(document, plane, tileX + 1, tileZ + 1), fractionX);
            int height = contourBlend(south, north, fractionZ);
            int newY;
            // Client dispatch (ObjectComposition.getModel*): clipType == 0
            // warps every vertex (param 0); clipType > 0 warps only the model
            // span above the partial threshold (param = clipType * 65536).
            // TSPS generalizes the partial path as contourGroundType 2.
            if ((type == 1 || type == 2) && parameter > 0) {
                // Client partial contour: ratio = (-y << 16) / max(-y) runs
                // 0 at the model top toward 65536 at the bottom; only
                // vertices above the parameter threshold warp, scaled by
                // (param - ratio) / param.
                int yRatio = ((-vertex.y()) << 16) / downwardHeight;
                if (yRatio < parameter) {
                    newY = vertex.y() + (parameter - yRatio) * (height - sceneHeight) / parameter;
                } else {
                    newY = vertex.y();
                }
            } else if (type == 3) {
                int delta = height - sceneHeight;
                if (parameter != 0) {
                    int limit = Math.abs(parameter);
                    delta = Math.max(-limit, Math.min(limit, delta));
                }
                newY = vertex.y() + delta;
            } else if (type == 4) {
                int aboveHeight = contourSampleAbove(document, plane, worldX, worldZ,
                        fractionX, fractionZ);
                newY = vertex.y() + aboveHeight - sceneHeight + verticalSpan;
            } else if (type == 5) {
                int aboveHeight = contourSampleAbove(document, plane, worldX, worldZ,
                        fractionX, fractionZ);
                int deltaHeight = height - aboveHeight;
                newY = (((vertex.y() << 8) / verticalSpan) * deltaHeight >> 8)
                        - (sceneHeight - height);
            } else {
                // Type 1 with parameter 0 (client clipType 0) and any
                // unmodelled type: full ground attachment.
                newY = vertex.y() + height - sceneHeight;
            }
            result.add(new RawVertex(vertex.x(), newY, vertex.z()));
        }
        return result;
    }

    /** Client bilinear step: {@code (first * (128 - amount) + second * amount) >> 7}. */
    private static int contourBlend(int first, int second, int amount) {
        return (first * (128 - amount) + second * amount) >> 7;
    }

    /** Bilinearly samples the plane above at contour time for TSPS types 4/5. */
    private static int contourSampleAbove(WorldDocument document, int plane, int worldX,
                                          int worldZ, int fractionX, int fractionZ) {
        int tileX = worldX >> 7;
        int tileZ = worldZ >> 7;
        int south = contourBlend(sampleGrid(document, plane + 1, tileX, tileZ),
                sampleGrid(document, plane + 1, tileX + 1, tileZ), fractionX);
        int north = contourBlend(sampleGrid(document, plane + 1, tileX, tileZ + 1),
                sampleGrid(document, plane + 1, tileX + 1, tileZ + 1), fractionX);
        return contourBlend(south, north, fractionZ);
    }

    /**
     * Resolves a shared corner-grid point the way the client's scene height
     * grid does: each grid point is written once per adjacent tile and the
     * east/south tile's write wins, so a point on a tile boundary reads the
     * containing tile's south-west corner. Edge points fall back to the
     * clamped edge tile's far corner.
     */
    private static int sampleGrid(WorldDocument document, int plane, int gridX, int gridZ) {
        boolean eastEdge = gridX >= document.width();
        boolean northEdge = gridZ >= document.length();
        int tileX = Math.max(0, eastEdge ? document.width() - 1 : gridX);
        int tileZ = Math.max(0, northEdge ? document.length() - 1 : gridZ);
        TileSnapshot tile = document.tile(plane, tileX, tileZ).snapshot();
        if (eastEdge && northEdge) return tile.northEastHeight();
        if (eastEdge) return tile.southEastHeight();
        if (northEdge) return tile.northWestHeight();
        return tile.southWestHeight();
    }

    /**
     * Client centerLocHeightWithSize placement height: the mean of the four
     * corner grid heights of the footprint's central block (TSPS SceneBuilder).
     */
    private int objectCenterHeight(WorldDocument document, WorldObject object,
                                   int footprintWidth, int footprintLength) {
        int anchorX = object.x() * 128;
        int anchorZ = object.y() * 128;
        int startX = anchorX + (footprintWidth >> 1) * 128;
        int startZ = anchorZ + (footprintLength >> 1) * 128;
        int endX = anchorX + (footprintWidth - (footprintWidth >> 1)) * 128;
        int endZ = anchorZ + (footprintLength - (footprintLength >> 1)) * 128;
        int plane = object.plane();
        long sum = (long) sampleGrid(document, plane, startX >> 7, startZ >> 7)
                + sampleGrid(document, plane, startX >> 7, endZ >> 7)
                + sampleGrid(document, plane, endX >> 7, startZ >> 7)
                + sampleGrid(document, plane, endX >> 7, endZ >> 7);
        return (int) (sum >> 2);
    }

    private static List<Normal> calculateNormals(List<RawVertex> vertices,
                                                   ModelGeometryView geometry, boolean mirror) {
        List<Normal> normals = new ArrayList<>();
        for (int index = 0; index < vertices.size(); index++) normals.add(new Normal(0, 0, 0, 0));
        int[] indices = geometry.triangleIndices();
        int[] renderTypes = geometry.triangleRenderTypes();
        for (int face = 0; face < geometry.triangleCount(); face++) {
            int index = face * 3;
            int a = indices[index];
            int b = indices[index + 1];
            int c = indices[index + 2];
            if (mirror) {
                int swap = b;
                b = c;
                c = swap;
            }
            Normal normal = faceNormal(vertices.get(a), vertices.get(b), vertices.get(c));
            // ModelData.calculateVertexNormals() accumulates only render type
            // 0 faces. Type 1 is flat-lit from faceNormals, while hidden and
            // other special faces must not pull a wall corner's smooth normal
            // toward an unrelated face. Including them produces the visible
            // bright/dark seams at wall joins that the client does not have.
            int renderType = valueAt(renderTypes, face, 0);
            if (renderType == 0) {
                addNormal(normals, a, normal);
                addNormal(normals, b, normal);
                addNormal(normals, c, normal);
            }
        }
        // RuneLite/TSPS retain the accumulated components and the face-count
        // magnitude. Lighting divides by that magnitude; normalizing the
        // components here while retaining the count would double-attenuate
        // smooth multi-face models.
        return List.copyOf(normals);
    }

    private static void addNormal(List<Normal> normals, int index, Normal value) {
        Normal current = normals.get(index);
        normals.set(index, new Normal(current.x + value.x, current.y + value.y,
                current.z + value.z, current.magnitude + 1));
    }

    private static Normal faceNormal(RawVertex first, RawVertex second, RawVertex third) {
        int x1 = second.x - first.x;
        int y1 = second.y - first.y;
        int z1 = second.z - first.z;
        int x2 = third.x - first.x;
        int y2 = third.y - first.y;
        int z2 = third.z - first.z;
        int x = y1 * z2 - y2 * z1;
        int y = z1 * x2 - z2 * x1;
        int z = x1 * y2 - x2 * y1;
        while (Math.abs(x) > 8192 || Math.abs(y) > 8192 || Math.abs(z) > 8192) {
            x >>= 1;
            y >>= 1;
            z >>= 1;
        }
        int magnitude = Math.max(1, (int) Math.sqrt((long) x * x + (long) y * y + (long) z * z));
        return new Normal(x * 256 / magnitude, y * 256 / magnitude, z * 256 / magnitude, 1);
    }

    private int lightness(Normal normal, ObjectAppearanceView appearance) {
        int ambient = 64 + appearance.ambient();
        int contrast = 768 + appearance.contrast();
        int intensity = Math.max(1, (lighting.lightMagnitude() * contrast) >> 8);
        return ambient + (lighting.lightX() * normal.x + lighting.lightY() * normal.y
                + lighting.lightZ() * normal.z) / Math.max(1, intensity * Math.max(1, normal.magnitude));
    }

    /** Flat faces use the client face-normal denominator (1.5 × intensity). */
    private int flatLightness(Normal normal, ObjectAppearanceView appearance) {
        int ambient = 64 + appearance.ambient();
        int contrast = 768 + appearance.contrast();
        int intensity = Math.max(1, (lighting.lightMagnitude() * contrast) >> 8);
        int denominator = Math.max(1, intensity + (intensity >> 1));
        return ambient + (lighting.lightX() * normal.x + lighting.lightY() * normal.y
                + lighting.lightZ() * normal.z) / denominator;
    }

    private static int recolor(int color, Map<Integer, Integer> replacements) {
        return replacements.getOrDefault(color, color);
    }

    private static int retexture(int texture, Map<Integer, Integer> replacements) {
        return replacements.getOrDefault(texture, texture);
    }

    private static int[] toUnsignedColors(short[] colors) {
        int[] result = new int[colors.length];
        for (int index = 0; index < colors.length; index++) result[index] = colors[index] & 0xFFFF;
        return result;
    }

    private static int valueAt(int[] values, int index, int fallback) {
        return index < values.length ? values[index] : fallback;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float normalized(int value, List<RawVertex> vertices, boolean xAxis) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (RawVertex vertex : vertices) {
            int candidate = xAxis ? vertex.x : vertex.z;
            min = Math.min(min, candidate);
            max = Math.max(max, candidate);
        }
        return max == min ? 0.0f : (value - min) / (float) (max - min);
    }

    private static int[] bounds(List<ModelVertex> vertices) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (ModelVertex vertex : vertices) {
            minX = Math.min(minX, vertex.x());
            minY = Math.min(minY, vertex.y());
            minZ = Math.min(minZ, vertex.z());
            maxX = Math.max(maxX, vertex.x());
            maxY = Math.max(maxY, vertex.y());
            maxZ = Math.max(maxZ, vertex.z());
        }
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    private static final class PacketParts {
        private final List<ModelVertex> vertices = new ArrayList<>();
        private final List<ModelVertex> clientBoundsVertices = new ArrayList<>();
        private final List<VertexRange> clientRenderableRanges = new ArrayList<>();
        private final List<ClientRenderablePlacement> clientRenderablePlacements = new ArrayList<>();
        private final List<Integer> unskewedVertexY = new ArrayList<>();
        private boolean contourApplied;
        private boolean animationTransformed;
        private final List<ModelTriangle> triangles = new ArrayList<>();
        private final List<TextureTriangle> textureTriangles = new ArrayList<>();
        private final List<VertexRange> wallVariantRanges = new ArrayList<>();
    }

    private record VertexRange(int start, int end) {
    }

    private record RawVertex(int x, int y, int z) {
    }

    private record Normal(int x, int y, int z, int magnitude) {
    }
}
