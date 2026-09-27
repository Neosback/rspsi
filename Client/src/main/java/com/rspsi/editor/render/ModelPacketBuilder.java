package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ModelGeometryView;
import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.ObjectDefinitionResolver;
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
import com.rspsi.osrs.rules.loc.LocModelSelection;
import com.rspsi.osrs.rules.model.ModelTransformPipeline;

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
    /**
     * RuneLite-style thread-confined scratch: scene workers reuse primitive
     * transform/normal arrays across model builds and animation refreshes.
     */
    private static final ThreadLocal<ModelBuildWorkspace> BUILD_WORKSPACE =
            ThreadLocal.withInitial(ModelBuildWorkspace::new);

    private final DefinitionProvider definitions;
    private final ObjectDefinitionResolver definitionResolver;
    private final LightingProfile lighting;
    private final ScenePresentation presentation;

    /** Editor ghost transparency (model alpha: 0 opaque, 255 invisible). */
    static final int GHOST_TRANSPARENCY = 150;

    public ModelPacketBuilder(DefinitionProvider definitions) {
        this(definitions, LightingProfile.osrs());
    }

    public ModelPacketBuilder(DefinitionProvider definitions, LightingProfile lighting) {
        this(definitions, lighting, ScenePresentation.PARITY);
    }

    public ModelPacketBuilder(DefinitionProvider definitions, LightingProfile lighting,
                              ScenePresentation presentation) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.presentation = Objects.requireNonNull(presentation, "presentation");
        this.definitionResolver = new ObjectDefinitionResolver(this.definitions, presentation.varState());
        this.lighting = Objects.requireNonNull(lighting, "lighting");
    }

    /** Builds every available static model packet in document order. */
    public List<ModelRenderPacket> build(WorldDocument document) {
        return build(document, 0);
    }

    /** Builds animated model packets at an explicit client-cycle position. */
    public List<ModelRenderPacket> build(WorldDocument document, int clientCycle) {
        Objects.requireNonNull(document, "document");
        return build(document, clientCycle, 0, 0, document.width() - 1, document.length() - 1);
    }

    /**
     * Builds packets for locations anchored inside the inclusive document rectangle,
     * clamped to the document, with scene normal merging across that set.
     */
    public List<ModelRenderPacket> build(WorldDocument document, int clientCycle,
                                         int minX, int minY, int maxX, int maxY) {
        Objects.requireNonNull(document, "document");
        if (clientCycle < 0) throw new IllegalArgumentException("Client cycle cannot be negative");
        int fromX = Math.max(0, minX);
        int fromY = Math.max(0, minY);
        int toX = Math.min(document.width() - 1, maxX);
        int toY = Math.min(document.length() - 1, maxY);
        List<ModelRenderPacket> packets = new ArrayList<>();
        for (int plane = 0; plane < document.planes(); plane++) {
            for (int x = fromX; x <= toX; x++) {
                for (int y = fromY; y <= toY; y++) {
                    List<WorldObject> objects = document.tile(plane, x, y).objects();
                    if (objects.isEmpty()) continue;
                    if (objects.size() == 1) {
                        packets.addAll(buildScenePackets(
                                objects.get(0), document, clientCycle, 0));
                        continue;
                    }

                    java.util.Map<WorldObject, Integer> occurrences =
                            new java.util.HashMap<>(objects.size());
                    for (WorldObject object : objects) {
                        int occurrence = occurrences.getOrDefault(object, 0);
                        occurrences.put(object, occurrence + 1);
                        packets.addAll(buildScenePackets(
                                object, document, clientCycle, occurrence));
                    }
                }
            }
        }
        return List.copyOf(mergeNormals(packets, document));
    }

    /**
     * Rebuilds model packets anchored on one document tile.
     *
     * <p>This preserves the scene traversal semantics for multi-renderable
     * locations such as shape-8 wall decorations and duplicate object
     * occurrences. Cross-object normal merging is only complete for objects
     * anchored on this tile; callers that depend on scene-wide
     * {@code mergeNormals} must conservatively fall back to a full scene
     * build.</p>
     */
    public List<ModelRenderPacket> buildTile(WorldDocument document,
                                             TileCoordinate coordinate,
                                             int clientCycle) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(coordinate, "coordinate");
        if (clientCycle < 0) throw new IllegalArgumentException("Client cycle cannot be negative");
        if (!document.contains(coordinate.plane(), coordinate.x(), coordinate.y())) {
            throw new IllegalArgumentException("Tile is outside the model document: " + coordinate);
        }

        List<WorldObject> objects = document.tile(coordinate).objects();
        if (objects.isEmpty()) return List.of();
        List<ModelRenderPacket> packets = new ArrayList<>();
        if (objects.size() == 1) {
            packets.addAll(buildScenePackets(objects.get(0), document, clientCycle, 0));
            return List.copyOf(mergeNormals(packets, document));
        }

        java.util.Map<WorldObject, Integer> occurrences =
                new java.util.HashMap<>(objects.size());
        for (WorldObject object : objects) {
            int occurrence = occurrences.getOrDefault(object, 0);
            occurrences.put(object, occurrence + 1);
            packets.addAll(buildScenePackets(object, document, clientCycle, occurrence));
        }
        return List.copyOf(mergeNormals(packets, document));
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
        List<ModelRenderPacket> packets = resolved == null
                ? List.of() : scenePackets(object, document, resolved, occurrence);
        if (!packets.isEmpty() || !presentation.editorGhosts()) return packets;
        return editorGhostPackets(object, document, clientCycle, occurrence);
    }

    /**
     * Editor-only stand-ins for a placed loc that submitted no geometry: a
     * multiloc hidden in the current var state ghosts its first visible
     * state; anything else (authored-empty models, no model for the shape,
     * missing definition) gets a translucent footprint marker.
     */
    private List<ModelRenderPacket> editorGhostPackets(WorldObject object, WorldDocument document,
                                                       int clientCycle, int occurrence) {
        ObjectDefinitionResolver.Resolution resolution =
                definitionResolver.resolveEditorDisplay(object.id());
        if (resolution.status() == ObjectDefinitionResolver.Status.HIDDEN_IN_VAR_STATE) {
            Optional<ObjectDefinitionView> visible = definitionResolver.firstVisibleState(object.id());
            if (visible.isPresent()) {
                ResolvedModelBuild ghost = resolveBuild(object, document, clientCycle, visible.orElseThrow());
                if (ghost != null) {
                    List<ModelRenderPacket> packets = scenePackets(object, document, ghost, occurrence);
                    if (!packets.isEmpty()) {
                        return packets.stream().map(packet -> packet.asEditorGhost(GHOST_TRANSPARENCY)).toList();
                    }
                }
            }
        }
        RenderObject footprint = RenderObject.resolve(object,
                definitions.object(object.id()).orElse(null), null,
                definitions.objectCollision(object.id()).orElse(null), null);
        return List.of(EditorGhostMarker.build(object, definitions, occurrence,
                objectCenterHeight(document, object,
                        footprint.footprintWidth(), footprint.footprintLength())));
    }

    private List<ModelRenderPacket> scenePackets(WorldObject object, WorldDocument document,
                                                 ResolvedModelBuild resolved, int occurrence) {
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
        return resolveBuild(object, document, clientCycle, null);
    }

    /** {@code displayOverride} replaces the resolved display definition (editor ghosts only). */
    private ResolvedModelBuild resolveBuild(WorldObject object, WorldDocument document,
                                            int clientCycle, ObjectDefinitionView displayOverride) {
        Objects.requireNonNull(object, "object");
        Objects.requireNonNull(document, "document");
        if (clientCycle < 0) throw new IllegalArgumentException("Client cycle cannot be negative");
        ObjectDefinitionResolver.Resolution definitionResolution =
                definitionResolver.resolveEditorDisplay(object.id());
        if (displayOverride == null && !definitionResolution.resolved()) return null;
        if (definitionResolution.placedDefinition().isEmpty()) return null;
        ObjectDefinitionView placementDefinition =
                definitionResolution.placedDefinition().orElseThrow();
        ObjectDefinitionView objectDefinition = displayOverride != null
                ? displayOverride : definitionResolution.displayDefinition().orElseThrow();

        ObjectAppearanceView placementAppearance =
                definitions.objectAppearance(placementDefinition.id())
                        .orElseGet(ObjectAppearanceView::empty);
        ObjectAppearanceView appearance = definitions.objectAppearance(objectDefinition.id())
                .orElseGet(ObjectAppearanceView::empty);

        // FriendSystem.addObjects creates DynamicObject with the PLACED
        // definition's animation id. DynamicObject.getModel() then resolves
        // the transform and invokes getModelDynamic() on the DISPLAY
        // definition, so scale/recolor/retexture/contour come from the child
        // while animation state remains sourced from the placed definition.
        int animationId = placementAppearance.animationId();
        ResolvedAnimation animation = (presentation.objectAnimations() && clientCycle >= 0)
                ? resolveAnimation(animationId, clientCycle)
                : resolveDisabledAnimation();
        int decorDisplacement =
                wallDecorationDisplacement(object, placementAppearance, document);

        // The scene GameObject/collision rectangle is created by addObjects()
        // from the placed definition before DynamicObject resolves a transform.
        int sceneFootprintWidth = object.rotation() % 2 == 0
                ? placementDefinition.width() : placementDefinition.length();
        int sceneFootprintLength = object.rotation() % 2 == 0
                ? placementDefinition.length() : placementDefinition.width();

        // DynamicObject.getModel() resolves the transformed definition first and
        // passes THAT definition's rotated size to getModelDynamic() only as the
        // contourGround centre and reference height. The model is still drawn at
        // the scene GameObject's centre and height, built from the placed size, so
        // a 1x1 state of a 1x2 multiloc sits on the 1x2 footprint.
        int modelFootprintWidth = object.rotation() % 2 == 0
                ? objectDefinition.width() : objectDefinition.length();
        int modelFootprintLength = object.rotation() % 2 == 0
                ? objectDefinition.length() : objectDefinition.width();

        return new ResolvedModelBuild(objectDefinition, appearance, animationId,
                animation.frame(), animation.cachedSkeletal(), animation.skeleton(),
                animation.state(), decorDisplacement,
                sceneFootprintWidth, sceneFootprintLength,
                modelFootprintWidth, modelFootprintLength);
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
            for (int modelId : LocModelSelection.select(resolved.objectDefinition(), variant.sourceType())) {
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
                        variant, resolved.sceneFootprintWidth(), resolved.sceneFootprintLength(),
                        resolved.modelFootprintWidth(), resolved.modelFootprintLength());
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
                        resolved.sceneFootprintWidth(), resolved.sceneFootprintLength(),
                        object.rotation(), modelDrawOrientation)
                : GameObjectSceneMetadata.none();
        SceneObjectIdentity sceneObjectIdentity = SceneObjectIdentity.of(
                object, resolved.sceneFootprintWidth(), resolved.sceneFootprintLength(), occurrence);
        int placementHeight = objectCenterHeight(
                document, object, resolved.sceneFootprintWidth(), resolved.sceneFootprintLength());
        ModelContourContract contourContract = resolved.appearance().contourGroundType() >= 0
                ? ModelContourContract.of(
                        resolved.appearance().contourGroundType(),
                        resolved.appearance().contourGroundParameter(),
                        objectCenterHeight(document, object,
                                resolved.modelFootprintWidth(), resolved.modelFootprintLength()),
                        parts.contourApplied, parts.unskewedVertexY)
                : ModelContourContract.none();
        ModelRenderPacket packet = new ModelRenderPacket(
                new TileCoordinate(object.plane(), object.x(), object.y()), object.id(),
                object.category(), parts.vertices, parts.triangles, parts.textureTriangles,
                resolved.animationId(), bounds[0], bounds[1], bounds[2],
                bounds[3], bounds[4], bounds[5], resolved.animationId() >= 0, false,
                placementHeight,
                object.shape().map(shape -> shape.id() >= 12 && shape.id() <= 21).orElse(false),
                GpuDrawCommand.RenderMode.DEFAULT, presentation, sceneMetadata,
                clientRenderableBounds, parts.clientRenderablePlacements,
                contourContract,
                resolved.animationState().withTransformed(parts.animationTransformed),
                sceneObjectIdentity);
        return Optional.of(sceneMergesNormals(object.id())
                ? mergeWallVariantNormals(packet, parts.wallVariantRanges) : packet);
    }

    private record ResolvedModelBuild(ObjectDefinitionView objectDefinition,
                                      ObjectAppearanceView appearance,
                                      int animationId,
                                      Optional<AnimationFrameView> animation,
                                      Optional<CachedSkeletalAnimationView> cachedSkeletal,
                                      Optional<SkeletonDefinitionView> animationSkeleton,
                                      ModelAnimationState animationState,
                                      int decorDisplacement,
                                      int sceneFootprintWidth,
                                      int sceneFootprintLength,
                                      int modelFootprintWidth,
                                      int modelFootprintLength) {
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

    private ResolvedAnimation resolveDisabledAnimation() {
        return new ResolvedAnimation(Optional.empty(), Optional.empty(), Optional.empty(),
                ModelAnimationState.none());
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
     * Whether the client's scene normal merge touches this location.
     *
     * <p>{@code FriendSystem.addObjects} builds a location through {@code getEntity}
     * only when its placed definition has {@code animationId == -1} and no
     * {@code transforms}; anything else becomes a {@code DynamicObject}.
     * {@code Scene.method5585} merges only {@code ModelData} renderables, so animated
     * locations and multilocs never merge, whatever their opcode-22 flag says.</p>
     */
    private boolean sceneMergesNormals(int placedObjectId) {
        Optional<ObjectDefinitionView> placed =
                definitionResolver.resolveEditorDisplay(placedObjectId).placedDefinition();
        if (placed.isEmpty() || placed.get().hasTransforms()) return false;
        ObjectAppearanceView appearance = definitions.objectAppearance(placed.get().id())
                .orElseGet(ObjectAppearanceView::empty);
        return appearance.animationId() == -1 && appearance.mergeNormals();
    }

    /**
     * Refreshes one tile's packets for a new client cycle.
     *
     * <p>Only animated locations change with the cycle. They never take part in the
     * scene normal merge ({@link #sceneMergesNormals}), so a rebuilt animated packet
     * is complete without its neighbours. Static packets on the same tile are taken
     * from {@code previous}, which still carries normals merged across tile borders.
     * Both lists must be in the same coordinate space.</p>
     */
    public static List<ModelRenderPacket> keepStaticPackets(List<ModelRenderPacket> rebuilt,
                                                            List<ModelRenderPacket> previous) {
        if (previous == null || previous.isEmpty()) return rebuilt;
        Map<SceneObjectIdentity, java.util.ArrayDeque<ModelRenderPacket>> staticByIdentity =
                new java.util.HashMap<>();
        for (ModelRenderPacket packet : previous) {
            if (packet.animationState().active()) continue;
            staticByIdentity.computeIfAbsent(packet.sceneObjectIdentity(),
                    ignored -> new java.util.ArrayDeque<>()).add(packet);
        }
        if (staticByIdentity.isEmpty()) return rebuilt;
        List<ModelRenderPacket> result = new ArrayList<>(rebuilt.size());
        for (ModelRenderPacket packet : rebuilt) {
            java.util.ArrayDeque<ModelRenderPacket> kept = packet.animationState().active()
                    ? null : staticByIdentity.get(packet.sceneObjectIdentity());
            ModelRenderPacket previousPacket = kept == null ? null : kept.poll();
            result.add(previousPacket != null ? previousPacket : packet);
        }
        return List.copyOf(result);
    }

    /** World units a wall decoration is lifted off its wall, toward the tile interior. */
    static final int DECORATION_NUDGE = 2;

    /**
     * Render-only offset of a wall-decoration model (source type 4) away from its wall.
     *
     * <p>The client draws a tile's decorations after its walls in painter's order, so a
     * decoration always covers its wall even where the model dips into the wall surface
     * (Lumbridge shutter slats sit 1 unit inside the wall face). A depth-tested renderer
     * cannot rely on submission order, and a view-space depth bias fades at grazing angles,
     * which made the result depend on camera angle and wall rotation. Moving the geometry
     * itself a couple of units along the client's own displacement directions
     * ({@code Tiles} straight / diagonal tables, as used for shapes 5, 6 and 8) keeps the
     * decoration in front of its wall at every angle and every rotation.</p>
     */
    static int decorationNudge(int modelRotation, boolean xAxis) {
        int rotation = modelRotation & 3;
        if (modelRotation < 4) {
            return DECORATION_NUDGE * (xAxis
                    ? com.rspsi.osrs.rules.loc.WallDecorationRules.DECOR_DISPLACEMENT_X[rotation]
                    : com.rspsi.osrs.rules.loc.WallDecorationRules.DECOR_DISPLACEMENT_Z[rotation]);
        }
        return DECORATION_NUDGE * (xAxis
                ? com.rspsi.osrs.rules.loc.WallDecorationRules.DIAGONAL_DISPLACEMENT_X[rotation]
                : com.rspsi.osrs.rules.loc.WallDecorationRules.DIAGONAL_DISPLACEMENT_Z[rotation]);
    }

    private ObjectAppearanceView resolvedAppearance(int placedObjectId) {
        ObjectDefinitionResolver.Resolution resolution =
                definitionResolver.resolveEditorDisplay(placedObjectId);
        return resolution.displayDefinition()
                .flatMap(definition -> definitions.objectAppearance(definition.id()))
                .orElseGet(ObjectAppearanceView::empty);
    }

    private int wallDecorationDisplacement(WorldObject decoration,
                                           ObjectAppearanceView ownAppearance,
                                           WorldDocument document) {
        return com.rspsi.osrs.rules.loc.WallDecorationRules.resolveDisplacement(decoration, ownAppearance, document, definitions);
    }

    private void append(PacketParts parts, WorldObject object, ObjectAppearanceView appearance,
                        ModelGeometryView geometry,
                        WorldDocument document, WallRules.LocModelVariant variant,
                        int footprintWidth, int footprintLength,
                        int contourWidth, int contourLength) {
        int vertexOffset = parts.vertices.size();
        int vertexCount = geometry.vertexCount();
        int[] positions = geometry.vertexPositions();
        ModelBuildWorkspace workspace = BUILD_WORKSPACE.get();
        workspace.prepare(vertexCount);

        // The client's getModelData mirrors via isRotated XOR (rotationParam
        // > 3), applied uniformly for every shape through the rotation value
        // passed to it - not an OR gated to sourceType==2.
        boolean mirror = appearance.rotated() ^ (variant.rotation() > 3);
        int centerX = footprintWidth * 64;
        int centerZ = footprintLength * 64;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        // Wall decorations are painted over their wall by the client's painter's order;
        // a depth buffer needs the geometry itself off the wall plane (see decorationNudge).
        int nudgeX = variant.sourceType() == 4 ? decorationNudge(variant.rotation(), true) : 0;
        int nudgeZ = variant.sourceType() == 4 ? decorationNudge(variant.rotation(), false) : 0;
        for (int index = 0; index < vertexCount; index++) {
            int offset = index * 3;
            int x = positions[offset];
            int y = positions[offset + 1];
            int z = positions[offset + 2];
            if (mirror) {
                z = -z;
            }
            if (variant.sourceType() == 4 && variant.rotation() > 3) {
                long diagonal = ModelTransformPipeline.rotateJagexAnglePacked(x, z, 256);
                x = ModelTransformPipeline.unpackX(diagonal) + 45;
                z = ModelTransformPipeline.unpackZ(diagonal) - 45;
            }
            long rotated = ModelTransformPipeline.rotateQuarterTurnPacked(
                    x, z, variant.rotation());
            x = ModelTransformPipeline.unpackX(rotated);
            z = ModelTransformPipeline.unpackZ(rotated);
            x = x * appearance.scaleX() / 128;
            y = y * appearance.scaleY() / 128;
            z = z * appearance.scaleZ() / 128;
            x += appearance.offsetX();
            y += appearance.offsetY();
            z += appearance.offsetZ();
            if (variant.rotateAfterScale()) {
                long diagonal = ModelTransformPipeline.rotateJagexAnglePacked(x, z, 256);
                x = ModelTransformPipeline.unpackX(diagonal);
                z = ModelTransformPipeline.unpackZ(diagonal);
            }
            x += centerX + variant.decorX() + nudgeX;
            z += centerZ + variant.decorZ() + nudgeZ;
            workspace.setVertex(index, x, y, z);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }

        // Pre-contour normals are accumulated directly into reusable primitive
        // arrays. Final ModelVertex records remain immutable packet output.
        calculateNormals(workspace, vertexCount, geometry, mirror);
        short[] colors = geometry.triangleColors();
        int[] alphas = geometry.triangleAlphas();
        int[] textures = geometry.triangleTextures();
        int[] renderTypes = geometry.triangleRenderTypes();
        int[] priorities = geometry.triangleRenderPriorities();
        int[] depthBias = geometry.triangleDepthBias();
        int renderPriority = definitions.model(geometry.id())
                .map(view -> view.renderPriority()).orElse(0);
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
            int alpha = rawAlpha & 0xFF;
            if (renderType == -1) renderType = 2;
            if (alpha == 255) renderType = 2;
            int texture = valueAt(textures, face, -1);
            int color = recolor(unsignedValueAt(colors, face, 0), appearance.recolors());
            int priority = clamp(valueAt(priorities, face, renderPriority), 0, 255);
            int bias = clamp(valueAt(depthBias, face, 0), 0, 255);

            workspace.computeFaceNormal(a, b, c);
            ModelFaceColorContract.LitFace lit = ModelFaceColorContract.shade(
                    color, texture >= 0, renderType,
                    lightness(workspace.normalX(a), workspace.normalY(a),
                            workspace.normalZ(a), workspace.normalMagnitude(a), appearance),
                    lightness(workspace.normalX(b), workspace.normalY(b),
                            workspace.normalZ(b), workspace.normalMagnitude(b), appearance),
                    lightness(workspace.normalX(c), workspace.normalY(c),
                            workspace.normalZ(c), workspace.normalMagnitude(c), appearance),
                    flatLightness(workspace.faceNormalX(), workspace.faceNormalY(),
                            workspace.faceNormalZ(), appearance));
            parts.triangles.add(new ModelTriangle(vertexOffset + a, vertexOffset + b,
                    vertexOffset + c, lit.colorA(), lit.colorB(), lit.colorC(),
                    texture < 0 ? -1 : retexture(texture, appearance.retextures()),
                    alpha, priority, renderType, uv.u0, uv.v0, uv.u1, uv.v1, uv.u2, uv.v2,
                    color, bias));
        }

        boolean retainUnskewedY = appearance.contourGroundType() >= 0;
        if (retainUnskewedY) {
            workspace.captureUnskewedY(vertexCount);
            if (applyContour(document, object, footprintWidth, footprintLength,
                    contourWidth, contourLength,
                    workspace, vertexCount, appearance, variant.decorX() + nudgeX, variant.decorZ() + nudgeZ)) {
                parts.contourApplied = true;
            }
        }

        for (int vertex = 0; vertex < vertexCount; vertex++) {
            int x = workspace.x(vertex);
            int y = workspace.y(vertex);
            int z = workspace.z(vertex);
            if (retainUnskewedY) {
                parts.unskewedVertexY.add(workspace.unskewedY(vertex));
            }
            parts.vertices.add(new ModelVertex(x, y, z,
                    workspace.normalX(vertex), workspace.normalY(vertex),
                    workspace.normalZ(vertex), workspace.normalMagnitude(vertex),
                    normalized(x, minX, maxX),
                    normalized(z, minZ, maxZ)));
            parts.clientBoundsVertices.add(new ModelVertex(
                    x - centerX - variant.decorX() - nudgeX, y,
                    z - centerZ - variant.decorZ() - nudgeZ,
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

    /**
     * Client {@code Scene.lightScene} normal sharing, applied once all objects of one
     * document are placed.
     *
     * <p>Only unlit merge locations ({@link #sceneMergesNormals}) take part, and wall
     * decorations never do. In the client's plane/x/y order, each boundary object, game
     * object and floor decoration merges pairwise ({@code ModelData.mergeNormals}) with the
     * neighbours that are still unlit: on its own plane the east column, north row and
     * south-east tiles, hiding faces whose three vertices are all shared; on the plane above,
     * the whole surrounding block, without hiding. A game object is listed on every tile it
     * covers, so a large neighbour can merge more than once, exactly as in the client.
     * Heights compare each model's local y offset by the average height of the tile it was
     * found on ({@code Scene.tileHeightDifference}).</p>
     */
    private List<ModelRenderPacket> mergeNormals(List<ModelRenderPacket> packets,
                                                 WorldDocument document) {
        int count = packets.size();
        SceneMergeModel[] models = new SceneMergeModel[count];
        Map<Long, List<SceneMergeModel>> boundaries = new java.util.HashMap<>();
        Map<Long, List<SceneMergeModel>> gameObjects = new java.util.HashMap<>();
        Map<Long, List<SceneMergeModel>> floorDecorations = new java.util.HashMap<>();
        List<SceneMergeModel> order = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            ModelRenderPacket packet = packets.get(index);
            int role = mergeRole(packet);
            if (role == MERGE_NONE || !sceneMergesNormals(packet.objectId())) continue;
            SceneMergeModel model = new SceneMergeModel(index, packet, role);
            models[index] = model;
            order.add(model);
            TileCoordinate anchor = packet.anchor();
            if (role == MERGE_BOUNDARY) {
                boundaries.computeIfAbsent(tileKey(anchor.plane(), anchor.x(), anchor.y()),
                        ignored -> new ArrayList<>(2)).add(model);
            } else if (role == MERGE_FLOOR) {
                floorDecorations.computeIfAbsent(tileKey(anchor.plane(), anchor.x(), anchor.y()),
                        ignored -> new ArrayList<>(1)).add(model);
            } else {
                for (int dx = 0; dx < model.sizeX; dx++) {
                    for (int dy = 0; dy < model.sizeY; dy++) {
                        gameObjects.computeIfAbsent(
                                tileKey(anchor.plane(), anchor.x() + dx, anchor.y() + dy),
                                ignored -> new ArrayList<>(2)).add(model);
                    }
                }
            }
        }
        if (order.isEmpty()) return packets;
        order.sort(java.util.Comparator.<SceneMergeModel>comparingInt(model -> model.plane)
                .thenComparingInt(model -> model.x)
                .thenComparingInt(model -> model.y)
                .thenComparingInt(model -> model.role));

        MergeMarkers markers = new MergeMarkers();
        for (SceneMergeModel model : order) {
            if (model.role == MERGE_FLOOR) {
                mergeFloorDecorationModels(model, document, floorDecorations, markers);
            } else {
                if (model.role == MERGE_BOUNDARY) {
                    long sameTileKey = tileKey(model.plane, model.x, model.y);
                    for (SceneMergeModel other : boundaries.getOrDefault(sameTileKey, List.of())) {
                        if (!other.lit && other != model) {
                            pairMerge(model, other, 0, false, markers);
                        }
                    }
                }
                mergeSceneModels(model, document, boundaries, gameObjects, markers);
            }
            model.lit = true;
        }

        @SuppressWarnings("unchecked")
        List<ModelVertex>[] mergedVertices = new List[count];
        @SuppressWarnings("unchecked")
        List<ModelTriangle>[] packetTriangles = new List[count];
        boolean[] changed = new boolean[count];
        for (int index = 0; index < count; index++) {
            SceneMergeModel model = models[index];
            if (model == null || !model.changed()) continue;
            changed[index] = true;
            mergedVertices[index] = model.mergedVertices();
            packetTriangles[index] = model.triangles();
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

    private static final int MERGE_NONE = -1;
    private static final int MERGE_BOUNDARY = 0;
    private static final int MERGE_GAME_OBJECT = 1;
    private static final int MERGE_FLOOR = 2;

    /** Which client scene slot a packet occupies for lightScene: walls, game objects, floor decor. */
    private static int mergeRole(ModelRenderPacket packet) {
        SceneObjectIdentity identity = packet.sceneObjectIdentity();
        if (!identity.present()) return MERGE_NONE;
        int shape = identity.shape();
        if (shape >= 0 && shape <= 3) return MERGE_BOUNDARY;
        if (shape >= 9 && shape <= 21) return MERGE_GAME_OBJECT;
        if (shape == 22) return MERGE_FLOOR;
        return MERGE_NONE;
    }

    private static long tileKey(int plane, int x, int y) {
        return ((long) plane << 42) | ((long) (x & 0x1FFFFF) << 21) | (y & 0x1FFFFF);
    }

    /** Scene.averageTileHeight: the mean of a tile's four corner heights. */
    private static int averageTileHeight(WorldDocument document, int plane, int x, int y) {
        return (sampleGrid(document, plane, x + 1, y + 1) + sampleGrid(document, plane, x, y)
                + sampleGrid(document, plane, x, y + 1) + sampleGrid(document, plane, x + 1, y)) / 4;
    }

    /** Scene.mergeSceneModels for a boundary object (1x1) or a game object (its scene size). */
    private void mergeSceneModels(SceneMergeModel model, WorldDocument document,
                                  Map<Long, List<SceneMergeModel>> boundaries,
                                  Map<Long, List<SceneMergeModel>> gameObjects,
                                  MergeMarkers markers) {
        boolean hideOccluded = true;
        int minX = model.x;
        int maxX = model.x + model.sizeX;
        int minY = model.y - 1;
        int maxY = model.y + model.sizeY;
        int ownHeight = averageTileHeight(document, model.plane, model.x, model.y);
        for (int plane = model.plane; plane <= model.plane + 1; plane++) {
            if (plane == document.planes()) continue;
            for (int x = minX; x <= maxX; x++) {
                if (x < 0 || x >= document.width()) continue;
                for (int y = minY; y <= maxY; y++) {
                    if (y < 0 || y >= document.length()) continue;
                    if (hideOccluded && !(x >= maxX || y >= maxY || (y < model.y && x != model.x))) {
                        continue;
                    }
                    int heightDelta = averageTileHeight(document, plane, x, y) - ownHeight;
                    long key = tileKey(plane, x, y);
                    for (SceneMergeModel other : boundaries.getOrDefault(key, List.of())) {
                        if (!other.lit && other != model) pairMerge(model, other, heightDelta, hideOccluded, markers);
                    }
                    for (SceneMergeModel other : gameObjects.getOrDefault(key, List.of())) {
                        if (!other.lit && other != model) pairMerge(model, other, heightDelta, hideOccluded, markers);
                    }
                }
            }
            minX--;
            hideOccluded = false;
        }
    }

    /** Scene.mergeFloorDecorationModels: the east column and north tile, hiding shared faces. */
    private void mergeFloorDecorationModels(SceneMergeModel model, WorldDocument document,
                                            Map<Long, List<SceneMergeModel>> floorDecorations,
                                            MergeMarkers markers) {
        int ownHeight = averageTileHeight(document, model.plane, model.x, model.y);
        for (int x = model.x; x <= model.x + 1; x++) {
            if (x < 0 || x >= document.width()) continue;
            for (int y = model.y - 1; y <= model.y + 1; y++) {
                if (y < 0 || y >= document.length() || !(x >= model.x + 1 || y >= model.y + 1)) continue;
                int heightDelta = averageTileHeight(document, model.plane, x, y) - ownHeight;
                for (SceneMergeModel other : floorDecorations.getOrDefault(
                        tileKey(model.plane, x, y), List.of())) {
                    if (!other.lit && other != model) pairMerge(model, other, heightDelta, true, markers);
                }
            }
        }
    }

    /**
     * ModelData.mergeNormals for one pair: every vertex of {@code a} at a position of
     * {@code b} adds the other's original normal to both. With at least three matches and
     * {@code hideOccluded}, faces whose three vertices all matched are hidden in both.
     */
    private static void pairMerge(SceneMergeModel a, SceneMergeModel b, int heightDelta,
                                  boolean hideOccluded, MergeMarkers markers) {
        int generation = markers.next();
        int[] markA = a.markers();
        int[] markB = b.markers();
        long[] bKeys = b.sortedKeys();
        int[] bVertices = b.sortedVertices();
        int matches = 0;
        List<ModelVertex> aVertices = a.packet.vertices();
        List<ModelVertex> bList = b.packet.vertices();
        int ax = a.x * 128;
        int az = a.y * 128;
        for (int vertexIndex = 0; vertexIndex < aVertices.size(); vertexIndex++) {
            ModelVertex vertex = aVertices.get(vertexIndex);
            if (vertex.normalMagnitude() == 0) continue;
            long key = positionKey(ax + vertex.x(), vertex.y() - heightDelta, az + vertex.z());
            int at = java.util.Arrays.binarySearch(bKeys, key);
            if (at < 0) {
                int insert = -at - 1;
                int best = -1;
                int bestDist = 3;
                if (insert < bKeys.length && Math.abs(bKeys[insert] - key) <= 2) {
                    best = insert;
                    bestDist = (int) Math.abs(bKeys[insert] - key);
                }
                if (insert > 0 && Math.abs(bKeys[insert - 1] - key) < bestDist) {
                    best = insert - 1;
                }
                if (best >= 0) {
                    key = bKeys[best];
                    at = best;
                }
            }
            if (at < 0) continue;
            while (at > 0 && bKeys[at - 1] == key) at--;
            for (; at < bKeys.length && bKeys[at] == key; at++) {
                int other = bVertices[at];
                ModelVertex source = bList.get(other);
                a.accumulate(vertexIndex, source);
                b.accumulate(other, vertex);
                matches++;
                markA[vertexIndex] = generation;
                markB[other] = generation;
            }
        }
        if (matches >= 3 && hideOccluded) {
            a.hideSharedFaces(markA, generation);
            b.hideSharedFaces(markB, generation);
        }
    }

    private static long positionKey(int x, int y, int z) {
        return ((long) (x + (1 << 20)) << 42) | ((long) (z + (1 << 20)) << 21) | ((y + (1 << 20)) & 0x1FFFFF);
    }

    /** Generation counter for per-pair vertex markers, like ModelData.mergeVertexMarkerGeneration. */
    private static final class MergeMarkers {
        private int generation;

        int next() {
            return ++generation;
        }
    }

    /** One unlit location during the lightScene pass; normals accumulate from originals. */
    private static final class SceneMergeModel {
        final int index;
        final ModelRenderPacket packet;
        final int role;
        final int plane;
        final int x;
        final int y;
        final int sizeX;
        final int sizeY;
        boolean lit;
        private int[] normals;
        private boolean[] hidden;
        private int[] markers;
        private long[] sortedKeys;
        private int[] sortedVertices;

        SceneMergeModel(int index, ModelRenderPacket packet, int role) {
            this.index = index;
            this.packet = packet;
            this.role = role;
            TileCoordinate anchor = packet.anchor();
            this.plane = anchor.plane();
            this.x = anchor.x();
            this.y = anchor.y();
            SceneObjectIdentity identity = packet.sceneObjectIdentity();
            this.sizeX = role == MERGE_GAME_OBJECT ? Math.max(1, identity.footprintWidth()) : 1;
            this.sizeY = role == MERGE_GAME_OBJECT ? Math.max(1, identity.footprintLength()) : 1;
        }

        int[] markers() {
            if (markers == null) markers = new int[packet.vertices().size()];
            return markers;
        }

        /** Vertex positions in plane-local world units, sorted for binary search. */
        long[] sortedKeys() {
            if (sortedKeys == null) buildIndex();
            return sortedKeys;
        }

        int[] sortedVertices() {
            if (sortedVertices == null) buildIndex();
            return sortedVertices;
        }

        private void buildIndex() {
            List<ModelVertex> vertices = packet.vertices();
            int usable = 0;
            for (ModelVertex vertex : vertices) {
                if (vertex.normalMagnitude() != 0) usable++;
            }
            long[] keys = new long[usable];
            int[] order = new int[usable];
            int cursor = 0;
            for (int index = 0; index < vertices.size(); index++) {
                ModelVertex vertex = vertices.get(index);
                if (vertex.normalMagnitude() == 0) continue;
                keys[cursor] = positionKey(x * 128 + vertex.x(), vertex.y(), y * 128 + vertex.z());
                order[cursor++] = index;
            }
            sortPaired(keys, order, 0, usable - 1);
            sortedKeys = keys;
            sortedVertices = order;
        }

        void accumulate(int vertexIndex, ModelVertex source) {
            if (normals == null) {
                List<ModelVertex> vertices = packet.vertices();
                normals = new int[vertices.size() * 4];
                for (int index = 0; index < vertices.size(); index++) {
                    ModelVertex vertex = vertices.get(index);
                    normals[index * 4] = vertex.normalX();
                    normals[index * 4 + 1] = vertex.normalY();
                    normals[index * 4 + 2] = vertex.normalZ();
                    normals[index * 4 + 3] = vertex.normalMagnitude();
                }
            }
            int base = vertexIndex * 4;
            normals[base] += source.normalX();
            normals[base + 1] += source.normalY();
            normals[base + 2] += source.normalZ();
            normals[base + 3] += source.normalMagnitude();
        }

        void hideSharedFaces(int[] marks, int generation) {
            List<ModelTriangle> triangles = packet.triangles();
            for (int face = 0; face < triangles.size(); face++) {
                ModelTriangle triangle = triangles.get(face);
                if (marks[triangle.a()] == generation && marks[triangle.b()] == generation
                        && marks[triangle.c()] == generation) {
                    if (hidden == null) hidden = new boolean[triangles.size()];
                    hidden[face] = true;
                }
            }
        }

        boolean changed() {
            return normals != null || hidden != null;
        }

        List<ModelVertex> mergedVertices() {
            List<ModelVertex> vertices = packet.vertices();
            if (normals == null) return vertices;
            List<ModelVertex> result = new ArrayList<>(vertices.size());
            for (int index = 0; index < vertices.size(); index++) {
                ModelVertex vertex = vertices.get(index);
                int base = index * 4;
                result.add(new ModelVertex(vertex.x(), vertex.y(), vertex.z(),
                        normals[base], normals[base + 1], normals[base + 2], normals[base + 3],
                        vertex.u(), vertex.v()));
            }
            return result;
        }

        List<ModelTriangle> triangles() {
            List<ModelTriangle> triangles = packet.triangles();
            if (hidden == null) return triangles;
            List<ModelTriangle> result = new ArrayList<>(triangles);
            for (int face = 0; face < hidden.length; face++) {
                if (hidden[face] && result.get(face).renderType() != 2) {
                    result.set(face, result.get(face).withRenderType(2));
                }
            }
            return result;
        }
    }

    /** Sorts {@code keys[low..high]} ascending, applying the same permutation to {@code values}. */
    private static void sortPaired(long[] keys, int[] values, int low, int high) {
        while (low < high) {
            if (high - low < 16) {
                for (int i = low + 1; i <= high; i++) {
                    long key = keys[i];
                    int value = values[i];
                    int j = i - 1;
                    while (j >= low && keys[j] > key) {
                        keys[j + 1] = keys[j];
                        values[j + 1] = values[j];
                        j--;
                    }
                    keys[j + 1] = key;
                    values[j + 1] = value;
                }
                return;
            }
            long pivot = keys[(low + high) >>> 1];
            int i = low;
            int j = high;
            while (i <= j) {
                while (keys[i] < pivot) i++;
                while (keys[j] > pivot) j--;
                if (i <= j) {
                    long key = keys[i]; keys[i] = keys[j]; keys[j] = key;
                    int value = values[i]; values[i] = values[j]; values[j] = value;
                    i++;
                    j--;
                }
            }
            // Recurse into the smaller side to bound stack depth.
            if (j - low < high - i) {
                sortPaired(keys, values, low, j);
                low = i;
            } else {
                sortPaired(keys, values, i, high);
                high = j;
            }
        }
    }

    private ModelRenderPacket relight(ModelRenderPacket packet, List<ModelVertex> vertices) {
        return relight(packet, vertices, packet.triangles());
    }

    private ModelRenderPacket relight(ModelRenderPacket packet, List<ModelVertex> vertices,
                                      List<ModelTriangle> sourceTriangles) {
        ObjectAppearanceView appearance = resolvedAppearance(packet.objectId());
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

    private record PositionKey(int x, int y, int z) {
    }

    private record VertexReference(int packetIndex, int vertexIndex, ModelVertex vertex) {
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
    private boolean applyContour(WorldDocument document, WorldObject object,
                                 int footprintWidth, int footprintLength,
                                 int contourWidth, int contourLength,
                                 ModelBuildWorkspace workspace, int vertexCount,
                                 ObjectAppearanceView appearance,
                                 int decorX, int decorZ) {
        int type = appearance.contourGroundType();
        int parameter = appearance.contourGroundParameter();
        if (type < 0) return false;
        boolean usesAbovePlane = type == 4 || type == 5;
        if (usesAbovePlane && document.planes() <= object.plane() + 1) return false;

        int centerX = footprintWidth * 64;
        int centerZ = footprintLength * 64;
        int downwardHeight = 0;
        long radiusSquared = 0L;
        int modelMinY = Integer.MAX_VALUE;
        int modelMaxY = Integer.MIN_VALUE;
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            int localX = workspace.x(vertex) - centerX - decorX;
            int localZ = workspace.z(vertex) - centerZ - decorZ;
            int y = workspace.y(vertex);
            if (-y > downwardHeight) downwardHeight = -y;
            long squared = (long) localX * localX + (long) localZ * localZ;
            if (squared > radiusSquared) radiusSquared = squared;
            modelMinY = Math.min(modelMinY, y);
            modelMaxY = Math.max(modelMaxY, y);
        }
        downwardHeight = Math.max(1, downwardHeight);
        int xzRadius = (int) (Math.sqrt((double) radiusSquared) + 0.99D);
        int verticalSpan = Math.max(1, modelMaxY - modelMinY);

        // Vertices are local to the placed footprint; contourGround samples around the
        // transformed definition's own centre (DynamicObject.getModel var10/var11).
        int contourCenterX = object.x() * 128 + contourWidth * 64;
        int contourCenterZ = object.y() * 128 + contourLength * 64;
        int plane = object.plane();
        int minWorldX = contourCenterX - xzRadius;
        int maxWorldX = contourCenterX + xzRadius;
        int minWorldZ = contourCenterZ - xzRadius;
        int maxWorldZ = contourCenterZ + xzRadius;
        if (minWorldX < 0 || (maxWorldX + 128) >> 7 >= document.width()
                || minWorldZ < 0 || (maxWorldZ + 128) >> 7 >= document.length()) {
            return false;
        }

        int sceneHeight = objectCenterHeight(
                document, object, contourWidth, contourLength);
        int startTileX = minWorldX >> 7;
        int endTileX = (maxWorldX + 127) >> 7;
        int startTileZ = minWorldZ >> 7;
        int endTileZ = (maxWorldZ + 127) >> 7;
        if (!usesAbovePlane
                && sampleGrid(document, plane, startTileX, startTileZ) == sceneHeight
                && sampleGrid(document, plane, endTileX, startTileZ) == sceneHeight
                && sampleGrid(document, plane, startTileX, endTileZ) == sceneHeight
                && sampleGrid(document, plane, endTileX, endTileZ) == sceneHeight) {
            return false;
        }

        for (int vertex = 0; vertex < vertexCount; vertex++) {
            int x = workspace.x(vertex);
            int y = workspace.y(vertex);
            int z = workspace.z(vertex);
            int localX = x - centerX - decorX;
            int localZ = z - centerZ - decorZ;
            int worldX = contourCenterX + localX;
            int worldZ = contourCenterZ + localZ;
            int fractionX = worldX & 127;
            int fractionZ = worldZ & 127;
            int tileX = worldX >> 7;
            int tileZ = worldZ >> 7;
            int south = contourBlend(sampleGrid(document, plane, tileX, tileZ),
                    sampleGrid(document, plane, tileX + 1, tileZ), fractionX);
            int north = contourBlend(sampleGrid(document, plane, tileX, tileZ + 1),
                    sampleGrid(document, plane, tileX + 1, tileZ + 1), fractionX);
            int height = contourBlend(south, north, fractionZ);
            int newY;
            if ((type == 1 || type == 2) && parameter > 0) {
                int yRatio = ((-y) << 16) / downwardHeight;
                if (yRatio < parameter) {
                    newY = y + (parameter - yRatio) * (height - sceneHeight) / parameter;
                } else {
                    newY = y;
                }
            } else if (type == 3) {
                int delta = height - sceneHeight;
                if (parameter != 0) {
                    int limit = Math.abs(parameter);
                    delta = Math.max(-limit, Math.min(limit, delta));
                }
                newY = y + delta;
            } else if (type == 4) {
                int aboveHeight = contourSampleAbove(document, plane, worldX, worldZ,
                        fractionX, fractionZ);
                newY = y + aboveHeight - sceneHeight + verticalSpan;
            } else if (type == 5) {
                int aboveHeight = contourSampleAbove(document, plane, worldX, worldZ,
                        fractionX, fractionZ);
                int deltaHeight = height - aboveHeight;
                newY = (((y << 8) / verticalSpan) * deltaHeight >> 8)
                        - (sceneHeight - height);
            } else {
                newY = y + height - sceneHeight;
            }
            workspace.setY(vertex, newY);
        }
        return true;
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

    private static void calculateNormals(ModelBuildWorkspace workspace,
                                         int vertexCount,
                                         ModelGeometryView geometry,
                                         boolean mirror) {
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
            workspace.computeFaceNormal(a, b, c);
            int renderType = valueAt(renderTypes, face, 0);
            if (renderType == 0) {
                workspace.accumulateFaceNormal(a);
                workspace.accumulateFaceNormal(b);
                workspace.accumulateFaceNormal(c);
            }
        }
    }

    private static Normal faceNormal(ModelVertex first, ModelVertex second, ModelVertex third) {
        int x1 = second.x() - first.x();
        int y1 = second.y() - first.y();
        int z1 = second.z() - first.z();
        int x2 = third.x() - first.x();
        int y2 = third.y() - first.y();
        int z2 = third.z() - first.z();
        int x = y1 * z2 - y2 * z1;
        int y = z1 * x2 - z2 * x1;
        int z = x1 * y2 - x2 * y1;
        while (Math.abs(x) > 8192 || Math.abs(y) > 8192 || Math.abs(z) > 8192) {
            x >>= 1;
            y >>= 1;
            z >>= 1;
        }
        int magnitude = Math.max(1,
                (int) Math.sqrt((long) x * x + (long) y * y + (long) z * z));
        return new Normal(x * 256 / magnitude, y * 256 / magnitude,
                z * 256 / magnitude, 1);
    }


    private int lightness(Normal normal, ObjectAppearanceView appearance) {
        return lightness(normal.x, normal.y, normal.z, normal.magnitude, appearance);
    }

    private int lightness(int normalX, int normalY, int normalZ, int normalMagnitude,
                          ObjectAppearanceView appearance) {
        int ambient = 64 + appearance.ambient();
        int contrast = 768 + appearance.contrast();
        int intensity = Math.max(1, (lighting.lightMagnitude() * contrast) >> 8);
        return ambient + (lighting.lightX() * normalX + lighting.lightY() * normalY
                + lighting.lightZ() * normalZ)
                / Math.max(1, intensity * Math.max(1, normalMagnitude));
    }

    /** Flat faces use the client face-normal denominator (1.5 × intensity). */
    private int flatLightness(Normal normal, ObjectAppearanceView appearance) {
        return flatLightness(normal.x, normal.y, normal.z, appearance);
    }

    private int flatLightness(int normalX, int normalY, int normalZ,
                              ObjectAppearanceView appearance) {
        int ambient = 64 + appearance.ambient();
        int contrast = 768 + appearance.contrast();
        int intensity = Math.max(1, (lighting.lightMagnitude() * contrast) >> 8);
        int denominator = Math.max(1, intensity + (intensity >> 1));
        return ambient + (lighting.lightX() * normalX + lighting.lightY() * normalY
                + lighting.lightZ() * normalZ) / denominator;
    }

    private static int recolor(int color, Map<Integer, Integer> replacements) {
        return replacements.getOrDefault(color, color);
    }

    private static int retexture(int texture, Map<Integer, Integer> replacements) {
        return replacements.getOrDefault(texture, texture);
    }

    private static int unsignedValueAt(short[] values, int index, int fallback) {
        return index < values.length ? values[index] & 0xFFFF : fallback;
    }

    private static int valueAt(int[] values, int index, int fallback) {
        return index < values.length ? values[index] : fallback;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }


    private static float normalized(int value, int min, int max) {
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



    private record Normal(int x, int y, int z, int magnitude) {
    }
}
