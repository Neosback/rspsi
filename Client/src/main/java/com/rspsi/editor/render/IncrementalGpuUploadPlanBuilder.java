package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Incremental flattener that caches immutable per-tile GPU fragments.
 *
 * <p>Dirty tiles still pay the face-expansion cost, but the production native
 * path no longer concatenates every fragment into giant scene-wide
 * vertex/index arrays. Instead it keeps an ordered lazy compatibility view,
 * derives the 8x8 zoned plan through non-materializing indexed access, and
 * preserves the exact global command/fingerprint contract for callers that
 * still require flat geometry.</p>
 */
public final class IncrementalGpuUploadPlanBuilder {
    private static final GpuUploadPlan EMPTY_GEOMETRY = new GpuUploadPlan(
            List.of(), List.of(), List.of(), List.of(), Map.of(), List.of(), "empty-tile-fragment");
    private static final int FRAGMENT_PARALLELISM = Math.max(1, Math.min(8,
            Runtime.getRuntime().availableProcessors() - 1));
    private static final AtomicInteger WORKER_IDS = new AtomicInteger();
    private static final ExecutorService FRAGMENT_EXECUTOR = Executors.newFixedThreadPool(
            FRAGMENT_PARALLELISM, task -> {
                Thread thread = new Thread(task,
                        "rspsi-gpu-fragment-" + WORKER_IDS.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });
    private static final ThreadLocal<GpuUploadPlanBuilder> FRAGMENT_BUILDERS =
            ThreadLocal.withInitial(GpuUploadPlanBuilder::new);

    private final Map<WorldTileAddress, TileFragment> cache = new LinkedHashMap<>();
    private IncrementalGpuZonedUploadPlanBuilder zonedBuilder =
            new IncrementalGpuZonedUploadPlanBuilder();

    public BuildResult buildInitial(GpuScenePacket packet) {
        cache.clear();
        zonedBuilder.invalidateAll();
        return build(packet, Set.of());
    }

    public BuildResult build(GpuScenePacket packet, Set<WorldZoneCoordinate> dirtyZones) {
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(dirtyZones, "dirtyZones");

        List<GpuUploadPlan> flatFragments = new ArrayList<>();
        List<GpuDrawCommand> commands = new ArrayList<>();
        List<GpuTextureTriangle> textureTriangles = new ArrayList<>();
        LinkedHashSet<SceneOccluder> occluders = new LinkedHashSet<>();
        Map<WorldTileAddress, TileFragment> nextCache = new LinkedHashMap<>();

        Map<WorldTileAddress, SceneTileSnapshot> rebuildTiles = new LinkedHashMap<>();
        int reused = 0;
        for (SceneTileSnapshot tile : packet.tiles()) {
            TileFragment fragment = cache.get(tile.worldAddress());
            boolean dirty = dirtyZones.contains(WorldZoneCoordinate.from(tile.worldAddress()));
            if (fragment == null || dirty || !sameTile(fragment.source(), tile)) {
                rebuildTiles.put(tile.worldAddress(), tile);
            } else {
                reused++;
            }
        }

        boolean parallel = FRAGMENT_PARALLELISM > 1 && rebuildTiles.size() > 1;
        Map<WorldTileAddress, CompletableFuture<TileFragment>> pending =
                parallel ? new LinkedHashMap<>() : Map.of();
        if (parallel) {
            rebuildTiles.forEach((address, tile) -> pending.put(address,
                    CompletableFuture.supplyAsync(() -> flattenTile(packet, tile),
                            FRAGMENT_EXECUTOR)));
        }

        int rebuilt = rebuildTiles.size();
        int indexBase = 0;
        try {
            for (SceneTileSnapshot tile : packet.tiles()) {
                TileFragment fragment;
                SceneTileSnapshot rebuild = rebuildTiles.get(tile.worldAddress());
                if (rebuild != null) {
                    fragment = parallel
                            ? await(pending.get(tile.worldAddress()))
                            : flattenTile(packet, rebuild);
                } else {
                    fragment = cache.get(tile.worldAddress());
                    if (fragment == null) {
                        throw new IllegalStateException(
                                "Missing cached GPU fragment for " + tile.worldAddress());
                    }
                }

                nextCache.put(tile.worldAddress(), fragment);
                GpuUploadPlan fragmentPlan = fragment.plan();
                flatFragments.add(fragmentPlan);
                append(fragment, indexBase, commands, textureTriangles, occluders);
                indexBase = Math.addExact(indexBase, fragmentPlan.indexCount());
            }
        } catch (RuntimeException | Error failure) {
            if (parallel) pending.values().forEach(future -> future.cancel(true));
            throw failure;
        }

        cache.clear();
        cache.putAll(nextCache);

        List<SceneOccluder> mergedOccluders = SceneOccluderMerger.merge(List.copyOf(occluders));
        LazyGpuFlatGeometry flatGeometry = new LazyGpuFlatGeometry(flatFragments);
        String fingerprint = GpuUploadPlanBuilder.fingerprint(
                packet.fingerprint(), flatGeometry, commands, textureTriangles,
                packet.textures(), mergedOccluders);
        GpuUploadPlan plan = GpuUploadPlan.lazy(flatGeometry, commands, textureTriangles,
                packet.textures(), mergedOccluders, fingerprint, packet.window());
        GpuZonedUploadPlan zonedPlan = zonedBuilder.build(plan, dirtyZones);
        return new BuildResult(plan, zonedPlan, rebuilt, reused,
                zonedBuilder.lastRebuiltZoneCount(), zonedBuilder.lastReusedZoneCount(),
                parallel ? rebuildTiles.size() : 0, FRAGMENT_PARALLELISM);
    }

    /** Creates an isolated cache snapshot for an asynchronous rebuild transaction. */
    public IncrementalGpuUploadPlanBuilder fork() {
        IncrementalGpuUploadPlanBuilder copy = new IncrementalGpuUploadPlanBuilder();
        copy.cache.putAll(cache);
        copy.zonedBuilder = zonedBuilder.fork();
        return copy;
    }

    public void invalidateAll() {
        cache.clear();
        zonedBuilder.invalidateAll();
    }

    public int cachedTileCount() {
        return cache.size();
    }

    private static TileFragment await(CompletableFuture<TileFragment> future) {
        try {
            return future.join();
        } catch (CompletionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw failure;
        }
    }

    private TileFragment flattenTile(GpuScenePacket packet, SceneTileSnapshot tile) {
        if (tile.terrain().isEmpty() && tile.models().isEmpty()) {
            return new TileFragment(tile, EMPTY_GEOMETRY);
        }

        // Fragment geometry only depends on the immutable tile snapshot.
        // Texture pixels/resources are attached once to the final global plan,
        // so hashing the full texture repository for every dirty tile would
        // turn a local rebuild back into O(tiles * textures) work.
        GpuScenePacket singleTile = new GpuScenePacket(
                packet.window(), List.of(tile), packet.lightingProfile(),
                "tile:" + tile.worldAddress() + ":" + tile.hashCode(), Map.of());
        return new TileFragment(tile, FRAGMENT_BUILDERS.get().build(singleTile));
    }

    private static boolean sameTile(SceneTileSnapshot cached, SceneTileSnapshot current) {
        return cached == current || cached.equals(current);
    }

    private static void append(TileFragment fragment, int indexBase,
                               List<GpuDrawCommand> commands,
                               List<GpuTextureTriangle> textureTriangles,
                               LinkedHashSet<SceneOccluder> occluders) {
        GpuUploadPlan plan = fragment.plan();
        for (GpuDrawCommand command : plan.commands()) {
            GpuDrawCommand shifted = new GpuDrawCommand(
                    command.tile(), command.scenePlane(), command.planeCullLevel(),
                    command.layer(), command.pass(),
                    indexBase + command.firstIndex(), command.indexCount(),
                    command.textureId(), command.priority(), command.depthBias(),
                    command.objectId(), command.renderMode(),
                    command.wallDecorationPresentation(), command.gameObjectSceneMetadata(),
                    command.clientRenderableBounds(), command.clientRenderablePlacements(),
                    command.contourMetadata(), command.sceneObjectIdentity(),
                    command.placementHeight(), command.modelAnchorX(), command.modelAnchorY());
            appendCommand(commands, shifted);
        }
        textureTriangles.addAll(plan.textureTriangles());
        occluders.addAll(fragment.source().occluders());
    }

    private static void appendCommand(List<GpuDrawCommand> commands, GpuDrawCommand command) {
        if (!commands.isEmpty() && command.pass() != GpuDrawCommand.SubmissionPass.ALPHA) {
            int last = commands.size() - 1;
            GpuDrawCommand previous = commands.get(last);
            if (previous.canMerge(command.tile(), command.scenePlane(), command.planeCullLevel(),
                    command.layer(), command.pass(), command.textureId(), command.priority(),
                    command.depthBias(), command.objectId(), command.firstIndex(),
                    command.renderMode(), command.wallDecorationPresentation(),
                    command.gameObjectSceneMetadata(), command.clientRenderableBounds(),
                    command.clientRenderablePlacements(), command.contourMetadata(),
                    command.sceneObjectIdentity(), command.placementHeight(),
                    command.modelAnchorX(), command.modelAnchorY())) {
                commands.set(last, previous.extend(command.indexCount()));
                return;
            }
        }
        commands.add(command);
    }

    private record TileFragment(SceneTileSnapshot source, GpuUploadPlan plan) {
        private TileFragment {
            source = Objects.requireNonNull(source, "source");
            plan = Objects.requireNonNull(plan, "plan");
        }
    }

    public record BuildResult(GpuUploadPlan plan, GpuZonedUploadPlan zonedPlan,
                              int rebuiltTiles, int reusedTiles,
                              int rebuiltZones, int reusedZones,
                              int parallelFragmentTasks, int fragmentWorkerParallelism) {
        public BuildResult {
            plan = Objects.requireNonNull(plan, "plan");
            zonedPlan = Objects.requireNonNull(zonedPlan, "zonedPlan");
            if (rebuiltTiles < 0 || reusedTiles < 0 || rebuiltZones < 0 || reusedZones < 0
                    || parallelFragmentTasks < 0 || fragmentWorkerParallelism < 1) {
                throw new IllegalArgumentException("Incremental build counts cannot be negative");
            }
            if (parallelFragmentTasks > rebuiltTiles) {
                throw new IllegalArgumentException(
                        "Parallel fragment tasks cannot exceed rebuilt tiles");
            }
        }

        /** Compatibility constructor for callers that do not consume concurrency diagnostics. */
        public BuildResult(GpuUploadPlan plan, GpuZonedUploadPlan zonedPlan,
                           int rebuiltTiles, int reusedTiles,
                           int rebuiltZones, int reusedZones) {
            this(plan, zonedPlan, rebuiltTiles, reusedTiles, rebuiltZones, reusedZones,
                    0, 1);
        }
    }
}
